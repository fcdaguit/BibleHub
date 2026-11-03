# Architecture

Single-module app, MVVM, offline-first. UI observes ViewModel state; ViewModels call repositories; repositories wrap Room, DataStore, files and the optional local LLM. Hilt wires everything.

```
ui/            Compose screens + @HiltViewModels
  sermons/       list, editor (edit/read modes, PDF/DOCX export, import)
  bible/         chapter reader, bookmarks & favorites
  search/        query UI, result cards
  assistant/     sermon assistant
  settings/      theme, language, translations, AI model
  components/    VerseLookupHost (floating verse window), LookupViewModel, LinkedText
  nav/           NavHost, bottom bar
data/
  db/            Room: entities, DAOs, AppDatabase
  bible/         BibleParsers (CSV / JSON / pipe), TopicIndex
  repo/          Bible, Sermon, Mark, Settings repositories + SearchEngine
domain/        BibleBooks (66 books, aliases), ReferenceParser, BibleRef
ai/            LocalLlm (MediaPipe wrapper), SermonAssistant
io/            DocumentIO (PDF/DOCX export; TXT/DOCX/PDF import)
di/            Hilt module
assets/        translations.json (download catalog), topics.json, sample_sermons.json
```

## Data model (Room)
- `bible_versions` (id, name, language, verseCount, downloadedAt)
- `verses` (versionId, book 1–66, chapter, verse, text), unique on the four key fields
- `verses_fts`, an FTS4 table over `verses.text` with the unicode61 tokenizer
- `sermons` (title, content, timestamps)
- `marks` (type = bookmark | favorite, book, chapter, verseStart, verseEnd). Translation-independent.

Settings (theme, language, preferred translation, model path) live in DataStore.

## Key flows
**Reference → verse popup.** `ReferenceParser` finds references with one generated regex (all book names and aliases, longest first), validates the chapter number, and returns `BibleRef`. In the editor a `VisualTransformation` underlines them; a tap or highlight changes the selection without changing the text, which triggers `LookupViewModel.show(ref)`. That loads the verses from the preferred translation and `VerseLookupHost` displays them in a floating dialog. In read mode and in the assistant, references are real `LinkAnnotation` links.

**Translation download.** Catalog entry → HTTP download to cache → streaming parse (`BibleParsers`) → batched insert in one transaction → FTS index is maintained by Room triggers. Downloaded sources are plain public GitHub files, listed in `assets/translations.json`; edit that file to add more.

**Search (`SearchEngine`).** (1) references in the query; (2) `TopicIndex` keyword scoring against curated topics/questions, which also supplies related passages; (3) FTS4: exact phrase, then all words (prefix), then any word; (4) if a model is loaded and the toggle is on, the LLM suggests references, which are looked up in the database. The model never supplies verse wording. Results are de-duplicated and tagged by origin.

**Assistant (`SermonAssistant`).** With a model: a structured prompt → text; references in the output are checked against the selected translation and unknown ones are flagged. Without a model: a template from `TopicIndex`.

**Documents.** PDF export: `StaticLayout` paginated onto `PdfDocument`. DOCX export: minimal OOXML zip. Import: TXT read; DOCX unzip → strip `document.xml`; PDF → `PdfRenderer` pages → ML Kit OCR.

## Extending
- More translations: add entries to `translations.json` (CSV with `Book,Chapter,Verse,Text` is easiest).
- Better topic search: extend `topics.json`, or add a cross-reference dataset (e.g. an openly licensed Treasury of Scripture Knowledge) and feed it into `TopicIndex.relatedTo`.
- Swap the LLM runtime (e.g. LiteRT-LM): reimplement `LocalLlm` only.
