# Google Play release runbook

This project uses `com.aiagents.app` for Play, Firebase, billing, and OAuth continuity. Play builds use the `release` variant and include the on-demand `:voice` dynamic feature in the AAB. Direct installs continue to use `sideload`.

## 1. Create the upload identity

Enroll with Play App Signing and let Google generate the app-signing key. Create a separate upload key locally; never reuse `cortex-debug.keystore`:

```bash
keytool -genkeypair -v \
  -keystore /secure/path/cortex-play-upload.jks \
  -alias cortex-play-upload \
  -keyalg RSA -keysize 4096 -validity 10000
```

Copy `keystore.properties.example` to the ignored `keystore.properties`, set the absolute path and secrets, then build:

```bash
./gradlew bundlePlayRelease
```

Back up the upload keystore and passwords in a secure credential vault. Google can reset a lost upload key, but it should not be treated as disposable.

## 2. Configure external services

After Play App Signing is active:

1. Copy the Play **app signing** SHA-1 and SHA-256 fingerprints into the Firebase Android app.
2. Keep the upload-key fingerprints documented separately; they are not the installed app identity.
3. Download the refreshed `google-services.json` if Firebase changes it.
4. Verify Google sign-in, anonymous-account upgrade, Play Billing purchase/restore, and RTDN in an internal test install delivered by Play.

## 3. Play Console declarations

- App access: explain that the app supports anonymous access and provide a reviewer Google account if a gated path needs one.
- Data safety: use `docs/play-data-safety.md` as the working inventory and re-audit every SDK before submission.
- Privacy policy: `https://cortex-agents-ai.web.app/privacy.html`.
- Account deletion URL: `https://cortex-agents-ai.web.app/delete-account.html`.
- AI-generated content: describe the flag button on every completed assistant response.
- Ads: declare no ads unless an ad SDK is added.
- Permissions: justify contacts/calls as assistant actions, calendar as user-created/read events, exact alarms as scheduled reminders, and system settings/app deletion as explicit device-control actions.
- Content rating and target audience: answer from actual product behavior; the app is not designed for children.

## 4. Release sequence

1. Deploy the deletion/reporting API and legal pages before uploading the bundle:

   ```bash
   firebase deploy --only functions:api,hosting:dashboard,firestore:indexes --project cortex-agents-ai
   ```

2. Run `./gradlew test lintRelease bundlePlayRelease` and `npm --prefix functions test`.
3. Upload `app/build/outputs/bundle/release/app-release.aab` to Internal testing.
4. Complete automated pre-launch testing, then test install/update, authentication, subscriptions, dynamic voice download, account deletion, and content reporting on a physical device.
5. Promote through closed testing before production. Personal developer accounts created after 13 November 2023 may need 12 opted-in testers for 14 continuous days before production access.

Increment `versionCode` for every uploaded bundle. Never upload a `sideload` APK/AAB or a build signed with a debug certificate.
