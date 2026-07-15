export type PlanId = "FREE" | "STARTER" | "PLUS" | "PRO" | "MAX" | "ULTRA";

export interface PlanDefinition {
  id: PlanId;
  productId?: string;
  budgetMicros: number;
  rank: number;
}

export interface ModelDefinition {
  id: string;
  displayName: string;
  minimumPlan: PlanId;
  vercelModel?: string;
  contextWindow: number;
  supportsVision?: boolean;
  inputMicrosPerToken?: number;
  outputMicrosPerToken?: number;
}

export const PLANS: Record<PlanId, PlanDefinition> = {
  FREE: { id: "FREE", budgetMicros: 0, rank: 0 },
  STARTER: { id: "STARTER", productId: "cortex_starter", budgetMicros: 2_500_000, rank: 1 },
  PLUS: { id: "PLUS", productId: "cortex_plus", budgetMicros: 5_000_000, rank: 2 },
  PRO: { id: "PRO", productId: "cortex_pro", budgetMicros: 10_000_000, rank: 3 },
  MAX: { id: "MAX", productId: "cortex_max", budgetMicros: 25_000_000, rank: 4 },
  ULTRA: { id: "ULTRA", productId: "cortex_ultra", budgetMicros: 50_000_000, rank: 5 },
};

const MODEL_LIST: ModelDefinition[] = [
  { id: "auto", displayName: "Auto", minimumPlan: "FREE", contextWindow: 128_000 },
  { id: "deepseek-v4-flash", displayName: "DeepSeek V4 Flash", minimumPlan: "STARTER", vercelModel: "deepseek/deepseek-v4-flash", contextWindow: 1_000_000, inputMicrosPerToken: 0.14, outputMicrosPerToken: 0.28 },
  { id: "deepseek-v4-pro", displayName: "DeepSeek V4 Pro", minimumPlan: "PLUS", vercelModel: "deepseek/deepseek-v4-pro", contextWindow: 1_000_000, inputMicrosPerToken: 0.435, outputMicrosPerToken: 0.87 },
  { id: "gpt-5.6-luna", displayName: "GPT-5.6 Luna", minimumPlan: "PRO", vercelModel: "openai/gpt-5.6-luna", contextWindow: 1_000_000, supportsVision: true, inputMicrosPerToken: 1, outputMicrosPerToken: 6 },
  { id: "mimo-v2.5-pro", displayName: "MiMo 2.5 Pro", minimumPlan: "PRO", vercelModel: "xiaomi/mimo-v2.5-pro", contextWindow: 1_000_000, inputMicrosPerToken: 0.435, outputMicrosPerToken: 0.87 },
  { id: "kimi-k2.7-code", displayName: "Kimi K2.7 Code", minimumPlan: "PRO", vercelModel: "moonshotai/kimi-k2.7-code", contextWindow: 262_144, supportsVision: true, inputMicrosPerToken: 0.95, outputMicrosPerToken: 4 },
  { id: "minimax-m3", displayName: "MiniMax M3", minimumPlan: "PRO", vercelModel: "minimax/minimax-m3", contextWindow: 1_000_000, supportsVision: true, inputMicrosPerToken: 0.3, outputMicrosPerToken: 1.2 },
  { id: "grok-4.5", displayName: "Grok 4.5", minimumPlan: "PRO", vercelModel: "xai/grok-4.5", contextWindow: 500_000, supportsVision: true, inputMicrosPerToken: 2, outputMicrosPerToken: 6 },
  { id: "gpt-5.6-terra", displayName: "GPT-5.6 Terra", minimumPlan: "MAX", vercelModel: "openai/gpt-5.6-terra", contextWindow: 1_000_000, supportsVision: true, inputMicrosPerToken: 2.5, outputMicrosPerToken: 15 },
  { id: "glm-5.2", displayName: "GLM 5.2", minimumPlan: "MAX", vercelModel: "zai/glm-5.2", contextWindow: 1_000_000, inputMicrosPerToken: 1.4, outputMicrosPerToken: 4.4 },
  { id: "claude-sonnet-5", displayName: "Claude Sonnet 5", minimumPlan: "MAX", vercelModel: "anthropic/claude-sonnet-5", contextWindow: 1_000_000, supportsVision: true, inputMicrosPerToken: 2, outputMicrosPerToken: 10 },
  { id: "claude-opus-4.8", displayName: "Claude Opus 4.8", minimumPlan: "MAX", vercelModel: "anthropic/claude-opus-4.8", contextWindow: 1_000_000, supportsVision: true, inputMicrosPerToken: 5, outputMicrosPerToken: 25 },
  { id: "gpt-5.6-sol", displayName: "GPT-5.6 Sol", minimumPlan: "ULTRA", vercelModel: "openai/gpt-5.6-sol", contextWindow: 1_000_000, supportsVision: true, inputMicrosPerToken: 5, outputMicrosPerToken: 30 },
  { id: "claude-fable-5", displayName: "Claude Fable 5", minimumPlan: "ULTRA", vercelModel: "anthropic/claude-fable-5", contextWindow: 1_000_000, supportsVision: true, inputMicrosPerToken: 10, outputMicrosPerToken: 50 },
];

