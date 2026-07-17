# Cortex usage dashboards

The private dashboard is hosted by the existing Firebase project and contains two
independent views:

- **Subscriptions**: managed-plan requests, exact upstream token usage when available,
  fallbacks and estimated Cortex cost.
- **Global Cortex usage**: managed subscriptions, user-provided providers (BYOK), and
  local models. This view intentionally has no cost total because BYOK and local costs
  are not comparable or reliably known by Cortex.

Both views read only backend-generated analytics projections. The browser is not a
direct Firestore client, so the deny-all client rules remain unchanged.

## URL and access

Firebase Hosting serves the dashboard at:

```text
https://cortex-agents-ai.web.app
```

Access requires Google sign-in plus one of these server-side checks:

- a verified email listed in the comma-separated `ADMIN_EMAILS` Functions variable;
- or a Firebase Auth custom claim named `admin` with the boolean value `true`.

`ADMIN_EMAILS` is stored as a Firebase Functions secret. Configure or rotate the
comma-separated allow-list with `firebase functions:secrets:set ADMIN_EMAILS`; do
not place real addresses in source control or implement access control in browser code.

## Subscription data model

Successful managed inference and final managed inference errors create backend-only
records in these collections:

- `usageEvents`: the most recent request-level rows used by the activity table;
- `usageDaily`: daily totals used by the chart and KPI cards;
- `usageDailyModels`: daily model/provider/gateway totals used by the breakdowns.

Stored fields are limited to Firebase UID, hashed turn ID, plan, logical
model, actual routed model when the upstream returns it, provider/gateway, input and
output token counts, estimated cost, latency, free/fallback flags, fallback category
and status. Prompts,
responses, images, tool arguments, tool output and credentials are never stored.

These records feed only the **Subscriptions** view.

## Global app data model

Managed inference is copied server-side into the global projection. Android wraps all
non-managed `AIClient` implementations and posts best-effort metadata after each BYOK
or local inference. These records use separate collections:

- `appUsageEvents`: recent request-level global activity;
- `appUsageDaily`: global daily totals and source counts;
- `appUsageDailyModels`: daily source/provider/model totals.

The client endpoint is `POST /v1/app-usage`. It requires a Firebase ID token, accepts
only `byok` or `local` as client sources, bounds every numeric field, and deduplicates
events by a client-generated ID. Clients cannot claim that an event came from a managed
subscription; those events are written only by the inference backend.

Global events store only a one-way shortened user hash, source, provider, model, input
and output token estimates, duration, operation, app version and success/error status.
Prompts, responses, files, images, tool payloads, API keys and provider credentials are
never sent to the telemetry endpoint. BYOK/local token counts are estimates; managed
requests retain the upstream measurement flag.

Global analytics is enabled by default and can be disabled in **Settings → Privacy and
analytics → Global usage statistics**. Reporting is asynchronous and failures never
change or delay a model response.

Both dashboards start collecting data only after their instrumented clients and
Functions are deployed; they cannot reconstruct historical requests.

## Local verification

```bash
cd functions
npm test
cd ..
firebase emulators:start --only functions,firestore,hosting
```

## Production deployment

```bash
firebase functions:secrets:set ADMIN_EMAILS --project cortex-agents-ai
firebase deploy --only functions:api,hosting --project cortex-agents-ai
```

The Hosting rewrite keeps `/v1/admin/usage` and `/v1/admin/app-usage` on the same
origin. The API verifies the Firebase ID token and admin authorization on every
request and returns no-store responses. Firestore remains inaccessible from browser
and Android clients.
