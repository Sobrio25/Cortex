import assert from "node:assert/strict";
import test from "node:test";
import { sanitizeForFree, validateFreeRequest } from "./privacy";

test("free route blocks credentials and private tool output", () => {
  assert.equal(validateFreeRequest({ messages: [{ role: "user", content: "my API key is abc" }] }).safe, false);
  assert.equal(validateFreeRequest({ messages: [{ role: "tool", name: "read_file", content: "x" }] }).safe, false);
});

test("free route keeps only public tools and replaces private system context", () => {
  const sanitized = sanitizeForFree({
    systemPrompt: "private memory",
    tools: [
      { function: { name: "weather" } },
      { function: { name: "read_file" } },
    ],
  });
  assert.equal(sanitized.tools.length, 1);
  assert.equal(sanitized.systemPrompt.includes("private memory"), false);
});
