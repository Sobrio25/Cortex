import { createHash } from "node:crypto";

/** Stable pseudonymous identifier used only where an event does not need the Firebase uid. */
export function userHash(uid: string): string {
  return createHash("sha256").update(uid).digest("hex").slice(0, 16);
}
