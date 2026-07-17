import { createHash } from "node:crypto";
import { FieldValue, Timestamp, getFirestore } from "firebase-admin/firestore";

export const USAGE_DAY_OPTIONS = [7, 30, 90] as const;
const AGGREGATE_FIELDS = [
  "requests",
  "successfulRequests",
  "errorRequests",
  "promptTokens",
  "completionTokens",
  "totalTokens",
  "costMicros",
  "durationMs",
  "freeRequests",
  "fallbackRequests",
] as const;

export interface UsageAdminClaims {
  admin?: unknown;
  email?: string;
  email_verified?: boolean;
}

export interface UsageEventInput {
  uid: string;
  turnId: string;
  plan: string;
  logicalModel: string;
  model: string;
  provider: string;
  gateway: string;
  promptTokens?: number;
  completionTokens?: number;
  usageEstimated?: boolean;
  costMicros?: number;
  durationMs: number;
  free: boolean;
  fallback: boolean;
  fallbackCategory?: string;
  status: "success" | "error";
  agentName?: string;
}

function finiteNonNegative(value: unknown): number {
  const parsed = Number(value ?? 0);
  return Number.isFinite(parsed) ? Math.max(0, Math.round(parsed)) : 0;
}

function cleanDimension(value: unknown, fallback: string, maxLength = 160): string {
  const cleaned = String(value ?? "").trim().replace(/[\u0000-\u001f\u007f]/g, "");
  return (cleaned || fallback).slice(0, maxLength);
}

export function utcDayKey(date: Date = new Date()): string {
  return date.toISOString().slice(0, 10);
}

export function parseUsageDays(value: unknown): number {
  const parsed = Number(value);
  return USAGE_DAY_OPTIONS.includes(parsed as (typeof USAGE_DAY_OPTIONS)[number]) ? parsed : 30;
}

export function isUsageAdmin(
  claims: UsageAdminClaims,
  configuredEmails: string = process.env.ADMIN_EMAILS ?? "",
): boolean {
  if (claims.admin === true) return true;
  if (claims.email_verified !== true || !claims.email) return false;
  const allowed = new Set(configuredEmails.split(",").map((email) => email.trim().toLowerCase()).filter(Boolean));
  return allowed.has(claims.email.trim().toLowerCase());
}

export async function recordUsageEvent(input: UsageEventInput): Promise<void> {
  const db = getFirestore();
  const now = new Date();
  const day = utcDayKey(now);
  const promptTokens = finiteNonNegative(input.promptTokens);
  const completionTokens = finiteNonNegative(input.completionTokens);
  const totalTokens = promptTokens + completionTokens;
  const costMicros = finiteNonNegative(input.costMicros);
  const status = input.status === "success" ? "success" : "error";
  const provider = cleanDimension(input.provider, "Unknown");
  const model = cleanDimension(input.model, input.logicalModel || "Unknown");
  const gateway = cleanDimension(input.gateway, "Managed routing");
  const logicalModel = cleanDimension(input.logicalModel, "auto");
  const dimensionHash = createHash("sha256")
    .update(`${provider}\u0000${model}\u0000${gateway}`)
    .digest("hex")
    .slice(0, 24);

  const eventRef = db.collection("usageEvents").doc();
  const dailyRef = db.collection("usageDaily").doc(day);
  const modelRef = db.collection("usageDailyModels").doc(`${day}_${dimensionHash}`);
  const batch = db.batch();
  const increments = {
    requests: FieldValue.increment(1),
    successfulRequests: FieldValue.increment(status === "success" ? 1 : 0),
    errorRequests: FieldValue.increment(status === "error" ? 1 : 0),
    promptTokens: FieldValue.increment(promptTokens),
    completionTokens: FieldValue.increment(completionTokens),
    totalTokens: FieldValue.increment(totalTokens),
    costMicros: FieldValue.increment(costMicros),
    durationMs: FieldValue.increment(finiteNonNegative(input.durationMs)),
    freeRequests: FieldValue.increment(input.free ? 1 : 0),
    fallbackRequests: FieldValue.increment(input.fallback ? 1 : 0),
  };

  batch.set(eventRef, {
    uid: cleanDimension(input.uid, "unknown", 128),
    turnId: cleanDimension(input.turnId, "unknown", 128),
    day,
    plan: cleanDimension(input.plan, "FREE", 32),
    logicalModel,
    model,
    provider,
    gateway,
    promptTokens,
    completionTokens,
    totalTokens,
    usageEstimated: input.usageEstimated === true,
    costMicros,
    durationMs: finiteNonNegative(input.durationMs),
    free: input.free === true,
    fallback: input.fallback === true,
    fallbackCategory: input.fallbackCategory
      ? cleanDimension(input.fallbackCategory, "UNKNOWN", 40)
      : undefined,
    agentName: input.agentName ? cleanDimension(input.agentName, "", 80) : undefined,
    status,
    createdAt: FieldValue.serverTimestamp(),
  });
  batch.set(dailyRef, {
    day,
    ...increments,
    updatedAt: FieldValue.serverTimestamp(),
  }, { merge: true });
  batch.set(modelRef, {
    day,
    provider,
    model,
    gateway,
    ...increments,
    updatedAt: FieldValue.serverTimestamp(),
  }, { merge: true });
  await batch.commit();
}

