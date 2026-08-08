import { timingSafeEqual } from "node:crypto";
import { Request, Response } from "express";
import { callMimoFree, UpstreamError } from "./upstream";

const MAX_IMAGE_BYTES = 1 * 1024 * 1024;
const MAX_DATA_URI_BYTES = 1_500_000;
const REQUEST_ID_RE = /^[A-Za-z0-9_-]{6,64}$/;
const LOCALE_RE = /^[a-z]{2}(?:-[A-Z]{2})?$/;
const IMAGE_RE = /^data:(image\/(?:jpeg|png|webp));base64,([A-Za-z0-9+/=]+)$/;
const FIELD_NAMES = ["nap", "drop", "ont"] as const;
const MIN_POWER_DBM = -100;
const MAX_POWER_DBM = 20;
const SERVICE_TOKEN_RE = /^fm1\.(\d{10})\.([A-Za-z0-9_-]{32,})$/;

export type ScanFieldName = typeof FIELD_NAMES[number];

export interface FiberMeterScanRequest {
  schemaVersion: 1;
  requestId: string;
  imageDataUri: string;
  locale: string;
}

export interface ScanField {
  value: number | null;
  visible: boolean;
  confidence: number;
}

export interface FiberMeterScanResponse {
  schemaVersion: 1;
  requestId: string;
  fields: Record<ScanFieldName, ScanField>;
}

export class FiberMeterScanError extends Error {
  constructor(public readonly status: number, public readonly code: string) {
    super(code);
  }
}

function hasOnlyKeys(value: Record<string, unknown>, allowed: string[]): boolean {
  return Object.keys(value).every((key) => allowed.includes(key));
}

function estimateBase64Bytes(encoded: string): number {
  const padding = encoded.endsWith("==") ? 2 : encoded.endsWith("=") ? 1 : 0;
  return Math.floor(encoded.length * 3 / 4) - padding;
}

export function parseFiberMeterScanRequest(body: unknown): FiberMeterScanRequest {
  if (!body || typeof body !== "object") {
    throw new FiberMeterScanError(400, "INVALID_REQUEST");
  }
  const row = body as Record<string, unknown>;
  if (!hasOnlyKeys(row, ["schemaVersion", "requestId", "imageDataUri", "locale"])) {
    throw new FiberMeterScanError(400, "INVALID_REQUEST");
  }
  if (row.schemaVersion !== 1) {
    throw new FiberMeterScanError(400, "INVALID_REQUEST");
  }
  const requestId = typeof row.requestId === "string" ? row.requestId : "";
  if (!REQUEST_ID_RE.test(requestId)) {
    throw new FiberMeterScanError(400, "INVALID_REQUEST");
  }
  const imageDataUri = typeof row.imageDataUri === "string" ? row.imageDataUri : "";
  if (Buffer.byteLength(imageDataUri, "utf8") > MAX_DATA_URI_BYTES) {
    throw new FiberMeterScanError(413, "IMAGE_TOO_LARGE");
  }
  const match = IMAGE_RE.exec(imageDataUri);
  if (!match) {
    throw new FiberMeterScanError(400, "INVALID_REQUEST");
  }
  if (estimateBase64Bytes(match[2]) > MAX_IMAGE_BYTES) {
    throw new FiberMeterScanError(413, "IMAGE_TOO_LARGE");
  }
  const decoded = Buffer.from(match[2], "base64");
  if (!decoded.length || decoded.length > MAX_IMAGE_BYTES) {
    throw new FiberMeterScanError(413, "IMAGE_TOO_LARGE");
  }
  const locale = row.locale == null ? "es" : String(row.locale);
  if (!LOCALE_RE.test(locale)) {
    throw new FiberMeterScanError(400, "INVALID_REQUEST");
  }
  return { schemaVersion: 1, requestId, imageDataUri, locale };
}

function parsePowerValue(value: unknown): number | null {
  if (typeof value === "number") {
    return Number.isFinite(value) ? value : null;
  }
  if (typeof value !== "string") return null;
  const normalized = value.replace(",", ".").replace(/\s*dBm\s*$/i, "").trim();
  if (!/^-?(?:\d+(?:\.\d+)?|\.\d+)$/.test(normalized)) return null;
  const parsed = Number(normalized);
  return Number.isFinite(parsed) ? parsed : null;
}

function normalizeField(raw: unknown): ScanField {
  if (!raw || typeof raw !== "object") {
    return { value: null, visible: false, confidence: 0 };
  }
  const row = raw as Record<string, unknown>;
  const value = parsePowerValue(row.value);
  const confidence = typeof row.confidence === "number" ? row.confidence : Number(row.confidence);
  if (
    value == null ||
    value < MIN_POWER_DBM ||
    value > MAX_POWER_DBM ||
    !Number.isFinite(confidence) ||
    confidence < 0 ||
    confidence > 1 ||
    row.visible !== true
  ) {
    return { value: null, visible: false, confidence: 0 };
  }
  return { value, visible: true, confidence };
}

