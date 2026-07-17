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
import {
  calculateCost,
  callFree,
  callPaid,
  estimateFreeTokenReservation,
  fallbackReason,
  isRetryableUpstreamError,
  providerNameForModel,
  UpstreamFailureCategory,
  UpstreamResult,
} from "./upstream";
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
import {
  getUsageDashboard,
  isUsageAdmin,
  parseUsageDays,
  recordUsageEvent,
} from "./usage";
import {
  getAppUsageDashboard,
  parseClientAppUsageEvent,
  recordAppUsageEvent,
} from "./app-usage";

initializeApp();
getFirestore().settings({ ignoreUndefinedProperties: true });
setGlobalOptions({ region: "us-central1", timeoutSeconds: 540, memory: "1GiB", maxInstances: 20 });

const vercelKey = defineSecret("VERCEL_AI_GATEWAY_KEY");
const openRouterKey = defineSecret("OPENROUTER_API_KEY");
const kiloKey = defineSecret("KILO_GATEWAY_API_KEY");
const openCodeKey = defineSecret("OPENCODE_API_KEY");
const adminEmails = defineSecret("ADMIN_EMAILS");

interface AuthenticatedRequest extends Request {
  uid: string;
  signInProvider?: string;
  email?: string;
  emailVerified?: boolean;
  admin?: unknown;
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
      authenticated.email = decoded.email;
      authenticated.emailVerified = decoded.email_verified;
      authenticated.admin = decoded.admin;
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

app.get("/v1/admin/usage", asyncRoute(async (req, res) => {
  const authenticated = req as AuthenticatedRequest;
  if (!isUsageAdmin({
    admin: authenticated.admin,
    email: authenticated.email,
    email_verified: authenticated.emailVerified,
  }, adminEmails.value())) {
    throw new EntitlementError(403, "Esta cuenta no tiene acceso al panel de uso");
  }
  res.set("Cache-Control", "private, no-store");
  res.json(await getUsageDashboard(parseUsageDays(req.query.days)));
}));

app.get("/v1/admin/app-usage", asyncRoute(async (req, res) => {
  const authenticated = req as AuthenticatedRequest;
  if (!isUsageAdmin({
    admin: authenticated.admin,
    email: authenticated.email,
    email_verified: authenticated.emailVerified,
  }, adminEmails.value())) {
    throw new EntitlementError(403, "Esta cuenta no tiene acceso al panel de uso");
  }
  res.set("Cache-Control", "private, no-store");
  res.json(await getAppUsageDashboard(parseUsageDays(req.query.days)));
}));

app.post("/v1/app-usage", asyncRoute(async (req, res) => {
  let event;
  try {
    event = parseClientAppUsageEvent(req.body);
  } catch (error) {
    throw new EntitlementError(400, error instanceof Error ? error.message : "Evento de uso inválido");
  }
  await recordAppUsageEvent({
    ...(event),
    uid: (req as AuthenticatedRequest).uid,
  });
  res.status(202).json({ accepted: true });
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
  const startedAt = Date.now();
  const authorization = await authorizeOperation(
    uid,
    turnId,
    logicalModel,
    req.body,
    (req as AuthenticatedRequest).signInProvider,
  );
  const agentName = typeof req.body?.assistantName === "string" ? req.body.assistantName : undefined;
  const usageOperation = Array.isArray(req.body?.tools) && req.body.tools.length > 0
    ? "chat_with_tools" as const
    : "chat" as const;
  const trackSuccess = async (
    result: UpstreamResult,
    free: boolean,
    fallback: boolean,
    costMicros = 0,
    fallbackCategory?: UpstreamFailureCategory | "BUDGET_LIMIT",
  ) => {
    const durationMs = Date.now() - startedAt;
    await Promise.all([
      recordUsageEvent({
        uid,
        turnId,
        plan: authorization.account.plan,
        logicalModel,
        model: result.upstream.model,
        provider: result.upstream.provider,
        gateway: result.upstream.gateway,
        promptTokens: result.usage.promptTokens,
        completionTokens: result.usage.completionTokens,
        usageEstimated: result.usage.estimated,
        costMicros,
        durationMs,
        free,
        fallback,
        fallbackCategory,
        status: "success",
        agentName,
      }).catch(() => undefined),
      recordAppUsageEvent({
        uid,
        source: "subscription",
        provider: result.upstream.provider,
        model: result.upstream.model,
        promptTokens: result.usage.promptTokens,
        completionTokens: result.usage.completionTokens,
        usageEstimated: result.usage.estimated,
        durationMs,
        status: "success",
        operation: usageOperation,
      }).catch(() => undefined),
    ]);
  };

  const publicUsage = (
    result: UpstreamResult,
    options: {
      free: boolean;
      costMicros?: number;
      fallback?: boolean;
      fallbackCategory?: UpstreamFailureCategory | "BUDGET_LIMIT";
    },
  ) => {
    const category = options.fallbackCategory ?? result.upstream.fallbackCategory;
    return {
      promptTokens: result.usage.promptTokens,
      completionTokens: result.usage.completionTokens,
      totalTokens: result.usage.promptTokens + result.usage.completionTokens,
      estimated: result.usage.estimated,
      costMicros: options.costMicros ?? 0,
      free: options.free,
      requestedModel: logicalModel,
      modelUsed: result.upstream.model,
      provider: result.upstream.provider,
      gateway: result.upstream.gateway,
      fallback: options.fallback === true || result.upstream.fallback,
      fallbackCategory: category,
      fallbackReason: category === "BUDGET_LIMIT"
        ? "Se agotó el presupuesto mensual del plan"
        : category ? fallbackReason(category) : undefined,
    };
  };

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

  try {
    if (authorization.useFree || !authorization.model) {
      const result = await runFree();
      const budgetFallback = authorization.freeReason === "BUDGET_EXHAUSTED";
      const category = budgetFallback ? "BUDGET_LIMIT" : result.upstream.fallbackCategory;
      const fallback = budgetFallback || result.upstream.fallback;
      await trackSuccess(result, true, fallback, 0, category);
      res.json({
        response: result.response,
        usage: publicUsage(result, { free: true, fallback, fallbackCategory: category }),
      });
      return;
    }

    try {
      const result = await callPaid(authorization.model, req.body, vercelKey.value());
      const costMicros = calculateCost(authorization.model, result.usage);
      await recordCost(uid, costMicros);
      await trackSuccess(result, false, false, costMicros);
      res.json({ response: result.response, usage: publicUsage(result, { costMicros, free: false }) });
      return;
    } catch (primaryError) {
      if (!isRetryableUpstreamError(primaryError)) throw primaryError;
      const primaryCategory = primaryError.category;
      for (const fallbackModel of paidFallbacks(authorization.account.plan, authorization.model.id)) {
        try {
          const result = await callPaid(fallbackModel, req.body, vercelKey.value());
          const costMicros = calculateCost(fallbackModel, result.usage);
          await recordCost(uid, costMicros);
          await trackSuccess(result, false, true, costMicros, primaryCategory);
          res.json({
            response: result.response,
            usage: publicUsage(result, {
              costMicros,
              free: false,
              fallback: true,
              fallbackCategory: primaryCategory,
            }),
          });
          return;
        } catch (fallbackError) {
          // A malformed request, invalid credential or denied request will fail for every
          // model on the same route. Retrying it would waste quota and duplicate work.
          if (!isRetryableUpstreamError(fallbackError)) throw fallbackError;
        }
      }
      const freeResult = await runFree();
      await trackSuccess(freeResult, true, true, 0, primaryCategory);
      res.json({
        response: freeResult.response,
        usage: publicUsage(freeResult, {
          free: true,
          fallback: true,
          fallbackCategory: primaryCategory,
        }),
      });
    }
  } catch (error) {
    const failedModel = authorization.model?.vercelModel ?? logicalModel;
    const failedProvider = providerNameForModel(failedModel, "Managed routing");
    const durationMs = Date.now() - startedAt;
    await Promise.all([
      recordUsageEvent({
        uid,
        turnId,
        plan: authorization.account.plan,
        logicalModel,
        model: failedModel,
        provider: failedProvider,
        gateway: authorization.model ? "Vercel AI Gateway" : "Managed routing",
        durationMs,
        free: authorization.useFree,
        fallback: false,
        status: "error",
        agentName,
      }).catch(() => undefined),
      recordAppUsageEvent({
        uid,
        source: "subscription",
        provider: failedProvider,
        model: failedModel,
        durationMs,
        status: "error",
        operation: usageOperation,
      }).catch(() => undefined),
    ]);
    throw error;
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
  secrets: [vercelKey, openRouterKey, kiloKey, openCodeKey, adminEmails],
  cors: false,
}, app);

export const googlePlayRtdn = onMessagePublished(
  { topic: "play-billing-rtdn", region: "us-central1", retry: true },
  async (event) => {
    const notification = event.data.message.json;
    await processRtdn(notification);
  },
);
