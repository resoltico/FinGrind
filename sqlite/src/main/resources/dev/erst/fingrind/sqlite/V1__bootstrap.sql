create table if not exists posting_fact (
    posting_id text primary key,
    effective_date text not null,
    recorded_at text not null,
    actor_id text not null,
    actor_type text not null,
    command_id text not null,
    idempotency_key text not null,
    causation_id text not null,
    correlation_id text,
    reason text,
    source_channel text not null,
    correction_kind text,
    prior_posting_id text,
    unique (idempotency_key)
);

create table if not exists journal_line (
    posting_id text not null,
    line_order integer not null,
    account_code text not null,
    entry_side text not null,
    currency_code text not null,
    amount text not null,
    primary key (posting_id, line_order),
    foreign key (posting_id) references posting_fact(posting_id)
);
