import assert from "node:assert/strict";
import test from "node:test";
import { FREE_MESSAGES_LIMIT, quotaPeriodKey } from "./store";

test("free allowance is 100 messages per ISO week", () => {
  assert.equal(FREE_MESSAGES_LIMIT, 100);
  assert.equal(quotaPeriodKey(new Date("2026-07-15T23:59:59Z")), "2026-W29");
});

test("weekly quota uses ISO year boundaries", () => {
  assert.equal(quotaPeriodKey(new Date("2027-01-01T12:00:00Z")), "2026-W53");
  assert.equal(quotaPeriodKey(new Date("2027-01-04T00:00:00Z")), "2027-W01");
});
