# Sermon Studio

Offline-first Android app for sermon preparation and Bible study. Kotlin, Jetpack Compose (Material 3), MVVM, Hilt, Room. minSdk 26.

> **Status: source complete, NOT yet compiled or run on a device.** It was written without an Android SDK available. Expect a few compile errors on first build (see docs/BUILD.md). All dependency versions in `app/build.gradle.kts` and `gradle/` were chosen from memory; verify them.

## What is implemented

| Area | State |
|---|---|
| Sermon list, folders, search, duplicate, archive, delete | Written |
| Editor (markup, live scripture detection, popup, undo/redo, auto-save) | Written |
| Bible reader, multi-version, side-by-side compare | Written |
| Version management (import TSV/Zefania/zip, URL download, delete) | Written |
| Hybrid search (reference, topic lexicon, FTS, hashed vectors) | Written |
| AI assistant: template engine (offline) | Written |
| AI assistant: on-device LLM via MediaPipe (optional model file) | Written, MediaPipe API unverified |
| Notes, tags, bookmarks | Written |
| Export TXT/PDF/DOCX; import TXT/DOCX/PDF | Written |
| Backup/restore (JSON, daily worker), settings, theme, EN/TL/CEB UI | Written |
| Unit tests | Reference parser and tokenizer only |

## Honest limitations

- **"Semantic" search is lexical.** It combines a hand-written topic lexicon (`assets/knowledge/topics.json`, 22 topics) with hashed n-gram vectors. It is not a neural embedding model. Quality will be weaker than true semantic search. `docs/ARCHITECTURE.md` describes the upgrade path.
- **topics.json is hand-authored** and should be reviewed by a pastor before use.
- **Only 62 KJV verses are bundled** (public domain) for demo. Install a full Bible file to use the app seriously.
- **No ESV/NIV/NKJV text is bundled.** They are copyrighted; obtain licensed files. Check the licence of any other translation (Tagalog, Cebuano, ASV) before importing. No download URLs are included because none were verified.
- **Tagalog and Cebuano UI strings were machine-written** and need native-speaker review.
- **AI drafts are starting points.** Review all generated text and verse references.
- Backups are stored in app-private storage; use "Export backup" to keep a copy elsewhere.
- The Gradle wrapper jar is not included. Run `gradle wrapper` or open in Android Studio.

## Layout

```
app/src/main/java/com/sermonstudio/app/
  data/{db,bible,search,knowledge,ai,io,prefs}   # Room, parser, search, AI, export/backup
  di/  ui/{theme,common,sermons,editor,bible,search,assistant,notes,settings}
app/src/main/assets/{bible,knowledge,samples}
docs/  .github/workflows/android.yml
```

See `docs/` for build, install, user manual, architecture and Bible import.
