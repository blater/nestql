# Wikidata Company Query Examples

These scripts query `wikidata-companies.json` through nestQL cache mode. For
example:

```bash
nestql --cache docs/data/wikidata-row-first.nql docs/data/wikidata-companies.json
```

The examples use the cache tables created from the dataset:

- `company`
- `company_industries`
- `company_roles`

| Script | Demonstrates |
|---|---|
| `wikidata-row-first.nql` | One output object per SQL result row without structure keys. |
| `wikidata-keyed-companies.nql` | A keyed company object with keyed industry children. |
| `wikidata-composite-nested-keys.nql` | Composite role keys nested below keyed companies. |
| `wikidata-sibling-keys.nql` | Keyed industry and role siblings from one join-expanded result. |
| `wikidata-hierarchy-union.nql` | Explicit `hierarchy union` branch composition. |
