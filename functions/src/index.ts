import express, { NextFunction, Request, Response } from "express";
import { initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";
import { defineSecret } from "firebase-functions/params";
import { onRequest } from "firebase-functions/v2/https";
import { onMessagePublished } from "firebase-functions/v2/pubsub";
import { setGlobalOptions } from "firebase-functions/v2";
import { verifyAndGrantPurchase, tokenHash } from "./billing";
import { paidFallbacks } from "./catalog";
import { prepareConsentedFreeRequest } from "./consent";
import { calculateCost, callFree, callPaid, estimateFreeTokenReservation } from "./upstream";
import {
  acceptFreeDataConsent,
  EntitlementError,
  authorizeOperation,
  findPurchaseBinding,
  getAccount,
  publicModels,
  recordCost,
  reserveFreeTokens,
  requireFreeDataConsent,
  revokePlan,
  settleFreeTokens,
} from "./store";

initializeApp();
getFirestore().settings({ ignoreUndefinedProperties: true });
setGlobalOptions({ region: "us-central1", timeoutSeconds: 540, memory: "1GiB", maxInstances: 20 });

const vercelKey = defineSecret("VERCEL_AI_GATEWAY_KEY");
const openRouterKey = defineSecret("OPENROUTER_API_KEY");
const kiloKey = defineSecret("KILO_GATEWAY_API_KEY");
const openCodeKey = defineSecret("OPENCODE_API_KEY");

interface AuthenticatedRequest extends Request {
  uid: string;
  signInProvider?: string;
}
const app = express();
app.disable("x-powered-by");
app.use(express.json({ limit: "12mb" }));

const asyncRoute = (handler: (req: Request, res: Response) => Promise<void>) =>
  (req: Request, res: Response, next: NextFunction) => handler(req, res).catch(next);

app.use((req, _res, next) => {
  const authorization = req.header("authorization") ?? "";
  if (!authorization.startsWith("Bearer ")) {
    next(new EntitlementError(401, "Sesión requerida"));
    return;
  }
  getAuth().verifyIdToken(authorization.slice(7), true)
    .then((decoded) => {
      const authenticated = req as AuthenticatedRequest;
      authenticated.uid = decoded.uid;
      authenticated.signInProvider = decoded.firebase?.sign_in_provider;
      next();
    })
    .catch(next);
});

app.get("/v1/account", asyncRoute(async (req, res) => {
  res.json(await getAccount((req as AuthenticatedRequest).uid));
}));

app.get("/v1/models", asyncRoute(async (req, res) => {
  const account = await getAccount((req as AuthenticatedRequest).uid);
  res.json(publicModels(account.plan));
}));

app.post("/v1/free-data-consent", asyncRoute(async (req, res) => {
  const authenticated = req as AuthenticatedRequest;
  res.json(await acceptFreeDataConsent(
    authenticated.uid,
    authenticated.signInProvider,
    req.body?.accepted,
    req.body?.version,
  ));
}));

app.post("/v1/turns/start", asyncRoute(async (req, res) => {
  const account = await getAccount((req as AuthenticatedRequest).uid);
  res.json({ accepted: true, plan: account.plan, freeTokensUsed: account.freeTokensUsed });
}));

app.post("/v1/inference/chat", asyncRoute(async (req, res) => {
  const uid = (req as AuthenticatedRequest).uid;
  const turnId = String(req.body?.turnId ?? "");
  const logicalModel = String(req.body?.logicalModel ?? "auto");
  const authorization = await authorizeOperation(
    uid,
    turnId,
    logicalModel,
    req.body,
    (req as AuthenticatedRequest).signInProvider,
  );

  const freeSecrets = {
    openRouter: openRouterKey.value() || undefined,
    kilo: kiloKey.value() || undefined,
    openCode: openCodeKey.value() || undefined,
  };

  const runFree = async () => {
    requireFreeDataConsent(true, authorization.account.freeDataConsentVersion);
    const consentedBody = prepareConsentedFreeRequest(req.body);
    const reservation = await reserveFreeTokens(uid, estimateFreeTokenReservation(consentedBody));
    let upstreamCompleted = false;
    try {
      const result = await callFree(consentedBody, freeSecrets, authorization.account.plan === "PLUS");
      upstreamCompleted = true;
      await settleFreeTokens(
        uid,
        reservation,
        result.usage.promptTokens + result.usage.completionTokens,
      );
      return result;
    } catch (error) {
      if (!upstreamCompleted) {
        await settleFreeTokens(uid, reservation, 0).catch(() => undefined);
      }
      throw error;
    }
  };

  if (authorization.useFree || !authorization.model) {
    const result = await runFree();
    res.json({ response: result.response, usage: { ...result.usage, free: true } });
    return;
  }

  try {
    const result = await callPaid(authorization.model, req.body, vercelKey.value());
    const costMicros = calculateCost(authorization.model, result.usage);
    await recordCost(uid, costMicros);
    res.json({ response: result.response, usage: { costMicros, free: false } });
  } catch {
    for (const fallbackModel of paidFallbacks(authorization.account.plan, authorization.model.id)) {
      try {
        const result = await callPaid(fallbackModel, req.body, vercelKey.value());
        const costMicros = calculateCost(fallbackModel, result.usage);
        await recordCost(uid, costMicros);
        res.json({
          response: result.response,
          usage: { costMicros, free: false, fallback: true, fallbackModel: fallbackModel.displayName },
        });
        return;
      } catch {
        // Continue to a lower model authorized by the same plan.
      }
    }
    const freeResult = await runFree();
    res.json({ response: freeResult.response, usage: { ...freeResult.usage, free: true, fallback: true } });
  }
}));

app.post("/v1/billing/google-play/verify", asyncRoute(async (req, res) => {
  const account = await verifyAndGrantPurchase(
    (req as AuthenticatedRequest).uid,
    String(req.body?.productId ?? ""),
    String(req.body?.purchaseToken ?? ""),
    String(req.body?.packageName ?? ""),
  );
  res.json(account);
}));

async function processRtdn(notification: any): Promise<void> {
  const purchaseToken = String(notification?.subscriptionNotification?.purchaseToken ?? "");
  if (!purchaseToken) return;
  const binding = await findPurchaseBinding(tokenHash(purchaseToken));
  if (!binding) return;

  try {
    // RTDN is only a change signal. Always ask Google Play for the current state.
    await verifyAndGrantPurchase(
      binding.uid,
      binding.productId,
      purchaseToken,
      "com.aiagents.app",
    );
  } catch (error) {
    if (error instanceof EntitlementError && error.status === 403) {
      await revokePlan(binding.uid);
      return;
    }
    throw error;
  }
}

app.use((error: unknown, _req: Request, res: Response, _next: NextFunction) => {
  if (error instanceof EntitlementError) {
    res.status(error.status).json({ error: error.message });
    return;
  }
  // Never include upstream bodies, credentials, prompts, responses, or provider identity.
  res.status(503).json({ error: "El servicio de IA no está disponible en este momento" });
});

export const api = onRequest({
  secrets: [vercelKey, openRouterKey, kiloKey, openCodeKey],
  cors: false,
}, app);

export const googlePlayRtdn = onMessagePublished(
  { topic: "play-billing-rtdn", region: "us-central1", retry: true },
  async (event) => {
    const notification = event.data.message.json;
    await processRtdn(notification);
  },
);
