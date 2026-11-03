# Architecture

**MVVM**: Compose screens observe `StateFlow`/Compose state in Hilt ViewModels; ViewModels use repositories and DAOs. Single-activity, navigation-compose.

**Bible DB**: Room tables `bible_versions`, `verses` (versionId, book 1-66, chapter, verse, text), FTS4 index on verse text (porter tokenizer) and `verse_vectors`. Books are numeric so a reference resolves in any version.

**Search** (`data/search/SearchEngine.kt`): 1) direct reference parse; 2) curated topic/question lexicon from `topics.json` (IDF-weighted overlap, gives verses, parallels, cross-refs); 3) FTS phrase/keyword; 4) cosine search over int8 512-dim hashed vectors cached in RAM, with query expansion from the lexicon. Scores are additive heuristics, untuned.

**Upgrade path to real semantic search**: implement `TextEmbedder` with an on-device sentence-embedding model (e.g. ONNX Runtime) and rebuild vectors; bind it in `BindingsModule`. Nothing else changes.

**AI**: `AiRepository` picks `TemplateSermonEngine` (offline, no free text) or `LlmSermonEngine` (MediaPipe, user-supplied model file) and falls back to the template on failure. LLM output is grounded on installed verses and every reference is re-validated by `ReferenceParser`.

**Export/Backup**: `ExportManager` (TXT, PDF via PdfDocument, minimal hand-built DOCX), `ImportManager`, `BackupManager` (JSON; WorkManager daily, keeps 7).

**Localisation**: `LocaleHelper` + `attachBaseContext`; `values-tl`, `values-b+ceb`.
