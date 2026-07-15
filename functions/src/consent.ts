export const FREE_DATA_CONSENT_VERSION = 1;

export function hasCurrentFreeDataConsent(acceptedVersion: unknown): boolean {
  const version = Number(acceptedVersion ?? 0);
  return Number.isInteger(version) && version >= FREE_DATA_CONSENT_VERSION;
}

/**
 * Once the user has accepted the free-plan disclosure, their request is sent
 * without content inspection or redaction. The disclosure, rather than a
 * heuristic filter, is the privacy boundary for this route.
 */
export function prepareConsentedFreeRequest<T>(body: T): T {
  return body;
}
