pragma application_id = 1179079236;
pragma user_version = 2;

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
    normal_balance text not null check (normal_balance in ('DEBIT', 'CREDIT')),
    active integer not null check (active in (0, 1)),
    declared_at text not null
) strict;

create table if not exists posting_fact (
    posting_id text primary key,
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
    source_channel text not null check (source_channel in ('CLI')),
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

create index if not exists posting_fact_by_prior_posting_id
    on posting_fact (prior_posting_id);

create index if not exists posting_fact_by_effective_recorded_posting
    on posting_fact (effective_date desc, recorded_at desc, posting_id desc);

create index if not exists journal_line_by_account_code
    on journal_line (account_code, posting_id, line_order);

create unique index if not exists posting_fact_one_reversal_per_target
    on posting_fact (prior_posting_id)
    where prior_posting_id is not null;
