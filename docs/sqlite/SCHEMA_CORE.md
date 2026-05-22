---
afad: "4.0"
version: "0.44.0"
domain: SQLITE_SCHEMA_CORE
updated: "2026-05-22"
route:
  keywords: [fingrind, sqlite, schema, book_meta, account, posting_fact, journal_line, audit_event, idempotency, canonical-schema, book-file, reversal]
  questions: ["what is the current fingrind sqlite schema", "which tables exist in the fingrind book file", "how is idempotency stored in the sqlite book", "what tables and indexes exist in a fingrind book"]
---

# SQLite Core Schema

**Purpose**: Current durable schema for one FinGrind book file.
**Source of truth**: [`book_schema.sql`](../../sqlite/src/main/resources/dev/erst/fingrind/sqlite/book_schema.sql)
**Generation**: This document is rendered from the canonical schema file by `scripts/render-sqlite-schema-doc.py`. Do not hand-edit the derived schema inventory below.

## Canonical SQL

```sql
pragma application_id = 1179079236;
pragma user_version = 15;

create table if not exists book_meta (
    meta_key text primary key check (
        meta_key in ('initialized_at', 'schema_fingerprint_sha256')
    ),
    value text not null check (length(trim(value)) > 0)
) strict;

create table if not exists book_identity (
    singleton_id integer primary key check (singleton_id = 1),
    entity_name text not null check (length(trim(entity_name)) > 0),
    functional_currency_code text not null check (
        length(functional_currency_code) = 3
        and functional_currency_code glob '[A-Z][A-Z][A-Z]'
    ),
    fiscal_year_start_month integer not null check (
        fiscal_year_start_month between 1 and 12
    ),
    fiscal_year_start_day integer not null check (
        fiscal_year_start_day between 1 and 31
    ),
    check (
        date(
            printf(
                '2000-%02d-%02d',
                fiscal_year_start_month,
                fiscal_year_start_day
            )
        ) is not null
    )
) strict;

create table if not exists entity_profile (
    singleton_id integer primary key check (singleton_id = 1),
    entity_form text not null check (
        entity_form in (
            'FREELANCER',
            'SOLE_PROPRIETORSHIP',
            'COMPANY',
            'PARTNERSHIP',
            'NONPROFIT',
            'BRANCH',
            'OTHER'
        )
    ),
    owner_model text not null check (
        owner_model in (
            'SOLE_OWNER',
            'MULTI_OWNER',
            'MEMBERSHIP_BODY',
            'NO_PRIVATE_OWNER'
        )
    ),
    business_activity_tags text not null,
    foreign key (singleton_id) references book_identity (singleton_id)
) strict;

create table if not exists book_policy (
    singleton_id integer primary key check (singleton_id = 1),
    policy_profile text not null check (
        policy_profile in ('INTERNAL_MANAGEMENT_SINGLE_ENTITY_V1')
    ),
    foreign key (singleton_id) references book_identity (singleton_id)
) strict;

create table if not exists account (
    account_code text primary key check (
        length(account_code) between 1 and 255
        and account_code glob '[A-Za-z0-9]*'
        and account_code not glob '*[^A-Za-z0-9._:/-]*'
    ),
    account_name text not null check (length(trim(account_name)) > 0),
    account_type text not null check (
        account_type in ('ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'EXPENSE')
    ),
    account_role text not null check (account_role in ('ORDINARY', 'CONTRA')),
    account_node_kind text not null check (account_node_kind in ('HEADER', 'POSTABLE')),
    parent_account_code text references account (account_code),
    financial_position_line_classification text check (
        financial_position_line_classification is null
        or financial_position_line_classification in (
            'CURRENT_ASSET',
            'NONCURRENT_ASSET',
            'CURRENT_LIABILITY',
            'NONCURRENT_LIABILITY',
            'OWNER_CAPITAL',
            'OWNER_DRAWINGS',
            'PARTNER_CAPITAL',
            'PARTNER_CURRENT',
            'SHARE_CAPITAL',
            'RETAINED_EARNINGS',
            'ACCUMULATED_SURPLUS',
            'RESERVE',
            'OTHER_EQUITY'
        )
    ),
    profit_and_loss_line_classification text check (
        profit_and_loss_line_classification is null or profit_and_loss_line_classification in (
            'OPERATING_REVENUE',
            'OTHER_REVENUE',
            'FINANCE_INCOME',
            'COST_OF_SALES',
            'OPERATING_EXPENSE',
            'DEPRECIATION_AND_AMORTIZATION',
            'FINANCE_EXPENSE',
            'TAX_EXPENSE'
        )
    ),
    active integer not null check (active in (0, 1)),
    declared_at text not null,
    check (
        parent_account_code is null or parent_account_code <> account_code
    ),
    check (
        (
            account_type = 'ASSET'
            and financial_position_line_classification in ('CURRENT_ASSET', 'NONCURRENT_ASSET')
            and profit_and_loss_line_classification is null
        )
        or
        (
            account_type = 'LIABILITY'
            and financial_position_line_classification in (
                'CURRENT_LIABILITY', 'NONCURRENT_LIABILITY'
            )
            and profit_and_loss_line_classification is null
        )
        or
        (
            account_type = 'EQUITY'
            and financial_position_line_classification in (
                'OWNER_CAPITAL',
                'OWNER_DRAWINGS',
                'PARTNER_CAPITAL',
                'PARTNER_CURRENT',
                'SHARE_CAPITAL',
                'RETAINED_EARNINGS',
                'ACCUMULATED_SURPLUS',
                'RESERVE',
                'OTHER_EQUITY'
            )
            and profit_and_loss_line_classification is null
        )
        or
        (
            account_type = 'REVENUE'
            and financial_position_line_classification is null
            and profit_and_loss_line_classification in (
                'OPERATING_REVENUE',
                'OTHER_REVENUE',
                'FINANCE_INCOME'
            )
        )
        or
        (
            account_type = 'EXPENSE'
            and financial_position_line_classification is null
            and profit_and_loss_line_classification in (
                'COST_OF_SALES',
                'OPERATING_EXPENSE',
                'DEPRECIATION_AND_AMORTIZATION',
                'FINANCE_EXPENSE',
                'TAX_EXPENSE'
            )
        )
    )
) strict;

create trigger if not exists account_validate_parent_on_insert
before insert on account
when new.parent_account_code is not null
begin
    select raise(fail, 'account parent must be active.')
    where exists (
        select 1
        from account as parent
        where
            parent.account_code = new.parent_account_code
            and parent.active = 0
    );
    select raise(fail, 'account parent must share the child account type.')
    where exists (
        select 1
        from account as parent
        where
            parent.account_code = new.parent_account_code
            and parent.account_type <> new.account_type
    );
    select raise(fail, 'account parent must share the child account role.')
    where exists (
        select 1
        from account as parent
        where
            parent.account_code = new.parent_account_code
            and parent.account_role <> new.account_role
    );
    select raise(fail, 'account parent must be a header node.')
    where exists (
        select 1
        from account as parent
        where
            parent.account_code = new.parent_account_code
            and parent.account_node_kind <> 'HEADER'
    );
    select raise(fail, 'account parent must share the child statement classification.')
    where exists (
        select 1
        from account as parent
        where
            parent.account_code = new.parent_account_code
            and (
                coalesce(parent.financial_position_line_classification, '')
                    <> coalesce(new.financial_position_line_classification, '')
                or coalesce(parent.profit_and_loss_line_classification, '')
                    <> coalesce(new.profit_and_loss_line_classification, '')
            )
    );
    with recursive ancestors (account_code, parent_account_code) as (
        select
            account_seed.account_code,
            account_seed.parent_account_code
        from account as account_seed
        where account_seed.account_code = new.parent_account_code
        union all
        select
            account.account_code,
            account.parent_account_code
        from account
        inner join ancestors on account.account_code = ancestors.parent_account_code
    )

    select raise(fail, 'account hierarchy cycle.')
    where exists (
        select 1
        from ancestors
        where ancestors.account_code = new.account_code
    );
end;

create trigger if not exists account_reject_immutable_update
before update on account
when
    old.account_type <> new.account_type
    or old.account_role <> new.account_role
    or old.account_node_kind <> new.account_node_kind
    or coalesce(old.parent_account_code, '') <> coalesce(new.parent_account_code, '')
    or coalesce(old.financial_position_line_classification, '')
    <> coalesce(new.financial_position_line_classification, '')
    or coalesce(old.profit_and_loss_line_classification, '')
    <> coalesce(new.profit_and_loss_line_classification, '')
begin
    select raise(fail, 'account immutable declaration fields cannot change.');
end;

create trigger if not exists account_reject_delete
before delete on account
begin
    select raise(fail, 'account rows are append-only.');
end;

create table if not exists posting_fact (
    posting_order integer primary key,
    posting_id text not null unique,
    posting_kind text not null check (
        posting_kind in ('STANDARD', 'OPENING_BALANCE', 'PERIOD_CLOSE')
    ),
    effective_date text not null,
    recorded_at text not null,
    actor_id text not null check (length(trim(actor_id)) > 0),
    actor_type text not null check (actor_type in ('HUMAN', 'SYSTEM', 'AGENT')),
    command_id text not null check (length(trim(command_id)) > 0),
    idempotency_key text not null check (
        length(idempotency_key) between 1 and 128
        and idempotency_key glob '[A-Za-z0-9]*'
        and idempotency_key not glob '*[^A-Za-z0-9._:/-]*'
    ),
    causation_id text not null check (length(trim(causation_id)) > 0),
    correlation_id text check (correlation_id is null or length(trim(correlation_id)) > 0),
    reason text,
    source_channel text not null,
    prior_posting_id text,
    unique (idempotency_key),
    foreign key (prior_posting_id) references posting_fact (posting_id),
    check (
        (prior_posting_id is null and reason is null)
        or
        (prior_posting_id is not null and reason is not null)
    )
) strict;

create trigger if not exists posting_fact_validate_opening_balance_window_on_insert
before insert on posting_fact
when new.posting_kind = 'OPENING_BALANCE'
begin
    select raise(fail, 'opening-balance postings must be committed before all other postings.')
    where exists (
        select 1
        from posting_fact
    );
end;

create trigger if not exists posting_fact_validate_closed_period_on_insert
before insert on posting_fact
when new.posting_kind <> 'PERIOD_CLOSE'
begin
    select raise(fail, 'posting effective date is already closed.')
    where exists (
        select 1
        from period_close
        where period_close.effective_date_to >= new.effective_date
    );
end;

create trigger if not exists posting_fact_validate_period_close_provenance_on_insert
before insert on posting_fact
when new.posting_kind = 'PERIOD_CLOSE'
begin
    select raise(fail, 'period-close postings must be system-authored.')
    where new.actor_type <> 'SYSTEM';
    select raise(fail, 'period-close postings must use the system source channel.')
    where new.source_channel <> 'SYSTEM';
    select raise(fail, 'period-close postings cannot reverse earlier postings.')
    where new.prior_posting_id is not null or new.reason is not null;
end;

create table if not exists posting_source_document (
    posting_id text not null,
    source_document_order integer not null check (source_document_order >= 0),
    source_document_id text not null check (
        length(source_document_id) between 1 and 255
        and source_document_id glob '[A-Za-z0-9]*'
        and source_document_id not glob '*[^A-Za-z0-9._:/-]*'
    ),
    source_document_type text not null check (
        length(source_document_type) between 1 and 64
        and source_document_type glob '[A-Za-z0-9]*'
        and source_document_type not glob '*[^A-Za-z0-9._:/-]*'
    ),
    document_date text not null,
    captured_at text not null,
    storage_locator text not null check (
        length(trim(storage_locator)) between 1 and 512
    ),
    content_sha256 text not null check (
        length(content_sha256) = 64
        and content_sha256 glob '[0-9a-f]*'
        and content_sha256 not glob '*[^0-9a-f]*'
    ),
    primary key (posting_id, source_document_order),
    unique (posting_id, source_document_id),
    foreign key (posting_id) references posting_fact (posting_id)
) strict;

create table if not exists posting_approval (
    posting_id text not null,
    approval_order integer not null check (approval_order >= 0),
    approval_id text not null check (
        length(approval_id) between 1 and 255
        and approval_id glob '[A-Za-z0-9]*'
        and approval_id not glob '*[^A-Za-z0-9._:/-]*'
    ),
    approval_type text not null check (
        length(approval_type) between 1 and 64
        and approval_type glob '[A-Za-z0-9]*'
        and approval_type not glob '*[^A-Za-z0-9._:/-]*'
    ),
    approver_id text not null check (length(trim(approver_id)) > 0),
    approver_type text not null check (approver_type in ('HUMAN', 'SYSTEM', 'AGENT')),
    decision text not null check (decision in ('APPROVED', 'REJECTED')),
    approved_at text not null,
    primary key (posting_id, approval_order),
    unique (posting_id, approval_id),
    foreign key (posting_id) references posting_fact (posting_id)
) strict;

create table if not exists journal_line (
    posting_id text not null,
    line_order integer not null check (line_order >= 0),
    account_code text not null check (
        length(account_code) between 1 and 255
        and account_code glob '[A-Za-z0-9]*'
        and account_code not glob '*[^A-Za-z0-9._:/-]*'
    ),
    entry_side text not null check (entry_side in ('DEBIT', 'CREDIT')),
    currency_code text not null check (
        length(currency_code) = 3
        and currency_code glob '[A-Z][A-Z][A-Z]'
    ),
    amount_minor integer not null check (amount_minor > 0),
    primary key (posting_id, line_order),
    foreign key (posting_id) references posting_fact (posting_id),
    foreign key (account_code) references account (account_code)
) strict;

create trigger if not exists journal_line_validate_active_account_on_insert
before insert on journal_line
begin
    select raise(fail, 'journal-line accounts must be active.')
    where exists (
        select 1
        from account
        where
            account.account_code = new.account_code
            and account.active = 0
    );
    select raise(fail, 'journal-line accounts must be postable.')
    where exists (
        select 1
        from account
        where
            account.account_code = new.account_code
            and account.account_node_kind <> 'POSTABLE'
    );
end;

create trigger if not exists journal_line_validate_functional_currency_on_insert
before insert on journal_line
begin
    select raise(fail, 'journal-line currency must match the book functional currency.')
    where exists (
        select 1
        from book_identity
        where
            book_identity.singleton_id = 1
            and new.currency_code <> book_identity.functional_currency_code
    );
end;

create trigger if not exists journal_line_validate_opening_balance_account_type_on_insert
before insert on journal_line
begin
    select raise(fail, 'opening-balance postings may touch only asset, liability, or equity accounts.')
    where exists (
        select 1
        from posting_fact
        inner join account on account.account_code = new.account_code
        where
            posting_fact.posting_id = new.posting_id
            and posting_fact.posting_kind = 'OPENING_BALANCE'
            and account.account_type not in ('ASSET', 'LIABILITY', 'EQUITY')
    );
end;

create table if not exists period_close (
    period_close_order integer primary key,
    effective_date_from text not null,
    effective_date_to text not null,
    closing_equity_account_code text not null references account (account_code),
    closed_at text not null,
    check (effective_date_from <= effective_date_to)
) strict;

create trigger if not exists period_close_validate_closing_equity_account_on_insert
before insert on period_close
begin
    select raise(fail, 'period-close target must be one active equity account.')
    where exists (
        select 1
        from account
        where
            account.account_code = new.closing_equity_account_code
            and (
                account.account_type <> 'EQUITY'
                or account.active = 0
            )
    );
end;

create table if not exists period_close_total (
    period_close_order integer not null,
    currency_code text not null check (
        length(currency_code) = 3
        and currency_code glob '[A-Z][A-Z][A-Z]'
    ),
    debit_total_minor integer not null check (debit_total_minor >= 0),
    credit_total_minor integer not null check (credit_total_minor >= 0),
    primary key (period_close_order, currency_code),
    foreign key (period_close_order) references period_close (period_close_order)
) strict;

create table if not exists period_close_posting (
    period_close_order integer not null,
    posting_id text not null,
    primary key (period_close_order, posting_id),
    foreign key (period_close_order) references period_close (period_close_order),
    foreign key (posting_id) references posting_fact (posting_id)
) strict;

create trigger if not exists period_close_posting_validate_period_close_posting_on_insert
before insert on period_close_posting
begin
    select raise(fail, 'period-close links must reference period-close postings.')
    where exists (
        select 1
        from posting_fact
        where
            posting_fact.posting_id = new.posting_id
            and posting_fact.posting_kind <> 'PERIOD_CLOSE'
    );
    select raise(fail, 'period-close links must reference system-authored postings.')
    where exists (
        select 1
        from posting_fact
        where
            posting_fact.posting_id = new.posting_id
            and posting_fact.actor_type <> 'SYSTEM'
    );
    select raise(fail, 'period-close links must reference system-source postings.')
    where exists (
        select 1
        from posting_fact
        where
            posting_fact.posting_id = new.posting_id
            and posting_fact.source_channel <> 'SYSTEM'
    );
    select raise(fail, 'period-close posting effective date must match the closed-through date.')
    where exists (
        select 1
        from period_close
        inner join posting_fact on posting_fact.posting_id = new.posting_id
        where
            period_close.period_close_order = new.period_close_order
            and posting_fact.effective_date <> period_close.effective_date_to
    );
end;

create table if not exists audit_event (
    audit_event_order integer primary key,
    recorded_at text not null check (length(trim(recorded_at)) > 0),
    event_kind text not null check (
        event_kind in (
            'BOOK_OPENED',
            'ACCOUNT_DECLARED',
            'ACCOUNT_REACTIVATED',
            'POSTING_COMMITTED',
            'POSTING_REVERSED',
            'BOOK_REKEYED',
            'BACKUP_CREATED',
            'BACKUP_RESTORED',
            'REKEY_ROLLBACK_RESTORED',
            'REKEY_ROLLBACK_DELETED',
            'BACKUP_CREATED_COMPENSATED',
            'REKEY_ROLLBACK_DELETED_COMPENSATED',
            'PERIOD_CLOSED'
        )
    ),
    account_code text,
    posting_id text,
    period_close_order integer,
    foreign key (account_code) references account (account_code),
    foreign key (posting_id) references posting_fact (posting_id),
    foreign key (period_close_order) references period_close (period_close_order),
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
            and period_close_order is null
        )
        or
        (
            event_kind in ('ACCOUNT_DECLARED', 'ACCOUNT_REACTIVATED')
            and account_code is not null
            and posting_id is null
            and period_close_order is null
        )
        or
        (
            event_kind in ('POSTING_COMMITTED', 'POSTING_REVERSED')
            and account_code is null
            and posting_id is not null
            and period_close_order is null
        )
        or
        (
            event_kind = 'PERIOD_CLOSED'
            and account_code is null
            and posting_id is null
            and period_close_order is not null
        )
    )
) strict;

create index if not exists posting_fact_by_prior_posting_id
on posting_fact (prior_posting_id);

create index if not exists posting_fact_by_effective_recorded_posting
on posting_fact (effective_date desc, recorded_at desc, posting_id desc);

create index if not exists journal_line_by_account_code
on journal_line (account_code, posting_id, line_order);

create index if not exists audit_event_by_recorded_at
on audit_event (recorded_at, audit_event_order);

create index if not exists period_close_by_effective_date_to
on period_close (effective_date_to desc, period_close_order desc);

create index if not exists period_close_total_by_currency
on period_close_total (currency_code, period_close_order);

create index if not exists period_close_posting_by_posting_id
on period_close_posting (posting_id, period_close_order);

create unique index if not exists posting_fact_one_reversal_per_target
on posting_fact (prior_posting_id)
where prior_posting_id is not null;

create trigger if not exists posting_fact_reject_update
before update on posting_fact
begin
    select raise(fail, 'posting_fact rows are append-only.');
end;

create trigger if not exists posting_fact_reject_delete
before delete on posting_fact
begin
    select raise(fail, 'posting_fact rows are append-only.');
end;

create trigger if not exists journal_line_reject_update
before update on journal_line
begin
    select raise(fail, 'journal_line rows are append-only.');
end;

create trigger if not exists journal_line_reject_delete
before delete on journal_line
begin
    select raise(fail, 'journal_line rows are append-only.');
end;

create trigger if not exists book_identity_reject_update
before update on book_identity
begin
    select raise(fail, 'book_identity rows are append-only.');
end;

create trigger if not exists book_identity_reject_delete
before delete on book_identity
begin
    select raise(fail, 'book_identity rows are append-only.');
end;

create trigger if not exists entity_profile_reject_update
before update on entity_profile
begin
    select raise(fail, 'entity_profile rows are append-only.');
end;

create trigger if not exists entity_profile_reject_delete
before delete on entity_profile
begin
    select raise(fail, 'entity_profile rows are append-only.');
end;

create trigger if not exists book_policy_reject_update
before update on book_policy
begin
    select raise(fail, 'book_policy rows are append-only.');
end;

create trigger if not exists book_policy_reject_delete
before delete on book_policy
begin
    select raise(fail, 'book_policy rows are append-only.');
end;

create trigger if not exists audit_event_reject_update
before update on audit_event
begin
    select raise(fail, 'audit_event rows are append-only.');
end;

create trigger if not exists audit_event_reject_delete
before delete on audit_event
begin
    select raise(fail, 'audit_event rows are append-only.');
end;

create trigger if not exists period_close_reject_update
before update on period_close
begin
    select raise(fail, 'period_close rows are append-only.');
end;

create trigger if not exists period_close_reject_delete
before delete on period_close
begin
    select raise(fail, 'period_close rows are append-only.');
end;

create trigger if not exists period_close_total_reject_update
before update on period_close_total
begin
    select raise(fail, 'period_close_total rows are append-only.');
end;

create trigger if not exists period_close_total_reject_delete
before delete on period_close_total
begin
    select raise(fail, 'period_close_total rows are append-only.');
end;

create trigger if not exists period_close_posting_reject_update
before update on period_close_posting
begin
    select raise(fail, 'period_close_posting rows are append-only.');
end;

create trigger if not exists period_close_posting_reject_delete
before delete on period_close_posting
begin
    select raise(fail, 'period_close_posting rows are append-only.');
end;
```

