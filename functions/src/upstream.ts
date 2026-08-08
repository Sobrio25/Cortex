import { ModelDefinition } from "./catalog";

export interface UpstreamResult {
  response: {
    content: string | null;
    toolCalls: unknown[] | null;
    finishReason: string | null;
    reasoning?: string | null;
  };
  usage: { promptTokens: number; completionTokens: number; estimated: boolean };
  upstream: {
    provider: string;
    model: string;
    gateway: string;
    fallback: boolean;
    fallbackCategory?: UpstreamFailureCategory;
  };
}

export type UpstreamFailureCategory =
  | "INVALID_REQUEST"
  | "AUTHENTICATION"
  | "PERMISSION"
  | "MODEL_UNAVAILABLE"
  | "RATE_LIMIT"
  | "TIMEOUT"
  | "CAPACITY"
  | "NETWORK"
  | "MALFORMED_RESPONSE"
  | "UNKNOWN";

export class UpstreamError extends Error {
  constructor(
    public readonly status: number,
    public readonly category: UpstreamFailureCategory,
    public readonly retryable: boolean,
  ) {
    super(`upstream_${category.toLowerCase()}`);
  }
}

export function classifyUpstreamStatus(status: number): {
  category: UpstreamFailureCategory;
  retryable: boolean;
} {
  if (status === 400 || status === 422) return { category: "INVALID_REQUEST", retryable: false };
  if (status === 401) return { category: "AUTHENTICATION", retryable: false };
  if (status === 403) return { category: "PERMISSION", retryable: false };
  if (status === 404) return { category: "MODEL_UNAVAILABLE", retryable: true };
  if (status === 408 || status === 504) return { category: "TIMEOUT", retryable: true };
  if (status === 425 || status === 429) return { category: "RATE_LIMIT", retryable: true };
  if (status >= 500) return { category: "CAPACITY", retryable: true };
  return { category: "UNKNOWN", retryable: false };
}

export function isRetryableUpstreamError(error: unknown): error is UpstreamError {
  return error instanceof UpstreamError && error.retryable;
}

export function fallbackReason(category: UpstreamFailureCategory): string {
  switch (category) {
    case "MODEL_UNAVAILABLE": return "El modelo solicitado no estaba disponible";
    case "RATE_LIMIT": return "El proveedor alcanzó temporalmente su límite";
    case "TIMEOUT": return "El proveedor tardó demasiado en responder";
    case "CAPACITY": return "El proveedor no tenía capacidad disponible";
    case "NETWORK": return "No se pudo conectar con el proveedor";
    case "MALFORMED_RESPONSE": return "El proveedor devolvió una respuesta incompleta";
    default: return "El proveedor principal no pudo completar la solicitud";
  }
}

const PROVIDER_NAMES: Record<string, string> = {
  anthropic: "Anthropic",
  deepseek: "DeepSeek",
  google: "Google",
  meta: "Meta",
  minimax: "MiniMax",
  moonshotai: "Moonshot AI",
  openai: "OpenAI",
  qwen: "Alibaba Qwen",
  xai: "xAI",
  xiaomi: "Xiaomi",
  zai: "Z.AI",
};

export function providerNameForModel(model: string, fallback: string): string {
  const namespace = model.split("/", 1)[0]?.toLowerCase();
  return PROVIDER_NAMES[namespace] ?? fallback;
}

function toOpenAiMessages(body: any): any[] {
  const messages: any[] = [{ role: "system", content: body.systemPrompt ?? "" }];
  for (const message of body.messages ?? []) {
    const converted: any = { role: message.role, content: message.content ?? "" };
    if (message.toolCallId) converted.tool_call_id = message.toolCallId;
    if (message.name) converted.name = message.name;
    if (message.toolCalls) {
      converted.tool_calls = message.toolCalls.map((call: any) => ({
        id: call.id,
        type: call.type ?? "function",
        function: call.function,
      }));
    }
    if (message.imageDataUri) {
      converted.content = [
        { type: "text", text: message.content || "Imagen:" },
        { type: "image_url", image_url: { url: message.imageDataUri } },
      ];
    }
    messages.push(converted);
  }
  return messages;
}

/**
 * Reserves a conservative upper bound before contacting a free upstream. UTF-8
 * payload bytes dominate tokenizer input tokens, while maxTokens bounds output.
 */