export const MODELS: Record<string, ModelDefinition> = Object.fromEntries(
  MODEL_LIST.map((model) => [model.id, model]),
);

export function planForProduct(productId: string): PlanId | undefined {
  return Object.values(PLANS).find((plan) => plan.productId === productId)?.id;
}

export function modelsForPlan(plan: PlanId): ModelDefinition[] {
  const rank = PLANS[plan].rank;
  return Object.values(MODELS).filter((model) => {
    if (PLANS[model.minimumPlan].rank > rank) return false;
    return rank >= PLANS.PRO.rank || model.id === "auto";
  });
}

export function resolveModel(plan: PlanId, requested: string, body: unknown): ModelDefinition | undefined {
  const planRank = PLANS[plan].rank;
  if (requested !== "auto") {
    const selected = MODELS[requested];
    if (!selected || planRank < PLANS[selected.minimumPlan].rank || planRank < PLANS.PRO.rank) return undefined;
    return selected;
  }
  if (plan === "FREE") return undefined;
  if (plan === "STARTER") return MODELS["deepseek-v4-flash"];
  if (plan === "PLUS") return MODELS["deepseek-v4-pro"];

  const text = JSON.stringify(body).toLowerCase();
  const hasVision = text.includes("data:image/") || text.includes("image_data_uri");
  const isCode = /\b(code|código|program|debug|repository|gradle|kotlin|typescript|python)\b/.test(text);
  const isLongAutonomous = /\b(autonomous|autonom\w*|autónom\w*|long[- ]running|investiga a fondo|proyecto completo)\b/.test(text);
  const isComplex = /\b(reason|razona|analiza|prove|demuestra|strategy|estrategia|complex|complej)\b/.test(text);

  if (plan === "ULTRA") {
    if (isLongAutonomous) return MODELS["claude-fable-5"];
    if (isComplex || hasVision) return MODELS["gpt-5.6-sol"];
  }
  if (PLANS[plan].rank >= PLANS.MAX.rank) {
    if (isCode) return MODELS["claude-sonnet-5"];
    if (hasVision) return MODELS["gpt-5.6-terra"];
    if (isComplex) return MODELS["claude-opus-4.8"];
    return MODELS["glm-5.2"];
  }
  if (isCode) return MODELS["kimi-k2.7-code"];
  if (hasVision) return MODELS["minimax-m3"];
  if (isComplex) return MODELS["gpt-5.6-luna"];
  return MODELS["deepseek-v4-pro"];
}

export function paidFallbacks(plan: PlanId, failedModelId: string): ModelDefinition[] {
  const failed = MODELS[failedModelId];
  const failedPrice = (failed?.inputMicrosPerToken ?? 0) + (failed?.outputMicrosPerToken ?? 0);
  const preferredIds = plan === "ULTRA"
    ? ["claude-opus-4.8", "claude-sonnet-5", "glm-5.2", "deepseek-v4-pro", "deepseek-v4-flash"]
    : plan === "MAX"
      ? ["claude-sonnet-5", "glm-5.2", "deepseek-v4-pro", "deepseek-v4-flash"]
      : plan === "PRO"
        ? ["deepseek-v4-pro", "deepseek-v4-flash"]
        : plan === "PLUS"
          ? ["deepseek-v4-flash"]
          : [];
  return preferredIds
    .filter((id) => id !== failedModelId)
    .map((id) => MODELS[id])
    .filter((model) =>
      PLANS[model.minimumPlan].rank <= PLANS[plan].rank &&
      ((model.inputMicrosPerToken ?? 0) + (model.outputMicrosPerToken ?? 0)) <= failedPrice,
    );
}