## Durable Tables

### `book_meta`

Columns:
- `meta_key`: `text primary key check ( meta_key in ('initialized_at', 'schema_fingerprint_sha256') )`
- `value`: `text not null check (length(trim(value)) > 0)`

Table-level constraints:
- None.

### `book_identity`

Columns:
- `singleton_id`: `integer primary key check (singleton_id = 1)`
- `entity_name`: `text not null check (length(trim(entity_name)) > 0)`
- `functional_currency_code`: `text not null check ( length(functional_currency_code) = 3 and functional_currency_code glob '[A-Z][A-Z][A-Z]' )`
- `fiscal_year_start_month`: `integer not null check ( fiscal_year_start_month between 1 and 12 )`
- `fiscal_year_start_day`: `integer not null check ( fiscal_year_start_day between 1 and 31 )`

Table-level constraints:
- `check ( date( printf( '2000-%02d-%02d', fiscal_year_start_month, fiscal_year_start_day ) ) is not null )`

### `entity_profile`

Columns:
- `singleton_id`: `integer primary key check (singleton_id = 1)`
- `entity_form`: `text not null check ( entity_form in ( 'FREELANCER', 'SOLE_PROPRIETORSHIP', 'COMPANY', 'PARTNERSHIP', 'NONPROFIT', 'BRANCH', 'OTHER' ) )`
- `owner_model`: `text not null check ( owner_model in ( 'SOLE_OWNER', 'MULTI_OWNER', 'MEMBERSHIP_BODY', 'NO_PRIVATE_OWNER' ) )`
- `business_activity_tags`: `text not null`

