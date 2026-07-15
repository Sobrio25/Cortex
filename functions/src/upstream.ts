import { ModelDefinition } from "./catalog";

export interface UpstreamResult {
  response: {
    content: string | null;
    toolCalls: unknown[] | null;
    finishReason: string | null;
    reasoning?: string | null;
  };
  usage: { promptTokens: number; completionTokens: number };
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

async function callCompatible(
  url: string,
  apiKey: string,
  model: string,
  body: any,
): Promise<UpstreamResult> {
  const payload: Record<string, unknown> = {
    model,
    messages: toOpenAiMessages(body),
    temperature: body.temperature ?? 0.7,
    max_tokens: Math.min(Number(body.maxTokens ?? 4096), 65_536),
    stream: false,
  };
  if (Array.isArray(body.tools) && body.tools.length) payload.tools = body.tools;
  const response = await fetch(url, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${apiKey}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(payload),
    signal: AbortSignal.timeout(540_000),
  });
  const json: any = await response.json().catch(() => ({}));
  if (!response.ok || json.error) {
    throw new Error(`upstream_${response.status}`);
  }
  const choice = json.choices?.[0] ?? {};
  const message = choice.message ?? {};
  const estimatedPromptTokens = Math.ceil(JSON.stringify(payload.messages).length / 4);
  const estimatedCompletionTokens = Math.ceil(
    (String(message.content ?? "").length + JSON.stringify(message.tool_calls ?? []).length) / 4,
  );
  return {
    response: {
      content: typeof message.content === "string" ? message.content : "",
      toolCalls: Array.isArray(message.tool_calls) ? message.tool_calls.map((call: any) => ({
        id: call.id,
        type: call.type ?? "function",
        function: call.function,
      })) : null,
      finishReason: choice.finish_reason ?? null,
      reasoning: message.reasoning ?? message.thinking ?? null,
    },
    usage: {
      promptTokens: Number(json.usage?.prompt_tokens ?? 0) || estimatedPromptTokens,
      completionTokens: Number(json.usage?.completion_tokens ?? 0) || estimatedCompletionTokens,
    },
  };
}

export async function callPaid(model: ModelDefinition, body: any, apiKey: string): Promise<UpstreamResult> {
  if (!model.vercelModel) throw new Error("paid_model_unavailable");
  return callCompatible(
    "https://ai-gateway.vercel.sh/v1/chat/completions",
    apiKey,
    model.vercelModel,
    body,
  );
}

export async function callFree(body: any, secrets: {
  openRouter?: string;
  kilo?: string;
  openCode?: string;
}, preferFlash: boolean): Promise<UpstreamResult> {
  const attempts: Array<() => Promise<UpstreamResult>> = [];
  if (preferFlash && secrets.openCode) {
    attempts.push(() => callCompatible(
      "https://opencode.ai/zen/v1/chat/completions",
      secrets.openCode!,
      "deepseek-v4-flash-free",
      body,
    ));
  }
  if (secrets.openRouter) {
    attempts.push(() => callCompatible(
      "https://openrouter.ai/api/v1/chat/completions",
      secrets.openRouter!,
      "openrouter/free",
      body,
    ));
  }
  if (secrets.kilo) {
    attempts.push(() => callCompatible(
      "https://api.kilo.ai/api/gateway/chat/completions",
      secrets.kilo!,
      "kilo-auto/free",
      body,
    ));
  }
  for (const attempt of attempts) {
    try {
      return await attempt();
    } catch {
      // Continue through the private fallback chain without exposing its identity.
    }
  }
  throw new Error("free_capacity_unavailable");
}

export function calculateCost(model: ModelDefinition, usage: UpstreamResult["usage"]): number {
  return Math.ceil(
    usage.promptTokens * (model.inputMicrosPerToken ?? 0) +
    usage.completionTokens * (model.outputMicrosPerToken ?? 0),
  );
}