export function estimateFreeTokenReservation(body: any): number {
  const inputPayload = {
    messages: toOpenAiMessages(body ?? {}),
    tools: Array.isArray(body?.tools) && body.tools.length ? body.tools : undefined,
  };
  const inputUpperBound = Buffer.byteLength(JSON.stringify(inputPayload), "utf8") + 512;
  const outputUpperBound = Math.min(Math.max(Number(body?.maxTokens ?? 4096), 1), 65_536);
  return Math.ceil(inputUpperBound + outputUpperBound);
}

interface CompatibleCallOptions {
  timeoutMs?: number;
  temperature?: number;
  maxTokens?: number;
  responseFormat?: Record<string, unknown>;
  acceptLanguage?: string;
}

interface OpenCodeCallOptions {
  timeoutMs?: number;
  temperature?: number;
  maxTokens?: number;
}

async function callOpenCodeModel(
  body: any,
  apiKey: string,
  model: string,
  options: OpenCodeCallOptions = {},
): Promise<UpstreamResult> {
  return callCompatible(
    "https://opencode.ai/zen/v1/chat/completions",
    apiKey,
    model,
    body,
    "OpenCode Zen",
    "OpenCode",
    { timeoutMs: 30_000, ...options },
  );
}

export function callMimoFree(body: any, apiKey: string): Promise<UpstreamResult> {
  return callOpenCodeModel(body, apiKey, "mimo-v2.5-free", {
    timeoutMs: 15_000,
    temperature: 0,
    maxTokens: 512,
  });
}


function extractCompatibleContent(content: unknown): string {
  if (typeof content === "string") return content;
  if (Array.isArray(content)) {
    return content
      .map((part: any) => typeof part === "string" ? part : typeof part?.text === "string" ? part.text : "")
      .join("");
  }
  if (content && typeof content === "object" && typeof (content as any).text === "string") {
    return (content as any).text;
  }
  return "";
}