Table-level constraints:
- `foreign key (singleton_id) references book_identity (singleton_id)`

### `book_policy`

Columns:
- `singleton_id`: `integer primary key check (singleton_id = 1)`
- `policy_profile`: `text not null check ( policy_profile in ('INTERNAL_MANAGEMENT_SINGLE_ENTITY_V1') )`

Table-level constraints:
- `foreign key (singleton_id) references book_identity (singleton_id)`

### `account`

Columns:
- `account_code`: `text primary key check ( length(account_code) between 1 and 255 and account_code glob '[A-Za-z0-9]*' and account_code not glob '*[^A-Za-z0-9._:/-]*' )`
- `account_name`: `text not null check (length(trim(account_name)) > 0)`
- `account_type`: `text not null check ( account_type in ('ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'EXPENSE') )`
- `account_role`: `text not null check (account_role in ('ORDINARY', 'CONTRA'))`
- `account_node_kind`: `text not null check (account_node_kind in ('HEADER', 'POSTABLE'))`
- `parent_account_code`: `text references account (account_code)`
- `financial_position_line_classification`: `text check ( financial_position_line_classification is null or financial_position_line_classification in ( 'CURRENT_ASSET', 'NONCURRENT_ASSET', 'CURRENT_LIABILITY', 'NONCURRENT_LIABILITY', 'OWNER_CAPITAL', 'OWNER_DRAWINGS', 'PARTNER_CAPITAL', 'PARTNER_CURRENT', 'SHARE_CAPITAL', 'RETAINED_EARNINGS', 'ACCUMULATED_SURPLUS', 'RESERVE', 'OTHER_EQUITY' ) )`
- `profit_and_loss_line_classification`: `text check ( profit_and_loss_line_classification is null or profit_and_loss_line_classification in ( 'OPERATING_REVENUE', 'OTHER_REVENUE', 'FINANCE_INCOME', 'COST_OF_SALES', 'OPERATING_EXPENSE', 'DEPRECIATION_AND_AMORTIZATION', 'FINANCE_EXPENSE', 'TAX_EXPENSE' ) )`
- `active`: `integer not null check (active in (0, 1))`
- `declared_at`: `text not null`

