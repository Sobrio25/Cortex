# Managed subscriptions

The Android client and Firebase backend are linked to project `cortex-agents-ai`.
End users only see plan and model names. Gateway/provider identities are internal and
must never be copied into onboarding, subscription UI, public API errors, or fallback notices.

The free allowance is 2,000,000 combined input and output tokens per ISO week
(Monday 00:00 UTC). Every inference operation is metered from upstream usage. A
conservative reservation prevents concurrent requests from exceeding the allowance
and is settled to actual usage when the response completes.

Free inference requires a Firebase session whose current sign-in provider is Google.
Anonymous sessions are used only to bootstrap account and purchase screens; the API
rejects them on the free route. This keeps the weekly allowance attached to the same
Google-backed Firebase UID after app data is cleared or the app is reinstalled.

## Routing invariant

- Every paid inference request goes to Vercel AI Gateway at
  `https://ai-gateway.vercel.sh/v1/chat/completions`.
- OpenRouter is only part of the free-capacity chain. OpenCode and Kilo are also
  free fallbacks; none is exposed by the public API.
- BYOK providers and local models remain independent of managed usage and billing.
- The backend stores entitlement, counters, token cost, latency/error metadata and
  hashed turn IDs. It does not store prompts, responses, images or tool output.

## Cloud setup completed

- Firebase project `cortex-agents-ai` and Android app `com.aiagents.app` are registered.
- Billing is linked, Firestore rules/indexes are deployed, and Authentication is
  configured for anonymous bootstrap plus required Google sign-in for free usage.
- Pub/Sub topic `projects/cortex-agents-ai/topics/play-billing-rtdn` exists and Google
  Play has the required publisher role.
- All four inference secrets have an enabled version and the `api` and
  `googlePlayRtdn` Functions are deployed in `us-central1`.
- The public API, anonymous authentication, free inference and both free fallbacks
  passed production smoke tests.

## Remaining production setup

1. Add a valid payment card to the Vercel team that owns the AI Gateway key. Vercel
   currently recognizes the key but rejects paid inference until a card is on file.
2. Link `255797346887-compute@developer.gserviceaccount.com` to the Play Console
   developer account with permission to read and acknowledge subscriptions.
3. Create these monthly Google Play subscription products and active base plans:

   | Product | Reference price |
   |---|---:|
   | `cortex_starter` | USD 4.99 |
   | `cortex_plus` | USD 9.99 |
   | `cortex_pro` | USD 19.99 |
   | `cortex_max` | USD 49.99 |
   | `cortex_ultra` | USD 99.99 |

4. Configure Real-time Developer Notifications in Play Console with the Pub/Sub topic
   `projects/cortex-agents-ai/topics/play-billing-rtdn`. The backend consumes the
   topic with an authenticated Firebase event trigger; there is no public RTDN endpoint.
5. Add the Play App Signing SHA-1 and SHA-256 fingerprints to the Firebase Android app
   before the production release. The local debug fingerprints are already registered.

## Deploy and verify

```bash
cd functions && npm test && cd ..
firebase deploy --only auth,firestore,functions --project cortex-agents-ai
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew test assembleDebug
```

After deployment, verify that anonymous free inference is rejected, Google sign-in
persists the same quota across reinstall, token usage is settled from upstream usage,
privacy blocking consumes no quota, and all five purchase/restore flows, renewal reset,
upgrade spend carry-over, budget exhaustion and paid-to-free fallback still work.
