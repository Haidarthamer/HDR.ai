# Firebase setup for NexusAI

The authentication code is in place, but authentication cannot work until this app is connected to a real Firebase project. The repository does not include `app/google-services.json`; the build workflow will now report this clearly instead of implying that auth is ready.

## 1. Create and register the Android app

1. Open the [Firebase Console](https://console.firebase.google.com/) and sign in to the Google account that should own the project.
2. Create a Firebase project, or select one you already own.
3. Add an **Android app** with this exact, case-sensitive package name: `com.aistudio.nexusai.qkzvpx`.
4. In the Android app's Firebase settings, add this SHA-1 certificate fingerprint for the stable CI debug key prepared for NexusAI:

   `7B:72:75:69:11:F5:A8:D1:59:FF:DB:D9:E8:00:1F:E8:6D:4C:02:CD`

   The CI workflow supports a stable signing key so that Google Sign-In's certificate fingerprint does not change on each build.

## 2. Enable both sign-in methods

In Firebase Console, open **Authentication → Sign-in method** (wording may be **Build → Authentication** in the current console):

- Enable **Email/Password** and save.
- Enable **Google**, choose a project support email, and save.

After adding the SHA-1 and enabling Google, download a fresh `google-services.json` for the Android app. The build workflow checks for the matching package and warns if the Web OAuth client ID required by Google Sign-In is missing.

Official guides:
- [Add Firebase to an Android project](https://firebase.google.com/docs/android/setup)
- [Email/password authentication on Android](https://firebase.google.com/docs/auth/android/password-auth)
- [Google Sign-In on Android](https://firebase.google.com/docs/auth/android/google-signin)

## 3. Add GitHub Actions secrets

Open the public repository's **Settings → Secrets and variables → Actions → New repository secret** page. Add these secrets exactly as named below.

| Secret name | Value |
|---|---|
| `GOOGLE_SERVICES_JSON` | The entire contents of the newly downloaded `google-services.json` file |
| `ANDROID_DEBUG_KEYSTORE_BASE64` | The entire one-line Base64 text from the private `ANDROID_DEBUG_KEYSTORE_BASE64.txt` file provided for this setup |
| `ANDROID_DEBUG_KEYSTORE_PASSWORD` | The password in the matching private setup file |
| `ANDROID_DEBUG_KEY_ALIAS` | `nexusai-ci-debug` |
| `ANDROID_DEBUG_KEY_PASSWORD` | The password in the matching private setup file (same as the store password) |

Do not commit the keystore file or its Base64 contents to the public repository. Do not put the keystore password in source code or in the repository's README.

## 4. Build and verify

Once all five secrets are saved, push any new commit or run **Actions → Build NexusAI APK → Run workflow**. A correctly configured run should show:

- Firebase JSON validation passed and the package name matches.
- A Web OAuth client ID was found for Google Sign-In.
- The stable CI debug signing key was configured.
- The APK's SHA-1 fingerprint matches the fingerprint above.

If the workflow says **Firebase configuration: MISSING**, the JSON secret was not saved or was empty. If the JSON validation fails, confirm you downloaded it for package `com.aistudio.nexusai.qkzvpx`. If Google Sign-In is missing a Web OAuth client ID, revisit the Google provider setup and download the JSON again.

## Important

- `google-services.json` is Firebase's Android client configuration. Never upload a **Firebase service-account JSON** or other private service-account key.
- Guest Mode does not require Firebase.
- Successful sign-in does not automatically secure or initialize Cloud Firestore. Create the Firestore database and configure restrictive security rules before storing user data.
- The CI keystore prepared here is for debug builds only, not for signing a production release.