Table-level constraints:
- `check ( parent_account_code is null or parent_account_code <> account_code )`
- `check ( ( account_type = 'ASSET' and financial_position_line_classification in ('CURRENT_ASSET', 'NONCURRENT_ASSET') and profit_and_loss_line_classification is null ) or ( account_type = 'LIABILITY' and financial_position_line_classification in ( 'CURRENT_LIABILITY', 'NONCURRENT_LIABILITY' ) and profit_and_loss_line_classification is null ) or ( account_type = 'EQUITY' and financial_position_line_classification in ( 'OWNER_CAPITAL', 'OWNER_DRAWINGS', 'PARTNER_CAPITAL', 'PARTNER_CURRENT', 'SHARE_CAPITAL', 'RETAINED_EARNINGS', 'ACCUMULATED_SURPLUS', 'RESERVE', 'OTHER_EQUITY' ) and profit_and_loss_line_classification is null ) or ( account_type = 'REVENUE' and financial_position_line_classification is null and profit_and_loss_line_classification in ( 'OPERATING_REVENUE', 'OTHER_REVENUE', 'FINANCE_INCOME' ) ) or ( account_type = 'EXPENSE' and financial_position_line_classification is null and profit_and_loss_line_classification in ( 'COST_OF_SALES', 'OPERATING_EXPENSE', 'DEPRECIATION_AND_AMORTIZATION', 'FINANCE_EXPENSE', 'TAX_EXPENSE' ) ) )`

