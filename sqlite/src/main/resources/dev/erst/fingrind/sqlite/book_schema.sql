pragma application_id = 1179079236;
pragma user_version = 7;

create table if not exists book_meta (
    key text primary key,
    value text not null
) strict;

create table if not exists account (
    account_code text primary key check (
        length(account_code) between 1 and 255
        and account_code glob '[A-Za-z0-9]*'
        and account_code not glob '*[^A-Za-z0-9._:/-]*'
    ),
    account_name text not null check (length(trim(account_name)) > 0),
    account_type text not null check (account_type in ('ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'EXPENSE')),
    account_role text not null check (account_role in ('ORDINARY', 'CONTRA')),
    parent_account_code text references account(account_code),
    financial_position_line_classification text check (
        financial_position_line_classification is null or financial_position_line_classification in (
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
            and financial_position_line_classification in ('CURRENT_LIABILITY', 'NONCURRENT_LIABILITY')
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

create table if not exists posting_fact (
    posting_id text primary key,
    posting_kind text not null check (posting_kind in ('STANDARD', 'OPENING_BALANCE', 'PERIOD_CLOSE')),
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
    foreign key (prior_posting_id) references posting_fact(posting_id),
    check (
        (prior_posting_id is null and reason is null)
        or
        (prior_posting_id is not null and reason is not null)
    )
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
    foreign key (posting_id) references posting_fact(posting_id),
    foreign key (account_code) references account(account_code)
) strict;

create table if not exists period_close (
    period_close_order integer primary key,
    effective_date_from text not null,
    effective_date_to text not null,
    closed_at text not null,
    check (effective_date_from <= effective_date_to)
) strict;

create table if not exists period_close_posting (
    period_close_order integer not null,
    posting_id text not null,
    primary key (period_close_order, posting_id),
    foreign key (period_close_order) references period_close(period_close_order),
    foreign key (posting_id) references posting_fact(posting_id)
) strict;

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
            'PERIOD_CLOSED'
        )
    ),
    account_code text,
    posting_id text,
    period_close_order integer,
    foreign key (account_code) references account(account_code),
    foreign key (posting_id) references posting_fact(posting_id),
    foreign key (period_close_order) references period_close(period_close_order),
    check (
        (event_kind in ('BOOK_OPENED', 'BOOK_REKEYED') and account_code is null and posting_id is null and period_close_order is null)
        or
        (event_kind in ('ACCOUNT_DECLARED', 'ACCOUNT_REACTIVATED') and account_code is not null and posting_id is null and period_close_order is null)
        or
        (event_kind in ('POSTING_COMMITTED', 'POSTING_REVERSED') and account_code is null and posting_id is not null and period_close_order is null)
        or
        (event_kind = 'PERIOD_CLOSED' and account_code is null and posting_id is null and period_close_order is not null)
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
