import assert from "node:assert/strict";
import test from "node:test";
import { MODELS, modelsForPlan, resolveModel } from "./catalog";
import { publicModels } from "./store";

test("free through plus expose automatic selection only", () => {
  assert.deepEqual(modelsForPlan("FREE").map((model) => model.id), ["auto"]);
  assert.deepEqual(modelsForPlan("STARTER").map((model) => model.id), ["auto"]);
  assert.deepEqual(modelsForPlan("PLUS").map((model) => model.id), ["auto"]);
});

test("paid model identifiers are routed through Vercel catalog identifiers", () => {
  for (const model of Object.values(MODELS).filter((item) => item.id !== "auto")) {
    assert.ok(model.vercelModel?.includes("/"));
    assert.equal(model.vercelModel?.includes("openrouter"), false);
  }
});

test("public model catalog never exposes routing or gateway identity", () => {
  for (const model of publicModels("ULTRA")) {
    assert.equal("vercelModel" in model, false);
    assert.equal("provider" in model, false);
    assert.equal("gateway" in model, false);
  }
});

test("manual selection never exceeds entitlement", () => {
  assert.equal(resolveModel("PRO", "gpt-5.6-sol", {}), undefined);
  assert.equal(resolveModel("ULTRA", "gpt-5.6-sol", {})?.id, "gpt-5.6-sol");
  assert.equal(resolveModel("PLUS", "deepseek-v4-pro", {}), undefined);
});

test("automatic routing follows task and plan", () => {
  assert.equal(resolveModel("STARTER", "auto", {})?.id, "deepseek-v4-flash");
  assert.equal(resolveModel("PRO", "auto", { prompt: "debug this Kotlin code" })?.id, "kimi-k2.7-code");
  assert.equal(resolveModel("ULTRA", "auto", { prompt: "proyecto autónomo completo" })?.id, "claude-fable-5");
});