### `posting_fact`

Columns:
- `posting_order`: `integer primary key`
- `posting_id`: `text not null unique`
- `posting_kind`: `text not null check ( posting_kind in ('STANDARD', 'OPENING_BALANCE', 'PERIOD_CLOSE') )`
- `effective_date`: `text not null`
- `recorded_at`: `text not null`
- `actor_id`: `text not null check (length(trim(actor_id)) > 0)`
- `actor_type`: `text not null check (actor_type in ('HUMAN', 'SYSTEM', 'AGENT'))`
- `command_id`: `text not null check (length(trim(command_id)) > 0)`
- `idempotency_key`: `text not null check ( length(idempotency_key) between 1 and 128 and idempotency_key glob '[A-Za-z0-9]*' and idempotency_key not glob '*[^A-Za-z0-9._:/-]*' )`
- `causation_id`: `text not null check (length(trim(causation_id)) > 0)`
- `correlation_id`: `text check (correlation_id is null or length(trim(correlation_id)) > 0)`
- `reason`: `text`
- `source_channel`: `text not null`
- `prior_posting_id`: `text`

Table-level constraints:
- `unique (idempotency_key)`
- `foreign key (prior_posting_id) references posting_fact (posting_id)`
- `check ( (prior_posting_id is null and reason is null) or (prior_posting_id is not null and reason is not null) )`

