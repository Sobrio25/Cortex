import { FieldValue, Timestamp, getFirestore } from "firebase-admin/firestore";
import { MODELS, PLANS, PlanId, ModelDefinition, resolveModel } from "./catalog";

export interface AccountState {
  plan: PlanId;
  freeMessagesUsed: number;
  freeMessagesLimit: number;
  freePeriod: string;
  spentMicros: number;
  budgetMicros: number;
  periodEndEpochMillis?: number;
  productId?: string;
}

export interface OperationAuthorization {
  account: AccountState;
  useFree: boolean;
  model?: ModelDefinition;
}

export class EntitlementError extends Error {
  constructor(public status: number, message: string) {
    super(message);
  }
}

const db = () => getFirestore();
const monthKey = () => new Date().toISOString().slice(0, 7);

function normalize(raw: FirebaseFirestore.DocumentData | undefined): AccountState {
  const now = Date.now();
  let plan = (raw?.plan && PLANS[raw.plan as PlanId] ? raw.plan : "FREE") as PlanId;
  const periodEndEpochMillis = raw?.periodEndEpochMillis ? Number(raw.periodEndEpochMillis) : undefined;
  if (plan !== "FREE" && periodEndEpochMillis && periodEndEpochMillis <= now) plan = "FREE";
  const currentPeriod = monthKey();
  return {
    plan,
    freeMessagesUsed: raw?.freePeriod === currentPeriod ? Number(raw?.freeMessagesUsed ?? 0) : 0,
    freeMessagesLimit: 30,
    freePeriod: currentPeriod,
    spentMicros: plan === "FREE" ? 0 : Number(raw?.spentMicros ?? 0),
    budgetMicros: PLANS[plan].budgetMicros,
    periodEndEpochMillis: plan === "FREE" ? undefined : periodEndEpochMillis,
    productId: plan === "FREE" ? undefined : raw?.productId,
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
    const model = useFree ? undefined : resolveModel(account.plan, requestedModel, requestBody);
    if (!useFree && !model) throw new EntitlementError(403, "Este modelo no está incluido en tu plan");

    transaction.set(accountRef, { ...account, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
    transaction.set(turnRef, {
      operations: operations + 1,
      updatedAt: FieldValue.serverTimestamp(),
      createdAt: turnSnapshot.exists ? turnSnapshot.data()?.createdAt : FieldValue.serverTimestamp(),
    }, { merge: true });
    return { account, useFree, model };
  });
}

export async function claimFreeTurn(uid: string, turnId: string): Promise<void> {
  const accountRef = db().collection("accounts").doc(uid);
  const turnRef = accountRef.collection("turns").doc(turnId);
  await db().runTransaction(async (transaction) => {
    const [accountSnapshot, turnSnapshot] = await Promise.all([
      transaction.get(accountRef),
      transaction.get(turnRef),
    ]);
    if (turnSnapshot.data()?.freeClaimed === true) return;
    const account = normalize(accountSnapshot.data());
    if (account.freeMessagesUsed >= account.freeMessagesLimit) {
      throw new EntitlementError(429, "Alcanzaste tus 30 mensajes gratuitos de este mes");
    }
    transaction.set(accountRef, {
      ...account,
      freeMessagesUsed: account.freeMessagesUsed + 1,
      updatedAt: FieldValue.serverTimestamp(),
    }, { merge: true });
    transaction.set(turnRef, { freeClaimed: true, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
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
    .filter((model) => PLANS[model.minimumPlan].rank <= rank && (rank >= PLANS.PRO.rank || model.id === "auto"))
    .map((model) => ({
      id: model.id,
      displayName: model.displayName,
      minimumPlan: model.minimumPlan,
      contextWindow: model.contextWindow,
      supportsVision: model.supportsVision ?? false,
      available: true,
    }));
}
