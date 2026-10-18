# Building NexusAI APK with GitHub Actions

The workflow is at [Build NexusAI APK](https://github.com/Haidarthamer/HDR.ai/actions/workflows/build-apk.yml).

## Run a fresh build after changing repository secrets

Repository-secret changes do not trigger a build automatically. Open the workflow page, tap **Run workflow**, select the `main` branch, and tap **Run workflow** again. The workflow reads `GOOGLE_SERVICES_JSON` at build time and validates that it matches the Android package `com.aistudio.nexusai.qkzvpx`.

## Download the APK

After the run finishes successfully, open that run and download the `NexusAI-debug-apk` artifact. GitHub Actions artifacts are temporary and expire after the retention period shown by GitHub.

## What a successful build means

A green build confirms that the APK compiled and the workflow's configuration checks passed. It does not by itself verify that Google Sign-In works on a physical device. The workflow summary reports whether the JSON config and Web OAuth client ID are present; Firebase Authentication providers must also be enabled in the Firebase Console.