### `posting_source_document`

Columns:
- `posting_id`: `text not null`
- `source_document_order`: `integer not null check (source_document_order >= 0)`
- `source_document_id`: `text not null check ( length(source_document_id) between 1 and 255 and source_document_id glob '[A-Za-z0-9]*' and source_document_id not glob '*[^A-Za-z0-9._:/-]*' )`
- `source_document_type`: `text not null check ( length(source_document_type) between 1 and 64 and source_document_type glob '[A-Za-z0-9]*' and source_document_type not glob '*[^A-Za-z0-9._:/-]*' )`
- `document_date`: `text not null`
- `captured_at`: `text not null`
- `storage_locator`: `text not null check ( length(trim(storage_locator)) between 1 and 512 )`
- `content_sha256`: `text not null check ( length(content_sha256) = 64 and content_sha256 glob '[0-9a-f]*' and content_sha256 not glob '*[^0-9a-f]*' )`

Table-level constraints:
- `primary key (posting_id, source_document_order)`
- `unique (posting_id, source_document_id)`
- `foreign key (posting_id) references posting_fact (posting_id)`

### `posting_approval`

Columns:
- `posting_id`: `text not null`
- `approval_order`: `integer not null check (approval_order >= 0)`
- `approval_id`: `text not null check ( length(approval_id) between 1 and 255 and approval_id glob '[A-Za-z0-9]*' and approval_id not glob '*[^A-Za-z0-9._:/-]*' )`
- `approval_type`: `text not null check ( length(approval_type) between 1 and 64 and approval_type glob '[A-Za-z0-9]*' and approval_type not glob '*[^A-Za-z0-9._:/-]*' )`
- `approver_id`: `text not null check (length(trim(approver_id)) > 0)`
- `approver_type`: `text not null check (approver_type in ('HUMAN', 'SYSTEM', 'AGENT'))`
- `decision`: `text not null check (decision in ('APPROVED', 'REJECTED'))`
- `approved_at`: `text not null`

