---
afad: "4.0"
version: "0.61.0"
domain: SQLITE_SCHEMA_CORE
updated: "2026-07-16"
route:
  keywords: [fingrind, sqlite, schema, book_meta, account, posting_fact, journal_line, audit_event, idempotency, canonical-schema, book-file, reversal]
  questions: ["what is the current fingrind sqlite schema", "which tables exist in the fingrind book file", "how is idempotency stored in the sqlite book", "what tables and indexes exist in a fingrind book"]
---

# SQLite Core Schema

**Purpose**: Current durable schema for one FinGrind book file.
**Source of truth**: [`book_schema.sql`](../../sqlite/src/main/resources/dev/erst/fingrind/sqlite/book_schema.sql)
**Generation**: This overview and its companion pages are rendered from the canonical schema file by `scripts/render-sqlite-schema-doc.py`. Do not hand-edit the generated schema reference set.

## Schema Map

Each companion page embeds the exact canonical SQL for one schema responsibility family, and the full set stays in source order with `book_schema.sql`.

- [SCHEMA_CORE_01_FOUNDATION.md](./SCHEMA_CORE_01_FOUNDATION.md): Application id, format version, book metadata, and book identity bootstrap.
- [SCHEMA_CORE_02_ACCOUNT_TABLE.md](./SCHEMA_CORE_02_ACCOUNT_TABLE.md): Declared-account storage, classifications, and parent pointers.
- [SCHEMA_CORE_03_ACCOUNT_RULES.md](./SCHEMA_CORE_03_ACCOUNT_RULES.md): Parent-shape invariants and account immutability triggers.
- [SCHEMA_CORE_03z_TAX_REGISTRATION.md](./SCHEMA_CORE_03z_TAX_REGISTRATION.md): Declared tax registrations, tax-code doctrine, registration-account validation, and tax-registration append-only rules.
- [SCHEMA_CORE_04_POSTING_FACT.md](./SCHEMA_CORE_04_POSTING_FACT.md): Persisted posting identity, provenance, and replay fingerprint.
- [SCHEMA_CORE_04z_POSTING_FACT_ADMISSION.md](./SCHEMA_CORE_04z_POSTING_FACT_ADMISSION.md): Posting effective-date, close-provenance, and opening-window admission gates.
- [SCHEMA_CORE_05_POSTING_SOURCE_DOCUMENT.md](./SCHEMA_CORE_05_POSTING_SOURCE_DOCUMENT.md): Durable source-document attribution for committed postings.
- [SCHEMA_CORE_06_POSTING_APPROVAL.md](./SCHEMA_CORE_06_POSTING_APPROVAL.md): Durable approval references for committed postings.
- [SCHEMA_CORE_06z_POSTING_APPLIED_TAX.md](./SCHEMA_CORE_06z_POSTING_APPLIED_TAX.md): Per-posting resolved tax facts, posting-origin tax admissibility rules, and applied-tax append-only enforcement.
- [SCHEMA_CORE_06za_POSTING_FOREIGN_EXCHANGE.md](./SCHEMA_CORE_06za_POSTING_FOREIGN_EXCHANGE.md): Per-posting owned foreign-exchange facts, posting-origin and functional-currency admissibility rules, and foreign-exchange append-only enforcement.
- [SCHEMA_CORE_07_JOURNAL_LINES.md](./SCHEMA_CORE_07_JOURNAL_LINES.md): Committed journal-line storage and journal-line-side admission gates.
- [SCHEMA_CORE_07z_INVENTORY_MOVEMENT.md](./SCHEMA_CORE_07z_INVENTORY_MOVEMENT.md): Append-only inventory movement replay, ordering, provenance, and opening-balance admission gates.
- [SCHEMA_CORE_07za_INVENTORY_ON_HAND.md](./SCHEMA_CORE_07za_INVENTORY_ON_HAND.md): Materialized quantity and cost-pool state plus inventory-account admission gates.
- [SCHEMA_CORE_08_INTERIM_RESULT_SWEEP_CORE.md](./SCHEMA_CORE_08_INTERIM_RESULT_SWEEP_CORE.md): Sweep-range facts and target-account doctrine for contiguous interim closes.
- [SCHEMA_CORE_09_INTERIM_RESULT_SWEEP_LINKS.md](./SCHEMA_CORE_09_INTERIM_RESULT_SWEEP_LINKS.md): Per-currency sweep totals and generated sweep-posting linkage.
- [SCHEMA_CORE_10_FISCAL_YEAR_CLOSE_TABLE.md](./SCHEMA_CORE_10_FISCAL_YEAR_CLOSE_TABLE.md): Year-close range facts and required target-account pointers.
- [SCHEMA_CORE_11_FISCAL_YEAR_CLOSE_TARGET_RULES.md](./SCHEMA_CORE_11_FISCAL_YEAR_CLOSE_TARGET_RULES.md): Capital, result-holding, and retained-accumulated target-account validation.
- [SCHEMA_CORE_12_FISCAL_YEAR_CLOSE_LINKS.md](./SCHEMA_CORE_12_FISCAL_YEAR_CLOSE_LINKS.md): Generated fiscal-year-close posting linkage and posting-side invariants.
- [SCHEMA_CORE_13_AUDIT_EVENTS.md](./SCHEMA_CORE_13_AUDIT_EVENTS.md): Append-only audit-event storage for lifecycle, posting, and close-operation facts.
- [SCHEMA_CORE_13z_ACCRUAL_CUTOFF.md](./SCHEMA_CORE_13z_ACCRUAL_CUTOFF.md): Durable origins for accrual-basis prepayments, deferred revenue, and accrued expenses.
- [SCHEMA_CORE_13za_ACCRUAL_CUTOFF_APPLICATIONS.md](./SCHEMA_CORE_13za_ACCRUAL_CUTOFF_APPLICATIONS.md): Append-only recognition, settlement, and compensating-reversal facts for accrual cut-offs.
- [SCHEMA_CORE_13zb_FIXED_ASSETS.md](./SCHEMA_CORE_13zb_FIXED_ASSETS.md): Immutable fixed-asset capitalizations plus depreciation and disposal applications.
- [SCHEMA_CORE_13zc_FINANCING.md](./SCHEMA_CORE_13zc_FINANCING.md): Immutable borrowing arrangements plus principal and interest applications.
- [SCHEMA_CORE_13zd_REALIZED_FOREIGN_EXCHANGE.md](./SCHEMA_CORE_13zd_REALIZED_FOREIGN_EXCHANGE.md): Foreign-currency receivable obligations and their one-time realized-settlement facts.
- [SCHEMA_CORE_13ze_LATVIAN_PAYROLL_RUNS.md](./SCHEMA_CORE_13ze_LATVIAN_PAYROLL_RUNS.md): Immutable Latvian monthly-payroll run origins and compensating reversals.
- [SCHEMA_CORE_13zf_LATVIAN_PAYROLL_SETTLEMENTS.md](./SCHEMA_CORE_13zf_LATVIAN_PAYROLL_SETTLEMENTS.md): Immutable Latvian payroll-obligation settlements and compensating reversals.
- [SCHEMA_CORE_14_INDEXES_AND_IMMUTABILITY.md](./SCHEMA_CORE_14_INDEXES_AND_IMMUTABILITY.md): Lookup indexes plus append-only triggers for durable rows that never mutate in place.

