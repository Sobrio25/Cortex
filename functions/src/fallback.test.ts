import assert from "node:assert/strict";
import test from "node:test";
import { MODELS, PLANS, paidFallbacks, resolveModel } from "./catalog";

const price = (modelId: string) => {
  const model = MODELS[modelId];
  return (model.pricing.inputMicrosPerToken ?? 0) + (model.pricing.outputMicrosPerToken ?? 0);
};

test("free plan never resolves a premium model", () => {
  assert.equal(resolveModel("FREE", "auto", { prompt: "analiza este código" }), undefined);
  assert.equal(resolveModel("FREE", "gpt-5.6-sol", {}), undefined);
  assert.deepEqual(paidFallbacks("FREE", "gpt-5.6-sol"), []);
});

test("premium fallback remains entitled and never costs more than the failed model", () => {
  const failedModelId = "gpt-5.6-luna";
  const fallbacks = paidFallbacks("PRO", failedModelId);

  assert.ok(fallbacks.length > 0);
  for (const fallback of fallbacks) {
    assert.notEqual(fallback.id, failedModelId);
    assert.ok(PLANS[fallback.minimumPlan].rank <= PLANS.PRO.rank);
    assert.ok(price(fallback.id) <= price(failedModelId));
  }
});

test("Starter fallback cannot cross into a higher subscription tier", () => {
  const fallbacks = paidFallbacks("STARTER", "mimo-v2.5");

  assert.deepEqual(fallbacks.map((model) => model.id), ["deepseek-v4-flash"]);
  assert.ok(fallbacks.every((model) => model.minimumPlan === "STARTER"));
});