export function normalizeFiberMeterScanResult(raw: unknown): Omit<FiberMeterScanResponse, "requestId"> {
  if (!raw || typeof raw !== "object") {
    throw new FiberMeterScanError(502, "INVALID_RESULT");
  }
  const row = raw as Record<string, unknown>;
  const fields = row.fields && typeof row.fields === "object"
    ? row.fields as Record<string, unknown>
    : row;
  if (!hasOnlyKeys(fields, [...FIELD_NAMES])) {
    throw new FiberMeterScanError(502, "INVALID_RESULT");
  }
  return {
    schemaVersion: 1,
    fields: {
      nap: normalizeField(fields.nap),
      drop: normalizeField(fields.drop),
      ont: normalizeField(fields.ont),
    },
  };
}

function parseModelJson(content: string): unknown {
  const trimmed = content.trim();
  try {
    return JSON.parse(trimmed);
  } catch (_) {
    const start = trimmed.indexOf("{");
    const end = trimmed.lastIndexOf("}");
    if (start < 0 || end <= start) throw new FiberMeterScanError(502, "INVALID_RESULT");
    try {
      return JSON.parse(trimmed.slice(start, end + 1));
    } catch (_) {
      throw new FiberMeterScanError(502, "INVALID_RESULT");
    }
  }
}

export function serviceTokenIsValid(
  provided: string,
  expected: string,
  nowSeconds: number = Math.floor(Date.now() / 1000),
): boolean {
  if (!provided || !expected) return false;
  const match = SERVICE_TOKEN_RE.exec(provided);
  if (!match || Number(match[1]) <= nowSeconds) return false;
  const providedBytes = Buffer.from(provided);
  const expectedBytes = Buffer.from(expected);
  return providedBytes.length === expectedBytes.length && timingSafeEqual(providedBytes, expectedBytes);
}

function sendError(res: Response, status: number, code: string): void {
  res.status(status).json({ error: code });
}

const SYSTEM_PROMPT = [
  "Lee la imagen de parámetros de una instalación de fibra óptica.",
  "Devuelve únicamente un objeto JSON con la clave fields y exactamente las claves nap, drop y ont.",
  "Para cada clave devuelve value como número en dBm, visible como booleano y confidence entre 0 y 1.",
  "Identifica únicamente niveles de potencia claramente visibles y relaciona cada lectura con NAP, DROP u ONT según su etiqueta.",
  "Si una lectura no aparece o no es legible, usa value null, visible false y confidence 0.",
  "No calcules, deduzcas ni inventes valores faltantes.",
].join(" ");

export function createFiberMeterScanHandler(dependencies: {
  getServiceToken: () => string;
  getOpenCodeKey: () => string;
}) {
  return async function fiberMeterScanHandler(req: Request, res: Response): Promise<void> {
    res.set("Cache-Control", "no-store");
    const authorization = req.header("authorization") ?? "";
    const providedToken = authorization.startsWith("Bearer ") ? authorization.slice(7) : "";
    if (!serviceTokenIsValid(providedToken, dependencies.getServiceToken())) {
      sendError(res, 401, "UNAUTHORIZED");
      return;
    }

    let input: FiberMeterScanRequest;
    try {
      input = parseFiberMeterScanRequest(req.body);
    } catch (error) {
      const scanError = error instanceof FiberMeterScanError
        ? error
        : new FiberMeterScanError(400, "INVALID_REQUEST");
      sendError(res, scanError.status, scanError.code);
      return;
    }

    try {
      const upstream = await callMimoFree({
        systemPrompt: SYSTEM_PROMPT,
        messages: [{
          role: "user",
          content: "Extrae los niveles visibles de potencia de esta imagen y devuelve únicamente JSON.",
          imageDataUri: input.imageDataUri,
        }],
      }, dependencies.getOpenCodeKey());
      const content = upstream.response.content;
      if (!content) throw new FiberMeterScanError(502, "INVALID_RESULT");
      const normalized = normalizeFiberMeterScanResult(parseModelJson(content));
      res.json({ ...normalized, requestId: input.requestId });
    } catch (error) {
      if (error instanceof FiberMeterScanError) {
        sendError(res, error.status, error.code);
        return;
      }
      if (error instanceof UpstreamError && error.category === "TIMEOUT") {
        sendError(res, 504, "TIMEOUT");
        return;
      }
      sendError(res, 503, "UNAVAILABLE");
    }
  };
}

export const _test = {
  estimateBase64Bytes,
  parseModelJson,
  parsePowerValue,
  serviceTokenIsValid,
  SYSTEM_PROMPT,
  MAX_IMAGE_BYTES,
  MAX_DATA_URI_BYTES,
};
