pragma application_id = 1179079236;
pragma user_version = 33;

create table if not exists book_meta (
    meta_key text primary key check (
        meta_key in ('initialized_at', 'schema_fingerprint_sha256')
    ),
    value text not null check (
        (
            meta_key = 'initialized_at'
            and (
                value glob '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9]Z'
                or (
                    substr(value, 1, 19) glob '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9]'
                    and substr(value, 20, 1) = '.'
                    and substr(value, length(value), 1) = 'Z'
                    and (
                        (length(value) = 24 and substr(value, 21, 3) not glob '*[^0-9]*')
                        or (length(value) = 27 and substr(value, 21, 6) not glob '*[^0-9]*')
                        or (length(value) = 30 and substr(value, 21, 9) not glob '*[^0-9]*')
                    )
                )
            )
            and substr(value, 6, 2) between '01' and '12'
            and (
                (
                    substr(value, 6, 2) in ('01', '03', '05', '07', '08', '10', '12')
                    and substr(value, 9, 2) between '01' and '31'
                )
                or (
                    substr(value, 6, 2) in ('04', '06', '09', '11')
                    and substr(value, 9, 2) between '01' and '30'
                )
                or (
                    substr(value, 6, 2) = '02'
                    and (
                        substr(value, 9, 2) between '01' and '28'
                        or (
                            substr(value, 9, 2) = '29'
                            and (
                                cast(substr(value, 1, 4) as integer) % 400 = 0
                                or (
                                    cast(substr(value, 1, 4) as integer) % 4 = 0
                                    and cast(substr(value, 1, 4) as integer) % 100 <> 0
                                )
                            )
                        )
                    )
                )
            )
            and substr(value, 12, 2) between '00' and '23'
            and substr(value, 15, 2) between '00' and '59'
            and substr(value, 18, 2) between '00' and '59'
        )
        or (
            meta_key = 'schema_fingerprint_sha256'
            and length(value) = 64
            and value glob '[0-9a-f]*'
            and value not glob '*[^0-9a-f]*'
        )
    )
) strict;

