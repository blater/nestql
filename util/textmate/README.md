# nestQL TextMate Bundle

`nestQL.tmbundle` provides TextMate syntax highlighting and editing support for
`.nql` files.

It covers:

- query mappings, hierarchy paths, attributes, mapping functions, and null policy;
- row-first `structure ... key (...)` declarations and composite keys;
- `hierarchy union` plus the legacy `xmlunion` and `createsnew` spellings;
- `using` metadata, output directives, templates, and statement terminators;
- mapped `insert`, `update`, `delete`, `returns`, and stored procedure calls;
- capture/temp rowsets, transaction controls, handlers, includes, and literal SQL;
- common SQL keywords, types, functions, operators, strings, and identifiers.

## Install

Open `util/textmate/nestQL.tmbundle` with TextMate, or place the bundle in:

```text
~/Library/Application Support/TextMate/Managed/Bundles/
```

Reload bundles from TextMate's Bundles menu after installation. The language is
selected automatically for files ending in `.nql`.

`Samples/Language Tour.nql` is a highlighting fixture covering the language
surface. It is illustrative and is not intended to run as one script.

## Validate

On macOS, validate every property list with:

```bash
find util/textmate/nestQL.tmbundle -name '*.plist' -o -name '*.tmLanguage' -o -name '*.tmPreferences' -o -name '*.tmSnippet' | xargs -n1 plutil -lint
```
