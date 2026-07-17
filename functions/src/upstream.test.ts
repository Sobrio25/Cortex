import assert from "node:assert/strict";
import test from "node:test";
import { MODELS } from "./catalog";
import { calculateCost, callFree, UpstreamError } from "./upstream";

test("cost uses configured input and output token prices", () => {
  assert.equal(calculateCost(MODELS["deepseek-v4-flash"], {
    promptTokens: 1_000,
    completionTokens: 500,
    estimated: false,
  }), 280);
});

test("free inference forwards tools and falls back without credentials or network", async () => {
  const originalFetch = globalThis.fetch;
  const requests: Array<{ url: string; body: any }> = [];
  globalThis.fetch = (async (input: string | URL | Request, init?: RequestInit) => {
    requests.push({
      url: String(input),
      body: JSON.parse(String(init?.body ?? "{}")),
    });
    if (requests.length === 1) {
      return new Response(JSON.stringify({ error: { message: "capacity" } }), {
        status: 503,
        headers: { "content-type": "application/json" },
      });
    }
    return new Response(JSON.stringify({
      choices: [{
        finish_reason: "tool_calls",
        message: {
          content: "",
          tool_calls: [{
            id: "weather-1",
            type: "function",
            function: { name: "weather_current", arguments: "{}" },
          }],
        },
      }],
      usage: { prompt_tokens: 20, completion_tokens: 5 },
    }), {
      status: 200,
      headers: { "content-type": "application/json" },
    });
  }) as typeof fetch;

  try {
    const tools = [{
      type: "function",
      function: { name: "weather_current", parameters: { type: "object" } },
    }];
    const result = await callFree(
      {
        systemPrompt: "Use tools.",
        messages: [{ role: "user", content: "¿Qué tiempo hace?" }],
        tools,
      },
      { openRouter: "test-openrouter", kilo: "test-kilo" },
      false,
    );

    assert.equal(requests.length, 2);
    assert.match(requests[0].url, /openrouter/);
    assert.match(requests[1].url, /kilo/);
    assert.equal(requests[0].body.model, "openrouter/free");
    assert.equal(requests[1].body.model, "kilo-auto/free");
    assert.deepEqual(requests[0].body.tools, tools);
    assert.deepEqual(requests[1].body.tools, tools);
    assert.equal((result.response.toolCalls as any[])[0].function.name, "weather_current");
    assert.deepEqual(result.usage, { promptTokens: 20, completionTokens: 5, estimated: false });
    assert.deepEqual(result.upstream, {
      provider: "Kilo AI",
      model: "kilo-auto/free",
      gateway: "Kilo AI",
      fallback: true,
      fallbackCategory: "CAPACITY",
    });
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("free flash preference uses the private flash route first", async () => {
  const originalFetch = globalThis.fetch;
  const requests: string[] = [];
  globalThis.fetch = (async (input: string | URL | Request) => {
    requests.push(String(input));
    return new Response(JSON.stringify({
      choices: [{ finish_reason: "stop", message: { content: "ok" } }],
      usage: { prompt_tokens: 1, completion_tokens: 1 },
    }), {
      status: 200,
      headers: { "content-type": "application/json" },
    });
  }) as typeof fetch;

  try {
    const result = await callFree(
      { messages: [{ role: "user", content: "hola" }] },
      { openCode: "test-opencode", openRouter: "test-openrouter", kilo: "test-kilo" },
      true,
    );

    assert.equal(result.response.content, "ok");
    assert.deepEqual(result.upstream, {
      provider: "OpenCode",
      model: "deepseek-v4-flash-free",
      gateway: "OpenCode Zen",
      fallback: false,
    });
    assert.equal(requests.length, 1);
    assert.match(requests[0], /opencode\.ai\/zen/);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("free routing does not retry non-recoverable 4xx responses", async () => {
  const originalFetch = globalThis.fetch;
  let requests = 0;
  globalThis.fetch = (async () => {
    requests += 1;
    return new Response(JSON.stringify({ error: { message: "invalid tools" } }), {
      status: 400,
      headers: { "content-type": "application/json" },
    });
  }) as typeof fetch;

  try {
    await assert.rejects(
      callFree(
        { messages: [{ role: "user", content: "hola" }] },
        { openRouter: "test-openrouter", kilo: "test-kilo" },
        false,
      ),
      (error: unknown) => error instanceof UpstreamError &&
        error.category === "INVALID_REQUEST" && error.retryable === false,
    );
    assert.equal(requests, 1);
  } finally {
    globalThis.fetch = originalFetch;
  }
});
