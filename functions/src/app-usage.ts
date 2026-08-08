import { createHash } from "node:crypto";
import { FieldValue, Timestamp, getFirestore } from "firebase-admin/firestore";
import { parseUsageDays, utcDayKey } from "./usage";
import { userHash } from "./privacy";

const APP_USAGE_FIELDS = [
  "requests",
  "successfulRequests",
  "errorRequests",
  "promptTokens",
  "completionTokens",
  "totalTokens",
  "durationMs",
  "subscriptionRequests",
  "byokRequests",
  "localRequests",
  "estimatedRequests",
] as const;

export type AppUsageSource = "subscription" | "byok" | "local";
export type AppUsageOperation = "chat" | "chat_with_tools" | "stream";

export interface AppUsageEventInput {
  uid: string;
  eventId?: string;
  source: AppUsageSource;
  provider: string;
  model: string;
  promptTokens?: number;
  completionTokens?: number;
  usageEstimated?: boolean;
  durationMs: number;
  status: "success" | "error";
  operation: AppUsageOperation;
  appVersion?: string;
}

export type ClientAppUsageEvent = Omit<AppUsageEventInput, "uid" | "source"> & {
  eventId: string;
  source: "byok" | "local";
};

function finiteNonNegative(value: unknown, maximum = Number.MAX_SAFE_INTEGER): number {
  const parsed = Number(value ?? 0);
  return Number.isFinite(parsed) ? Math.min(maximum, Math.max(0, Math.round(parsed))) : 0;
}

function cleanDimension(value: unknown, fallback: string, maxLength = 160): string {
  const cleaned = String(value ?? "").trim().replace(/[\u0000-\u001f\u007f]/g, "");
  return (cleaned || fallback).slice(0, maxLength);
}

function requireDimension(value: unknown, name: string, maxLength: number): string {
  const cleaned = cleanDimension(value, "", maxLength);
  if (!cleaned) throw new Error(`${name} es obligatorio`);
  return cleaned;
}

/** Accepts only the metadata emitted by Cortex. Unknown fields are deliberately discarded. */
export function parseClientAppUsageEvent(body: unknown): ClientAppUsageEvent {
  if (!body || typeof body !== "object") throw new Error("Evento de uso inválido");
  const row = body as Record<string, unknown>;
  const eventId = requireDimension(row.eventId, "eventId", 100);
  if (!/^[A-Za-z0-9_-]{8,100}$/.test(eventId)) throw new Error("eventId inválido");
  if (row.source !== "byok" && row.source !== "local") throw new Error("Origen de uso inválido");
  if (row.status !== "success" && row.status !== "error") throw new Error("Estado de uso inválido");
  if (row.operation !== "chat" && row.operation !== "chat_with_tools" && row.operation !== "stream") {
    throw new Error("Operación de uso inválida");
  }
  return {
    eventId,
    source: row.source,
    provider: requireDimension(row.provider, "provider", 80),
    model: requireDimension(row.model, "model", 200),
    promptTokens: finiteNonNegative(row.promptTokens, 100_000_000),
    completionTokens: finiteNonNegative(row.completionTokens, 100_000_000),
    usageEstimated: row.usageEstimated !== false,
    durationMs: finiteNonNegative(row.durationMs, 24 * 60 * 60 * 1_000),
    status: row.status,
    operation: row.operation,
    appVersion: row.appVersion ? cleanDimension(row.appVersion, "", 40) : undefined,
  };
}

function incrementsFor(input: AppUsageEventInput) {
  const promptTokens = finiteNonNegative(input.promptTokens, 100_000_000);
  const completionTokens = finiteNonNegative(input.completionTokens, 100_000_000);
  return {
    requests: FieldValue.increment(1),
    successfulRequests: FieldValue.increment(input.status === "success" ? 1 : 0),
    errorRequests: FieldValue.increment(input.status === "error" ? 1 : 0),
    promptTokens: FieldValue.increment(promptTokens),
    completionTokens: FieldValue.increment(completionTokens),
    totalTokens: FieldValue.increment(promptTokens + completionTokens),
    durationMs: FieldValue.increment(finiteNonNegative(input.durationMs, 24 * 60 * 60 * 1_000)),
    subscriptionRequests: FieldValue.increment(input.source === "subscription" ? 1 : 0),
    byokRequests: FieldValue.increment(input.source === "byok" ? 1 : 0),
    localRequests: FieldValue.increment(input.source === "local" ? 1 : 0),
    estimatedRequests: FieldValue.increment(input.usageEstimated === true ? 1 : 0),
  };
}