Table-level constraints:
- `primary key (posting_id, approval_order)`
- `unique (posting_id, approval_id)`
- `foreign key (posting_id) references posting_fact (posting_id)`

### `journal_line`

Columns:
- `posting_id`: `text not null`
- `line_order`: `integer not null check (line_order >= 0)`
- `account_code`: `text not null check ( length(account_code) between 1 and 255 and account_code glob '[A-Za-z0-9]*' and account_code not glob '*[^A-Za-z0-9._:/-]*' )`
- `entry_side`: `text not null check (entry_side in ('DEBIT', 'CREDIT'))`
- `currency_code`: `text not null check ( length(currency_code) = 3 and currency_code glob '[A-Z][A-Z][A-Z]' )`
- `amount_minor`: `integer not null check (amount_minor > 0)`

Table-level constraints:
- `primary key (posting_id, line_order)`
- `foreign key (posting_id) references posting_fact (posting_id)`
- `foreign key (account_code) references account (account_code)`

### `period_close`

Columns:
- `period_close_order`: `integer primary key`
- `effective_date_from`: `text not null`
- `effective_date_to`: `text not null`
- `closing_equity_account_code`: `text not null references account (account_code)`
- `closed_at`: `text not null`

Table-level constraints:
- `check (effective_date_from <= effective_date_to)`

### `period_close_total`

Columns:
- `period_close_order`: `integer not null`
- `currency_code`: `text not null check ( length(currency_code) = 3 and currency_code glob '[A-Z][A-Z][A-Z]' )`
- `debit_total_minor`: `integer not null check (debit_total_minor >= 0)`
- `credit_total_minor`: `integer not null check (credit_total_minor >= 0)`

Table-level constraints:
- `primary key (period_close_order, currency_code)`
- `foreign key (period_close_order) references period_close (period_close_order)`

### `period_close_posting`

Columns:
- `period_close_order`: `integer not null`
- `posting_id`: `text not null`

Table-level constraints:
- `primary key (period_close_order, posting_id)`
- `foreign key (period_close_order) references period_close (period_close_order)`
- `foreign key (posting_id) references posting_fact (posting_id)`

### `audit_event`

