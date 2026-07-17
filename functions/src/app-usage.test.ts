import assert from "node:assert/strict";
import test from "node:test";
import { parseClientAppUsageEvent } from "./app-usage";

test("client app usage accepts only BYOK or local metadata", () => {
  const parsed = parseClientAppUsageEvent({
    eventId: "8b684b31-776d-48d7-b657-f5d0be147913",
    source: "byok",
    provider: "OpenAI",
    model: "gpt-5.1",
    promptTokens: 123.4,
    completionTokens: 45.7,
    durationMs: 1500.2,
    status: "success",
    operation: "chat_with_tools",
    appVersion: "0.3.1",
    prompt: "este contenido nunca debe conservarse",
    response: "ni este",
    apiKey: "secret",
  });

  assert.deepEqual(parsed, {
    eventId: "8b684b31-776d-48d7-b657-f5d0be147913",
    source: "byok",
    provider: "OpenAI",
    model: "gpt-5.1",
    promptTokens: 123,
    completionTokens: 46,
    usageEstimated: true,
    durationMs: 1500,
    status: "success",
    operation: "chat_with_tools",
    appVersion: "0.3.1",
  });
  assert.equal("prompt" in parsed, false);
  assert.equal("response" in parsed, false);
  assert.equal("apiKey" in parsed, false);
});

test("client app usage rejects subscription spoofing and invalid dimensions", () => {
  const base = {
    eventId: "event-123456",
    provider: "Local",
    model: "model.gguf",
    status: "success",
    operation: "chat",
  };
  assert.throws(() => parseClientAppUsageEvent({ ...base, source: "subscription" }), /Origen/);
  assert.throws(() => parseClientAppUsageEvent({ ...base, source: "local", model: "" }), /model/);
  assert.throws(() => parseClientAppUsageEvent({ ...base, source: "local", eventId: "bad id" }), /eventId/);
});

test("client app usage clamps untrusted numeric values", () => {
  const parsed = parseClientAppUsageEvent({
    eventId: "event-123456",
    source: "local",
    provider: "LOCAL",
    model: "qwen",
    promptTokens: -20,
    completionTokens: Number.POSITIVE_INFINITY,
    durationMs: 999_999_999,
    status: "error",
    operation: "stream",
    usageEstimated: false,
  });
  assert.equal(parsed.promptTokens, 0);
  assert.equal(parsed.completionTokens, 0);
  assert.equal(parsed.durationMs, 86_400_000);
  assert.equal(parsed.usageEstimated, false);
});