export async function recordAppUsageEvent(input: AppUsageEventInput): Promise<void> {
  const db = getFirestore();
  const day = utcDayKey();
  const provider = cleanDimension(input.provider, "Unknown", 80);
  const model = cleanDimension(input.model, "Unknown", 200);
  const source: AppUsageSource = input.source === "local"
    ? "local"
    : input.source === "subscription" ? "subscription" : "byok";
  const status = input.status === "success" ? "success" : "error";
  const operation: AppUsageOperation = input.operation === "stream"
    ? "stream"
    : input.operation === "chat_with_tools" ? "chat_with_tools" : "chat";
  const promptTokens = finiteNonNegative(input.promptTokens, 100_000_000);
  const completionTokens = finiteNonNegative(input.completionTokens, 100_000_000);
  const durationMs = finiteNonNegative(input.durationMs, 24 * 60 * 60 * 1_000);
  const hashedUser = userHash(input.uid);
  const dimensionHash = createHash("sha256")
    .update(`${source}\u0000${provider}\u0000${model}`)
    .digest("hex")
    .slice(0, 24);
  const eventDocumentId = input.eventId
    ? createHash("sha256").update(`${input.uid}\u0000${input.eventId}`).digest("hex")
    : undefined;
  const eventRef = eventDocumentId
    ? db.collection("appUsageEvents").doc(eventDocumentId)
    : db.collection("appUsageEvents").doc();
  const dailyRef = db.collection("appUsageDaily").doc(day);
  const modelRef = db.collection("appUsageDailyModels").doc(`${day}_${dimensionHash}`);
  const increments = incrementsFor({ ...input, source, status, operation });

  await db.runTransaction(async (transaction) => {
    if (eventDocumentId && (await transaction.get(eventRef)).exists) return;
    transaction.create(eventRef, {
      userHash: hashedUser,
      day,
      source,
      provider,
      model,
      promptTokens,
      completionTokens,
      totalTokens: promptTokens + completionTokens,
      usageEstimated: input.usageEstimated === true,
      durationMs,
      status,
      operation,
      appVersion: input.appVersion ? cleanDimension(input.appVersion, "", 40) : undefined,
      createdAt: FieldValue.serverTimestamp(),
    });
    transaction.set(dailyRef, {
      day,
      ...increments,
      updatedAt: FieldValue.serverTimestamp(),
    }, { merge: true });
    transaction.set(modelRef, {
      day,
      source,
      provider,
      model,
      ...increments,
      updatedAt: FieldValue.serverTimestamp(),
    }, { merge: true });
  });
}

function aggregateRows(rows: FirebaseFirestore.DocumentData[]): Record<string, number> {
  return rows.reduce((totals, row) => {
    for (const field of APP_USAGE_FIELDS) {
      totals[field] = (totals[field] ?? 0) + finiteNonNegative(row[field]);
    }
    return totals;
  }, Object.fromEntries(APP_USAGE_FIELDS.map((field) => [field, 0])) as Record<string, number>);
}

function rangeDays(start: Date, count: number): string[] {
  return Array.from({ length: count }, (_, offset) => {
    const date = new Date(start);
    date.setUTCDate(start.getUTCDate() + offset);
    return utcDayKey(date);
  });
}

export async function getAppUsageDashboard(days: number, now: Date = new Date()): Promise<Record<string, unknown>> {
  const safeDays = parseUsageDays(days);
  const end = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate()));
  const start = new Date(end);
  start.setUTCDate(end.getUTCDate() - safeDays + 1);
  const startDay = utcDayKey(start);
  const endDay = utcDayKey(end);
  const db = getFirestore();
  const [dailySnapshot, modelSnapshot, recentSnapshot] = await Promise.all([
    db.collection("appUsageDaily").where("day", ">=", startDay).where("day", "<=", endDay).orderBy("day", "asc").get(),
    db.collection("appUsageDailyModels").where("day", ">=", startDay).where("day", "<=", endDay).orderBy("day", "asc").get(),
    db.collection("appUsageEvents").where("createdAt", ">=", Timestamp.fromDate(start)).orderBy("createdAt", "desc").limit(100).get(),
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
    const key = JSON.stringify([row.source, row.provider, row.model]);
    groupedModels.set(key, [...(groupedModels.get(key) ?? []), row]);
  }
  const models = [...groupedModels.entries()].map(([key, rows]) => {
    const [source, provider, model] = JSON.parse(key) as string[];
    const aggregates = aggregateRows(rows);
    return {
      source,
      provider,
      model,
      ...aggregates,
      totalTokens: finiteNonNegative(aggregates.totalTokens),
      requests: finiteNonNegative(aggregates.requests),
    };
  }).sort((left, right) =>
    finiteNonNegative(right["totalTokens"]) - finiteNonNegative(left["totalTokens"]) ||
    finiteNonNegative(right["requests"]) - finiteNonNegative(left["requests"]));

  const groupedProviders = new Map<string, FirebaseFirestore.DocumentData[]>();
  for (const doc of modelSnapshot.docs) {
    const row = doc.data();
    const provider = cleanDimension(row.provider, "Unknown", 80);
    groupedProviders.set(provider, [...(groupedProviders.get(provider) ?? []), row]);
  }
  const providers = [...groupedProviders.entries()].map(([provider, rows]) => {
    const aggregates = aggregateRows(rows);
    return {
      provider,
      ...aggregates,
      totalTokens: finiteNonNegative(aggregates.totalTokens),
    };
  }).sort((left, right) =>
    finiteNonNegative(right["totalTokens"]) - finiteNonNegative(left["totalTokens"]));

  const sources = (["subscription", "byok", "local"] as const).map((source) => ({
    source,
    requests: finiteNonNegative(totals[`${source}Requests`]),
  }));
  const recent = recentSnapshot.docs.map((doc) => {
    const row = doc.data();
    return {
      id: doc.id,
      createdAt: row.createdAt instanceof Timestamp ? row.createdAt.toMillis() : null,
      user: row.userHash ? `${cleanDimension(row.userHash, "", 16)}…` : "—",
      source: row.source === "subscription" || row.source === "local" ? row.source : "byok",
      model: cleanDimension(row.model, "Unknown", 200),
      provider: cleanDimension(row.provider, "Unknown", 80),
      promptTokens: finiteNonNegative(row.promptTokens),
      completionTokens: finiteNonNegative(row.completionTokens),
      totalTokens: finiteNonNegative(row.totalTokens),
      usageEstimated: row.usageEstimated === true,
      durationMs: finiteNonNegative(row.durationMs),
      operation: row.operation === "stream" || row.operation === "chat_with_tools" ? row.operation : "chat",
      appVersion: row.appVersion ? cleanDimension(row.appVersion, "", 40) : null,
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
    sources,
    recent,
  };
}
