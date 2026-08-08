import assert from "node:assert/strict";
import test from "node:test";
import { parseContentReport } from "./content-reports";
import { userHash } from "./privacy";

test("userHash is stable and does not expose the uid", () => {
  const uid = "firebase-user-123";
  assert.equal(userHash(uid), userHash(uid));
  assert.equal(userHash(uid).length, 16);
  assert.notEqual(userHash(uid), uid);
});

test("parseContentReport keeps only bounded supported fields", () => {
  const parsed = parseContentReport({
    messageId: 42,
    category: "unsafe",
    content: "respuesta",
    comment: "contexto",
    secret: "must-not-be-stored",
  });
  assert.deepEqual(parsed, {
    messageId: "42",
    category: "unsafe",
    content: "respuesta",
    comment: "contexto",
  });
});

test("parseContentReport rejects unsupported categories and empty content", () => {
  assert.throws(() => parseContentReport({ messageId: "1", category: "spam", content: "x" }));
  assert.throws(() => parseContentReport({ messageId: "1", category: "other", content: "" }));
});
