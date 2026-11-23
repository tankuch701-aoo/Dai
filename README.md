<img src="docs/logo.svg" width="96" alt="Dai logo">

# Dai

Chat and code with Gemini, ChatGPT and Claude. Version 2.0.

```
.github/workflows/
  android.yml         builds APK, lint report, signed release on v* tags
  deploy.yml          publishes web/ to GitHub Pages
  ci.yml              checks web JS on pull requests
app_ai/               Android app (Kotlin, Jetpack Compose)
  build.gradle
  src/main/
    AndroidManifest.xml
    java/com/dai/app/ MainActivity, ChatViewModel, data/, ui/
    res/
      values/         strings.xml, colors.xml, themes.xml
      drawable/       ic_launcher_foreground, ic_launcher_background, ic_logo
      mipmap-anydpi-v26/  ic_launcher, ic_launcher_round
web/                  browser version
docs/logo.svg         logo source
```

## Get the APK

Push to GitHub. Open **Actions → Android** and download **dai-debug-apk** from the run.

## Signed release

1. Create a keystore: `keytool -genkeypair -v -keystore release.jks -alias dai -keyalg RSA -keysize 2048 -validity 10000`
2. In **Settings → Secrets and variables → Actions**, add `KEYSTORE_BASE64` (output of `base64 -w0 release.jks`), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
3. Push a tag: `git tag v2.0 && git push origin v2.0`. The release gets the debug and signed APKs.

Never commit the keystore or passwords.
