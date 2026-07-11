"""Canonical schema-doc section ownership for the SQLite schema renderer."""

from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class SchemaSection:
    key: str
    file_name: str
    title: str
    purpose: str
    coverage: str
    domain: str


SECTIONS = [
    SchemaSection(
        key="foundation",
        file_name="SCHEMA_CORE_01_FOUNDATION.md",
        title="SQLite Schema: Foundation",
        purpose="Application id, format version, book metadata, and book identity bootstrap.",
        coverage="`pragma application_id`, `pragma user_version`, `book_meta`, and `book_identity`.",
        domain="SQLITE_SCHEMA_CORE_FOUNDATION",
    ),
    SchemaSection(
        key="account-table",
        file_name="SCHEMA_CORE_02_ACCOUNT_TABLE.md",
        title="SQLite Schema: Account Table",
        purpose="Declared-account storage, classifications, and parent pointers.",
        coverage="`account`.",
        domain="SQLITE_SCHEMA_CORE_ACCOUNT_TABLE",
    ),
    SchemaSection(
        key="account-rules",
        file_name="SCHEMA_CORE_03_ACCOUNT_RULES.md",
        title="SQLite Schema: Account Rules",
        purpose="Parent-shape invariants and account immutability triggers.",
        coverage="Account parent validation and account append-only triggers.",
        domain="SQLITE_SCHEMA_CORE_ACCOUNT_RULES",
    ),
    SchemaSection(
        key="tax-registration",
        file_name="SCHEMA_CORE_03z_TAX_REGISTRATION.md",
        title="SQLite Schema: Tax Registration",
        purpose="Declared tax registrations, tax-code doctrine, registration-account validation, and tax-registration append-only rules.",
        coverage="`tax_registration`, `tax_registration_code`, and tax-registration validation/append-only triggers.",
        domain="SQLITE_SCHEMA_CORE_TAX_REGISTRATION",
    ),
    SchemaSection(
        key="posting-fact",
        file_name="SCHEMA_CORE_04_POSTING_FACT.md",
        title="SQLite Schema: Posting Fact",
        purpose="Persisted posting identity, provenance, and replay fingerprint.",
        coverage="`posting_fact`.",
        domain="SQLITE_SCHEMA_CORE_POSTING_FACT",
    ),
    SchemaSection(
        key="posting-fact-admission",
        file_name="SCHEMA_CORE_04z_POSTING_FACT_ADMISSION.md",
        title="SQLite Schema: Posting Fact Admission",
        purpose="Posting effective-date, close-provenance, and opening-window admission gates.",
        coverage="Posting-fact admission triggers.",
        domain="SQLITE_SCHEMA_CORE_POSTING_FACT_ADMISSION",
    ),
    SchemaSection(
        key="posting-source-document",
        file_name="SCHEMA_CORE_05_POSTING_SOURCE_DOCUMENT.md",
        title="SQLite Schema: Posting Source Documents",
        purpose="Durable source-document attribution for committed postings.",
        coverage="`posting_source_document`.",
        domain="SQLITE_SCHEMA_CORE_POSTING_SOURCE_DOCUMENT",
    ),
    SchemaSection(
        key="posting-approval",
        file_name="SCHEMA_CORE_06_POSTING_APPROVAL.md",
        title="SQLite Schema: Posting Approvals",
        purpose="Durable approval references for committed postings.",
        coverage="`posting_approval`.",
        domain="SQLITE_SCHEMA_CORE_POSTING_APPROVAL",
    ),
    SchemaSection(
        key="posting-applied-tax",
        file_name="SCHEMA_CORE_06z_POSTING_APPLIED_TAX.md",
        title="SQLite Schema: Posting Applied Tax",
        purpose="Per-posting resolved tax facts, posting-origin tax admissibility rules, and applied-tax append-only enforcement.",
        coverage="`posting_applied_tax` and posting-applied-tax validation/append-only triggers.",
        domain="SQLITE_SCHEMA_CORE_POSTING_APPLIED_TAX",
    ),
    SchemaSection(
        key="posting-foreign-exchange",
        file_name="SCHEMA_CORE_06za_POSTING_FOREIGN_EXCHANGE.md",
        title="SQLite Schema: Posting Foreign Exchange",
        purpose="Per-posting owned foreign-exchange facts, posting-origin and functional-currency admissibility rules, and foreign-exchange append-only enforcement.",
        coverage="`posting_foreign_exchange` and posting-foreign-exchange validation/append-only triggers.",
        domain="SQLITE_SCHEMA_CORE_POSTING_FOREIGN_EXCHANGE",
    ),
    SchemaSection(
        key="journal-lines",
        file_name="SCHEMA_CORE_07_JOURNAL_LINES.md",
        title="SQLite Schema: Journal Lines",
        purpose="Committed journal-line storage and journal-line-side admission gates.",
        coverage="`journal_line` and journal-line validation triggers.",
        domain="SQLITE_SCHEMA_CORE_JOURNAL_LINES",
    ),
    SchemaSection(
        key="inventory-movement",
        file_name="SCHEMA_CORE_07z_INVENTORY_MOVEMENT.md",
        title="SQLite Schema: Inventory Movement Ledger",
        purpose="Append-only inventory movement replay, ordering, provenance, and opening-balance admission gates.",
        coverage="`inventory_movement` and inventory-movement validation triggers.",
        domain="SQLITE_SCHEMA_CORE_INVENTORY_MOVEMENT",
    ),
    SchemaSection(
        key="inventory-on-hand",
        file_name="SCHEMA_CORE_07za_INVENTORY_ON_HAND.md",
        title="SQLite Schema: Inventory On-Hand State",
        purpose="Materialized quantity and cost-pool state plus inventory-account admission gates.",
        coverage="`inventory_on_hand` and inventory-on-hand validation triggers.",
        domain="SQLITE_SCHEMA_CORE_INVENTORY_ON_HAND",
    ),
    SchemaSection(
        key="interim-result-sweep-core",
        file_name="SCHEMA_CORE_08_INTERIM_RESULT_SWEEP_CORE.md",
        title="SQLite Schema: Interim Result Sweep Core",
        purpose="Sweep-range facts and target-account doctrine for contiguous interim closes.",
        coverage="`interim_result_sweep` and interim-result-sweep range/target triggers.",
        domain="SQLITE_SCHEMA_CORE_INTERIM_RESULT_SWEEP_CORE",
    ),
    SchemaSection(
        key="interim-result-sweep-links",
        file_name="SCHEMA_CORE_09_INTERIM_RESULT_SWEEP_LINKS.md",
        title="SQLite Schema: Interim Result Sweep Links",
        purpose="Per-currency sweep totals and generated sweep-posting linkage.",
        coverage="`interim_result_sweep_total`, `interim_result_sweep_posting`, and posting-link validation triggers.",
        domain="SQLITE_SCHEMA_CORE_INTERIM_RESULT_SWEEP_LINKS",
    ),
    SchemaSection(
        key="fiscal-year-close-table",
        file_name="SCHEMA_CORE_10_FISCAL_YEAR_CLOSE_TABLE.md",
        title="SQLite Schema: Fiscal Year Close Table",
        purpose="Year-close range facts and required target-account pointers.",
        coverage="`fiscal_year_close`.",
        domain="SQLITE_SCHEMA_CORE_FISCAL_YEAR_CLOSE_TABLE",
    ),
    SchemaSection(
        key="fiscal-year-close-target-rules",
        file_name="SCHEMA_CORE_11_FISCAL_YEAR_CLOSE_TARGET_RULES.md",
        title="SQLite Schema: Fiscal Year Close Target Rules",
        purpose="Capital, result-holding, and retained-accumulated target-account validation.",
        coverage="Fiscal-year-close target-account validation trigger.",
        domain="SQLITE_SCHEMA_CORE_FISCAL_YEAR_CLOSE_TARGET_RULES",
    ),
    SchemaSection(
        key="fiscal-year-close-links",
        file_name="SCHEMA_CORE_12_FISCAL_YEAR_CLOSE_LINKS.md",
        title="SQLite Schema: Fiscal Year Close Links",
        purpose="Generated fiscal-year-close posting linkage and posting-side invariants.",
        coverage="`fiscal_year_close_posting` and posting-link validation triggers.",
        domain="SQLITE_SCHEMA_CORE_FISCAL_YEAR_CLOSE_LINKS",
    ),
    SchemaSection(
        key="audit-events",
        file_name="SCHEMA_CORE_13_AUDIT_EVENTS.md",
        title="SQLite Schema: Audit Events",
        purpose="Append-only audit-event storage for lifecycle, posting, and close-operation facts.",
        coverage="`audit_event` and close-operation audit validation trigger.",
        domain="SQLITE_SCHEMA_CORE_AUDIT_EVENTS",
    ),
    SchemaSection(
        key="indexes-and-immutability",
        file_name="SCHEMA_CORE_14_INDEXES_AND_IMMUTABILITY.md",
        title="SQLite Schema: Indexes And Immutability",
        purpose="Lookup indexes plus append-only triggers for durable rows that never mutate in place.",
        coverage="All durable indexes and append-only reject-update/reject-delete triggers.",
        domain="SQLITE_SCHEMA_CORE_INDEXES_AND_IMMUTABILITY",
    ),
]
SECTION_BY_KEY = {section.key: section for section in SECTIONS}

