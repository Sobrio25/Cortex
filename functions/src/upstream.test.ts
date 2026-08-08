import assert from "node:assert/strict";
import test from "node:test";
import { MODELS } from "./catalog";
import { calculateCost, callFree, callMimoFree, callZai, UpstreamError } from "./upstream";

test("cost uses configured input and output token prices", () => {
  assert.equal(calculateCost(MODELS["deepseek-v4-flash"], {
    promptTokens: 1_000,
    completionTokens: 500,
    estimated: false,
  }), 280);
});

test("compatible responses accept text, text objects, and text parts", async () => {
  const originalFetch = globalThis.fetch;
  const responses = [
    { choices: [{ message: { content: "plain" }, finish_reason: "stop" }] },
    { choices: [{ message: { content: { text: "object" } }, finish_reason: "stop" }] },
    { choices: [{ message: { content: [{ type: "text", text: "parts" }] }, finish_reason: "stop" }] },
  ];
  globalThis.fetch = (async () => new Response(JSON.stringify(responses.shift()), {
    status: 200,
    headers: { "content-type": "application/json" },
  })) as typeof fetch;
  try {
    for (const expected of ["plain", "object", "parts"]) {
      const result = await callMimoFree({ messages: [{ role: "user", content: "test" }] }, "test-opencode");
      assert.equal(result.response.content, expected);
    }
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("compatible responses reject content without text", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = (async () => new Response(JSON.stringify({
    choices: [{ message: { content: { value: true } }, finish_reason: "stop" }],
  }), { status: 200, headers: { "content-type": "application/json" } })) as typeof fetch;
  try {
    await assert.rejects(
      callMimoFree({ messages: [{ role: "user", content: "test" }] }, "test-opencode"),
      (error: unknown) => error instanceof UpstreamError && error.category === "MALFORMED_RESPONSE",
    );
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("free inference forwards tools and falls back without credentials or network", async () => {
  const originalFetch = globalThis.fetch;
  const originalRandom = Math.random;
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
    Math.random = () => 0.999;
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
    Math.random = originalRandom;
    globalThis.fetch = originalFetch;
  }
});

test("free flash preference includes the private flash route in deterministic order", async () => {
  const originalFetch = globalThis.fetch;
  const originalRandom = Math.random;
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
    Math.random = () => 0.999;
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
    Math.random = originalRandom;
    globalThis.fetch = originalFetch;
  }
});

test("direct Z.AI route sends the requested model and language header", async () => {
  const originalFetch = globalThis.fetch;
  let request: { url: string; init?: RequestInit } | undefined;
  globalThis.fetch = (async (input: string | URL | Request, init?: RequestInit) => {
    request = { url: String(input), init };
    return new Response(JSON.stringify({
      model: "glm-4.6v-flash",
      choices: [{ finish_reason: "stop", message: { content: "ok", reasoning_content: "" } }],
      usage: { prompt_tokens: 4, completion_tokens: 2 },
    }), { status: 200, headers: { "content-type": "application/json" } });
  }) as typeof fetch;

  try {
    const result = await callZai({
      messages: [{ role: "user", content: "hola" }],
    }, "test-zai");
    const headers = request?.init?.headers as Record<string, string>;
    const body = JSON.parse(String(request?.init?.body));
    assert.equal(request?.url, "https://api.z.ai/api/paas/v4/chat/completions");
    assert.equal(headers.Authorization, "Bearer test-zai");
    assert.equal(headers["Accept-Language"], "en-US,en");
    assert.equal(body.model, "glm-4.6v-flash");
    assert.equal(result.upstream.gateway, "Z.AI API");
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("MiMo Free route sends image data through OpenCode Zen", async () => {
  const originalFetch = globalThis.fetch;
  let request: { url: string; init?: RequestInit } | undefined;
  globalThis.fetch = (async (input: string | URL | Request, init?: RequestInit) => {
    request = { url: String(input), init };
    return new Response(JSON.stringify({
      model: "mimo-v2.5-free",
      choices: [{ finish_reason: "stop", message: { content: JSON.stringify({ fields: {} }) } }],
      usage: { prompt_tokens: 5, completion_tokens: 3 },
    }), { status: 200, headers: { "content-type": "application/json" } });
  }) as typeof fetch;

  try {
    const result = await callMimoFree({
      systemPrompt: "Extract values.",
      messages: [{ role: "user", content: "imagen", imageDataUri: "data:image/jpeg;base64,AA==" }],
    }, "test-opencode");
    const headers = request?.init?.headers as Record<string, string>;
    const body = JSON.parse(String(request?.init?.body));
    assert.equal(request?.url, "https://opencode.ai/zen/v1/chat/completions");
    assert.equal(headers.Authorization, "Bearer test-opencode");
    assert.equal(body.model, "mimo-v2.5-free");
    assert.deepEqual(body.messages[1].content[1], { type: "image_url", image_url: { url: "data:image/jpeg;base64,AA==" } });
    assert.equal(result.upstream.model, "mimo-v2.5-free");
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("free image routing uses MiMo Free through OpenCode Zen", async () => {
  const originalFetch = globalThis.fetch;
  let requestUrl = "";
  globalThis.fetch = (async (input: string | URL | Request) => {
    requestUrl = String(input);
    return new Response(JSON.stringify({
      model: "mimo-v2.5-free",
      choices: [{ finish_reason: "stop", message: { content: "ok" } }],
      usage: { prompt_tokens: 1, completion_tokens: 1 },
    }), { status: 200, headers: { "content-type": "application/json" } });
  }) as typeof fetch;

  try {
    const result = await callFree({
      messages: [{ role: "user", content: "imagen", imageDataUri: "data:image/jpeg;base64,AA==" }],
    }, { openCode: "test-opencode" }, false);
    assert.equal(requestUrl, "https://opencode.ai/zen/v1/chat/completions");
    assert.equal(result.upstream.model, "mimo-v2.5-free");
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
