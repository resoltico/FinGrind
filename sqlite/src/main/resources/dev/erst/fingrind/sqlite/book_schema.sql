pragma application_id = 1179079236;
pragma user_version = 8;

create table if not exists book_meta (
    key text primary key check (key in ('initialized_at', 'schema_fingerprint_sha256')),
    value text not null check (length(trim(value)) > 0)
) strict;

create table if not exists book_identity (
    singleton_id integer primary key check (singleton_id = 1),
    entity_name text not null check (length(trim(entity_name)) > 0),
    functional_currency_code text not null check (
        length(functional_currency_code) = 3
        and functional_currency_code glob '[A-Z][A-Z][A-Z]'
    ),
    fiscal_year_start text not null check (
        length(fiscal_year_start) = 5
        and fiscal_year_start glob '[0-1][0-9]-[0-3][0-9]'
    )
) strict;

create table if not exists entity_profile (
    singleton_id integer primary key check (singleton_id = 1),
    entity_form text not null check (
        entity_form in ('FREELANCER', 'SOLE_PROPRIETORSHIP', 'COMPANY', 'PARTNERSHIP', 'NONPROFIT', 'BRANCH', 'OTHER')
    ),
    owner_model text not null check (
        owner_model in ('SOLE_OWNER', 'MULTI_OWNER', 'MEMBER_FUNDED', 'INSTITUTIONALLY_GOVERNED', 'UNKNOWN')
    ),
    reporting_obligation_status text not null check (
        reporting_obligation_status in ('INTERNAL_MANAGEMENT_ONLY', 'EXTERNAL_GENERAL_PURPOSE', 'MIXED', 'UNSPECIFIED')
    ),
    tax_registration_status text not null check (
        tax_registration_status in ('REGISTERED', 'NOT_REGISTERED', 'UNSPECIFIED')
    ),
    business_activity_tags text not null,
    foreign key (singleton_id) references book_identity(singleton_id)
) strict;

create table if not exists book_policy (
    singleton_id integer primary key check (singleton_id = 1),
    accounting_basis text not null check (accounting_basis in ('CASH', 'ACCRUAL')),
    foreign key (singleton_id) references book_identity(singleton_id)
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

create trigger if not exists account_validate_parent_on_insert
before insert on account
when new.parent_account_code is not null
begin
    select raise(fail, 'account parent must be active.')
    where exists (
        select 1
        from account parent
        where parent.account_code = new.parent_account_code
          and parent.active = 0
    );
    select raise(fail, 'account parent must share the child account type.')
    where exists (
        select 1
        from account parent
        where parent.account_code = new.parent_account_code
          and parent.account_type <> new.account_type
    );
    with recursive ancestors(account_code, parent_account_code) as (
        select account_code, parent_account_code
        from account
        where account_code = new.parent_account_code
        union all
        select account.account_code, account.parent_account_code
        from account
        join ancestors on account.account_code = ancestors.parent_account_code
    )
    select raise(fail, 'account hierarchy cycle.')
    where exists (
        select 1
        from ancestors
        where account_code = new.account_code
    );
end;

create trigger if not exists account_reject_immutable_update
before update on account
when
    old.account_type <> new.account_type
    or old.account_role <> new.account_role
    or ifnull(old.parent_account_code, '') <> ifnull(new.parent_account_code, '')
    or ifnull(old.financial_position_line_classification, '') <> ifnull(new.financial_position_line_classification, '')
    or ifnull(old.profit_and_loss_line_classification, '') <> ifnull(new.profit_and_loss_line_classification, '')
begin
    select raise(fail, 'account immutable declaration fields cannot change.');
end;

create trigger if not exists account_reject_delete
before delete on account
begin
    select raise(fail, 'account rows are append-only.');
end;

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
    closing_equity_account_code text not null references account(account_code),
    closed_at text not null,
    check (effective_date_from <= effective_date_to)
) strict;

create table if not exists period_close_total (
    period_close_order integer not null,
    currency_code text not null check (
        length(currency_code) = 3
        and currency_code glob '[A-Z][A-Z][A-Z]'
    ),
    debit_total_minor integer not null check (debit_total_minor >= 0),
    credit_total_minor integer not null check (credit_total_minor >= 0),
    primary key (period_close_order, currency_code),
    foreign key (period_close_order) references period_close(period_close_order)
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