function aggregateRows(rows: FirebaseFirestore.DocumentData[]): Record<string, number> {
  return rows.reduce((totals, row) => {
    for (const field of AGGREGATE_FIELDS) {
      totals[field] = (totals[field] ?? 0) + finiteNonNegative(row[field]);
    }
    return totals;
  }, Object.fromEntries(AGGREGATE_FIELDS.map((field) => [field, 0])) as Record<string, number>);
}

function rangeDays(start: Date, count: number): string[] {
  return Array.from({ length: count }, (_, offset) => {
    const date = new Date(start);
    date.setUTCDate(start.getUTCDate() + offset);
    return utcDayKey(date);
  });
}

export async function getUsageDashboard(days: number, now: Date = new Date()): Promise<Record<string, unknown>> {
  const safeDays = parseUsageDays(days);
  const end = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate()));
  const start = new Date(end);
  start.setUTCDate(end.getUTCDate() - safeDays + 1);
  const startDay = utcDayKey(start);
  const endDay = utcDayKey(end);
  const db = getFirestore();

  const [dailySnapshot, modelSnapshot, recentSnapshot] = await Promise.all([
    db.collection("usageDaily")
      .where("day", ">=", startDay)
      .where("day", "<=", endDay)
      .orderBy("day", "asc")
      .get(),
    db.collection("usageDailyModels")
      .where("day", ">=", startDay)
      .where("day", "<=", endDay)
      .orderBy("day", "asc")
      .get(),
    db.collection("usageEvents")
      .where("createdAt", ">=", Timestamp.fromDate(start))
      .orderBy("createdAt", "desc")
      .limit(100)
      .get(),
  ]);

  const dailyByDay = new Map(dailySnapshot.docs.map((doc) => [doc.id, doc.data()]));
  const daily = rangeDays(start, safeDays).map((day) => ({
    day,
    ...aggregateRows(dailyByDay.has(day) ? [dailyByDay.get(day)!] : []),
  }));
  const totals = aggregateRows(dailySnapshot.docs.map((doc) => doc.data()));

  const groupedModels = new Map<string, FirebaseFirestore.DocumentData[]>();
  for (const doc of modelSnapshot.docs) {
    const row = doc.data();
    const key = JSON.stringify([row.provider, row.model, row.gateway]);
    groupedModels.set(key, [...(groupedModels.get(key) ?? []), row]);
  }
  const models = [...groupedModels.entries()].map(([key, rows]) => {
    const [provider, model, gateway] = JSON.parse(key) as string[];
    const aggregates = aggregateRows(rows);
    return {
      provider,
      model,
      gateway,
      ...aggregates,
      costMicros: aggregates.costMicros ?? 0,
      totalTokens: aggregates.totalTokens ?? 0,
    };
  }).sort((left, right) => right.costMicros - left.costMicros || right.totalTokens - left.totalTokens);

  const groupedProviders = new Map<string, FirebaseFirestore.DocumentData[]>();
  for (const doc of modelSnapshot.docs) {
    const row = doc.data();
    const provider = cleanDimension(row.provider, "Unknown");
    groupedProviders.set(provider, [...(groupedProviders.get(provider) ?? []), row]);
  }
  const providers = [...groupedProviders.entries()].map(([provider, rows]) => {
    const aggregates = aggregateRows(rows);
    return {
      provider,
      ...aggregates,
      totalTokens: aggregates.totalTokens ?? 0,
    };
  }).sort((left, right) => right.totalTokens - left.totalTokens);

  const recent = recentSnapshot.docs.map((doc) => {
    const row = doc.data();
    const createdAt = row.createdAt instanceof Timestamp ? row.createdAt.toMillis() : null;
    const uid = String(row.uid ?? "");
    return {
      id: doc.id,
      createdAt,
      user: uid ? `${uid.slice(0, 8)}…` : "—",
      plan: cleanDimension(row.plan, "—", 32),
      agentName: row.agentName ? cleanDimension(row.agentName, "", 80) : null,
      model: cleanDimension(row.model, "Unknown"),
      provider: cleanDimension(row.provider, "Unknown"),
      gateway: cleanDimension(row.gateway, "Managed routing"),
      promptTokens: finiteNonNegative(row.promptTokens),
      completionTokens: finiteNonNegative(row.completionTokens),
      totalTokens: finiteNonNegative(row.totalTokens),
      usageEstimated: row.usageEstimated === true,
      costMicros: finiteNonNegative(row.costMicros),
      durationMs: finiteNonNegative(row.durationMs),
      free: row.free === true,
      fallback: row.fallback === true,
      fallbackCategory: row.fallbackCategory
        ? cleanDimension(row.fallbackCategory, "UNKNOWN", 40)
        : null,
      status: row.status === "success" ? "success" : "error",
    };
  });

  return {
    generatedAt: Date.now(),
    range: { days: safeDays, startDay, endDay, timezone: "UTC" },
    totals,
    daily,
    models,
    providers,
    recent,
  };
}
