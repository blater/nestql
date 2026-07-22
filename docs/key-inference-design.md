# DQL Key Inference Design

## Contract

nestQL builds a query-independent database graph from JDBC metadata and caches it through the existing persistent H2 cache. Mapped DQL queries bind their relation aliases and hierarchy paths to that graph before execution. Required keys that are absent from the user projection are added as hidden result columns and never appear in output.

Inference uses schema metadata only. It does not sample result rows, retry a query, or change cached decisions in response to data. DML does not use this feature.

`--no-key-inference` disables automatic DQL keys and preserves row-first mapping for paths without explicit structure. `--debug` logs the inferred output-path-to-relation mappings, selected key evidence, and any parent metadata relationships actually used by each query to stderr.

## Explicit Structure Precedence

`structure {path} key (...)` is authoritative for its exact hierarchy path. Its tuple is used verbatim: inference cannot add, remove, reorder, replace, or validate its expressions. Metadata remains available for undeclared ancestors, descendants, and siblings, including paths that use the same alias or base table.

This is deliberately path-scoped. A key can span several aliases, and one table can occur several times with different semantic roles. Table-wide suppression would therefore make partial structure declarations unpredictable. Use `--no-key-inference` when inference must be disabled for the whole query.

## Inferred Graph

The graph records visible non-system tables and views, their columns and types, primary keys, composite primary keys, unique indexes, foreign keys, conventional ID/key columns, type-compatible naming relationships, and logical composite keys for association tables. Candidate precedence is primary key, unique index, conventional key, then logical association key.

The implementation is cohesive under `blater.nestql.inference`:

| Class | Responsibility |
|---|---|
| `KeyInference` | Feature entry point and cache/refresh orchestration |
| `DatabaseStructureInferrer` | Query-independent JDBC graph discovery |
| `SchemaNamingHeuristics` | Deterministic naming and logical-key rules |
| `DatabaseStructure` | Immutable cached graph |
| `KeyInferencePlanner` | Query/path binding and hidden-key selection |
| `CompiledSelect` | Executable SQL and effective mapping plan |

`PersistentCache` remains the single persistent-cache implementation. Database graphs are versioned artifacts under the same cache root and participate in normal listing, expiry, and clearing.

## Ambiguity and Safety

An explicit path has no inference ambiguity: its supplied tuple wins. For undeclared paths, the planner considers mapped expression lineage, aliases, parent relationships, declared relationship strength, candidate-key strength, and stable qualified ordering. Equal-strength choices are deterministic and produce a warning identifying possible data loss.

An all-null inferred key represents an absent joined object. A partially null inferred key uses row-local identity rather than coalescing unrelated rows. If a complete inferred key coalesces rows with conflicting scalar values, nestQL retains the first value and warns once per affected path that data may have been lost. Existing explicit-key conflict behaviour is unchanged.

## Query Shapes

- Ordinary joins use relation keys.
- Grouped queries use the grouping tuple.
- An ungrouped aggregate naturally has singleton result grain.
- `DISTINCT` queries retain their projected grain; inference does not add columns that change distinctness.
- Unsupported or unbindable query shapes fall back to existing row-first behaviour with no query retry.

## Cache and CLI

The default graph expiry is 24 hours. `--metadata-expiry-hours N` persists a target-specific expiry; zero refreshes every use. `--metadata-refresh` rebuilds the selected JDBC or active input-cache target transactionally and exits. Cache identities include connection URL/product/catalog/schema but never passwords.

## Test Strategy

Tests use small H2 schemas and semantic hierarchy assertions. Coverage includes declared/composite/unique/logical keys, naming relationships, persistent graph artifacts, hidden omitted keys, nested parent/child coalescing, exact-path explicit precedence with inferred descendants, opt-out, grouped queries, null fallback, conflict warnings, metadata commands, and regression coverage. Tests avoid full generated-SQL snapshots, exhaustive spelling matrices, sleeps, and repeating every scenario for every output writer.
