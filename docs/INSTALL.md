# Installation guide

## Install the APK
1. On GitHub, open **Actions → Build APK →** latest green run → download the **sermonprep-debug-apk** artifact (or the `.apk` from **Releases** if a version tag was pushed). Unzip it.
2. Copy the `.apk` to your phone (Android 8.0 or newer, 64-bit ARM).
3. Open it. Android will ask to allow installs from this source (your browser or file manager). Allow it, then install.

Debug builds are signed with a debug key, so you will see a "not from Play Store" warning. For a normal release signature, see `docs/BUILD.md`.

## First run
1. Open **Settings → Bible translations** and tap **Download** on at least one translation (needs internet once; roughly 4–5 MB each). The first one becomes your preferred translation.
2. Pick Light / Dark and your language in Settings if you want.
3. Open **Sermons**. Three samples are provided; tap one, then tap a highlighted reference (e.g. *John 3:16*) to see the verse window.

After step 1 everything works with no connection.

## Optional: offline AI
1. Download a model file on a computer or phone yourself, e.g. **Gemma 3n E2B/E4B** (`.litertlm`) or a smaller Gemma `.task` file from Google's AI Edge / LiteRT community pages. Gemma downloads require accepting Google's licence on the host site.
2. **Settings → Offline AI model → Import model file**, and pick it. Copying several GB takes a while.
3. In **Search**, turn on *Use offline AI*. In **Assistant**, generate an outline.

Models are large (a few GB) and need plenty of free RAM; low-end phones may crash while loading. Start with the smaller model.

## Your own Bible file
**Settings → Import from file…** accepts:
- CSV with header `Book,Chapter,Verse,Text`
- JSON array of 66 books, each `{"chapters": [["verse 1", "verse 2"], ...]}`
- text lines `book|chapter|verse|text` (book as 1–66 or a name)