create table if not exists book_identity (
    singleton_id integer primary key check (singleton_id = 1),
    entity_name text not null check (length(trim(entity_name)) > 0),
    accounting_kernel_profile text not null check (
        length(accounting_kernel_profile) between 1 and 120
        and accounting_kernel_profile not glob '*[^a-z0-9-]*'
        and accounting_kernel_profile not like '-%'
        and accounting_kernel_profile not like '%-'
        and accounting_kernel_profile not like '%--%'
    ),
    accounting_basis text not null check (
        accounting_basis in ('CASH_BASIS')
    ),
    accounting_framework_position text not null check (
        accounting_framework_position in ('NON_STATUTORY_INTERNAL_MANAGEMENT')
    ),
    entity_form text not null check (
        entity_form in ('OWNER_MANAGED_SINGLE_ENTITY')
    ),
    book_template_id text not null check (
        book_template_id in ('OWNER_MANAGED_SERVICE')
    ),
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
        (
            fiscal_year_start_month in (1, 3, 5, 7, 8, 10, 12)
            and fiscal_year_start_day between 1 and 31
        )
        or
        (
            fiscal_year_start_month in (4, 6, 9, 11)
            and fiscal_year_start_day between 1 and 30
        )
        or
        (
            fiscal_year_start_month = 2
            and fiscal_year_start_day between 1 and 29
        )
    )
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
    account_node_kind text not null check (account_node_kind in ('HEADER', 'POSTABLE')),
    parent_account_code text references account (account_code),
    financial_position_line_classification text check (
        financial_position_line_classification is null
        or financial_position_line_classification in (
            'CURRENT_ASSET',
            'NONCURRENT_ASSET',
            'CURRENT_LIABILITY',
            'NONCURRENT_LIABILITY',
            'EQUITY_CONTRIBUTION',
            'EQUITY_WITHDRAWAL',
            'RESULT_HOLDING',
            'RETAINED_ACCUMULATED',
            'RESERVE',
            'OTHER_EQUITY'
        )
    ),
    cash_flow_asset_classification text check (
        cash_flow_asset_classification is null
        or cash_flow_asset_classification in (
            'CASH_AND_CASH_EQUIVALENT',
            'NON_CASH'
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
            'OTHER_EXPENSE'
        )
    ),
    active integer not null check (active in (0, 1)),
    declared_at text not null check (
        (
            declared_at glob '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9]Z'
            or (
                substr(declared_at, 1, 19) glob '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9]'
                and substr(declared_at, 20, 1) = '.'
                and substr(declared_at, length(declared_at), 1) = 'Z'
                and (
                    (length(declared_at) = 24 and substr(declared_at, 21, 3) not glob '*[^0-9]*')
                    or (length(declared_at) = 27 and substr(declared_at, 21, 6) not glob '*[^0-9]*')
                    or (length(declared_at) = 30 and substr(declared_at, 21, 9) not glob '*[^0-9]*')
                )
            )
        )
        and substr(declared_at, 6, 2) between '01' and '12'
        and (
            (
                substr(declared_at, 6, 2) in ('01', '03', '05', '07', '08', '10', '12')
                and substr(declared_at, 9, 2) between '01' and '31'
            )
            or (
                substr(declared_at, 6, 2) in ('04', '06', '09', '11')
                and substr(declared_at, 9, 2) between '01' and '30'
            )
            or (
                substr(declared_at, 6, 2) = '02'
                and (
                    substr(declared_at, 9, 2) between '01' and '28'
                    or (
                        substr(declared_at, 9, 2) = '29'
                        and (
                            cast(substr(declared_at, 1, 4) as integer) % 400 = 0
                            or (
                                cast(substr(declared_at, 1, 4) as integer) % 4 = 0
                                and cast(substr(declared_at, 1, 4) as integer) % 100 <> 0
                            )
                        )
                    )
                )
            )
        )
        and substr(declared_at, 12, 2) between '00' and '23'
        and substr(declared_at, 15, 2) between '00' and '59'
        and substr(declared_at, 18, 2) between '00' and '59'
    ),
    check (
        parent_account_code is null or parent_account_code <> account_code
    ),
    check (
        (
            account_type = 'ASSET'
            and financial_position_line_classification in ('CURRENT_ASSET', 'NONCURRENT_ASSET')
            and cash_flow_asset_classification in ('CASH_AND_CASH_EQUIVALENT', 'NON_CASH')
            and profit_and_loss_line_classification is null
        )
        or
        (
            account_type = 'LIABILITY'
            and financial_position_line_classification in (
                'CURRENT_LIABILITY', 'NONCURRENT_LIABILITY'
            )
            and cash_flow_asset_classification is null
            and profit_and_loss_line_classification is null
        )
        or
        (
            account_type = 'EQUITY'
            and financial_position_line_classification in (
                'EQUITY_CONTRIBUTION',
                'EQUITY_WITHDRAWAL',
                'RESULT_HOLDING',
                'RETAINED_ACCUMULATED',
                'RESERVE',
                'OTHER_EQUITY'
            )
            and cash_flow_asset_classification is null
            and profit_and_loss_line_classification is null
        )
        or
        (
            account_type = 'REVENUE'
            and financial_position_line_classification is null
            and cash_flow_asset_classification is null
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
            and cash_flow_asset_classification is null
            and profit_and_loss_line_classification in (
                'COST_OF_SALES',
                'OPERATING_EXPENSE',
                'DEPRECIATION_AND_AMORTIZATION',
                'FINANCE_EXPENSE',
                'OTHER_EXPENSE'
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
                or coalesce(parent.cash_flow_asset_classification, '')
                    <> coalesce(new.cash_flow_asset_classification, '')
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
    or old.account_node_kind <> new.account_node_kind
    or coalesce(old.parent_account_code, '') <> coalesce(new.parent_account_code, '')
    or coalesce(old.financial_position_line_classification, '')
    <> coalesce(new.financial_position_line_classification, '')
    or coalesce(old.cash_flow_asset_classification, '')
    <> coalesce(new.cash_flow_asset_classification, '')
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

create table if not exists tax_registration (
    tax_registration_id text primary key check (
        length(tax_registration_id) between 1 and 120
        and tax_registration_id glob '[a-z0-9]*'
        and tax_registration_id not glob '*[^a-z0-9-]*'
        and tax_registration_id not like '-%'
        and tax_registration_id not like '%-'
        and tax_registration_id not like '%--%'
    ),
    tax_registration_name text not null check (length(trim(tax_registration_name)) between 1 and 200),
    jurisdiction text not null check (length(trim(jurisdiction)) between 1 and 120),
    registration_number text check (registration_number is null or length(trim(registration_number)) between 1 and 120),
    payable_account_code text not null references account (account_code),
    recoverable_account_code text not null references account (account_code),
    obligation_frequency text not null check (obligation_frequency in ('MONTHLY', 'QUARTERLY', 'ANNUAL')),
    due_days_after_period_end integer not null check (due_days_after_period_end between 0 and 366),
    declared_at text not null check (
        (
            declared_at glob '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9]Z'
            or (
                substr(declared_at, 1, 19) glob '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9]'
                and substr(declared_at, 20, 1) = '.'
                and substr(declared_at, length(declared_at), 1) = 'Z'
                and (
                    (length(declared_at) = 24 and substr(declared_at, 21, 3) not glob '*[^0-9]*')
                    or (length(declared_at) = 27 and substr(declared_at, 21, 6) not glob '*[^0-9]*')
                    or (length(declared_at) = 30 and substr(declared_at, 21, 9) not glob '*[^0-9]*')
                )
            )
        )
        and substr(declared_at, 6, 2) between '01' and '12'
        and (
            (
                substr(declared_at, 6, 2) in ('01', '03', '05', '07', '08', '10', '12')
                and substr(declared_at, 9, 2) between '01' and '31'
            )
            or (
                substr(declared_at, 6, 2) in ('04', '06', '09', '11')
                and substr(declared_at, 9, 2) between '01' and '30'
            )
            or (
                substr(declared_at, 6, 2) = '02'
                and (
                    substr(declared_at, 9, 2) between '01' and '28'
                    or (
                        substr(declared_at, 9, 2) = '29'
                        and (
                            cast(substr(declared_at, 1, 4) as integer) % 400 = 0
                            or (
                                cast(substr(declared_at, 1, 4) as integer) % 4 = 0
                                and cast(substr(declared_at, 1, 4) as integer) % 100 <> 0
                            )
                        )
                    )
                )
            )
        )
        and substr(declared_at, 12, 2) between '00' and '23'
        and substr(declared_at, 15, 2) between '00' and '59'
        and substr(declared_at, 18, 2) between '00' and '59'
    )
) strict;

create trigger if not exists tax_registration_validate_accounts_on_insert
before insert on tax_registration
begin
    select raise(fail, 'tax payable account must be active liability current-liability postable.')
    where exists (
        select 1
        from account
        where
            account.account_code = new.payable_account_code
            and (
                account.active = 0
                or account.account_type <> 'LIABILITY'
                or account.account_node_kind <> 'POSTABLE'
                or account.financial_position_line_classification <> 'CURRENT_LIABILITY'
            )
    );
    select raise(fail, 'tax recoverable account must be active asset current-asset non-cash postable.')
    where exists (
        select 1
        from account
        where
            account.account_code = new.recoverable_account_code
            and (
                account.active = 0
                or account.account_type <> 'ASSET'
                or account.account_node_kind <> 'POSTABLE'
                or account.financial_position_line_classification <> 'CURRENT_ASSET'
                or account.cash_flow_asset_classification <> 'NON_CASH'
            )
    );
end;

create trigger if not exists tax_registration_validate_accounts_on_update
before update on tax_registration
begin
    select raise(fail, 'tax registration id and declared_at are immutable.')
    where
        old.tax_registration_id <> new.tax_registration_id
        or old.declared_at <> new.declared_at;
    select raise(fail, 'tax payable account must be active liability current-liability postable.')
    where exists (
        select 1
        from account
        where
            account.account_code = new.payable_account_code
            and (
                account.active = 0
                or account.account_type <> 'LIABILITY'
                or account.account_node_kind <> 'POSTABLE'
                or account.financial_position_line_classification <> 'CURRENT_LIABILITY'
            )
    );
    select raise(fail, 'tax recoverable account must be active asset current-asset non-cash postable.')
    where exists (
        select 1
        from account
        where
            account.account_code = new.recoverable_account_code
            and (
                account.active = 0
                or account.account_type <> 'ASSET'
                or account.account_node_kind <> 'POSTABLE'
                or account.financial_position_line_classification <> 'CURRENT_ASSET'
                or account.cash_flow_asset_classification <> 'NON_CASH'
            )
    );
end;

create trigger if not exists tax_registration_reject_delete
before delete on tax_registration
begin
    select raise(fail, 'tax_registration rows are append-only.');
end;

create table if not exists tax_registration_code (
    tax_registration_id text not null references tax_registration (tax_registration_id),
    tax_code text not null check (
        length(tax_code) between 1 and 120
        and tax_code glob '[a-z0-9]*'
        and tax_code not glob '*[^a-z0-9-]*'
        and tax_code not like '-%'
        and tax_code not like '%-'
        and tax_code not like '%--%'
    ),
    tax_code_name text not null check (length(trim(tax_code_name)) between 1 and 200),
    rate_parts_per_million_of_whole integer not null check (rate_parts_per_million_of_whole between 0 and 1000000),
    inclusion_mode text not null check (inclusion_mode in ('INCLUSIVE', 'EXCLUSIVE')),
    application_kind text not null check (application_kind in ('OUTPUT_SALE', 'INPUT_EXPENSE_RECOVERABLE', 'INPUT_EXPENSE_NONRECOVERABLE')),
    primary key (tax_registration_id, tax_code)
) strict;

create table if not exists posting_fact (
    posting_order integer primary key,
    posting_id text not null unique,
    posting_kind text not null check (
        posting_kind in (
            'STANDARD',
            'OPENING_BALANCE',
            'INTERIM_RESULT_SWEEP',
            'FISCAL_YEAR_CLOSE'
        )
    ),
    posting_origin_kind text not null check (
        posting_origin_kind in (
            'DIRECT_JOURNAL',
            'SALE',
            'EXPENSE',
            'OWNER_CONTRIBUTION',
            'OWNER_WITHDRAWAL',
            'OPENING_POSITION',
            'REVERSAL',
            'INTERIM_RESULT_SWEEP',
            'FISCAL_YEAR_CLOSE'
        )
    ),
    entry_cash_account_code text,
    entry_revenue_account_code text,
    entry_expense_account_code text,
    entry_equity_account_code text,
    entry_amount_currency_code text check (
        entry_amount_currency_code is null
        or entry_amount_currency_code glob '[A-Z][A-Z][A-Z]'
    ),
    entry_amount_minor integer check (
        entry_amount_minor is null or entry_amount_minor > 0
    ),
    effective_date text not null check (
        effective_date glob '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]'
        and substr(effective_date, 6, 2) between '01' and '12'
        and (
            (
                substr(effective_date, 6, 2) in ('01', '03', '05', '07', '08', '10', '12')
                and substr(effective_date, 9, 2) between '01' and '31'
            )
            or (
                substr(effective_date, 6, 2) in ('04', '06', '09', '11')
                and substr(effective_date, 9, 2) between '01' and '30'
            )
            or (
                substr(effective_date, 6, 2) = '02'
                and (
                    substr(effective_date, 9, 2) between '01' and '28'
                    or (
                        substr(effective_date, 9, 2) = '29'
                        and (
                            cast(substr(effective_date, 1, 4) as integer) % 400 = 0
                            or (
                                cast(substr(effective_date, 1, 4) as integer) % 4 = 0
                                and cast(substr(effective_date, 1, 4) as integer) % 100 <> 0
                            )
                        )
                    )
                )
            )
        )
    ),
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
    actor_id text not null check (length(trim(actor_id)) > 0),
    actor_type text not null check (actor_type in ('PERSON', 'SYSTEM', 'AGENT')),
    command_id text not null check (length(trim(command_id)) > 0),
    idempotency_key text not null check (
        length(idempotency_key) between 1 and 128
        and idempotency_key glob '[A-Za-z0-9]*'
        and idempotency_key not glob '*[^A-Za-z0-9._:/-]*'
    ),
    causation_id text not null check (length(trim(causation_id)) > 0),
    correlation_id text check (correlation_id is null or length(trim(correlation_id)) > 0),
    reason text,
    source_channel text not null check (source_channel in ('CLI', 'SYSTEM')),
    prior_posting_id text,
    request_fingerprint_version integer not null check (request_fingerprint_version >= 1),
    request_fingerprint_sha256 text not null check (
        length(request_fingerprint_sha256) = 64
        and request_fingerprint_sha256 glob '[0-9a-f]*'
        and request_fingerprint_sha256 not glob '*[^0-9a-f]*'
    ),
    unique (idempotency_key),
    foreign key (entry_cash_account_code) references account (account_code),
    foreign key (entry_revenue_account_code) references account (account_code),
    foreign key (entry_expense_account_code) references account (account_code),
    foreign key (entry_equity_account_code) references account (account_code),
    foreign key (prior_posting_id) references posting_fact (posting_id),
    check (
        (prior_posting_id is null and reason is null)
        or
        (prior_posting_id is not null and reason is not null)
    ),
    check (
        (
            posting_origin_kind = 'SALE'
            and entry_cash_account_code is not null
            and entry_revenue_account_code is not null
            and entry_expense_account_code is null
            and entry_equity_account_code is null
            and entry_amount_currency_code is not null
            and entry_amount_minor is not null
        )
        or (
            posting_origin_kind = 'EXPENSE'
            and entry_cash_account_code is not null
            and entry_revenue_account_code is null
            and entry_expense_account_code is not null
            and entry_equity_account_code is null
            and entry_amount_currency_code is not null
            and entry_amount_minor is not null
        )
        or (
            posting_origin_kind = 'OWNER_CONTRIBUTION'
            and entry_cash_account_code is not null
            and entry_revenue_account_code is null
            and entry_expense_account_code is null
            and entry_equity_account_code is not null
            and entry_amount_currency_code is not null
            and entry_amount_minor is not null
        )
        or (
            posting_origin_kind = 'OWNER_WITHDRAWAL'
            and entry_cash_account_code is not null
            and entry_revenue_account_code is null
            and entry_expense_account_code is null
            and entry_equity_account_code is not null
            and entry_amount_currency_code is not null
            and entry_amount_minor is not null
        )
        or (
            posting_origin_kind in (
                'DIRECT_JOURNAL',
                'OPENING_POSITION',
                'REVERSAL',
                'INTERIM_RESULT_SWEEP',
                'FISCAL_YEAR_CLOSE'
            )
            and entry_cash_account_code is null
            and entry_revenue_account_code is null
            and entry_expense_account_code is null
            and entry_equity_account_code is null
            and entry_amount_currency_code is null
            and entry_amount_minor is null
        )
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
when new.posting_kind not in ('INTERIM_RESULT_SWEEP', 'FISCAL_YEAR_CLOSE')
begin
    select raise(fail, 'posting effective date is already closed.')
    where exists (
        select 1
        from interim_result_sweep
        where interim_result_sweep.effective_date_to >= new.effective_date
    );
end;

create trigger if not exists posting_fact_validate_generated_close_provenance_on_insert
before insert on posting_fact
when new.posting_kind in ('INTERIM_RESULT_SWEEP', 'FISCAL_YEAR_CLOSE')
begin
    select raise(fail, 'generated close postings must be system-authored.')
    where new.actor_type <> 'SYSTEM';
    select raise(fail, 'generated close postings must use the system source channel.')
    where new.source_channel <> 'SYSTEM';
    select raise(fail, 'generated close postings cannot reverse earlier postings.')
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
    document_date text not null check (
        document_date glob '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]'
        and substr(document_date, 6, 2) between '01' and '12'
        and (
            (
                substr(document_date, 6, 2) in ('01', '03', '05', '07', '08', '10', '12')
                and substr(document_date, 9, 2) between '01' and '31'
            )
            or (
                substr(document_date, 6, 2) in ('04', '06', '09', '11')
                and substr(document_date, 9, 2) between '01' and '30'
            )
            or (
                substr(document_date, 6, 2) = '02'
                and (
                    substr(document_date, 9, 2) between '01' and '28'
                    or (
                        substr(document_date, 9, 2) = '29'
                        and (
                            cast(substr(document_date, 1, 4) as integer) % 400 = 0
                            or (
                                cast(substr(document_date, 1, 4) as integer) % 4 = 0
                                and cast(substr(document_date, 1, 4) as integer) % 100 <> 0
                            )
                        )
                    )
                )
            )
        )
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
    approver_type text not null check (approver_type in ('PERSON', 'SYSTEM', 'AGENT')),
    decision text not null check (decision in ('APPROVED', 'REJECTED')),
    approved_at text not null check (
        (
            approved_at glob '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9]Z'
            or (
                substr(approved_at, 1, 19) glob '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9]'
                and substr(approved_at, 20, 1) = '.'
                and substr(approved_at, length(approved_at), 1) = 'Z'
                and (
                    (length(approved_at) = 24 and substr(approved_at, 21, 3) not glob '*[^0-9]*')
                    or (length(approved_at) = 27 and substr(approved_at, 21, 6) not glob '*[^0-9]*')
                    or (length(approved_at) = 30 and substr(approved_at, 21, 9) not glob '*[^0-9]*')
                )
            )
        )
        and substr(approved_at, 6, 2) between '01' and '12'
        and (
            (
                substr(approved_at, 6, 2) in ('01', '03', '05', '07', '08', '10', '12')
                and substr(approved_at, 9, 2) between '01' and '31'
            )
            or (
                substr(approved_at, 6, 2) in ('04', '06', '09', '11')
                and substr(approved_at, 9, 2) between '01' and '30'
            )
            or (
                substr(approved_at, 6, 2) = '02'
                and (
                    substr(approved_at, 9, 2) between '01' and '28'
                    or (
                        substr(approved_at, 9, 2) = '29'
                        and (
                            cast(substr(approved_at, 1, 4) as integer) % 400 = 0
                            or (
                                cast(substr(approved_at, 1, 4) as integer) % 4 = 0
                                and cast(substr(approved_at, 1, 4) as integer) % 100 <> 0
                            )
                        )
                    )
                )
            )
        )
        and substr(approved_at, 12, 2) between '00' and '23'
        and substr(approved_at, 15, 2) between '00' and '59'
        and substr(approved_at, 18, 2) between '00' and '59'
    ),
    primary key (posting_id, approval_order),
    unique (posting_id, approval_id),
    foreign key (posting_id) references posting_fact (posting_id)
) strict;

create table if not exists posting_applied_tax (
    posting_id text primary key references posting_fact (posting_id),
    tax_registration_id text not null references tax_registration (tax_registration_id),
    tax_code text not null check (
        length(tax_code) between 1 and 120
        and tax_code glob '[a-z0-9]*'
        and tax_code not glob '*[^a-z0-9-]*'
        and tax_code not like '-%'
        and tax_code not like '%-'
        and tax_code not like '%--%'
    ),
    tax_code_name text not null check (length(trim(tax_code_name)) between 1 and 200),
    rate_parts_per_million_of_whole integer not null check (rate_parts_per_million_of_whole between 0 and 1000000),
    inclusion_mode text not null check (inclusion_mode in ('INCLUSIVE', 'EXCLUSIVE')),
    application_kind text not null check (application_kind in ('OUTPUT_SALE', 'INPUT_EXPENSE_RECOVERABLE', 'INPUT_EXPENSE_NONRECOVERABLE')),
    currency_code text not null check (
        length(currency_code) = 3
        and currency_code glob '[A-Z][A-Z][A-Z]'
    ),
    taxable_amount_minor integer not null check (taxable_amount_minor >= 0),
    tax_amount_minor integer not null check (tax_amount_minor >= 0),
    gross_amount_minor integer not null check (gross_amount_minor >= 0),
    tax_account_code text references account (account_code),
    check (gross_amount_minor = taxable_amount_minor + tax_amount_minor),
    check (
        (
            application_kind in ('OUTPUT_SALE', 'INPUT_EXPENSE_RECOVERABLE')
            and tax_account_code is not null
        )
        or (
            application_kind = 'INPUT_EXPENSE_NONRECOVERABLE'
            and tax_account_code is null
        )
    )
) strict;

create trigger if not exists posting_applied_tax_validate_origin_on_insert
before insert on posting_applied_tax
begin
    select raise(fail, 'posting_applied_tax requires SALE or EXPENSE posting origin.')
    where exists (
        select 1
        from posting_fact
        where
            posting_fact.posting_id = new.posting_id
            and posting_fact.posting_origin_kind not in ('SALE', 'EXPENSE')
    );
    select raise(fail, 'sale tax application must use OUTPUT_SALE.')
    where exists (
        select 1
        from posting_fact
        where
            posting_fact.posting_id = new.posting_id
            and posting_fact.posting_origin_kind = 'SALE'
            and new.application_kind <> 'OUTPUT_SALE'
    );
    select raise(fail, 'expense tax application cannot use OUTPUT_SALE.')
    where exists (
        select 1
        from posting_fact
        where
            posting_fact.posting_id = new.posting_id
            and posting_fact.posting_origin_kind = 'EXPENSE'
            and new.application_kind = 'OUTPUT_SALE'
    );
end;

create trigger if not exists posting_applied_tax_reject_update
before update on posting_applied_tax
begin
    select raise(fail, 'posting_applied_tax rows are append-only.');
end;

create trigger if not exists posting_applied_tax_reject_delete
before delete on posting_applied_tax
begin
    select raise(fail, 'posting_applied_tax rows are append-only.');
end;

create table if not exists posting_foreign_exchange (
    posting_id text primary key references posting_fact (posting_id),
    treatment_kind text not null check (
        treatment_kind in (
            'SPOT_SETTLEMENT',
            'REALIZED_SETTLEMENT',
            'UNREALIZED_REMEASUREMENT'
        )
    ),
    transaction_currency_code text not null check (
        length(transaction_currency_code) = 3
        and transaction_currency_code glob '[A-Z][A-Z][A-Z]'
    ),
    transaction_amount_minor integer not null check (transaction_amount_minor > 0),
    functional_currency_code text not null check (
        length(functional_currency_code) = 3
        and functional_currency_code glob '[A-Z][A-Z][A-Z]'
    ),
    functional_amount_minor integer not null check (functional_amount_minor > 0),
    quoted_transaction_amount_minor integer not null check (quoted_transaction_amount_minor > 0),
    quoted_functional_amount_minor integer not null check (quoted_functional_amount_minor > 0),
    quoted_on text not null check (
        quoted_on glob '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]'
        and substr(quoted_on, 6, 2) between '01' and '12'
        and (
            (
                substr(quoted_on, 6, 2) in ('01', '03', '05', '07', '08', '10', '12')
                and substr(quoted_on, 9, 2) between '01' and '31'
            )
            or (
                substr(quoted_on, 6, 2) in ('04', '06', '09', '11')
                and substr(quoted_on, 9, 2) between '01' and '30'
            )
            or (
                substr(quoted_on, 6, 2) = '02'
                and (
                    substr(quoted_on, 9, 2) between '01' and '28'
                    or (
                        substr(quoted_on, 9, 2) = '29'
                        and (
                            cast(substr(quoted_on, 1, 4) as integer) % 400 = 0
                            or (
                                cast(substr(quoted_on, 1, 4) as integer) % 4 = 0
                                and cast(substr(quoted_on, 1, 4) as integer) % 100 <> 0
                            )
                        )
                    )
                )
            )
        )
    ),
    quote_source text not null check (length(trim(quote_source)) between 1 and 200),
    check (transaction_currency_code <> functional_currency_code)
) strict;

create trigger if not exists posting_foreign_exchange_validate_origin_on_insert
before insert on posting_foreign_exchange
begin
    select raise(fail, 'posting_foreign_exchange requires DIRECT_JOURNAL, SALE, EXPENSE, OWNER_CONTRIBUTION, OWNER_WITHDRAWAL, or REVERSAL posting origin.')
    where exists (
        select 1
        from posting_fact
        where
            posting_fact.posting_id = new.posting_id
            and posting_fact.posting_origin_kind not in (
                'DIRECT_JOURNAL',
                'SALE',
                'EXPENSE',
                'OWNER_CONTRIBUTION',
                'OWNER_WITHDRAWAL',
                'REVERSAL'
            )
    );
    select raise(fail, 'posting_foreign_exchange functional currency must match the book functional currency.')
    where exists (
        select 1
        from book_identity
        where
            book_identity.singleton_id = 1
            and new.functional_currency_code <> book_identity.functional_currency_code
    );
    select raise(fail, 'posting_foreign_exchange typed entry amount must match the retained functional amount.')
    where exists (
        select 1
        from posting_fact
        where
            posting_fact.posting_id = new.posting_id
            and posting_fact.entry_amount_currency_code is not null
            and (
                posting_fact.entry_amount_currency_code <> new.functional_currency_code
                or posting_fact.entry_amount_minor <> new.functional_amount_minor
            )
    );
end;

create trigger if not exists posting_foreign_exchange_reject_update
before update on posting_foreign_exchange
begin
    select raise(fail, 'posting_foreign_exchange rows are append-only.');
end;

create trigger if not exists posting_foreign_exchange_reject_delete
before delete on posting_foreign_exchange
begin
    select raise(fail, 'posting_foreign_exchange rows are append-only.');
end;

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

create table if not exists interim_result_sweep (
    interim_result_sweep_order integer primary key,
    effective_date_from text not null check (
        effective_date_from glob '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]'
        and substr(effective_date_from, 6, 2) between '01' and '12'
        and (
            (
                substr(effective_date_from, 6, 2) in ('01', '03', '05', '07', '08', '10', '12')
                and substr(effective_date_from, 9, 2) between '01' and '31'
            )
            or (
                substr(effective_date_from, 6, 2) in ('04', '06', '09', '11')
                and substr(effective_date_from, 9, 2) between '01' and '30'
            )
            or (
                substr(effective_date_from, 6, 2) = '02'
                and (
                    substr(effective_date_from, 9, 2) between '01' and '28'
                    or (
                        substr(effective_date_from, 9, 2) = '29'
                        and (
                            cast(substr(effective_date_from, 1, 4) as integer) % 400 = 0
                            or (
                                cast(substr(effective_date_from, 1, 4) as integer) % 4 = 0
                                and cast(substr(effective_date_from, 1, 4) as integer) % 100 <> 0
                            )
                        )
                    )
                )
            )
        )
    ),
    effective_date_to text not null check (
        effective_date_to glob '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]'
        and substr(effective_date_to, 6, 2) between '01' and '12'
        and (
            (
                substr(effective_date_to, 6, 2) in ('01', '03', '05', '07', '08', '10', '12')
                and substr(effective_date_to, 9, 2) between '01' and '31'
            )
            or (
                substr(effective_date_to, 6, 2) in ('04', '06', '09', '11')
                and substr(effective_date_to, 9, 2) between '01' and '30'
            )
            or (
                substr(effective_date_to, 6, 2) = '02'
                and (
                    substr(effective_date_to, 9, 2) between '01' and '28'
                    or (
                        substr(effective_date_to, 9, 2) = '29'
                        and (
                            cast(substr(effective_date_to, 1, 4) as integer) % 400 = 0
                            or (
                                cast(substr(effective_date_to, 1, 4) as integer) % 4 = 0
                                and cast(substr(effective_date_to, 1, 4) as integer) % 100 <> 0
                            )
                        )
                    )
                )
            )
        )
    ),
    result_holding_account_code text not null references account (account_code),
    swept_at text not null check (
        (
            swept_at glob '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9]Z'
            or (
                substr(swept_at, 1, 19) glob '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9]'
                and substr(swept_at, 20, 1) = '.'
                and substr(swept_at, length(swept_at), 1) = 'Z'
                and (
                    (length(swept_at) = 24 and substr(swept_at, 21, 3) not glob '*[^0-9]*')
                    or (length(swept_at) = 27 and substr(swept_at, 21, 6) not glob '*[^0-9]*')
                    or (length(swept_at) = 30 and substr(swept_at, 21, 9) not glob '*[^0-9]*')
                )
            )
        )
        and substr(swept_at, 6, 2) between '01' and '12'
        and (
            (
                substr(swept_at, 6, 2) in ('01', '03', '05', '07', '08', '10', '12')
                and substr(swept_at, 9, 2) between '01' and '31'
            )
            or (
                substr(swept_at, 6, 2) in ('04', '06', '09', '11')
                and substr(swept_at, 9, 2) between '01' and '30'
            )
            or (
                substr(swept_at, 6, 2) = '02'
                and (
                    substr(swept_at, 9, 2) between '01' and '28'
                    or (
                        substr(swept_at, 9, 2) = '29'
                        and (
                            cast(substr(swept_at, 1, 4) as integer) % 400 = 0
                            or (
                                cast(substr(swept_at, 1, 4) as integer) % 4 = 0
                                and cast(substr(swept_at, 1, 4) as integer) % 100 <> 0
                            )
                        )
                    )
                )
            )
        )
        and substr(swept_at, 12, 2) between '00' and '23'
        and substr(swept_at, 15, 2) between '00' and '59'
        and substr(swept_at, 18, 2) between '00' and '59'
    ),
    check (effective_date_from <= effective_date_to)
) strict;

create trigger if not exists interim_result_sweep_validate_result_holding_account_on_insert
before insert on interim_result_sweep
begin
    select raise(
        fail,
        'interim-result-sweep target must be one active RESULT_HOLDING equity account.'
    )
    where exists (
        select 1
        from account
        where
            account.account_code = new.result_holding_account_code
            and (
                account.account_type <> 'EQUITY'
                or account.active = 0
                or account.financial_position_line_classification <> 'RESULT_HOLDING'
            )
    );
end;

create trigger if not exists interim_result_sweep_validate_contiguous_horizon_on_insert
before insert on interim_result_sweep
when exists (select 1 from interim_result_sweep)
begin
    select raise(
        fail,
        'interim-result-sweep ranges must append contiguously from the prior swept-through date.'
    )
    where new.effective_date_from <> (
        select date(max(interim_result_sweep.effective_date_to), '+1 day')
        from interim_result_sweep
    );
end;

create table if not exists interim_result_sweep_total (
    interim_result_sweep_order integer not null,
    currency_code text not null check (
        length(currency_code) = 3
        and currency_code glob '[A-Z][A-Z][A-Z]'
    ),
    debit_total_minor integer not null check (debit_total_minor >= 0),
    credit_total_minor integer not null check (credit_total_minor >= 0),
    primary key (interim_result_sweep_order, currency_code),
    foreign key (interim_result_sweep_order) references interim_result_sweep (interim_result_sweep_order)
) strict;

create table if not exists interim_result_sweep_posting (
    interim_result_sweep_order integer not null,
    posting_id text not null,
    primary key (interim_result_sweep_order, posting_id),
    foreign key (interim_result_sweep_order) references interim_result_sweep (interim_result_sweep_order),
    foreign key (posting_id) references posting_fact (posting_id)
) strict;

create trigger if not exists interim_result_sweep_posting_validate_interim_result_sweep_posting_on_insert
before insert on interim_result_sweep_posting
begin
    select raise(
        fail,
        'interim-result-sweep links must reference interim-result-sweep postings.'
    )
    where exists (
        select 1
        from posting_fact
        where
            posting_fact.posting_id = new.posting_id
            and posting_fact.posting_kind <> 'INTERIM_RESULT_SWEEP'
    );
    select raise(fail, 'interim-result-sweep links must reference system-authored postings.')
    where exists (
        select 1
        from posting_fact
        where
            posting_fact.posting_id = new.posting_id
            and posting_fact.actor_type <> 'SYSTEM'
    );
    select raise(fail, 'interim-result-sweep links must reference system-source postings.')
    where exists (
        select 1
        from posting_fact
        where
            posting_fact.posting_id = new.posting_id
            and posting_fact.source_channel <> 'SYSTEM'
    );
    select raise(
        fail,
        'interim-result-sweep posting effective date must match the swept-through date.'
    )
    where exists (
        select 1
        from interim_result_sweep
        inner join posting_fact on posting_fact.posting_id = new.posting_id
        where
            interim_result_sweep.interim_result_sweep_order = new.interim_result_sweep_order
            and posting_fact.effective_date <> interim_result_sweep.effective_date_to
    );
end;

create table if not exists fiscal_year_close (
    fiscal_year_close_order integer primary key,
    effective_date_from text not null check (
        effective_date_from glob '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]'
        and substr(effective_date_from, 6, 2) between '01' and '12'
        and (
            (
                substr(effective_date_from, 6, 2) in ('01', '03', '05', '07', '08', '10', '12')
                and substr(effective_date_from, 9, 2) between '01' and '31'
            )
            or (
                substr(effective_date_from, 6, 2) in ('04', '06', '09', '11')
                and substr(effective_date_from, 9, 2) between '01' and '30'
            )
            or (
                substr(effective_date_from, 6, 2) = '02'
                and (
                    substr(effective_date_from, 9, 2) between '01' and '28'
                    or (
                        substr(effective_date_from, 9, 2) = '29'
                        and (
                            cast(substr(effective_date_from, 1, 4) as integer) % 400 = 0
                            or (
                                cast(substr(effective_date_from, 1, 4) as integer) % 4 = 0
                                and cast(substr(effective_date_from, 1, 4) as integer) % 100 <> 0
                            )
                        )
                    )
                )
            )
        )
    ),
    effective_date_to text not null check (
        effective_date_to glob '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]'
        and substr(effective_date_to, 6, 2) between '01' and '12'
        and (
            (
                substr(effective_date_to, 6, 2) in ('01', '03', '05', '07', '08', '10', '12')
                and substr(effective_date_to, 9, 2) between '01' and '31'
            )
            or (
                substr(effective_date_to, 6, 2) in ('04', '06', '09', '11')
                and substr(effective_date_to, 9, 2) between '01' and '30'
            )
            or (
                substr(effective_date_to, 6, 2) = '02'
                and (
                    substr(effective_date_to, 9, 2) between '01' and '28'
                    or (
                        substr(effective_date_to, 9, 2) = '29'
                        and (
                            cast(substr(effective_date_to, 1, 4) as integer) % 400 = 0
                            or (
                                cast(substr(effective_date_to, 1, 4) as integer) % 4 = 0
                                and cast(substr(effective_date_to, 1, 4) as integer) % 100 <> 0
                            )
                        )
                    )
                )
            )
        )
    ),
    capital_account_code text not null references account (account_code),
    result_holding_account_code text not null references account (account_code),
    retained_accumulated_account_code text not null references account (account_code),
    closed_at text not null check (
        (
            closed_at glob '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9]Z'
            or (
                substr(closed_at, 1, 19) glob '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9]'
                and substr(closed_at, 20, 1) = '.'
                and substr(closed_at, length(closed_at), 1) = 'Z'
                and (
                    (length(closed_at) = 24 and substr(closed_at, 21, 3) not glob '*[^0-9]*')
                    or (length(closed_at) = 27 and substr(closed_at, 21, 6) not glob '*[^0-9]*')
                    or (length(closed_at) = 30 and substr(closed_at, 21, 9) not glob '*[^0-9]*')
                )
            )
        )
        and substr(closed_at, 6, 2) between '01' and '12'
        and (
            (
                substr(closed_at, 6, 2) in ('01', '03', '05', '07', '08', '10', '12')
                and substr(closed_at, 9, 2) between '01' and '31'
            )
            or (
                substr(closed_at, 6, 2) in ('04', '06', '09', '11')
                and substr(closed_at, 9, 2) between '01' and '30'
            )
            or (
                substr(closed_at, 6, 2) = '02'
                and (
                    substr(closed_at, 9, 2) between '01' and '28'
                    or (
                        substr(closed_at, 9, 2) = '29'
                        and (
                            cast(substr(closed_at, 1, 4) as integer) % 400 = 0
                            or (
                                cast(substr(closed_at, 1, 4) as integer) % 4 = 0
                                and cast(substr(closed_at, 1, 4) as integer) % 100 <> 0
                            )
                        )
                    )
                )
            )
        )
        and substr(closed_at, 12, 2) between '00' and '23'
        and substr(closed_at, 15, 2) between '00' and '59'
        and substr(closed_at, 18, 2) between '00' and '59'
    ),
    check (effective_date_from <= effective_date_to)
) strict;

create trigger if not exists fiscal_year_close_validate_target_accounts_on_insert
before insert on fiscal_year_close
begin
    select raise(
        fail,
        'fiscal-year-close capital target must be one active EQUITY_CONTRIBUTION equity account.'
    )
    where exists (
        select 1
        from account
        where
            account.account_code = new.capital_account_code
            and (
                account.account_type <> 'EQUITY'
                or account.active = 0
                or account.financial_position_line_classification <> 'EQUITY_CONTRIBUTION'
            )
    );
    select raise(
        fail,
        'fiscal-year-close result-holding target must be one active RESULT_HOLDING equity account.'
    )
    where exists (
        select 1
        from account
        where
            account.account_code = new.result_holding_account_code
            and (
                account.account_type <> 'EQUITY'
                or account.active = 0
                or account.financial_position_line_classification <> 'RESULT_HOLDING'
            )
    );
    select raise(
        fail,
        'fiscal-year-close retained-accumulated target must be one active RETAINED_ACCUMULATED equity account.'
    )
    where exists (
        select 1
        from account
        where
            account.account_code = new.retained_accumulated_account_code
            and (
                account.account_type <> 'EQUITY'
                or account.active = 0
                or account.financial_position_line_classification <> 'RETAINED_ACCUMULATED'
            )
    );
end;

create table if not exists fiscal_year_close_posting (
    fiscal_year_close_order integer not null,
    posting_id text not null,
    primary key (fiscal_year_close_order, posting_id),
    foreign key (fiscal_year_close_order) references fiscal_year_close (fiscal_year_close_order),
    foreign key (posting_id) references posting_fact (posting_id)
) strict;

create trigger if not exists fiscal_year_close_posting_validate_fiscal_year_close_posting_on_insert
before insert on fiscal_year_close_posting
begin
    select raise(
        fail,
        'fiscal-year-close links must reference fiscal-year-close postings.'
    )
    where exists (
        select 1
        from posting_fact
        where
            posting_fact.posting_id = new.posting_id
            and posting_fact.posting_kind <> 'FISCAL_YEAR_CLOSE'
    );
    select raise(fail, 'fiscal-year-close links must reference system-authored postings.')
    where exists (
        select 1
        from posting_fact
        where
            posting_fact.posting_id = new.posting_id
            and posting_fact.actor_type <> 'SYSTEM'
    );
    select raise(fail, 'fiscal-year-close links must reference system-source postings.')
    where exists (
        select 1
        from posting_fact
        where
            posting_fact.posting_id = new.posting_id
            and posting_fact.source_channel <> 'SYSTEM'
    );
    select raise(
        fail,
        'fiscal-year-close posting effective date must match the closed-through date.'
    )
    where exists (
        select 1
        from fiscal_year_close
        inner join posting_fact on posting_fact.posting_id = new.posting_id
        where
            fiscal_year_close.fiscal_year_close_order = new.fiscal_year_close_order
            and posting_fact.effective_date <> fiscal_year_close.effective_date_to
    );
end;

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
            event_kind in ('ACCOUNT_DECLARED', 'ACCOUNT_REACTIVATED', 'ACCOUNT_RENAMED')
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

create index if not exists posting_fact_by_prior_posting_id
on posting_fact (prior_posting_id);

create index if not exists posting_fact_by_effective_recorded_posting
on posting_fact (effective_date desc, recorded_at desc, posting_id desc);

create index if not exists tax_registration_code_by_registration_id
on tax_registration_code (tax_registration_id, tax_code);

create index if not exists posting_applied_tax_by_tax_registration_id
on posting_applied_tax (tax_registration_id, posting_id);

create index if not exists journal_line_by_account_code
on journal_line (account_code, posting_id, line_order);

create index if not exists audit_event_by_recorded_at
on audit_event (recorded_at, audit_event_order);

create index if not exists interim_result_sweep_by_effective_date_to
on interim_result_sweep (effective_date_to desc, interim_result_sweep_order desc);

create index if not exists interim_result_sweep_total_by_currency
on interim_result_sweep_total (currency_code, interim_result_sweep_order);

create index if not exists interim_result_sweep_posting_by_posting_id
on interim_result_sweep_posting (posting_id, interim_result_sweep_order);

create index if not exists fiscal_year_close_by_effective_date_to
on fiscal_year_close (effective_date_to desc, fiscal_year_close_order desc);

create index if not exists fiscal_year_close_posting_by_posting_id
on fiscal_year_close_posting (posting_id, fiscal_year_close_order);

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

create trigger if not exists interim_result_sweep_reject_update
before update on interim_result_sweep
begin
    select raise(fail, 'interim_result_sweep rows are append-only.');
end;

create trigger if not exists interim_result_sweep_reject_delete
before delete on interim_result_sweep
begin
    select raise(fail, 'interim_result_sweep rows are append-only.');
end;

create trigger if not exists interim_result_sweep_total_reject_update
before update on interim_result_sweep_total
begin
    select raise(fail, 'interim_result_sweep_total rows are append-only.');
end;

create trigger if not exists interim_result_sweep_total_reject_delete
before delete on interim_result_sweep_total
begin
    select raise(fail, 'interim_result_sweep_total rows are append-only.');
end;

create trigger if not exists interim_result_sweep_posting_reject_update
before update on interim_result_sweep_posting
begin
    select raise(fail, 'interim_result_sweep_posting rows are append-only.');
end;

create trigger if not exists interim_result_sweep_posting_reject_delete
before delete on interim_result_sweep_posting
begin
    select raise(fail, 'interim_result_sweep_posting rows are append-only.');
end;

create trigger if not exists fiscal_year_close_reject_update
before update on fiscal_year_close
begin
    select raise(fail, 'fiscal_year_close rows are append-only.');
end;

create trigger if not exists fiscal_year_close_reject_delete
before delete on fiscal_year_close
begin
    select raise(fail, 'fiscal_year_close rows are append-only.');
end;

create trigger if not exists fiscal_year_close_posting_reject_update
before update on fiscal_year_close_posting
begin
    select raise(fail, 'fiscal_year_close_posting rows are append-only.');
end;

create trigger if not exists fiscal_year_close_posting_reject_delete
before delete on fiscal_year_close_posting
begin
    select raise(fail, 'fiscal_year_close_posting rows are append-only.');
end;
