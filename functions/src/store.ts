import { FieldValue, Timestamp, getFirestore } from "firebase-admin/firestore";
import { MODELS, PLANS, PlanId, ModelDefinition, resolveModel } from "./catalog";
import { FREE_DATA_CONSENT_VERSION, hasCurrentFreeDataConsent } from "./consent";

export interface AccountState {
  plan: PlanId;
  freeTokensUsed: number;
  freeTokensLimit: number;
  freePeriod: string;
  spentMicros: number;
  budgetMicros: number;
  periodEndEpochMillis?: number;
  productId?: string;
  freeDataConsentVersion: number;
  freeDataConsentRequiredVersion: number;
}

export interface OperationAuthorization {
  account: AccountState;
  useFree: boolean;
  model?: ModelDefinition;
  freeReason?: "PLAN_FREE" | "BUDGET_EXHAUSTED";
}

export class EntitlementError extends Error {
  constructor(public status: number, message: string) {
    super(message);
  }
}

export function requireGoogleSignInForFree(plan: PlanId, signInProvider?: string): void {
  if (plan === "FREE" && signInProvider !== "google.com") {
    throw new EntitlementError(403, "Inicia sesión con Google para usar el plan Gratis");
  }
}

export function requireFreeDataConsent(useFree: boolean, acceptedVersion: unknown): void {
  if (useFree && !hasCurrentFreeDataConsent(acceptedVersion)) {
    throw new EntitlementError(
      428,
      "Lee y acepta el aviso sobre el procesamiento de datos antes de usar el plan Gratis",
    );
  }
}

const db = () => getFirestore();
export const FREE_TOKENS_LIMIT = 500_000;

/** ISO-8601 week in UTC. Weeks start on Monday and belong to the year of Thursday. */
export function quotaPeriodKey(now: Date = new Date()): string {
  const thursday = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate()));
  const isoDay = thursday.getUTCDay() || 7;
  thursday.setUTCDate(thursday.getUTCDate() + 4 - isoDay);
  const isoYear = thursday.getUTCFullYear();
  const yearStart = new Date(Date.UTC(isoYear, 0, 1));
  const week = Math.ceil(((thursday.getTime() - yearStart.getTime()) / 86_400_000 + 1) / 7);
  return `${isoYear}-W${String(week).padStart(2, "0")}`;
}

function normalize(raw: FirebaseFirestore.DocumentData | undefined): AccountState {
  const now = Date.now();
  let plan = (raw?.plan && PLANS[raw.plan as PlanId] ? raw.plan : "FREE") as PlanId;
  const periodEndEpochMillis = raw?.periodEndEpochMillis ? Number(raw.periodEndEpochMillis) : undefined;
  if (plan !== "FREE" && periodEndEpochMillis && periodEndEpochMillis <= now) plan = "FREE";
  const currentPeriod = quotaPeriodKey();
  const storedFreeTokens = Number(raw?.freeTokensUsed ?? 0);
  return {
    plan,
    freeTokensUsed: raw?.freePeriod === currentPeriod && Number.isFinite(storedFreeTokens)
      ? Math.max(0, storedFreeTokens) : 0,
    freeTokensLimit: FREE_TOKENS_LIMIT,
    freePeriod: currentPeriod,
    spentMicros: plan === "FREE" ? 0 : Number(raw?.spentMicros ?? 0),
    budgetMicros: PLANS[plan].budgetMicros,
    periodEndEpochMillis: plan === "FREE" ? undefined : periodEndEpochMillis,
    productId: plan === "FREE" ? undefined : raw?.productId,
    freeDataConsentVersion: Number(raw?.freeDataConsentVersion ?? 0),
    freeDataConsentRequiredVersion: FREE_DATA_CONSENT_VERSION,
  };
}