async function callCompatible(
  url: string,
  apiKey: string,
  model: string,
  body: any,
  gateway: string,
  fallbackProvider: string,
  options: CompatibleCallOptions = {},
): Promise<UpstreamResult> {
  const payload: Record<string, unknown> = {
    model,
    messages: toOpenAiMessages(body),
    temperature: options.temperature ?? body.temperature ?? 0.7,
    max_tokens: Math.min(Number(options.maxTokens ?? body.maxTokens ?? 4096), 65_536),
    stream: false,
  };
  if (Array.isArray(body.tools) && body.tools.length) payload.tools = body.tools;
  if (options.responseFormat) payload.response_format = options.responseFormat;
  let response: Response;
  try {
    response = await fetch(url, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${apiKey}`,
        "Content-Type": "application/json",
        ...(options.acceptLanguage ? { "Accept-Language": options.acceptLanguage } : {}),
      },
      body: JSON.stringify(payload),
      signal: AbortSignal.timeout(options.timeoutMs ?? 540_000),
    });
  } catch (error) {
    const timedOut = error instanceof Error &&
      (error.name === "TimeoutError" || error.name === "AbortError");
    throw new UpstreamError(0, timedOut ? "TIMEOUT" : "NETWORK", true);
  }
  const json: any = await response.json().catch(() => ({}));
  if (!response.ok || json.error || json.code) {
    const status = response.ok && json.code ? 400 : response.status;
    const classified = classifyUpstreamStatus(status);
    throw new UpstreamError(status, classified.category, classified.retryable);
  }
  if (!Array.isArray(json.choices) || json.choices.length === 0) {
    throw new UpstreamError(response.status, "MALFORMED_RESPONSE", true);
  }
  const choice = json.choices?.[0] ?? {};
  const message = choice.message ?? {};
  const content = extractCompatibleContent(message.content);
  if (!content && !Array.isArray(message.tool_calls)) {
    throw new UpstreamError(response.status, "MALFORMED_RESPONSE", true);
  }
  const estimatedPromptTokens = Math.ceil(JSON.stringify(payload.messages).length / 4);
  const estimatedCompletionTokens = Math.ceil(
    (content.length + JSON.stringify(message.tool_calls ?? []).length) / 4,
  );
  const resolvedModel = typeof json.model === "string" && json.model.trim()
    ? json.model.trim()
    : model;
  const hasPromptUsage = Number.isFinite(Number(json.usage?.prompt_tokens));
  const hasCompletionUsage = Number.isFinite(Number(json.usage?.completion_tokens));
  return {
    response: {
      content,
      toolCalls: Array.isArray(message.tool_calls) ? message.tool_calls.map((call: any) => ({
        id: call.id,
        type: call.type ?? "function",
        function: call.function,
      })) : null,
      finishReason: choice.finish_reason ?? null,
      reasoning: message.reasoning ?? message.reasoning_content ?? message.thinking ?? null,
    },
    usage: {
      promptTokens: hasPromptUsage ? Number(json.usage.prompt_tokens) : estimatedPromptTokens,
      completionTokens: hasCompletionUsage ? Number(json.usage.completion_tokens) : estimatedCompletionTokens,
      estimated: !hasPromptUsage || !hasCompletionUsage,
    },
    upstream: {
      provider: providerNameForModel(resolvedModel, fallbackProvider),
      model: resolvedModel,
      gateway,
      fallback: false,
    },
  };
}

export function callZai(
  body: any,
  apiKey: string,
  options: CompatibleCallOptions = {},
): Promise<UpstreamResult> {
  return callCompatible(
    "https://api.z.ai/api/paas/v4/chat/completions",
    apiKey,
    "glm-4.6v-flash",
    body,
    "Z.AI API",
    "Z.AI",
    { timeoutMs: 30_000, acceptLanguage: "en-US,en", ...options },
  );
}

export async function callPaid(model: ModelDefinition, body: any, apiKey: string): Promise<UpstreamResult> {
  if (!model.vercelModel) throw new UpstreamError(404, "MODEL_UNAVAILABLE", true);
  return callCompatible(
    "https://ai-gateway.vercel.sh/v1/chat/completions",
    apiKey,
    model.vercelModel,
    body,
    "Vercel AI Gateway",
    providerNameForModel(model.vercelModel, "Vercel AI Gateway"),
  );
}

export async function callFree(body: any, secrets: {
  openRouter?: string;
  kilo?: string;
  openCode?: string;
  zai?: string;
}, preferFlash: boolean): Promise<UpstreamResult> {
  const attempts: Array<() => Promise<UpstreamResult>> = [];
  const hasImage = JSON.stringify(body ?? {}).includes("data:image/") ||
    JSON.stringify(body ?? {}).includes("imageDataUri");

  if (hasImage) {
    if (secrets.openCode) attempts.push(() => callMimoFree(body, secrets.openCode!));
    if (secrets.zai) attempts.push(() => callZai(body, secrets.zai!));
  } else {
    if (preferFlash && secrets.openCode) {
      attempts.push(() => callCompatible(
        "https://opencode.ai/zen/v1/chat/completions",
        secrets.openCode!,
        "deepseek-v4-flash-free",
        body,
        "OpenCode Zen",
        "OpenCode",
      ));
    }
    if (secrets.openCode) attempts.push(() => callMimoFree(body, secrets.openCode!));
    if (secrets.openRouter) {
      attempts.push(() => callCompatible(
        "https://openrouter.ai/api/v1/chat/completions",
        secrets.openRouter!,
        "openrouter/free",
        body,
        "OpenRouter",
        "OpenRouter",
      ));
    }
    if (secrets.kilo) {
      attempts.push(() => callCompatible(
        "https://api.kilo.ai/api/gateway/chat/completions",
        secrets.kilo!,
        "kilo-auto/free",
        body,
        "Kilo AI",
        "Kilo AI",
      ));
    }
    if (secrets.zai) {
      attempts.push(() => callZai(body, secrets.zai!));
    }
  }

  for (let index = attempts.length - 1; index > 0; index -= 1) {
    const randomIndex = Math.floor(Math.random() * (index + 1));
    [attempts[index], attempts[randomIndex]] = [attempts[randomIndex], attempts[index]];
  }

  let firstFailure: UpstreamFailureCategory | undefined;
  for (let index = 0; index < attempts.length; index += 1) {
    try {
      const result = await attempts[index]();
      return {
        ...result,
        upstream: {
          ...result.upstream,
          fallback: index > 0,
          ...(index > 0 && firstFailure ? { fallbackCategory: firstFailure } : {}),
        },
      };
    } catch (error) {
      if (!isRetryableUpstreamError(error)) throw error;
      firstFailure ??= error.category;
    }
  }
  throw new UpstreamError(503, firstFailure ?? "CAPACITY", true);
}

export function calculateCost(model: ModelDefinition, usage: UpstreamResult["usage"]): number {
  return Math.ceil(
    usage.promptTokens * (model.pricing.inputMicrosPerToken ?? 0) +
    usage.completionTokens * (model.pricing.outputMicrosPerToken ?? 0),
  );
}
