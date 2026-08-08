# Play Data Safety working inventory

This is the implementation inventory for the Play Console form, not a substitute for reviewing the exact current questions and every bundled SDK at submission time.

| Data type | Collected/shared | Purpose | Handling |
| --- | --- | --- | --- |
| Account identity (Firebase uid, optional Google email/profile) | Collected | Account management, authentication, subscriptions | Encrypted in transit; deletion available |
| Purchases and subscription identifiers | Collected | Verify and maintain entitlement, prevent token reuse | Encrypted in transit; deleted with account except required Google records |
| Prompts, responses, attachments, tool results | Processed when cloud/managed AI is used; shared with the chosen AI provider | App functionality | Local for on-device mode; cloud processing depends on selected mode/provider |
| Conversations, workspaces, memory, local files | Stored on device | App functionality and personalization | Included in Android backup except encrypted secrets; removed by in-app account deletion/local data clear |
| API keys and provider endpoints | Stored on device | User-configured BYOK access | Encrypted preferences; API keys excluded from backup |
| Optional usage metadata | Collected only when the user enables global statistics; managed-service operational usage is collected | Analytics, reliability, quota, billing | No prompt/response/file/API-key content; user-linked event rows deleted with account; anonymous aggregates may remain |
| Crash and technical diagnostics | Collected when error reporting is enabled | Reliability and security | No prompts, responses, files, URLs, tool arguments, or credentials intentionally attached |
| Reported AI content and optional comment | Collected only when user taps Report | Safety, abuse prevention, quality | Pseudonymous user id; retained up to 90 days; deleted with account |
| Contacts, calendar, precise/approximate location, microphone, camera | Accessed after runtime permission and explicit action | Assistant features requested by user | May remain on-device or be included in the selected AI request depending on the action |

## Console checks before submission

- Confirm whether Play treats each upstream AI provider as a service provider or a third party for the selected processing mode.
- Confirm Firebase Crashlytics collection state and its current SDK disclosures.
- Confirm Google Play Billing and Firebase Authentication disclosures.
- Verify that no new dependency adds advertising ID, analytics, device identifiers, or background collection.
- Ensure the privacy policy, in-app disclosures, consent screen, and form use the same retention and deletion language.
- Deploy the included Firestore TTL policy on `contentReports.expiresAt` so the stated 90-day report retention is enforced automatically.
