---
afad: "5.0.1"
version: "0.61.0"
domain: SQLITE_SCHEMA_CORE_AUDIT_EVENTS
updated: "2026-07-17"
---

# SQLite Schema: Audit Events

**Purpose**: Append-only audit-event storage for lifecycle, posting, and close-operation facts.
**Source of truth**: [`book_schema.sql`](../../sqlite/src/main/resources/dev/erst/fingrind/sqlite/book_schema.sql)
**Generation**: This page is rendered from the canonical schema file by `scripts/render-sqlite-schema-doc.py`. **Coverage**: `audit_event` and close-operation audit validation trigger.

```sql
create table if not exists audit_event (
    audit_event_order integer primary key,
    recorded_at text not null check (
        (
            recorded_at glob '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9]Z'
            or (
                substr(recorded_at, 1, 19) glob '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9]'
                and substr(recorded_at, 20, 1) = '.'
                and substr(recorded_at, length(recorded_at), 1) = 'Z'
                and (
                    (length(recorded_at) = 24 and substr(recorded_at, 21, 3) not glob '*[^0-9]*')
                    or (length(recorded_at) = 27 and substr(recorded_at, 21, 6) not glob '*[^0-9]*')
                    or (length(recorded_at) = 30 and substr(recorded_at, 21, 9) not glob '*[^0-9]*')
                )
            )
        )
        and substr(recorded_at, 6, 2) between '01' and '12'
        and (
            (
                substr(recorded_at, 6, 2) in ('01', '03', '05', '07', '08', '10', '12')
                and substr(recorded_at, 9, 2) between '01' and '31'
            )
            or (
                substr(recorded_at, 6, 2) in ('04', '06', '09', '11')
                and substr(recorded_at, 9, 2) between '01' and '30'
            )
            or (
                substr(recorded_at, 6, 2) = '02'
                and (
                    substr(recorded_at, 9, 2) between '01' and '28'
                    or (
                        substr(recorded_at, 9, 2) = '29'
                        and (
                            cast(substr(recorded_at, 1, 4) as integer) % 400 = 0
                            or (
                                cast(substr(recorded_at, 1, 4) as integer) % 4 = 0
                                and cast(substr(recorded_at, 1, 4) as integer) % 100 <> 0
                            )
                        )
                    )
                )
            )
        )
        and substr(recorded_at, 12, 2) between '00' and '23'
        and substr(recorded_at, 15, 2) between '00' and '59'
        and substr(recorded_at, 18, 2) between '00' and '59'
    ),
    event_kind text not null check (
        event_kind in (
            'BOOK_OPENED',
            'ACCOUNT_DECLARED',
            'ACCOUNT_REACTIVATED',
            'ACCOUNT_RENAMED',
            'ACCOUNT_AMENDED',
            'ACCOUNT_RETIRED',
            'POSTING_COMMITTED',
            'POSTING_REVERSED',
            'BOOK_REKEYED',
            'BACKUP_CREATED',
            'BACKUP_RESTORED',
            'REKEY_ROLLBACK_RESTORED',
            'REKEY_ROLLBACK_DELETED',
            'BACKUP_CREATED_COMPENSATED',
            'REKEY_ROLLBACK_DELETED_COMPENSATED',
            'INTERIM_RESULT_SWEPT',
            'FISCAL_YEAR_CLOSED'
        )
    ),
    account_code text,
    posting_id text,
    close_operation_order integer,
    foreign key (account_code) references account (account_code),
    foreign key (posting_id) references posting_fact (posting_id),
    check (
        (
            event_kind in (
                'BOOK_OPENED',
                'BOOK_REKEYED',
                'BACKUP_CREATED',
                'BACKUP_RESTORED',
                'REKEY_ROLLBACK_RESTORED',
                'REKEY_ROLLBACK_DELETED',
                'BACKUP_CREATED_COMPENSATED',
                'REKEY_ROLLBACK_DELETED_COMPENSATED'
            )
            and account_code is null
            and posting_id is null
            and close_operation_order is null
        )
        or
        (
            event_kind in (
                'ACCOUNT_DECLARED',
                'ACCOUNT_REACTIVATED',
                'ACCOUNT_RENAMED',
                'ACCOUNT_AMENDED',
                'ACCOUNT_RETIRED'
            )
            and account_code is not null
            and posting_id is null
            and close_operation_order is null
        )
        or
        (
            event_kind in ('POSTING_COMMITTED', 'POSTING_REVERSED')
            and account_code is null
            and posting_id is not null
            and close_operation_order is null
        )
        or
        (
            event_kind in ('INTERIM_RESULT_SWEPT', 'FISCAL_YEAR_CLOSED')
            and account_code is null
            and posting_id is null
            and close_operation_order is not null
        )
    )
) strict;

create trigger if not exists audit_event_validate_close_operation_order_on_insert
before insert on audit_event
when new.close_operation_order is not null
begin
    select raise(
        fail,
        'interim-result-swept audit events must reference one existing interim_result_sweep row.'
    )
    where
        new.event_kind = 'INTERIM_RESULT_SWEPT'
        and not exists (
            select 1
            from interim_result_sweep
            where interim_result_sweep.interim_result_sweep_order = new.close_operation_order
        );
    select raise(
        fail,
        'fiscal-year-closed audit events must reference one existing fiscal_year_close row.'
    )
    where
        new.event_kind = 'FISCAL_YEAR_CLOSED'
        and not exists (
            select 1
            from fiscal_year_close
            where fiscal_year_close.fiscal_year_close_order = new.close_operation_order
        );
end;
```
