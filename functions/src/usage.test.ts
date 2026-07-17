import assert from "node:assert/strict";
import test from "node:test";
import { isUsageAdmin, parseUsageDays, utcDayKey } from "./usage";
import { providerNameForModel } from "./upstream";

test("usage range only accepts supported windows", () => {
  assert.equal(parseUsageDays("7"), 7);
  assert.equal(parseUsageDays(90), 90);
  assert.equal(parseUsageDays("365"), 30);
  assert.equal(parseUsageDays(undefined), 30);
});

test("usage day keys are UTC calendar days", () => {
  assert.equal(utcDayKey(new Date("2026-07-15T23:59:59-06:00")), "2026-07-16");
});

test("usage admins require a custom claim or a verified allow-listed email", () => {
  assert.equal(isUsageAdmin({ admin: true }, ""), true);
  assert.equal(isUsageAdmin({ email: "owner@example.com", email_verified: true }, "owner@example.com"), true);
  assert.equal(isUsageAdmin({ email: "OWNER@example.com", email_verified: true }, "owner@example.com"), true);
  assert.equal(isUsageAdmin({ email: "owner@example.com", email_verified: false }, "owner@example.com"), false);
  assert.equal(isUsageAdmin({ email: "other@example.com", email_verified: true }, "owner@example.com"), false);
});

test("model namespaces map to readable providers", () => {
  assert.equal(providerNameForModel("openai/gpt-5", "Gateway"), "OpenAI");
  assert.equal(providerNameForModel("moonshotai/kimi-k2", "Gateway"), "Moonshot AI");
  assert.equal(providerNameForModel("custom/model", "Gateway"), "Gateway");
});
