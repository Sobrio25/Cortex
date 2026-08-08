import assert from "node:assert/strict";
import test from "node:test";
import {
  FiberMeterScanError,
  _test,
  normalizeFiberMeterScanResult,
  parseFiberMeterScanRequest,
} from "./fibermeter-scan";

const imageDataUri = `data:image/jpeg;base64,${Buffer.alloc(12).toString("base64")}`;

function validBody() {
  return {
    schemaVersion: 1,
    requestId: "scan-test-1",
    imageDataUri,
    locale: "es",
  };
}

test("scan request accepts a bounded image data URI", () => {
  const parsed = parseFiberMeterScanRequest(validBody());
  assert.equal(parsed.schemaVersion, 1);
  assert.equal(parsed.locale, "es");
});

test("scan request rejects unknown fields and invalid image types", () => {
  assert.throws(
    () => parseFiberMeterScanRequest({ ...validBody(), extra: true }),
    (error: unknown) => error instanceof FiberMeterScanError && error.code === "INVALID_REQUEST",
  );
  assert.throws(
    () => parseFiberMeterScanRequest({ ...validBody(), imageDataUri: imageDataUri.replace("image/jpeg", "image/gif") }),
    (error: unknown) => error instanceof FiberMeterScanError && error.code === "INVALID_REQUEST",
  );
});

test("scan result normalizes decimal commas and hides invalid readings", () => {
  const result = normalizeFiberMeterScanResult({
    fields: {
      nap: { value: "-17,4 dBm", visible: true, confidence: 0.9 },
      drop: { value: "not visible", visible: true, confidence: 0.9 },
      ont: { value: -101, visible: true, confidence: 1 },
    },
  });
  assert.deepEqual(result.fields.nap, { value: -17.4, visible: true, confidence: 0.9 });
  assert.deepEqual(result.fields.drop, { value: null, visible: false, confidence: 0 });
  assert.deepEqual(result.fields.ont, { value: null, visible: false, confidence: 0 });
});

test("scan result rejects unknown field names", () => {
  assert.throws(
    () => normalizeFiberMeterScanResult({ fields: { nap: {}, drop: {}, ont: {}, serial: {} } }),
    (error: unknown) => error instanceof FiberMeterScanError && error.code === "INVALID_RESULT",
  );
});

test("model JSON parser accepts a fenced JSON object without exposing surrounding text", () => {
  assert.deepEqual(_test.parseModelJson("```json\n{\"fields\":{}}\n```"), { fields: {} });
});

test("service token is valid only before its embedded expiration", () => {
  const random = "a".repeat(43);
  const valid = `fm1.1700604800.${random}`;
  const expired = `fm1.1700000000.${random}`;

  assert.equal(_test.serviceTokenIsValid(valid, valid, 1700000000), true);
  assert.equal(_test.serviceTokenIsValid(expired, expired, 1700604800), false);
  assert.equal(_test.serviceTokenIsValid(valid, `${valid}x`, 1700000000), false);
  assert.equal(_test.serviceTokenIsValid("secret", "secret", 1700000000), false);
});

test("scan handler uses the OpenCode credential for the free vision route", () => {
  assert.match(_test.SYSTEM_PROMPT, /niveles de potencia claramente visibles/);
});