## Runtime Integrity Semantics

- Initialized FinGrind books record both `book_meta.initialized_at` and `book_meta.schema_fingerprint_sha256`.
- An opened book is accepted as canonical only when `PRAGMA integrity_check` returns `ok`, `PRAGMA foreign_key_check` returns no rows, the recorded schema fingerprint matches the live canonical schema-object fingerprint, every persisted posting owns journal lines and balances to zero inside one currency bucket, and every persisted money triple decodes through the exact-money codec.
- Posting commits stage journal lines in temporary `pending_journal_line` rows and persist them only after the SQL aggregate gate proves at least two lines, at least one debit, at least one credit, exactly one currency bucket, and a zero signed minor-unit total.

## Schema Posture

- `application_id`: `1179079236`
- `user_version`: `46`
- Canonical durable tables: `book_meta`, `book_identity`, `account`, `tax_registration`, `tax_registration_code`, `posting_fact`, `posting_source_document`, `posting_approval`, `posting_applied_tax`, `posting_foreign_exchange`, `journal_line`, `inventory_movement`, `inventory_on_hand`, `interim_result_sweep`, `interim_result_sweep_total`, `interim_result_sweep_posting`, `fiscal_year_close`, `fiscal_year_close_posting`, `audit_event`, `accrual_cutoff`, `accrual_cutoff_application`, `fixed_asset`, `fixed_asset_application`, `fixed_asset_reversal`, `fixed_asset_application_reversal`, `financing_arrangement`, `financing_application`, `financing_arrangement_reversal`, `financing_application_reversal`, `foreign_currency_obligation`, `foreign_currency_obligation_settlement`, `foreign_currency_obligation_reversal`, `foreign_currency_obligation_settlement_reversal`, `latvian_payroll_run`, `latvian_payroll_run_reversal`, `latvian_payroll_settlement`, `latvian_payroll_settlement_reversal`
- Canonical durable indexes: `latvian_payroll_run_by_employee_month`, `latvian_payroll_settlement_by_run_kind`, `posting_fact_by_prior_posting_id`, `posting_fact_by_effective_recorded_posting`, `tax_registration_code_by_registration_id`, `posting_applied_tax_by_tax_registration_id`, `journal_line_by_account_code`, `inventory_movement_by_account_replay`, `inventory_movement_by_posting_id`, `accrual_cutoff_application_by_cutoff_horizon`, `audit_event_by_recorded_at`, `interim_result_sweep_by_effective_date_to`, `interim_result_sweep_total_by_currency`, `interim_result_sweep_posting_by_posting_id`, `fiscal_year_close_by_effective_date_to`, `fiscal_year_close_posting_by_posting_id`, `posting_fact_one_reversal_per_target`
- There is no schema version table.
- There are no migration files.
- The current public line rejects non-matching book formats instead of upgrading them in place.
