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
import { calculateCost, callFree, callPaid } from "./upstream";
import { sanitizeForFree, validateFreeRequest } from "./privacy";
import {
  EntitlementError,
  authorizeOperation,
  claimFreeTurn,
  findPurchaseBinding,
  getAccount,
  publicModels,
  recordCost,
  revokePlan,
} from "./store";

initializeApp();
getFirestore().settings({ ignoreUndefinedProperties: true });
setGlobalOptions({ region: "us-central1", timeoutSeconds: 540, memory: "1GiB", maxInstances: 20 });

const vercelKey = defineSecret("VERCEL_AI_GATEWAY_KEY");
const openRouterKey = defineSecret("OPENROUTER_API_KEY");
const kiloKey = defineSecret("KILO_GATEWAY_API_KEY");
const openCodeKey = defineSecret("OPENCODE_API_KEY");

interface AuthenticatedRequest extends Request { uid: string }
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
      (req as AuthenticatedRequest).uid = decoded.uid;
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

app.post("/v1/turns/start", asyncRoute(async (req, res) => {
  const account = await getAccount((req as AuthenticatedRequest).uid);
  res.json({ accepted: true, plan: account.plan, freeMessagesUsed: account.freeMessagesUsed });
}));

app.post("/v1/inference/chat", asyncRoute(async (req, res) => {
  const uid = (req as AuthenticatedRequest).uid;
  const turnId = String(req.body?.turnId ?? "");
  const logicalModel = String(req.body?.logicalModel ?? "auto");
  const authorization = await authorizeOperation(uid, turnId, logicalModel, req.body);

  const freeSecrets = {
    openRouter: openRouterKey.value() || undefined,
    kilo: kiloKey.value() || undefined,
    openCode: openCodeKey.value() || undefined,
  };

  const runFree = async () => {
    const privacy = validateFreeRequest(req.body);
    if (!privacy.safe) throw new EntitlementError(422, privacy.reason ?? "Esta solicitud no puede usar la ruta gratuita");
    await claimFreeTurn(uid, turnId);
    const safeBody = sanitizeForFree(req.body);
    return callFree(safeBody, freeSecrets, authorization.account.plan === "PLUS");
  };

  if (authorization.useFree || !authorization.model) {
    const result = await runFree();
    res.json({ response: result.response, usage: { free: true } });
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
    res.json({ response: freeResult.response, usage: { free: true, fallback: true } });
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