export async function getAccount(uid: string): Promise<AccountState> {
  const ref = db().collection("accounts").doc(uid);
  return db().runTransaction(async (transaction) => {
    const snapshot = await transaction.get(ref);
    const account = normalize(snapshot.data());
    transaction.set(ref, { ...account, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
    return account;
  });
}

export async function authorizeOperation(
  uid: string,
  turnId: string,
  requestedModel: string,
  requestBody: unknown,
  signInProvider?: string,
): Promise<OperationAuthorization> {
  if (!/^[a-f0-9]{16,64}$/i.test(turnId)) throw new EntitlementError(400, "Identificador de turno inválido");
  const accountRef = db().collection("accounts").doc(uid);
  const turnRef = accountRef.collection("turns").doc(turnId);
  return db().runTransaction(async (transaction) => {
    const [accountSnapshot, turnSnapshot] = await Promise.all([
      transaction.get(accountRef),
      transaction.get(turnRef),
    ]);
    const account = normalize(accountSnapshot.data());
    const operations = Number(turnSnapshot.data()?.operations ?? 0);
    if (operations >= 25) throw new EntitlementError(429, "Este mensaje alcanzó el máximo de operaciones de IA");

    const useFree = account.plan === "FREE" || account.spentMicros >= account.budgetMicros;
    if (account.plan === "FREE") requireGoogleSignInForFree(account.plan, signInProvider);
    requireFreeDataConsent(useFree, account.freeDataConsentVersion);
    const model = useFree ? undefined : resolveModel(account.plan, requestedModel, requestBody);
    if (!useFree && !model) throw new EntitlementError(403, "Este modelo no está incluido en tu plan");

    transaction.set(accountRef, { ...account, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
    transaction.set(turnRef, {
      operations: operations + 1,
      updatedAt: FieldValue.serverTimestamp(),
      createdAt: turnSnapshot.exists ? turnSnapshot.data()?.createdAt : FieldValue.serverTimestamp(),
    }, { merge: true });
    return {
      account,
      useFree,
      model,
      freeReason: useFree
        ? (account.plan === "FREE" ? "PLAN_FREE" : "BUDGET_EXHAUSTED")
        : undefined,
    };
  });
}

export async function acceptFreeDataConsent(
  uid: string,
  signInProvider: string | undefined,
  accepted: unknown,
  version: unknown,
): Promise<AccountState> {
  if (signInProvider !== "google.com") {
    throw new EntitlementError(403, "Inicia sesión con Google para activar el plan Gratis");
  }
  if (accepted !== true || Number(version) !== FREE_DATA_CONSENT_VERSION) {
    throw new EntitlementError(400, "Debes confirmar que leíste el aviso vigente del plan Gratis");
  }

  const accountRef = db().collection("accounts").doc(uid);
  return db().runTransaction(async (transaction) => {
    const snapshot = await transaction.get(accountRef);
    const account = normalize(snapshot.data());
    const next = { ...account, freeDataConsentVersion: FREE_DATA_CONSENT_VERSION };
    const existingAcceptedAt = snapshot.data()?.freeDataConsentAcceptedAt;
    transaction.set(accountRef, {
      ...next,
      freeDataConsentAcceptedAt: existingAcceptedAt ?? FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    }, { merge: true });
    return next;
  });
}

export interface FreeTokenReservation {
  tokens: number;
  period: string;
}

/**
 * Reserves at most the account's remaining allowance.
 *
 * The request estimate is intentionally conservative, so rejecting whenever the estimate is
 * larger than the remaining balance can strand usable quota below 100%. Reserving the remainder
 * lets one final request run while the Firestore transaction still prevents concurrent requests
 * from exceeding the visible weekly allowance.
 */
export function freeTokenReservationAmount(
  usedTokens: number,
  tokenLimit: number,
  requestedTokens: number,
): number {
  const requested = Math.max(1, Math.ceil(requestedTokens));
  if (!Number.isFinite(requested)) {
    throw new EntitlementError(429, "No se pudo calcular la cuota necesaria para esta solicitud");
  }
  const limit = Math.max(0, Math.floor(tokenLimit));
  const used = Math.min(limit, Math.max(0, Math.ceil(usedTokens)));
  const remaining = limit - used;
  if (remaining <= 0) {
    throw new EntitlementError(429, "Alcanzaste tus 500,000 tokens gratuitos de esta semana");
  }
  return Math.min(requested, remaining);
}

export async function reserveFreeTokens(uid: string, requestedTokens: number): Promise<FreeTokenReservation> {
  const accountRef = db().collection("accounts").doc(uid);
  return db().runTransaction(async (transaction) => {
    const accountSnapshot = await transaction.get(accountRef);
    const account = normalize(accountSnapshot.data());
    const tokens = freeTokenReservationAmount(
      account.freeTokensUsed,
      account.freeTokensLimit,
      requestedTokens,
    );
    transaction.set(accountRef, {
      ...account,
      freeTokensUsed: account.freeTokensUsed + tokens,
      updatedAt: FieldValue.serverTimestamp(),
    }, { merge: true });
    return { tokens, period: account.freePeriod };
  });
}

export async function settleFreeTokens(
  uid: string,
  reservation: FreeTokenReservation,
  actualTokens: number,
): Promise<void> {
  const roundedActual = Math.ceil(actualTokens);
  const actual = Number.isFinite(roundedActual) ? Math.max(0, roundedActual) : reservation.tokens;
  const accountRef = db().collection("accounts").doc(uid);
  await db().runTransaction(async (transaction) => {
    const snapshot = await transaction.get(accountRef);
    const raw = snapshot.data();
    // A request that crosses the weekly boundary stays charged to the week in which it began.
    if (raw?.freePeriod !== reservation.period) return;
    const storedUsed = Number(raw?.freeTokensUsed ?? reservation.tokens);
    const used = Number.isFinite(storedUsed) ? Math.max(0, storedUsed) : reservation.tokens;
    transaction.set(accountRef, {
      freeTokensUsed: Math.min(
        FREE_TOKENS_LIMIT,
        Math.max(0, used - reservation.tokens + actual),
      ),
      updatedAt: FieldValue.serverTimestamp(),
    }, { merge: true });
  });
}

export async function recordCost(uid: string, costMicros: number): Promise<void> {
  if (!Number.isFinite(costMicros) || costMicros <= 0) return;
  await db().collection("accounts").doc(uid).set({
    spentMicros: FieldValue.increment(Math.ceil(costMicros)),
    updatedAt: FieldValue.serverTimestamp(),
  }, { merge: true });
}

export async function grantPlan(
  uid: string,
  plan: PlanId,
  periodEndEpochMillis: number,
  purchaseTokenHash: string,
  productId: string,
): Promise<AccountState> {
  const accountRef = db().collection("accounts").doc(uid);
  const tokenRef = db().collection("purchaseTokens").doc(purchaseTokenHash);
  return db().runTransaction(async (transaction) => {
    const current = normalize((await transaction.get(accountRef)).data());
    const next: AccountState = {
      ...current,
      plan,
      budgetMicros: PLANS[plan].budgetMicros,
      periodEndEpochMillis,
      productId,
      // Upgrades keep spend from the active cycle, renewals reset after the previous end.
      spentMicros: (
        current.productId === productId &&
        current.periodEndEpochMillis &&
        periodEndEpochMillis > current.periodEndEpochMillis + 24 * 60 * 60 * 1000
      ) || (current.periodEndEpochMillis && current.periodEndEpochMillis < Date.now())
        ? 0 : current.spentMicros,
    };
    transaction.set(accountRef, { ...next, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
    transaction.set(tokenRef, {
      uid,
      productId,
      periodEndEpochMillis,
      updatedAt: FieldValue.serverTimestamp(),
      expiresAt: Timestamp.fromMillis(periodEndEpochMillis + 90 * 24 * 60 * 60 * 1000),
    }, { merge: true });
    return next;
  });
}

export async function findPurchaseBinding(
  purchaseTokenHash: string,
): Promise<{ uid: string; productId: string } | undefined> {
  const data = (await db().collection("purchaseTokens").doc(purchaseTokenHash).get()).data();
  if (!data?.uid || !data?.productId) return undefined;
  return { uid: String(data.uid), productId: String(data.productId) };
}

export async function revokePlan(uid: string): Promise<void> {
  await db().collection("accounts").doc(uid).set({
    plan: "FREE",
    budgetMicros: 0,
    spentMicros: 0,
    periodEndEpochMillis: FieldValue.delete(),
    productId: FieldValue.delete(),
    updatedAt: FieldValue.serverTimestamp(),
  }, { merge: true });
}

export function publicModels(plan: PlanId): Array<Record<string, unknown>> {
  const rank = PLANS[plan].rank;
  return Object.values(MODELS)
    .filter((model) => PLANS[model.minimumPlan].rank <= rank)
    .map((model) => ({
      id: model.id,
      displayName: model.displayName,
      minimumPlan: model.minimumPlan,
      contextWindow: model.contextWindow,
      capabilities: model.capabilities,
      pricing: model.pricing,
      available: true,
      selectable: rank >= PLANS.PRO.rank || model.id === "auto",
    }));
}
