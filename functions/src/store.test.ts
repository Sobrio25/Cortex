import assert from "node:assert/strict";
import test from "node:test";
import { FREE_TOKENS_LIMIT, quotaPeriodKey, requireGoogleSignInForFree } from "./store";
import { estimateFreeTokenReservation } from "./upstream";

test("free allowance is five hundred thousand tokens per ISO week", () => {
  assert.equal(FREE_TOKENS_LIMIT, 500_000);
  assert.equal(quotaPeriodKey(new Date("2026-07-15T23:59:59Z")), "2026-W29");
});

test("weekly quota uses ISO year boundaries", () => {
  assert.equal(quotaPeriodKey(new Date("2027-01-01T12:00:00Z")), "2026-W53");
  assert.equal(quotaPeriodKey(new Date("2027-01-04T00:00:00Z")), "2027-W01");
});

test("free reservation includes request bytes and maximum completion", () => {
  const body = { messages: [{ role: "user", content: "hola" }], maxTokens: 128 };
  assert.ok(estimateFreeTokenReservation(body) > 128 + Buffer.byteLength("hola", "utf8"));
});

test("free reservation caps the requested completion", () => {
  const capped = estimateFreeTokenReservation({ maxTokens: 1_000_000 });
  const oneToken = estimateFreeTokenReservation({ maxTokens: 1 });
  assert.equal(capped - oneToken, 65_535);
});

test("free plan requires a Google-backed Firebase session", () => {
  assert.throws(() => requireGoogleSignInForFree("FREE", "anonymous"), /Google/);
  assert.doesNotThrow(() => requireGoogleSignInForFree("FREE", "google.com"));
  assert.doesNotThrow(() => requireGoogleSignInForFree("PRO", "anonymous"));
});
