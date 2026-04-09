---
afad: "3.5"
version: "0.1.0-SNAPSHOT"
domain: SQLITE_SCHEMA_COUNTRY_PACKS
updated: "2026-04-08"
route:
  keywords: [fingrind, sqlite, country-pack, jurisdiction, extension, schema-policy, future-rules]
  questions: ["how should future country-specific sqlite schema be documented in fingrind", "where do jurisdiction tables belong in fingrind", "how should country packs extend the sqlite schema"]
---

# SQLite Country-Pack Schema Policy

**Purpose**: Documentation policy for future country-specific SQLite schema extensions.

## Current State

FinGrind does not currently ship any country-pack tables.

Today there is only:
- the jurisdiction-agnostic core schema in [SCHEMA_CORE.md](./SCHEMA_CORE.md)

## Future Documentation Rule

When country-specific schema appears, document it in separate files under `docs/sqlite/`.

Recommended shape:

```text
docs/sqlite/
├── SCHEMA_CORE.md
├── SCHEMA_COUNTRY_PACKS.md
├── SCHEMA_COUNTRY_LV.md
├── SCHEMA_COUNTRY_DE.md
└── ...
```

## Future Design Rules

Country-pack extensions may add:
- tax tables
- reporting mappings
- evidence-reference tables
- jurisdiction-specific validation or filing state

Country-pack extensions must not:
- redefine debit or credit semantics
- replace the core posting and journal-line tables
- weaken the one-book-per-file contract
- hide jurisdiction state inside undocumented free-form blobs

## Migration Discipline

When a country-pack schema is introduced:
- add a dedicated documentation file for that jurisdiction
- document the new tables and their link back to the core posting identity
- keep the core-schema document current rather than copying it into country files

This file is intentionally a policy placeholder until the first real country-pack schema exists.
