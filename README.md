# Sermon Prep

Offline sermon preparation and Bible study for Android. Kotlin · Jetpack Compose · Material 3 · MVVM · Room · Hilt · min SDK 26.

## What it does

| Area | Feature |
|---|---|
| Outlines | Create, edit (auto-save), duplicate, delete. Import TXT / DOCX / PDF. Export PDF / DOCX. Three sample outlines included. |
| Bible | Download translations on demand (KJV, ASV, BSB, NHEB, Tagalog 1905, Cebuano) or import your own file. Stored in Room; works offline afterwards. Chapter reader, verse-by-verse stepping, bookmarks, favorites. |
| References | `John 3:16`, `Psalm 23:1-6`, `1 Cor 13`, `I John 4:8`, `Jude 3`, plus some Tagalog/Cebuano book names are detected in outlines and open a floating verse window (tap the reference, or highlight it). Text comes from your selected translation. |
| Search | Offline, three layers: exact references → curated topic/question index (`assets/topics.json`) → full-text (SQLite FTS4) over one or all downloaded translations. Related-passage chips on results. Copy, share, add to outline, open chapter. |
| Offline AI (optional) | Import a Gemma model file you download yourself. It powers "meaning" search (the model proposes references; verse text always comes from your Bible database) and the Sermon Assistant. Without a model the assistant gives a template from the topic index. |
| Settings | Preferred translation, manage downloads, Light / Dark / System, language (English, Tagalog, Bisaya). |

## Read this before relying on it

- **Not compiled or run yet.** The code was written without an Android SDK or Maven access. The GitHub Actions workflow will be its first real build, so expect to fix a few compile errors on first push. Checked so far: the reference regex and CSV reader were executed in Java against the real Bible files and matched; string resources are consistent across all three languages.
- **ESV is not included.** Crossway does not allow free offline distribution. If you hold a licence, use *Import from file*.
- **Cebuano text licence is unconfirmed.** The file is in a public GitHub dataset, but I could not verify the copyright status of that translation. Check before distributing the app with it preloaded.
- **Tagalog / Bisaya UI strings are my own translations.** Have a native speaker review them.
- **"Semantic" search is limited.** The topic index is a hand-written starter set (about 30 topics, English keywords). It does handle the five example queries from the spec by design, but this is not yet tested on a device. Real meaning-based search needs the optional AI model, and model suggestions can be wrong (the UI labels them).
- **MediaPipe's LLM API is maintenance-only** per Google's docs; LiteRT-LM is the recommended successor. All runtime code lives in `ai/LocalLlm.kt` so it can be swapped.
- **PDF import uses OCR** (ML Kit, bundled, offline): accuracy depends on the file, capped at 40 pages.
- **arm64 devices only** (the AI runtime ships arm64 libraries). Remove `abiFilters` in `app/build.gradle.kts` to change that.

## Docs

- [Installation guide](docs/INSTALL.md)
- [Build instructions](docs/BUILD.md)
- [Architecture](docs/ARCHITECTURE.md)
