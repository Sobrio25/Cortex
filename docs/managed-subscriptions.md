# Managed subscriptions

The Android client and Firebase backend are linked to project `cortex-agents-ai`.
End users only see plan and model names. Gateway/provider identities are internal and
must never be copied into onboarding, subscription UI, public API errors, or fallback notices.

The free allowance is 30 user messages per calendar month (UTC). Tool-call loops for
the same user message reuse one allowance entry.

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
- Billing is linked, Firestore rules/indexes are deployed, and anonymous Authentication
  is enabled and verified.
- Pub/Sub topic `projects/cortex-agents-ai/topics/play-billing-rtdn` exists and Google
  Play has the required publisher role.

## Remaining production setup

1. Store real keys without writing them to the repository:

   ```bash
   firebase functions:secrets:set VERCEL_AI_GATEWAY_KEY
   firebase functions:secrets:set OPENROUTER_API_KEY
   firebase functions:secrets:set KILO_GATEWAY_API_KEY
   firebase functions:secrets:set OPENCODE_API_KEY
   ```

2. Link the Functions runtime service account to the Play Console developer account
   with permission to read and acknowledge subscriptions.
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

## Deploy and verify

```bash
cd functions && npm test && cd ..
firebase deploy --only firestore,functions --project cortex-agents-ai
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew test assembleDebug
```

After deployment, verify an anonymous sign-in, account fetch, one free turn, privacy
blocking without quota consumption, all five purchase/restore flows, renewal reset,
upgrade spend carry-over, budget exhaustion and paid-to-free fallback.
