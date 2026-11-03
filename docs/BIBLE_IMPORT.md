# Importing Bible versions

Settings > Manage Bible versions > Import Bible file (or Download from URL). Enter a short ID (e.g. KJV), name, language code and licence note.

## Formats

**TSV** (UTF-8): `book<TAB>chapter<TAB>verse<TAB>text`. `book` is 1-66 (Genesis=1 ... Revelation=66) or a book name. Lines starting with `#` are comments. See `app/src/main/assets/bible/sample_kjv.tsv`.

**Zefania XML**: standard `<XMLBIBLE><BIBLEBOOK bnumber=..><CHAPTER cnumber=..><VERS vnumber=..>` structure.

A `.zip` containing one of these is accepted.

## Licensing

You are responsible for the licence of any text you import. ESV, NIV and NKJV are copyrighted. The catalog (`assets/bible/catalog.json`) ships empty on purpose; no download sources were verified.

Search vectors are built on import, so large Bibles take a minute or more.