Columns:
- `audit_event_order`: `integer primary key`
- `recorded_at`: `text not null check (length(trim(recorded_at)) > 0)`
- `event_kind`: `text not null check ( event_kind in ( 'BOOK_OPENED', 'ACCOUNT_DECLARED', 'ACCOUNT_REACTIVATED', 'POSTING_COMMITTED', 'POSTING_REVERSED', 'BOOK_REKEYED', 'BACKUP_CREATED', 'BACKUP_RESTORED', 'REKEY_ROLLBACK_RESTORED', 'REKEY_ROLLBACK_DELETED', 'BACKUP_CREATED_COMPENSATED', 'REKEY_ROLLBACK_DELETED_COMPENSATED', 'PERIOD_CLOSED' ) )`
- `account_code`: `text`
- `posting_id`: `text`
- `period_close_order`: `integer`

Table-level constraints:
- `foreign key (account_code) references account (account_code)`
- `foreign key (posting_id) references posting_fact (posting_id)`
- `foreign key (period_close_order) references period_close (period_close_order)`
- `check ( ( event_kind in ( 'BOOK_OPENED', 'BOOK_REKEYED', 'BACKUP_CREATED', 'BACKUP_RESTORED', 'REKEY_ROLLBACK_RESTORED', 'REKEY_ROLLBACK_DELETED', 'BACKUP_CREATED_COMPENSATED', 'REKEY_ROLLBACK_DELETED_COMPENSATED' ) and account_code is null and posting_id is null and period_close_order is null ) or ( event_kind in ('ACCOUNT_DECLARED', 'ACCOUNT_REACTIVATED') and account_code is not null and posting_id is null and period_close_order is null ) or ( event_kind in ('POSTING_COMMITTED', 'POSTING_REVERSED') and account_code is null and posting_id is not null and period_close_order is null ) or ( event_kind = 'PERIOD_CLOSED' and account_code is null and posting_id is null and period_close_order is not null ) )`

## Durable Indexes

- `posting_fact_by_prior_posting_id` on `posting_fact`: `create index if not exists posting_fact_by_prior_posting_id on posting_fact (prior_posting_id);`
- `posting_fact_by_effective_recorded_posting` on `posting_fact`: `create index if not exists posting_fact_by_effective_recorded_posting on posting_fact (effective_date desc, recorded_at desc, posting_id desc);`
- `journal_line_by_account_code` on `journal_line`: `create index if not exists journal_line_by_account_code on journal_line (account_code, posting_id, line_order);`
- `audit_event_by_recorded_at` on `audit_event`: `create index if not exists audit_event_by_recorded_at on audit_event (recorded_at, audit_event_order);`
- `period_close_by_effective_date_to` on `period_close`: `create index if not exists period_close_by_effective_date_to on period_close (effective_date_to desc, period_close_order desc);`
- `period_close_total_by_currency` on `period_close_total`: `create index if not exists period_close_total_by_currency on period_close_total (currency_code, period_close_order);`
- `period_close_posting_by_posting_id` on `period_close_posting`: `create index if not exists period_close_posting_by_posting_id on period_close_posting (posting_id, period_close_order);`
- `posting_fact_one_reversal_per_target` on `posting_fact`: `create unique index if not exists posting_fact_one_reversal_per_target on posting_fact (prior_posting_id) where prior_posting_id is not null;`

## Runtime Integrity Semantics

- Initialized FinGrind books record both `book_meta.initialized_at` and `book_meta.schema_fingerprint_sha256`.
- An opened book is accepted as canonical only when `PRAGMA integrity_check` returns `ok`, `PRAGMA foreign_key_check` returns no rows, the recorded schema fingerprint matches the live canonical schema-object fingerprint, every persisted posting owns journal lines and balances to zero inside one currency bucket, and every persisted money triple decodes through the exact-money codec.
- Posting commits stage journal lines in temporary `pending_journal_line` rows and persist them only after the SQL aggregate gate proves at least two lines, at least one debit, at least one credit, exactly one currency bucket, and a zero signed minor-unit total.

## Schema Posture

- `application_id`: `1179079236`
- `user_version`: `14`
- Canonical durable tables: `book_meta`, `book_identity`, `entity_profile`, `book_policy`, `account`, `posting_fact`, `posting_source_document`, `posting_approval`, `journal_line`, `period_close`, `period_close_total`, `period_close_posting`, `audit_event`
- Canonical durable indexes: `posting_fact_by_prior_posting_id`, `posting_fact_by_effective_recorded_posting`, `journal_line_by_account_code`, `audit_event_by_recorded_at`, `period_close_by_effective_date_to`, `period_close_total_by_currency`, `period_close_posting_by_posting_id`, `posting_fact_one_reversal_per_target`
- There is no schema version table.
- There are no migration files.
- The current public line rejects non-matching book formats instead of upgrading them in place.
