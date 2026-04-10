create table if not exists posting_fact (
    posting_id text primary key,
    effective_date text not null,
    recorded_at text not null,
    actor_id text not null,
    actor_type text not null check (actor_type in ('USER', 'SYSTEM', 'AGENT')),
    command_id text not null,
    idempotency_key text not null,
    causation_id text not null,
    correlation_id text,
    reason text,
    source_channel text not null check (source_channel in ('CLI')),
    correction_kind text,
    prior_posting_id text,
    unique (idempotency_key),
    foreign key (prior_posting_id) references posting_fact(posting_id),
    check (
        (correction_kind is null and prior_posting_id is null)
        or
        (correction_kind in ('REVERSAL', 'AMENDMENT') and prior_posting_id is not null)
    )
);

create table if not exists journal_line (
    posting_id text not null,
    line_order integer not null check (line_order >= 0),
    account_code text not null,
    entry_side text not null check (entry_side in ('DEBIT', 'CREDIT')),
    currency_code text not null,
    amount text not null,
    primary key (posting_id, line_order),
    foreign key (posting_id) references posting_fact(posting_id)
);

create index if not exists posting_fact_by_prior_posting_id
    on posting_fact (prior_posting_id);

create unique index if not exists posting_fact_one_reversal_per_target
    on posting_fact (prior_posting_id)
    where correction_kind = 'REVERSAL';
