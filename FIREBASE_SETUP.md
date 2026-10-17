# Firebase setup for NexusAI

Google sign-in cannot be activated by Android code alone. This repository currently has no `app/google-services.json`, so the APK can build for Guest Mode, but Firebase Authentication does not have a project to connect to.

## One-time setup

1. Open [Firebase Console](https://console.firebase.google.com/) and create or select a Firebase project you own.
2. In Project settings, add an **Android app** with this exact package name: `com.aistudio.nexusai.qkzvpx`.
3. Download the generated `google-services.json` file.
4. In Firebase Console, open **Authentication → Sign-in method** and enable **Google**. Complete the support-email prompt.
5. Add the config to GitHub without pasting it into a chat:
   - Preferred for this public repository: open **Settings → Secrets and variables → Actions → New repository secret**, name it `GOOGLE_SERVICES_JSON`, and paste the entire contents of `google-services.json` as the secret value.
   - Alternatively, add the downloaded file at `app/google-services.json` in a private local checkout. Avoid posting config screenshots or any private credentials publicly.
6. Re-run the **Build NexusAI APK** workflow. If Google Sign-In later reports a developer-configuration error, the Android signing certificate SHA-1 may also need to be added under the Android app in Firebase/Google Cloud.

## Important

- Do not upload a Firebase service-account JSON or any private service-account key. The Android `google-services.json` is a client configuration file, not a replacement for securing Firebase rules.
- Guest Mode does not require Firebase sign-in.
- Firestore security rules must be configured separately before storing user data in the cloud.
