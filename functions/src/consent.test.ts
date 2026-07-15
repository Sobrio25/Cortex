import assert from "node:assert/strict";
import test from "node:test";
import {
  FREE_DATA_CONSENT_VERSION,
  hasCurrentFreeDataConsent,
  prepareConsentedFreeRequest,
} from "./consent";

test("free data consent is explicit and versioned", () => {
  assert.equal(hasCurrentFreeDataConsent(undefined), false);
  assert.equal(hasCurrentFreeDataConsent(FREE_DATA_CONSENT_VERSION - 1), false);
  assert.equal(hasCurrentFreeDataConsent(FREE_DATA_CONSENT_VERSION), true);
});

test("consented free requests are not inspected, blocked, or redacted", () => {
  const body = {
    systemPrompt: "Today is 2026-07-15. Keep the configured identity.",
    messages: [
      { role: "user", content: "A public URL contains /20260424113331-di.html" },
      { role: "tool", name: "read_file", content: "User-authorized tool output" },
    ],
    tools: [{ function: { name: "read_file" } }],
  };

  assert.strictEqual(prepareConsentedFreeRequest(body), body);
  assert.deepEqual(prepareConsentedFreeRequest(body), body);
});
