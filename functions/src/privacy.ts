const SENSITIVE_PATTERNS = [
  /\b(password|contraseña|api[_ -]?key|secret|token privado|private key)\b/i,
  /\b(?:\d[ -]*?){13,19}\b/,
  /\b(?:ssn|social security|curp|rfc|número de cuenta|bank account)\b/i,
  /-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----/,
];

const SAFE_TOOL_NAMES = new Set([
  "web_search",
  "web_fetch",
  "unified_web_search",
  "academic_search",
  "weather",
  "weather_current",
  "weather_forecast",
  "weather_air_quality",
  "pubmed_search",
  "pubmed_fetch_article",
  "wikipedia_search",
  "arxiv_search",
  "duckduckgo_search",
  "brave_web_search",
  "search_web",
  "set_assistant_name",
]);

function safeAssistantName(value: unknown): string {
  const normalized = String(value ?? "")
    .normalize("NFKC")
    .replace(/[^\p{L}\p{N} _.'’\-]/gu, " ")
    .replace(/\s+/g, " ")
    .trim()
    .slice(0, 40);
  return normalized || "Assistant";
}

export interface PrivacyResult {
  safe: boolean;
  reason?: string;
}

export function validateFreeRequest(body: any): PrivacyResult {
  const messages = Array.isArray(body?.messages) ? body.messages : [];
  if (messages.some((message: any) => message?.imageDataUri || message?.toolResultImageUri)) {
    return { safe: false, reason: "Las imágenes y archivos requieren un modelo protegido por un plan con presupuesto." };
  }
  for (const message of messages) {
    const content = String(message?.content ?? "");
    if (SENSITIVE_PATTERNS.some((pattern) => pattern.test(content))) {
      return { safe: false, reason: "Este mensaje parece contener información sensible y no se enviará mediante la ruta gratuita." };
    }
    if (message?.role === "tool" && message?.name && !SAFE_TOOL_NAMES.has(message.name)) {
      return { safe: false, reason: "El resultado de esta herramienta puede contener datos privados y no se enviará mediante la ruta gratuita." };
    }
  }
  return { safe: true };
}

export function sanitizeForFree(body: any): any {
  const assistantName = safeAssistantName(body?.assistantName);
  const tools = Array.isArray(body?.tools) ? body.tools.filter((tool: any) => {
    const name = tool?.function?.name;
    return typeof name === "string" && SAFE_TOOL_NAMES.has(name);
  }) : [];
  return {
    ...body,
    systemPrompt: `Your configured name is "${assistantName}". This name is authoritative; never claim a product, project, default, or legacy name instead. Be helpful and answer in the user's language. Do not request or reveal personal, confidential, or sensitive information. Use only the public tools provided. If the user explicitly asks to rename you, call set_assistant_name.`,
    tools,
  };
}
