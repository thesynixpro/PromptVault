# PromptVault

PromptVault is a local-first Android app for storing, organizing, searching, and backing up AI prompts, notes, text documents, code snippets, templates, and frequently used text. It is built entirely with Kotlin, Jetpack Compose, and Material Design 3.

## Highlights

- **Local-first:** Room is the source of truth. All content works offline.
- **Optional Telegram cloud backup:** Background WorkManager sync with retry queue and verified Telegram Bot API responses.
- **Rich editor:** Titles, content, categories, folders, tags, favorites, automatic drafts, undo/redo, word/character/line counts.
- **Organization:** Nested folders, custom categories, favorites, filters, sorting, grid/list views.
- **Search:** Instant offline search across titles, full content, tags, categories, and folders, with recent searches and suggestions.
- **Import/export:** Android Storage Access Framework JSON backups, individual TXT/JSON exports, and TXT/JSON imports with duplicate protection.
- **Security:** Bot credentials are encrypted with Android Keystore-backed preferences and are never logged or shown in full.
- **Themes:** Light, dark, system, dynamic color where supported, edge-to-edge UI.

## Telegram backup setup

1. Open [@BotFather](https://t.me/BotFather) and create a bot.
2. Create a private Telegram channel.
3. Add the bot to the channel as an administrator with permission to post messages (and edit/delete if you want those operations).
4. In PromptVault, open **Telegram Cloud**, enter the bot token and channel ID, tap **Verify channel**, then **Save configuration**.

PromptVault uses these Bot API methods: `getMe`, `getChat`, `getChatMember`, `sendMessage`, `sendDocument`, `editMessageText`, and `deleteMessage`. Short content is sent as a message; large content is uploaded as a UTF-8 TXT document. Nothing is marked synced until Telegram confirms the upload.

Deleting an item locally never deletes its Telegram backup unless you explicitly choose cloud deletion from the item action menu.

## Security limitations

A bot token stored inside a mobile app can be extracted by someone with sufficient access to the device or app package. PromptVault encrypts credentials at rest using Android Keystore-backed preferences, but this is not equivalent to a server-side secret. For multi-user or production deployment, use a backend that holds the Telegram token and exposes scoped, authenticated endpoints to the app.

## Architecture

```text
app/src/main/java/com/promptvault/
├── data/
│   ├── database/      Room entities, DAOs, converters, database
│   ├── preferences/   Preferences DataStore settings
│   └── repository/    Local-first repository implementation
├── domain/            Models, search, backup formatting, file payloads
├── security/          Android Keystore-backed credential storage
├── telegram/          Telegram Bot API client and repository
├── workers/           WorkManager sync worker and scheduler
├── ui/
│   ├── components/    Reusable Material 3 components
│   ├── navigation/    Navigation Compose graph and drawer
│   ├── screens/       Functional app screens
│   ├── theme/         Material 3 theme and typography
│   └── viewmodels/    StateFlow-based ViewModels
├── AppContainer.kt    Manual dependency container
└── MainActivity.kt
```

## Build

Requirements:

- JDK 17
- Android SDK platform 35 and build tools 35.0.0 (installed automatically by Gradle or CI)
- Android 8.0 (API 26) or newer device/emulator

```bash
chmod +x gradlew
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`

## GitHub Actions

`.github/workflows/build-apk.yml` runs on every push to `main` and on manual dispatch. It runs unit tests, builds the debug APK, uploads it as an Actions artifact, and then publishes it in a GitHub Release tagged `v1.0.<run number>` with `make_latest: true`.

## Logo

The supplied PromptVault logo is preserved as `app/src/main/res/drawable-nodpi/logo.png` and is used for the launcher adaptive icon and the About screen.

## Version

`versionName = 1.0`, `versionCode = 1`.
