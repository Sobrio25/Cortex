import { FieldValue, Timestamp, getFirestore } from "firebase-admin/firestore";
import { userHash } from "./privacy";

export const CONTENT_REPORT_CATEGORIES = ["offensive", "unsafe", "inaccurate", "other"] as const;
export type ContentReportCategory = typeof CONTENT_REPORT_CATEGORIES[number];

export interface ContentReportInput {
  messageId: string;
  category: ContentReportCategory;
  comment?: string;
  content: string;
  model?: string;
  appVersion?: string;
}

function cleanText(value: unknown, maxLength: number): string {
  return String(value ?? "").trim().replace(/[\u0000\u007f]/g, "").slice(0, maxLength);
}

/** Accepts only fields required to investigate a report. Unknown fields are discarded. */
export function parseContentReport(body: unknown): ContentReportInput {
  if (!body || typeof body !== "object") throw new Error("Reporte inválido");
  const row = body as Record<string, unknown>;
  const messageId = cleanText(row.messageId, 128);
  const content = cleanText(row.content, 20_000);
  if (!messageId) throw new Error("messageId es obligatorio");
  if (!content) throw new Error("El contenido reportado es obligatorio");
  if (!CONTENT_REPORT_CATEGORIES.includes(row.category as ContentReportCategory)) {
    throw new Error("Categoría de reporte inválida");
  }
  const comment = cleanText(row.comment, 1_000);
  const model = cleanText(row.model, 200);
  const appVersion = cleanText(row.appVersion, 40);
  return {
    messageId,
    category: row.category as ContentReportCategory,
    content,
    ...(comment ? { comment } : {}),
    ...(model ? { model } : {}),
    ...(appVersion ? { appVersion } : {}),
  };
}

export async function recordContentReport(uid: string, report: ContentReportInput): Promise<void> {
  const now = Date.now();
  await getFirestore().collection("contentReports").add({
    ...report,
    userHash: userHash(uid),
    createdAt: FieldValue.serverTimestamp(),
    expiresAt: Timestamp.fromMillis(now + 90 * 24 * 60 * 60 * 1_000),
  });
}