TABLE_SECTION_BY_NAME = {
    "book_meta": "foundation",
    "book_identity": "foundation",
    "account": "account-table",
    "tax_registration": "tax-registration",
    "tax_registration_code": "tax-registration",
    "posting_fact": "posting-fact",
    "posting_source_document": "posting-source-document",
    "posting_approval": "posting-approval",
    "posting_foreign_exchange": "posting-foreign-exchange",
    "posting_applied_tax": "posting-applied-tax",
    "journal_line": "journal-lines",
    "inventory_movement": "inventory-movement",
    "inventory_on_hand": "inventory-on-hand",
    "interim_result_sweep": "interim-result-sweep-core",
    "interim_result_sweep_total": "interim-result-sweep-links",
    "interim_result_sweep_posting": "interim-result-sweep-links",
    "fiscal_year_close": "fiscal-year-close-table",
    "fiscal_year_close_posting": "fiscal-year-close-links",
    "audit_event": "audit-events",
}

TRIGGER_SECTION_BY_PREFIX = (
    ("account_validate_", "account-rules"),
    ("account_reject_", "account-rules"),
    ("tax_registration_validate_", "tax-registration"),
    ("tax_registration_reject_", "tax-registration"),
    ("posting_fact_validate_", "posting-fact-admission"),
    ("posting_fact_reject_", "indexes-and-immutability"),
    ("posting_foreign_exchange_validate_", "posting-foreign-exchange"),
    ("posting_foreign_exchange_reject_", "posting-foreign-exchange"),
    ("posting_applied_tax_validate_", "posting-applied-tax"),
    ("posting_applied_tax_reject_", "posting-applied-tax"),
    ("journal_line_validate_", "journal-lines"),
    ("journal_line_reject_", "indexes-and-immutability"),
    ("inventory_movement_validate_", "inventory-movement"),
    ("inventory_movement_reject_", "indexes-and-immutability"),
    ("inventory_on_hand_validate_", "inventory-on-hand"),
    ("book_identity_reject_", "indexes-and-immutability"),
    ("interim_result_sweep_validate_", "interim-result-sweep-core"),
    ("interim_result_sweep_reject_", "indexes-and-immutability"),
    ("interim_result_sweep_total_reject_", "indexes-and-immutability"),
    ("interim_result_sweep_posting_validate_", "interim-result-sweep-links"),
    ("interim_result_sweep_posting_reject_", "indexes-and-immutability"),
    ("fiscal_year_close_validate_", "fiscal-year-close-target-rules"),
    ("fiscal_year_close_reject_", "indexes-and-immutability"),
    ("fiscal_year_close_posting_validate_", "fiscal-year-close-links"),
    ("fiscal_year_close_posting_reject_", "indexes-and-immutability"),
    ("audit_event_validate_", "audit-events"),
    ("audit_event_reject_", "indexes-and-immutability"),
)
