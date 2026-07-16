pragma application_id = 1179079236;
pragma user_version = 46;

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
        accounting_basis in ('CASH', 'ACCRUAL')
    ),
    accounting_framework_position text not null check (
        accounting_framework_position in ('NON_STATUTORY_INTERNAL_MANAGEMENT')
    ),
    entity_form text not null check (
        entity_form in ('OWNER_MANAGED_SINGLE_ENTITY')
    ),
    book_template_id text not null check (
        book_template_id in ('OWNER_MANAGED_SERVICE', 'OWNER_MANAGED_TRADING')
    ),
    costing_doctrine text check (
        costing_doctrine is null
        or costing_doctrine in ('WEIGHTED_AVERAGE')
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
    ),
    check (
        (
            book_template_id = 'OWNER_MANAGED_SERVICE'
            and costing_doctrine is null
        )
        or (
            book_template_id = 'OWNER_MANAGED_TRADING'
            and coalesce(costing_doctrine, '') = 'WEIGHTED_AVERAGE'
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
            'INVENTORY',
            'PREPAID_EXPENSE',
            'NONCURRENT_ASSET',
            'TRADE_RECEIVABLE',
            'CURRENT_LIABILITY',
            'NONCURRENT_LIABILITY',
            'TRADE_PAYABLE',
            'DEFERRED_REVENUE',
            'ACCRUED_EXPENSE',
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
            'SALES_DISCOUNT_ALLOWANCE',
            'OTHER_REVENUE',
            'FINANCE_INCOME',
            'COST_OF_SALES',
            'OPERATING_EXPENSE',
            'DEPRECIATION_AND_AMORTIZATION',
            'SETTLEMENT_FEE',
            'BAD_DEBT_WRITE_OFF',
            'FINANCE_EXPENSE',
            'OTHER_EXPENSE'
        )
    ),
    unit_of_measure text check (
        unit_of_measure is null
        or (
            length(unit_of_measure) between 1 and 64
            and unit_of_measure glob '[A-Za-z0-9]*'
            and unit_of_measure not glob '*[^A-Za-z0-9._:/-]*'
        )
    ),
    quantity_scale integer check (
        quantity_scale is null
        or quantity_scale between 0 and 9
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
            financial_position_line_classification = 'INVENTORY'
            and unit_of_measure is not null
            and quantity_scale is not null
        )
        or (
            coalesce(financial_position_line_classification, '') <> 'INVENTORY'
            and unit_of_measure is null
            and quantity_scale is null
        )
    ),
    check (
        (
            account_type = 'ASSET'
            and financial_position_line_classification in (
                'CURRENT_ASSET',
                'INVENTORY',
                'PREPAID_EXPENSE',
                'NONCURRENT_ASSET',
                'TRADE_RECEIVABLE'
            )
            and cash_flow_asset_classification in ('CASH_AND_CASH_EQUIVALENT', 'NON_CASH')
            and profit_and_loss_line_classification is null
        )
        or
        (
            account_type = 'LIABILITY'
            and financial_position_line_classification in (
                'CURRENT_LIABILITY',
                'NONCURRENT_LIABILITY',
                'TRADE_PAYABLE',
                'DEFERRED_REVENUE',
                'ACCRUED_EXPENSE'
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
                'SALES_DISCOUNT_ALLOWANCE',
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
                'SETTLEMENT_FEE',
                'BAD_DEBT_WRITE_OFF',
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

create trigger if not exists account_validate_lifecycle_update
before update on account
begin
    select raise(fail, 'account code and declared-at are immutable.')
    where
        old.account_code <> new.account_code
        or old.declared_at <> new.declared_at;
    select raise(fail, 'account definition has dependents.')
    where
        (
            old.account_name <> new.account_name
            or old.account_type <> new.account_type
            or old.account_node_kind <> new.account_node_kind
            or coalesce(old.parent_account_code, '') <> coalesce(new.parent_account_code, '')
            or coalesce(old.financial_position_line_classification, '')
            <> coalesce(new.financial_position_line_classification, '')
            or coalesce(old.cash_flow_asset_classification, '')
            <> coalesce(new.cash_flow_asset_classification, '')
            or coalesce(old.profit_and_loss_line_classification, '')
            <> coalesce(new.profit_and_loss_line_classification, '')
            or coalesce(old.unit_of_measure, '') <> coalesce(new.unit_of_measure, '')
            or coalesce(old.quantity_scale, -1) <> coalesce(new.quantity_scale, -1)
        )
        and (
            exists (select 1 from journal_line where account_code = old.account_code)
            or exists (
                select 1
                from tax_registration
                where payable_account_code = old.account_code
                    or recoverable_account_code = old.account_code
            )
            or exists (select 1 from account where parent_account_code = old.account_code)
        );
    select raise(fail, 'retired accounts must have zero current balance.')
    where
        old.active = 1
        and new.active = 0
        and exists (
            select 1
            from journal_line
            where account_code = old.account_code
            group by currency_code
            having sum(case entry_side when 'DEBIT' then amount_minor else -amount_minor end) <> 0
        );
    select raise(fail, 'retired accounts must have no live operational bindings.')
    where
        old.active = 1
        and new.active = 0
        and (
            exists (
                select 1
                from tax_registration
                where payable_account_code = old.account_code
                    or recoverable_account_code = old.account_code
            )
            or exists (select 1 from account where parent_account_code = old.account_code)
        );
end;

create trigger if not exists account_validate_parent_on_update
before update on account
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
            'SALE_SETTLED',
            'SALE_ON_CREDIT',
            'PURCHASE_SETTLED',
            'PURCHASE_ON_CREDIT',
            'INVENTORY_CAPITALIZATION_SETTLED',
            'INVENTORY_CAPITALIZATION_ON_CREDIT',
            'INVENTORY_WRITE_DOWN',
            'INVENTORY_SHRINKAGE',
            'INVENTORY_COUNT_INCREASE',
            'PREPAYMENT',
            'DEFERRED_REVENUE',
            'ACCRUED_EXPENSE',
            'ACCRUAL_CUTOFF_RECOGNITION',
            'ACCRUED_EXPENSE_SETTLEMENT',
            'LATVIAN_MONTHLY_PAYROLL',
            'LATVIAN_PAYROLL_NET_WAGE_SETTLEMENT',
            'LATVIAN_PAYROLL_STATE_REMITTANCE',
            'FIXED_ASSET_CAPITALIZATION',
            'FIXED_ASSET_DEPRECIATION',
            'FIXED_ASSET_DISPOSAL',
            'FINANCING_BORROWING',
            'FINANCING_PRINCIPAL_REPAYMENT',
            'FINANCING_INTEREST_ACCRUAL',
            'FINANCING_INTEREST_PAYMENT',
            'FOREIGN_CURRENCY_OBLIGATION',
            'REALIZED_FOREIGN_EXCHANGE_SETTLEMENT',
            'EXPENSE_SETTLED',
            'EXPENSE_ON_CREDIT',
            'RECEIPT',
            'PAYMENT',
            'OWNER_CONTRIBUTION',
            'OWNER_WITHDRAWAL',
            'OPENING_POSITION',
            'REVERSAL',
            'INTERIM_RESULT_SWEEP',
            'FISCAL_YEAR_CLOSE'
        )
    ),
    entry_primary_debit_account_code text,
    entry_primary_credit_account_code text,
    entry_adjunct_account_code text,
    entry_amount_currency_code text check (
        entry_amount_currency_code is null
        or entry_amount_currency_code glob '[A-Z][A-Z][A-Z]'
    ),
    entry_amount_minor integer check (
        entry_amount_minor is null or entry_amount_minor > 0
    ),
    entry_adjunct_amount_minor integer check (
        entry_adjunct_amount_minor is null or entry_adjunct_amount_minor > 0
    ),
    entry_quantity text check (
        entry_quantity is null
        or (
            length(entry_quantity) between 1 and 64
            and entry_quantity = trim(entry_quantity)
            and entry_quantity not like '+%'
            and entry_quantity not like '-%'
            and entry_quantity not glob '*[^0-9.]*'
        )
    ),
    entry_unit_cost_currency_code text check (
        entry_unit_cost_currency_code is null
        or entry_unit_cost_currency_code glob '[A-Z][A-Z][A-Z]'
    ),
    entry_unit_cost_minor integer check (
        entry_unit_cost_minor is null or entry_unit_cost_minor > 0
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
    foreign key (entry_primary_debit_account_code) references account (account_code),
    foreign key (entry_primary_credit_account_code) references account (account_code),
    foreign key (entry_adjunct_account_code) references account (account_code),
    foreign key (prior_posting_id) references posting_fact (posting_id),
    check (
        (prior_posting_id is null and reason is null)
        or
        (prior_posting_id is not null and reason is not null)
    ),
    check (
        (
            posting_origin_kind in (
                'SALE_SETTLED',
                'SALE_ON_CREDIT',
                'INVENTORY_CAPITALIZATION_SETTLED',
                'INVENTORY_CAPITALIZATION_ON_CREDIT',
                'INVENTORY_WRITE_DOWN',
                'FIXED_ASSET_CAPITALIZATION',
                'FIXED_ASSET_DEPRECIATION',
                'FIXED_ASSET_DISPOSAL',
                'FINANCING_BORROWING',
                'FINANCING_PRINCIPAL_REPAYMENT',
                'FINANCING_INTEREST_ACCRUAL',
                'FINANCING_INTEREST_PAYMENT',
                'FOREIGN_CURRENCY_OBLIGATION',
                'REALIZED_FOREIGN_EXCHANGE_SETTLEMENT',
                'EXPENSE_SETTLED',
                'EXPENSE_ON_CREDIT',
                'OWNER_CONTRIBUTION',
                'OWNER_WITHDRAWAL'
            )
            and entry_primary_debit_account_code is not null
            and entry_primary_credit_account_code is not null
            and entry_adjunct_account_code is null
            and entry_amount_currency_code is not null
            and entry_amount_minor is not null
            and entry_adjunct_amount_minor is null
            and entry_unit_cost_currency_code is null
            and entry_unit_cost_minor is null
        )
        or (
            posting_origin_kind in (
                'PREPAYMENT',
                'DEFERRED_REVENUE',
                'ACCRUED_EXPENSE',
                'ACCRUAL_CUTOFF_RECOGNITION',
                'ACCRUED_EXPENSE_SETTLEMENT'
            )
            and entry_primary_debit_account_code is not null
            and entry_primary_credit_account_code is not null
            and entry_adjunct_account_code is null
            and entry_amount_currency_code is not null
            and entry_amount_minor is not null
            and entry_adjunct_amount_minor is null
            and entry_quantity is null
            and entry_unit_cost_currency_code is null
            and entry_unit_cost_minor is null
        )
        or (
            posting_origin_kind in (
                'PURCHASE_SETTLED',
                'PURCHASE_ON_CREDIT',
                'INVENTORY_COUNT_INCREASE'
            )
            and entry_primary_debit_account_code is not null
            and entry_primary_credit_account_code is not null
            and entry_adjunct_account_code is null
            and entry_amount_currency_code is null
            and entry_amount_minor is null
            and entry_adjunct_amount_minor is null
            and entry_quantity is not null
            and entry_unit_cost_currency_code is not null
            and entry_unit_cost_minor is not null
        )
        or (
            posting_origin_kind = 'INVENTORY_SHRINKAGE'
            and entry_primary_debit_account_code is not null
            and entry_primary_credit_account_code is not null
            and entry_adjunct_account_code is null
            and entry_amount_currency_code is null
            and entry_amount_minor is null
            and entry_adjunct_amount_minor is null
            and entry_quantity is not null
            and entry_unit_cost_currency_code is null
            and entry_unit_cost_minor is null
        )
        or (
            posting_origin_kind in (
                'SALE_SETTLED',
                'SALE_ON_CREDIT',
                'INVENTORY_CAPITALIZATION_SETTLED',
                'INVENTORY_CAPITALIZATION_ON_CREDIT',
                'INVENTORY_WRITE_DOWN',
                'FIXED_ASSET_CAPITALIZATION',
                'FIXED_ASSET_DEPRECIATION',
                'FIXED_ASSET_DISPOSAL',
                'FINANCING_BORROWING',
                'FINANCING_PRINCIPAL_REPAYMENT',
                'FINANCING_INTEREST_ACCRUAL',
                'FINANCING_INTEREST_PAYMENT',
                'FOREIGN_CURRENCY_OBLIGATION',
                'REALIZED_FOREIGN_EXCHANGE_SETTLEMENT',
                'EXPENSE_SETTLED',
                'EXPENSE_ON_CREDIT',
                'OWNER_CONTRIBUTION',
                'OWNER_WITHDRAWAL'
            )
            and entry_quantity is null
            and entry_unit_cost_currency_code is null
            and entry_unit_cost_minor is null
        )
        or (
            posting_origin_kind in ('RECEIPT', 'PAYMENT')
            and entry_primary_debit_account_code is not null
            and entry_primary_credit_account_code is not null
            and entry_amount_currency_code is not null
            and entry_amount_minor is not null
            and entry_quantity is null
            and entry_unit_cost_currency_code is null
            and entry_unit_cost_minor is null
            and (
                (
                    entry_adjunct_account_code is null
                    and entry_adjunct_amount_minor is null
                )
                or (
                    entry_adjunct_account_code is not null
                    and entry_adjunct_amount_minor is not null
                )
            )
        )
        or (
            posting_origin_kind in (
                'DIRECT_JOURNAL',
                'OPENING_POSITION',
                'REVERSAL',
                'INTERIM_RESULT_SWEEP',
                'FISCAL_YEAR_CLOSE',
                'LATVIAN_MONTHLY_PAYROLL',
                'LATVIAN_PAYROLL_NET_WAGE_SETTLEMENT',
                'LATVIAN_PAYROLL_STATE_REMITTANCE'
            )
            and entry_primary_debit_account_code is null
            and entry_primary_credit_account_code is null
            and entry_adjunct_account_code is null
            and entry_amount_currency_code is null
            and entry_amount_minor is null
            and entry_adjunct_amount_minor is null
            and entry_quantity is null
            and entry_unit_cost_currency_code is null
            and entry_unit_cost_minor is null
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
    select raise(fail, 'posting_applied_tax requires sale, purchase, capitalization, or expense posting origin.')
    where exists (
        select 1
        from posting_fact
        where
            posting_fact.posting_id = new.posting_id
            and posting_fact.posting_origin_kind not in (
                'SALE_SETTLED',
                'SALE_ON_CREDIT',
                'PURCHASE_SETTLED',
                'PURCHASE_ON_CREDIT',
                'INVENTORY_CAPITALIZATION_SETTLED',
                'INVENTORY_CAPITALIZATION_ON_CREDIT',
                'EXPENSE_SETTLED',
                'EXPENSE_ON_CREDIT'
            )
    );
    select raise(fail, 'sale tax application must use OUTPUT_SALE.')
    where exists (
        select 1
        from posting_fact
        where
            posting_fact.posting_id = new.posting_id
            and posting_fact.posting_origin_kind in ('SALE_SETTLED', 'SALE_ON_CREDIT')
            and new.application_kind <> 'OUTPUT_SALE'
    );
    select raise(fail, 'input tax application cannot use OUTPUT_SALE.')
    where exists (
        select 1
        from posting_fact
        where
            posting_fact.posting_id = new.posting_id
            and posting_fact.posting_origin_kind in (
                'EXPENSE_SETTLED',
                'EXPENSE_ON_CREDIT',
                'PURCHASE_SETTLED',
                'PURCHASE_ON_CREDIT',
                'INVENTORY_CAPITALIZATION_SETTLED',
                'INVENTORY_CAPITALIZATION_ON_CREDIT'
            )
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
            'SPOT_TRANSACTION',
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
    select raise(fail, 'posting_foreign_exchange requires one foreign-exchange-capable posting origin.')
    where exists (
        select 1
        from posting_fact
        where
            posting_fact.posting_id = new.posting_id
            and posting_fact.posting_origin_kind not in (
                'DIRECT_JOURNAL',
                'SALE_SETTLED',
                'SALE_ON_CREDIT',
                'PURCHASE_SETTLED',
                'PURCHASE_ON_CREDIT',
                'INVENTORY_CAPITALIZATION_SETTLED',
                'INVENTORY_CAPITALIZATION_ON_CREDIT',
                'EXPENSE_SETTLED',
                'EXPENSE_ON_CREDIT',
                'OWNER_CONTRIBUTION',
                'OWNER_WITHDRAWAL',
                'FOREIGN_CURRENCY_OBLIGATION',
                'REALIZED_FOREIGN_EXCHANGE_SETTLEMENT',
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
    )
    and not exists (
        select 1
        from posting_fact
        where posting_fact.posting_id = new.posting_id
            and posting_fact.prior_posting_id is not null
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

create table if not exists inventory_movement (
    movement_id text primary key check (length(trim(movement_id)) > 0),
    inventory_account text not null references account (account_code),
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
    account_sequence integer not null check (account_sequence >= 1),
    kind text not null check (
        kind in (
            'ACQUISITION',
            'CAPITALIZATION',
            'COUNT_INCREASE',
            'OPENING',
            'DISPOSAL',
            'WRITE_DOWN',
            'SHRINKAGE',
            'REVERSAL_COMP'
        )
    ),
    quantity_delta integer not null,
    cost_delta_minor integer not null,
    posting_id text not null references posting_fact (posting_id),
    unique (inventory_account, account_sequence),
    check (quantity_delta <> 0 or cost_delta_minor <> 0)
) strict;

create trigger if not exists inventory_movement_validate_inventory_account_on_insert
before insert on inventory_movement
begin
    select raise(
        fail,
        'inventory movements must reference one active postable inventory account.'
    )
    where exists (
        select 1
        from account
        where
            account.account_code = new.inventory_account
            and (
                account.active = 0
                or account.account_type <> 'ASSET'
                or account.account_node_kind <> 'POSTABLE'
                or account.financial_position_line_classification <> 'INVENTORY'
            )
    );
    select raise(
        fail,
        'inventory movement effective date must match the referenced posting effective date.'
    )
    where exists (
        select 1
        from posting_fact
        where
            posting_fact.posting_id = new.posting_id
            and posting_fact.effective_date <> new.effective_date
    );
end;

create trigger if not exists inventory_movement_validate_account_horizon_on_insert
before insert on inventory_movement
when exists (select 1 from inventory_movement where inventory_account = new.inventory_account)
begin
    select raise(
        fail,
        'inventory movements must append in non-decreasing effective-date order per account.'
    )
    where new.effective_date < (
        select max(effective_date)
        from inventory_movement
        where inventory_account = new.inventory_account
    );
end;

create trigger if not exists inventory_movement_validate_account_sequence_on_insert
before insert on inventory_movement
begin
    select raise(
        fail,
        'inventory movements must append with the next store-owned account sequence per account.'
    )
    where new.account_sequence <> (
        select coalesce(max(account_sequence), 0) + 1
        from inventory_movement
        where inventory_account = new.inventory_account
    );
end;

create trigger if not exists inventory_movement_validate_typed_posting_origin_on_insert
before insert on inventory_movement
begin
    select raise(
        fail,
        'inventory movements require a matching typed posting origin.'
    )
    where not exists (
        select 1
        from posting_fact
        where
            posting_fact.posting_id = new.posting_id
            and (
                (
                    new.kind = 'ACQUISITION'
                    and posting_fact.posting_origin_kind in ('PURCHASE_SETTLED', 'PURCHASE_ON_CREDIT')
                )
                or (
                    new.kind = 'CAPITALIZATION'
                    and posting_fact.posting_origin_kind in (
                        'INVENTORY_CAPITALIZATION_SETTLED',
                        'INVENTORY_CAPITALIZATION_ON_CREDIT'
                    )
                )
                or (
                    new.kind = 'COUNT_INCREASE'
                    and posting_fact.posting_origin_kind = 'INVENTORY_COUNT_INCREASE'
                )
                or (
                    new.kind = 'OPENING'
                    and posting_fact.posting_origin_kind = 'OPENING_POSITION'
                )
                or (
                    new.kind = 'DISPOSAL'
                    and posting_fact.posting_origin_kind in ('SALE_SETTLED', 'SALE_ON_CREDIT')
                )
                or (
                    new.kind = 'WRITE_DOWN'
                    and posting_fact.posting_origin_kind = 'INVENTORY_WRITE_DOWN'
                )
                or (
                    new.kind = 'SHRINKAGE'
                    and posting_fact.posting_origin_kind = 'INVENTORY_SHRINKAGE'
                )
                or (
                    new.kind = 'REVERSAL_COMP'
                    and posting_fact.posting_origin_kind = 'REVERSAL'
                )
            )
    );
end;

create trigger if not exists inventory_movement_validate_opening_on_insert
before insert on inventory_movement
when new.kind = 'OPENING'
begin
    select raise(
        fail,
        'inventory opening movements must be the first durable movement for their inventory account.'
    )
    where exists (
        select 1
        from inventory_movement
        where inventory_account = new.inventory_account
    );
end;

create table if not exists inventory_on_hand (
    inventory_account text primary key references account (account_code),
    quantity integer not null check (quantity >= 0),
    cost_pool_minor integer not null check (cost_pool_minor >= 0),
    last_movement_date text not null check (
        last_movement_date glob '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]'
        and substr(last_movement_date, 6, 2) between '01' and '12'
        and (
            (
                substr(last_movement_date, 6, 2) in ('01', '03', '05', '07', '08', '10', '12')
                and substr(last_movement_date, 9, 2) between '01' and '31'
            )
            or (
                substr(last_movement_date, 6, 2) in ('04', '06', '09', '11')
                and substr(last_movement_date, 9, 2) between '01' and '30'
            )
            or (
                substr(last_movement_date, 6, 2) = '02'
                and (
                    substr(last_movement_date, 9, 2) between '01' and '28'
                    or (
                        substr(last_movement_date, 9, 2) = '29'
                        and (
                            cast(substr(last_movement_date, 1, 4) as integer) % 400 = 0
                            or (
                                cast(substr(last_movement_date, 1, 4) as integer) % 4 = 0
                                and cast(substr(last_movement_date, 1, 4) as integer) % 100 <> 0
                            )
                        )
                    )
                )
            )
        )
    ),
    check ((quantity = 0) = (cost_pool_minor = 0))
) strict;

create trigger if not exists inventory_on_hand_validate_inventory_account_on_insert
before insert on inventory_on_hand
begin
    select raise(
        fail,
        'inventory_on_hand rows must reference one active postable inventory account.'
    )
    where exists (
        select 1
        from account
        where
            account.account_code = new.inventory_account
            and (
                account.active = 0
                or account.account_type <> 'ASSET'
                or account.account_node_kind <> 'POSTABLE'
                or account.financial_position_line_classification <> 'INVENTORY'
            )
    );
end;

create trigger if not exists inventory_on_hand_validate_inventory_account_on_update
before update on inventory_on_hand
begin
    select raise(
        fail,
        'inventory_on_hand rows must reference one active postable inventory account.'
    )
    where exists (
        select 1
        from account
        where
            account.account_code = new.inventory_account
            and (
                account.active = 0
                or account.account_type <> 'ASSET'
                or account.account_node_kind <> 'POSTABLE'
                or account.financial_position_line_classification <> 'INVENTORY'
            )
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
    check (effective_date_from <= effective_date_to),
    unique (effective_date_from, effective_date_to)
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

create table if not exists accrual_cutoff (
    accrual_cutoff_id text primary key check (
        length(accrual_cutoff_id) between 1 and 120
        and accrual_cutoff_id glob '[a-z0-9]*'
        and accrual_cutoff_id not glob '*[^a-z0-9-]*'
        and accrual_cutoff_id not like '-%'
        and accrual_cutoff_id not like '%-'
        and accrual_cutoff_id not like '%--%'
    ),
    kind text not null check (kind in ('PREPAYMENT', 'DEFERRED_REVENUE', 'ACCRUED_EXPENSE')),
    origin_posting_id text not null unique references posting_fact (posting_id),
    originated_on text not null,
    cutoff_account_code text not null references account (account_code),
    recognition_account_code text not null references account (account_code),
    amount_currency_code text not null check (amount_currency_code glob '[A-Z][A-Z][A-Z]'),
    original_amount_minor integer not null check (original_amount_minor > 0),
    recognition_start_date text,
    recognition_end_date text,
    check (
        (
            kind in ('PREPAYMENT', 'DEFERRED_REVENUE')
            and recognition_start_date is not null
            and recognition_end_date is not null
            and recognition_start_date <= recognition_end_date
            and originated_on <= recognition_start_date
        )
        or (
            kind = 'ACCRUED_EXPENSE'
            and recognition_start_date is null
            and recognition_end_date is null
        )
    )
) strict;

create trigger if not exists accrual_cutoff_validate_origin_on_insert
before insert on accrual_cutoff
begin
    select raise(fail, 'accrual_cutoff origin posting must be the matching typed cut-off event.')
    where not exists (
        select 1
        from posting_fact
        where posting_fact.posting_id = new.origin_posting_id
            and posting_fact.effective_date = new.originated_on
            and posting_fact.entry_amount_currency_code = new.amount_currency_code
            and posting_fact.entry_amount_minor = new.original_amount_minor
            and (
                (
                    new.kind = 'PREPAYMENT'
                    and posting_fact.posting_origin_kind = 'PREPAYMENT'
                    and posting_fact.entry_primary_debit_account_code = new.cutoff_account_code
                )
                or (
                    new.kind = 'DEFERRED_REVENUE'
                    and posting_fact.posting_origin_kind = 'DEFERRED_REVENUE'
                    and posting_fact.entry_primary_credit_account_code = new.cutoff_account_code
                )
                or (
                    new.kind = 'ACCRUED_EXPENSE'
                    and posting_fact.posting_origin_kind = 'ACCRUED_EXPENSE'
                    and posting_fact.entry_primary_debit_account_code = new.recognition_account_code
                    and posting_fact.entry_primary_credit_account_code = new.cutoff_account_code
                )
            )
    );
    select raise(fail, 'prepayment cut-offs require a prepayment asset and expense account.')
    where new.kind = 'PREPAYMENT'
        and (
            not exists (
                select 1 from account
                where account_code = new.cutoff_account_code
                    and account_type = 'ASSET'
                    and financial_position_line_classification = 'PREPAID_EXPENSE'
            )
            or not exists (
                select 1 from account
                where account_code = new.recognition_account_code
                    and account_type = 'EXPENSE'
            )
        );
    select raise(fail, 'prepayment cut-offs require a declared cash credit account.')
    where new.kind = 'PREPAYMENT'
        and not exists (
            select 1
            from posting_fact
            inner join account on account.account_code = posting_fact.entry_primary_credit_account_code
            where posting_fact.posting_id = new.origin_posting_id
                and account.account_type = 'ASSET'
                and account.cash_flow_asset_classification = 'CASH_AND_CASH_EQUIVALENT'
        );
    select raise(fail, 'deferred-revenue cut-offs require a deferred-revenue liability and revenue account.')
    where new.kind = 'DEFERRED_REVENUE'
        and (
            not exists (
                select 1 from account
                where account_code = new.cutoff_account_code
                    and account_type = 'LIABILITY'
                    and financial_position_line_classification = 'DEFERRED_REVENUE'
            )
            or not exists (
                select 1 from account
                where account_code = new.recognition_account_code
                    and account_type = 'REVENUE'
            )
        );
    select raise(fail, 'deferred-revenue cut-offs require a declared cash debit account.')
    where new.kind = 'DEFERRED_REVENUE'
        and not exists (
            select 1
            from posting_fact
            inner join account on account.account_code = posting_fact.entry_primary_debit_account_code
            where posting_fact.posting_id = new.origin_posting_id
                and account.account_type = 'ASSET'
                and account.cash_flow_asset_classification = 'CASH_AND_CASH_EQUIVALENT'
        );
    select raise(fail, 'accrued-expense cut-offs require an accrued-expense liability and expense account.')
    where new.kind = 'ACCRUED_EXPENSE'
        and (
            not exists (
                select 1 from account
                where account_code = new.cutoff_account_code
                    and account_type = 'LIABILITY'
                    and financial_position_line_classification = 'ACCRUED_EXPENSE'
            )
            or not exists (
                select 1 from account
                where account_code = new.recognition_account_code
                    and account_type = 'EXPENSE'
            )
        );
end;

create table if not exists accrual_cutoff_application (
    application_posting_id text primary key references posting_fact (posting_id),
    accrual_cutoff_id text not null references accrual_cutoff (accrual_cutoff_id),
    application_kind text not null check (application_kind in ('RECOGNITION', 'SETTLEMENT', 'ORIGIN_REVERSAL', 'APPLICATION_REVERSAL')),
    effective_date text not null,
    amount_currency_code text not null check (amount_currency_code glob '[A-Z][A-Z][A-Z]'),
    amount_minor integer not null check (amount_minor <> 0)
) strict;

create trigger if not exists accrual_cutoff_application_validate_on_insert
before insert on accrual_cutoff_application
begin
    select raise(fail, 'accrual_cutoff_application must use the cut-off currency.')
    where exists (
        select 1
        from accrual_cutoff
        where accrual_cutoff_id = new.accrual_cutoff_id
            and amount_currency_code <> new.amount_currency_code
    );
    select raise(fail, 'accrual_cutoff_application posting facts must match the application facts.')
    where not exists (
        select 1
        from posting_fact
        where posting_fact.posting_id = new.application_posting_id
            and posting_fact.effective_date = new.effective_date
            and (
                new.application_kind in ('ORIGIN_REVERSAL', 'APPLICATION_REVERSAL')
                or (
                    posting_fact.entry_amount_currency_code = new.amount_currency_code
                    and posting_fact.entry_amount_minor = abs(new.amount_minor)
                )
            )
    );
    select raise(fail, 'accrual_cutoff_application must use the matching typed application event and accounts.')
    where not exists (
        select 1
        from accrual_cutoff
        inner join posting_fact on posting_fact.posting_id = new.application_posting_id
        where accrual_cutoff.accrual_cutoff_id = new.accrual_cutoff_id
            and (
                (
                    new.application_kind = 'RECOGNITION'
                    and accrual_cutoff.kind = 'PREPAYMENT'
                    and posting_fact.posting_origin_kind = 'ACCRUAL_CUTOFF_RECOGNITION'
                    and posting_fact.entry_primary_debit_account_code = accrual_cutoff.recognition_account_code
                    and posting_fact.entry_primary_credit_account_code = accrual_cutoff.cutoff_account_code
                )
                or (
                    new.application_kind = 'RECOGNITION'
                    and accrual_cutoff.kind = 'DEFERRED_REVENUE'
                    and posting_fact.posting_origin_kind = 'ACCRUAL_CUTOFF_RECOGNITION'
                    and posting_fact.entry_primary_debit_account_code = accrual_cutoff.cutoff_account_code
                    and posting_fact.entry_primary_credit_account_code = accrual_cutoff.recognition_account_code
                )
                or (
                    new.application_kind = 'SETTLEMENT'
                    and accrual_cutoff.kind = 'ACCRUED_EXPENSE'
                    and posting_fact.posting_origin_kind = 'ACCRUED_EXPENSE_SETTLEMENT'
                    and posting_fact.entry_primary_debit_account_code = accrual_cutoff.cutoff_account_code
                )
                or (
                    new.application_kind = 'ORIGIN_REVERSAL'
                    and posting_fact.posting_origin_kind = 'REVERSAL'
                    and posting_fact.prior_posting_id = accrual_cutoff.origin_posting_id
                )
                or (
                    new.application_kind = 'APPLICATION_REVERSAL'
                    and posting_fact.posting_origin_kind = 'REVERSAL'
                    and exists (
                        select 1
                        from accrual_cutoff_application original_application
                        where original_application.application_posting_id = posting_fact.prior_posting_id
                            and original_application.accrual_cutoff_id = accrual_cutoff.accrual_cutoff_id
                            and original_application.application_kind in ('RECOGNITION', 'SETTLEMENT')
                            and original_application.amount_currency_code = new.amount_currency_code
                            and original_application.amount_minor = -new.amount_minor
                    )
                )
            )
    );
    select raise(fail, 'accrual_cutoff_application must not precede its origin posting.')
    where exists (
        select 1
        from accrual_cutoff
        where accrual_cutoff_id = new.accrual_cutoff_id
            and new.effective_date < originated_on
    );
    select raise(fail, 'accrual_cutoff_application must append in non-decreasing effective-date order.')
    where exists (
        select 1
        from accrual_cutoff_application
        where accrual_cutoff_id = new.accrual_cutoff_id
            and effective_date > new.effective_date
    );
    select raise(fail, 'accrual cut-off recognition must remain inside its declared inclusive interval.')
    where exists (
        select 1
        from accrual_cutoff
        where accrual_cutoff_id = new.accrual_cutoff_id
            and new.application_kind = 'RECOGNITION'
            and (
                new.effective_date < recognition_start_date
                or new.effective_date > recognition_end_date
            )
    );
    select raise(fail, 'accrued-expense settlement requires a declared cash credit account.')
    where exists (
        select 1
        from accrual_cutoff
        where accrual_cutoff.accrual_cutoff_id = new.accrual_cutoff_id
            and accrual_cutoff.kind = 'ACCRUED_EXPENSE'
            and new.application_kind = 'SETTLEMENT'
    )
    and not exists (
        select 1
        from posting_fact
        inner join account on account.account_code = posting_fact.entry_primary_credit_account_code
        where posting_fact.posting_id = new.application_posting_id
            and account.account_type = 'ASSET'
            and account.cash_flow_asset_classification = 'CASH_AND_CASH_EQUIVALENT'
    );
    select raise(fail, 'accrual_cutoff_application amount must not exceed the remaining cut-off amount.')
    where exists (
        select 1
        from accrual_cutoff
        where accrual_cutoff_id = new.accrual_cutoff_id
            and original_amount_minor < (
                new.amount_minor + coalesce((
                    select sum(amount_minor)
                    from accrual_cutoff_application
                    where accrual_cutoff_id = new.accrual_cutoff_id
                ), 0)
            )
    );
    select raise(fail, 'accrual_cutoff_application amount must not make the applied cut-off amount negative.')
    where exists (
        select 1
        from accrual_cutoff
        where accrual_cutoff_id = new.accrual_cutoff_id
            and 0 > (
                new.amount_minor + coalesce((
                    select sum(amount_minor)
                    from accrual_cutoff_application
                    where accrual_cutoff_id = new.accrual_cutoff_id
                ), 0)
            )
    );
end;

create trigger if not exists accrual_cutoff_reject_update
before update on accrual_cutoff
begin
    select raise(fail, 'accrual_cutoff rows are append-only.');
end;

create trigger if not exists accrual_cutoff_reject_delete
before delete on accrual_cutoff
begin
    select raise(fail, 'accrual_cutoff rows are append-only.');
end;

create trigger if not exists accrual_cutoff_application_reject_update
before update on accrual_cutoff_application
begin
    select raise(fail, 'accrual_cutoff_application rows are append-only.');
end;

create trigger if not exists accrual_cutoff_application_reject_delete
before delete on accrual_cutoff_application
begin
    select raise(fail, 'accrual_cutoff_application rows are append-only.');
end;

create table if not exists fixed_asset (
    fixed_asset_id text primary key check (
        length(fixed_asset_id) between 1 and 120
        and fixed_asset_id glob '[a-z0-9]*'
        and fixed_asset_id not glob '*[^a-z0-9-]*'
        and fixed_asset_id not like '-%'
        and fixed_asset_id not like '%-'
        and fixed_asset_id not like '%--%'
    ),
    origin_posting_id text not null unique references posting_fact (posting_id),
    capitalized_on text not null,
    asset_account_code text not null references account (account_code),
    accumulated_depreciation_account_code text not null references account (account_code),
    depreciation_expense_account_code text not null references account (account_code),
    disposal_gain_account_code text not null references account (account_code),
    disposal_loss_account_code text not null references account (account_code),
    currency_code text not null check (currency_code glob '[A-Z][A-Z][A-Z]'),
    cost_minor integer not null check (cost_minor > 0),
    residual_value_minor integer not null check (residual_value_minor >= 0 and residual_value_minor < cost_minor),
    in_service_date text not null check (in_service_date >= capitalized_on),
    useful_life_months integer not null check (useful_life_months between 1 and 1200),
    check (
        asset_account_code <> accumulated_depreciation_account_code
        and asset_account_code <> depreciation_expense_account_code
        and asset_account_code <> disposal_gain_account_code
        and asset_account_code <> disposal_loss_account_code
        and accumulated_depreciation_account_code <> depreciation_expense_account_code
        and accumulated_depreciation_account_code <> disposal_gain_account_code
        and accumulated_depreciation_account_code <> disposal_loss_account_code
        and depreciation_expense_account_code <> disposal_gain_account_code
        and depreciation_expense_account_code <> disposal_loss_account_code
        and disposal_gain_account_code <> disposal_loss_account_code
    )
) strict;

create table if not exists fixed_asset_application (
    application_posting_id text primary key references posting_fact (posting_id),
    fixed_asset_id text not null references fixed_asset (fixed_asset_id),
    application_kind text not null check (application_kind in ('DEPRECIATION', 'DISPOSAL')),
    effective_date text not null,
    currency_code text not null check (currency_code glob '[A-Z][A-Z][A-Z]'),
    amount_minor integer not null check (amount_minor >= 0)
) strict;

create trigger if not exists fixed_asset_validate_origin_on_insert
before insert on fixed_asset
begin
    select raise(fail, 'fixed_asset origin must be the matching typed capitalization posting.')
    where not exists (
        select 1 from posting_fact
        where posting_id = new.origin_posting_id
            and posting_origin_kind = 'FIXED_ASSET_CAPITALIZATION'
            and effective_date = new.capitalized_on
            and entry_amount_currency_code = new.currency_code
            and entry_amount_minor = new.cost_minor
    );
    select raise(fail, 'fixed_asset accounts must use the required asset, expense, revenue, expense taxonomy.')
    where
        not exists (select 1 from account where account_code = new.asset_account_code and account_type = 'ASSET' and financial_position_line_classification = 'NONCURRENT_ASSET' and cash_flow_asset_classification = 'NON_CASH')
        or not exists (select 1 from account where account_code = new.accumulated_depreciation_account_code and account_type = 'ASSET' and financial_position_line_classification = 'NONCURRENT_ASSET' and cash_flow_asset_classification = 'NON_CASH')
        or not exists (select 1 from account where account_code = new.depreciation_expense_account_code and account_type = 'EXPENSE')
        or not exists (select 1 from account where account_code = new.disposal_gain_account_code and account_type = 'REVENUE')
        or not exists (select 1 from account where account_code = new.disposal_loss_account_code and account_type = 'EXPENSE');
end;

create trigger if not exists fixed_asset_application_validate_on_insert
before insert on fixed_asset_application
begin
    select raise(fail, 'fixed_asset_application must use the fixed asset currency.')
    where exists (select 1 from fixed_asset where fixed_asset_id = new.fixed_asset_id and currency_code <> new.currency_code);
    select raise(fail, 'fixed_asset_application must use the matching typed posting kind.')
    where not exists (
        select 1 from posting_fact
        where posting_id = new.application_posting_id and effective_date = new.effective_date
            and ((new.application_kind = 'DEPRECIATION' and posting_origin_kind = 'FIXED_ASSET_DEPRECIATION')
                or (new.application_kind = 'DISPOSAL' and posting_origin_kind = 'FIXED_ASSET_DISPOSAL'))
    );
    select raise(fail, 'fixed_asset depreciation must match retained typed posting facts.')
    where new.application_kind = 'DEPRECIATION' and not exists (
        select 1 from posting_fact
        where posting_id = new.application_posting_id
            and entry_amount_currency_code = new.currency_code
            and entry_amount_minor = new.amount_minor
    );
    select raise(fail, 'fixed_asset_application must not precede the asset lifecycle horizon.')
    where exists (select 1 from fixed_asset where fixed_asset_id = new.fixed_asset_id and new.effective_date < capitalized_on)
        or exists (select 1 from fixed_asset_application where fixed_asset_id = new.fixed_asset_id and effective_date > new.effective_date);
    select raise(fail, 'fixed_asset depreciation must not exceed depreciable cost.')
    where new.application_kind = 'DEPRECIATION' and exists (
        select 1 from fixed_asset where fixed_asset_id = new.fixed_asset_id and new.amount_minor + coalesce((
            select sum(amount_minor) from fixed_asset_application
            where fixed_asset_id = new.fixed_asset_id and application_kind = 'DEPRECIATION'
                and not exists (
                    select 1 from fixed_asset_application_reversal reversal
                    where reversal.application_posting_id = fixed_asset_application.application_posting_id
                )
        ), 0) > cost_minor - residual_value_minor
    );
    select raise(fail, 'fixed_asset accepts at most one disposal.')
    where new.application_kind = 'DISPOSAL' and exists (
        select 1 from fixed_asset_application
        where fixed_asset_id = new.fixed_asset_id and application_kind = 'DISPOSAL'
            and not exists (
                select 1 from fixed_asset_application_reversal reversal
                where reversal.application_posting_id = fixed_asset_application.application_posting_id
            )
    );
    select raise(fail, 'fixed_asset disposal must use the immutable carrying amount.')
    where new.application_kind = 'DISPOSAL' and not exists (
        select 1
        from fixed_asset asset
        where asset.fixed_asset_id = new.fixed_asset_id
            and new.amount_minor = asset.cost_minor - coalesce((
                select sum(application.amount_minor)
                from fixed_asset_application application
                where application.fixed_asset_id = asset.fixed_asset_id
                    and application.application_kind = 'DEPRECIATION'
                    and not exists (
                        select 1 from fixed_asset_application_reversal reversal
                        where reversal.application_posting_id = application.application_posting_id
                    )
            ), 0)
    );
end;

create trigger if not exists fixed_asset_reject_update before update on fixed_asset begin select raise(fail, 'fixed_asset rows are append-only.'); end;
create trigger if not exists fixed_asset_reject_delete before delete on fixed_asset begin select raise(fail, 'fixed_asset rows are append-only.'); end;
create trigger if not exists fixed_asset_application_reject_update before update on fixed_asset_application begin select raise(fail, 'fixed_asset_application rows are append-only.'); end;
create trigger if not exists fixed_asset_application_reject_delete before delete on fixed_asset_application begin select raise(fail, 'fixed_asset_application rows are append-only.'); end;

create table if not exists fixed_asset_reversal (
    reversal_posting_id text primary key references posting_fact (posting_id),
    fixed_asset_id text not null unique references fixed_asset (fixed_asset_id)
) strict;

create table if not exists fixed_asset_application_reversal (
    reversal_posting_id text primary key references posting_fact (posting_id),
    application_posting_id text not null unique references fixed_asset_application (application_posting_id)
) strict;

create trigger if not exists fixed_asset_reversal_validate_on_insert
before insert on fixed_asset_reversal
begin
    select raise(fail, 'fixed_asset reversal must negate its capitalization posting.')
    where not exists (
        select 1 from fixed_asset
        inner join posting_fact on posting_fact.posting_id = new.reversal_posting_id
        where fixed_asset.fixed_asset_id = new.fixed_asset_id
            and posting_fact.posting_origin_kind = 'REVERSAL'
            and posting_fact.prior_posting_id = fixed_asset.origin_posting_id
    );
    select raise(fail, 'fixed_asset capitalization cannot be reversed while active lifecycle applications remain.')
    where exists (
        select 1 from fixed_asset_application application
        where application.fixed_asset_id = new.fixed_asset_id
            and not exists (
                select 1 from fixed_asset_application_reversal reversal
                where reversal.application_posting_id = application.application_posting_id
            )
    );
end;

create trigger if not exists fixed_asset_application_reversal_validate_on_insert
before insert on fixed_asset_application_reversal
begin
    select raise(fail, 'fixed_asset application reversal must negate its application posting.')
    where not exists (
        select 1 from fixed_asset_application application
        inner join posting_fact on posting_fact.posting_id = new.reversal_posting_id
        where application.application_posting_id = new.application_posting_id
            and posting_fact.posting_origin_kind = 'REVERSAL'
            and posting_fact.prior_posting_id = application.application_posting_id
    );
end;

create trigger if not exists fixed_asset_reversal_reject_update before update on fixed_asset_reversal begin select raise(fail, 'fixed_asset_reversal rows are append-only.'); end;
create trigger if not exists fixed_asset_reversal_reject_delete before delete on fixed_asset_reversal begin select raise(fail, 'fixed_asset_reversal rows are append-only.'); end;
create trigger if not exists fixed_asset_application_reversal_reject_update before update on fixed_asset_application_reversal begin select raise(fail, 'fixed_asset_application_reversal rows are append-only.'); end;
create trigger if not exists fixed_asset_application_reversal_reject_delete before delete on fixed_asset_application_reversal begin select raise(fail, 'fixed_asset_application_reversal rows are append-only.'); end;

create table if not exists financing_arrangement (
    financing_arrangement_id text primary key check (
        length(financing_arrangement_id) between 1 and 120
        and financing_arrangement_id glob '[a-z0-9]*'
        and financing_arrangement_id not glob '*[^a-z0-9-]*'
        and financing_arrangement_id not like '-%'
        and financing_arrangement_id not like '%-'
        and financing_arrangement_id not like '%--%'
    ),
    origin_posting_id text not null unique references posting_fact (posting_id),
    originated_on text not null,
    principal_liability_account_code text not null references account (account_code),
    interest_payable_account_code text not null references account (account_code),
    currency_code text not null check (currency_code glob '[A-Z][A-Z][A-Z]'),
    original_principal_minor integer not null check (original_principal_minor > 0),
    check (principal_liability_account_code <> interest_payable_account_code)
) strict;

create table if not exists financing_application (
    application_posting_id text primary key references posting_fact (posting_id),
    financing_arrangement_id text not null references financing_arrangement (financing_arrangement_id),
    application_kind text not null check (application_kind in ('PRINCIPAL_REPAYMENT', 'INTEREST_ACCRUAL', 'INTEREST_PAYMENT')),
    effective_date text not null,
    currency_code text not null check (currency_code glob '[A-Z][A-Z][A-Z]'),
    amount_minor integer not null check (amount_minor > 0)
) strict;

create table if not exists financing_arrangement_reversal (
    reversal_posting_id text primary key references posting_fact (posting_id),
    financing_arrangement_id text not null unique references financing_arrangement (financing_arrangement_id)
) strict;

create table if not exists financing_application_reversal (
    reversal_posting_id text primary key references posting_fact (posting_id),
    application_posting_id text not null unique references financing_application (application_posting_id)
) strict;

create trigger if not exists financing_arrangement_validate_origin_on_insert
before insert on financing_arrangement
begin
    select raise(fail, 'financing_arrangement origin must be the matching typed borrowing posting.')
    where not exists (
        select 1 from posting_fact
        where posting_id = new.origin_posting_id and posting_origin_kind = 'FINANCING_BORROWING'
            and effective_date = new.originated_on and entry_amount_currency_code = new.currency_code
            and entry_amount_minor = new.original_principal_minor
    );
    select raise(fail, 'financing_arrangement requires liability accounts.')
    where not exists (select 1 from account where account_code = new.principal_liability_account_code and account_type = 'LIABILITY')
        or not exists (select 1 from account where account_code = new.interest_payable_account_code and account_type = 'LIABILITY' and financial_position_line_classification = 'CURRENT_LIABILITY');
end;

create trigger if not exists financing_application_validate_on_insert
before insert on financing_application
begin
    select raise(fail, 'financing_application must use the arrangement currency.')
    where exists (select 1 from financing_arrangement where financing_arrangement_id = new.financing_arrangement_id and currency_code <> new.currency_code);
    select raise(fail, 'financing_application must use the matching typed posting kind.')
    where not exists (
        select 1 from posting_fact
        where posting_id = new.application_posting_id and effective_date = new.effective_date
            and ((new.application_kind = 'PRINCIPAL_REPAYMENT' and posting_origin_kind = 'FINANCING_PRINCIPAL_REPAYMENT')
                or (new.application_kind = 'INTEREST_ACCRUAL' and posting_origin_kind = 'FINANCING_INTEREST_ACCRUAL')
                or (new.application_kind = 'INTEREST_PAYMENT' and posting_origin_kind = 'FINANCING_INTEREST_PAYMENT'))
    );
    select raise(fail, 'financing_application must match retained typed posting facts.')
    where not exists (
        select 1 from posting_fact
        where posting_id = new.application_posting_id
            and entry_amount_currency_code = new.currency_code
            and entry_amount_minor = new.amount_minor
    );
    select raise(fail, 'financing_application must not precede the arrangement lifecycle horizon.')
    where exists (select 1 from financing_arrangement where financing_arrangement_id = new.financing_arrangement_id and new.effective_date < originated_on)
        or exists (select 1 from financing_application where financing_arrangement_id = new.financing_arrangement_id and effective_date > new.effective_date);
    select raise(fail, 'financing principal repayment must not exceed principal outstanding.')
    where new.application_kind = 'PRINCIPAL_REPAYMENT' and exists (
        select 1 from financing_arrangement where financing_arrangement_id = new.financing_arrangement_id and new.amount_minor + coalesce((
            select sum(amount_minor) from financing_application
            where financing_arrangement_id = new.financing_arrangement_id
                and application_kind = 'PRINCIPAL_REPAYMENT'
                and not exists (
                    select 1 from financing_application_reversal reversal
                    where reversal.application_posting_id = financing_application.application_posting_id
                )
        ), 0) > original_principal_minor
    );
    select raise(fail, 'financing interest payment must not exceed accrued interest.')
    where new.application_kind = 'INTEREST_PAYMENT' and new.amount_minor > coalesce((
        select sum(case when application_kind = 'INTEREST_ACCRUAL' then amount_minor else -amount_minor end)
        from financing_application
        where financing_arrangement_id = new.financing_arrangement_id
            and application_kind in ('INTEREST_ACCRUAL', 'INTEREST_PAYMENT')
            and not exists (
                select 1 from financing_application_reversal reversal
                where reversal.application_posting_id = financing_application.application_posting_id
            )
    ), 0);
end;

create trigger if not exists financing_arrangement_reversal_validate_on_insert
before insert on financing_arrangement_reversal
begin
    select raise(fail, 'financing arrangement reversal must negate its borrowing posting.')
    where not exists (
        select 1
        from financing_arrangement arrangement
        inner join posting_fact reversal on reversal.posting_id = new.reversal_posting_id
        where arrangement.financing_arrangement_id = new.financing_arrangement_id
            and reversal.posting_origin_kind = 'REVERSAL'
            and reversal.prior_posting_id = arrangement.origin_posting_id
    );
    select raise(fail, 'financing borrowing cannot be reversed while active lifecycle applications remain.')
    where exists (
        select 1
        from financing_application application
        where application.financing_arrangement_id = new.financing_arrangement_id
            and not exists (
                select 1 from financing_application_reversal reversal
                where reversal.application_posting_id = application.application_posting_id
            )
    );
end;

create trigger if not exists financing_application_reversal_validate_on_insert
before insert on financing_application_reversal
begin
    select raise(fail, 'financing application reversal must negate its lifecycle posting.')
    where not exists (
        select 1
        from financing_application application
        inner join posting_fact reversal on reversal.posting_id = new.reversal_posting_id
        where application.application_posting_id = new.application_posting_id
            and reversal.posting_origin_kind = 'REVERSAL'
            and reversal.prior_posting_id = application.application_posting_id
    );
end;

create trigger if not exists financing_arrangement_reject_update before update on financing_arrangement begin select raise(fail, 'financing_arrangement rows are append-only.'); end;
create trigger if not exists financing_arrangement_reject_delete before delete on financing_arrangement begin select raise(fail, 'financing_arrangement rows are append-only.'); end;
create trigger if not exists financing_application_reject_update before update on financing_application begin select raise(fail, 'financing_application rows are append-only.'); end;
create trigger if not exists financing_application_reject_delete before delete on financing_application begin select raise(fail, 'financing_application rows are append-only.'); end;
create trigger if not exists financing_arrangement_reversal_reject_update before update on financing_arrangement_reversal begin select raise(fail, 'financing_arrangement_reversal rows are append-only.'); end;
create trigger if not exists financing_arrangement_reversal_reject_delete before delete on financing_arrangement_reversal begin select raise(fail, 'financing_arrangement_reversal rows are append-only.'); end;
create trigger if not exists financing_application_reversal_reject_update before update on financing_application_reversal begin select raise(fail, 'financing_application_reversal rows are append-only.'); end;
create trigger if not exists financing_application_reversal_reject_delete before delete on financing_application_reversal begin select raise(fail, 'financing_application_reversal rows are append-only.'); end;

create table if not exists foreign_currency_obligation (
    foreign_currency_obligation_id text primary key check (
        length(foreign_currency_obligation_id) between 1 and 120
        and foreign_currency_obligation_id glob '[a-z0-9]*'
        and foreign_currency_obligation_id not glob '*[^a-z0-9-]*'
        and foreign_currency_obligation_id not like '-%'
        and foreign_currency_obligation_id not like '%-'
        and foreign_currency_obligation_id not like '%--%'
    ),
    origin_posting_id text not null unique references posting_fact (posting_id),
    originated_on text not null,
    receivable_account_code text not null references account (account_code),
    realized_gain_account_code text not null references account (account_code),
    realized_loss_account_code text not null references account (account_code),
    transaction_currency_code text not null check (transaction_currency_code glob '[A-Z][A-Z][A-Z]'),
    transaction_amount_minor integer not null check (transaction_amount_minor > 0),
    functional_currency_code text not null check (functional_currency_code glob '[A-Z][A-Z][A-Z]'),
    functional_carrying_amount_minor integer not null check (functional_carrying_amount_minor > 0),
    check (transaction_currency_code <> functional_currency_code and receivable_account_code <> realized_gain_account_code and receivable_account_code <> realized_loss_account_code and realized_gain_account_code <> realized_loss_account_code)
) strict;

create table if not exists foreign_currency_obligation_settlement (
    settlement_posting_id text primary key references posting_fact (posting_id),
    foreign_currency_obligation_id text not null references foreign_currency_obligation (foreign_currency_obligation_id),
    effective_date text not null,
    functional_currency_code text not null check (functional_currency_code glob '[A-Z][A-Z][A-Z]'),
    functional_settlement_amount_minor integer not null check (functional_settlement_amount_minor > 0)
) strict;

create table if not exists foreign_currency_obligation_reversal (
    reversal_posting_id text primary key references posting_fact (posting_id),
    foreign_currency_obligation_id text not null unique references foreign_currency_obligation (foreign_currency_obligation_id)
) strict;

create table if not exists foreign_currency_obligation_settlement_reversal (
    reversal_posting_id text primary key references posting_fact (posting_id),
    settlement_posting_id text not null unique references foreign_currency_obligation_settlement (settlement_posting_id)
) strict;

create trigger if not exists foreign_currency_obligation_validate_origin_on_insert
before insert on foreign_currency_obligation
begin
    select raise(fail, 'foreign_currency_obligation origin must be the matching typed receivable posting.')
    where not exists (select 1 from posting_fact where posting_id = new.origin_posting_id and posting_origin_kind = 'FOREIGN_CURRENCY_OBLIGATION' and effective_date = new.originated_on);
    select raise(fail, 'foreign_currency_obligation must match retained posting foreign-exchange facts.')
    where not exists (select 1 from posting_foreign_exchange where posting_id = new.origin_posting_id and transaction_currency_code = new.transaction_currency_code and transaction_amount_minor = new.transaction_amount_minor and functional_currency_code = new.functional_currency_code and functional_amount_minor = new.functional_carrying_amount_minor);
    select raise(fail, 'foreign_currency_obligation requires receivable, revenue, and expense accounts.')
    where not exists (select 1 from account where account_code = new.receivable_account_code and account_type = 'ASSET' and financial_position_line_classification = 'TRADE_RECEIVABLE')
        or not exists (select 1 from account where account_code = new.realized_gain_account_code and account_type = 'REVENUE')
        or not exists (select 1 from account where account_code = new.realized_loss_account_code and account_type = 'EXPENSE');
end;

create trigger if not exists foreign_currency_obligation_settlement_validate_on_insert
before insert on foreign_currency_obligation_settlement
begin
    select raise(fail, 'foreign_currency obligation settlement must use the matching typed settlement posting.')
    where not exists (select 1 from posting_fact where posting_id = new.settlement_posting_id and posting_origin_kind = 'REALIZED_FOREIGN_EXCHANGE_SETTLEMENT' and effective_date = new.effective_date);
    select raise(fail, 'foreign_currency obligation settlement must use the retained functional currency.')
    where exists (select 1 from foreign_currency_obligation where foreign_currency_obligation_id = new.foreign_currency_obligation_id and functional_currency_code <> new.functional_currency_code);
    select raise(fail, 'foreign_currency obligation settlement must match retained transaction and functional foreign-exchange facts.')
    where not exists (
        select 1
        from foreign_currency_obligation obligation
        inner join posting_foreign_exchange foreign_exchange
            on foreign_exchange.posting_id = new.settlement_posting_id
        where obligation.foreign_currency_obligation_id = new.foreign_currency_obligation_id
            and foreign_exchange.transaction_currency_code = obligation.transaction_currency_code
            and foreign_exchange.transaction_amount_minor = obligation.transaction_amount_minor
            and foreign_exchange.functional_currency_code = new.functional_currency_code
            and foreign_exchange.functional_amount_minor = new.functional_settlement_amount_minor
    );
    select raise(fail, 'foreign_currency obligation settlement must not precede its origin.')
    where exists (select 1 from foreign_currency_obligation where foreign_currency_obligation_id = new.foreign_currency_obligation_id and new.effective_date < originated_on);
    select raise(fail, 'foreign_currency obligation settlement must not precede its lifecycle horizon.')
    where exists (
        select 1
        from foreign_currency_obligation_settlement settlement
        where settlement.foreign_currency_obligation_id = new.foreign_currency_obligation_id
            and settlement.effective_date > new.effective_date
    );
    select raise(fail, 'foreign_currency obligation accepts only one active settlement.')
    where exists (
        select 1
        from foreign_currency_obligation_settlement settlement
        where settlement.foreign_currency_obligation_id = new.foreign_currency_obligation_id
            and not exists (
                select 1 from foreign_currency_obligation_settlement_reversal reversal
                where reversal.settlement_posting_id = settlement.settlement_posting_id
            )
    );
end;

create trigger if not exists foreign_currency_obligation_reversal_validate_on_insert
before insert on foreign_currency_obligation_reversal
begin
    select raise(fail, 'foreign_currency obligation reversal must negate its receivable posting.')
    where not exists (
        select 1
        from foreign_currency_obligation obligation
        inner join posting_fact reversal on reversal.posting_id = new.reversal_posting_id
        where obligation.foreign_currency_obligation_id = new.foreign_currency_obligation_id
            and reversal.posting_origin_kind = 'REVERSAL'
            and reversal.prior_posting_id = obligation.origin_posting_id
    );
    select raise(fail, 'foreign_currency obligation cannot be reversed while an active settlement remains.')
    where exists (
        select 1
        from foreign_currency_obligation_settlement settlement
        where settlement.foreign_currency_obligation_id = new.foreign_currency_obligation_id
            and not exists (
                select 1 from foreign_currency_obligation_settlement_reversal reversal
                where reversal.settlement_posting_id = settlement.settlement_posting_id
            )
    );
end;

create trigger if not exists foreign_currency_obligation_settlement_reversal_validate_on_insert
before insert on foreign_currency_obligation_settlement_reversal
begin
    select raise(fail, 'foreign_currency obligation settlement reversal must negate its settlement posting.')
    where not exists (
        select 1
        from foreign_currency_obligation_settlement settlement
        inner join posting_fact reversal on reversal.posting_id = new.reversal_posting_id
        where settlement.settlement_posting_id = new.settlement_posting_id
            and reversal.posting_origin_kind = 'REVERSAL'
            and reversal.prior_posting_id = settlement.settlement_posting_id
    );
end;

create trigger if not exists foreign_currency_obligation_reject_update before update on foreign_currency_obligation begin select raise(fail, 'foreign_currency_obligation rows are append-only.'); end;
create trigger if not exists foreign_currency_obligation_reject_delete before delete on foreign_currency_obligation begin select raise(fail, 'foreign_currency_obligation rows are append-only.'); end;
create trigger if not exists foreign_currency_obligation_settlement_reject_update before update on foreign_currency_obligation_settlement begin select raise(fail, 'foreign_currency_obligation_settlement rows are append-only.'); end;
create trigger if not exists foreign_currency_obligation_settlement_reject_delete before delete on foreign_currency_obligation_settlement begin select raise(fail, 'foreign_currency_obligation_settlement rows are append-only.'); end;
create trigger if not exists foreign_currency_obligation_reversal_reject_update before update on foreign_currency_obligation_reversal begin select raise(fail, 'foreign_currency_obligation_reversal rows are append-only.'); end;
create trigger if not exists foreign_currency_obligation_reversal_reject_delete before delete on foreign_currency_obligation_reversal begin select raise(fail, 'foreign_currency_obligation_reversal rows are append-only.'); end;
create trigger if not exists foreign_currency_obligation_settlement_reversal_reject_update before update on foreign_currency_obligation_settlement_reversal begin select raise(fail, 'foreign_currency_obligation_settlement_reversal rows are append-only.'); end;
create trigger if not exists foreign_currency_obligation_settlement_reversal_reject_delete before delete on foreign_currency_obligation_settlement_reversal begin select raise(fail, 'foreign_currency_obligation_settlement_reversal rows are append-only.'); end;

create table if not exists latvian_payroll_run (
    payroll_run_id text primary key check (
        length(payroll_run_id) between 1 and 120
        and payroll_run_id glob '[a-z0-9]*'
        and payroll_run_id not glob '*[^a-z0-9-]*'
        and payroll_run_id not like '-%'
        and payroll_run_id not like '%-'
        and payroll_run_id not like '%--%'
    ),
    origin_posting_id text not null unique references posting_fact (posting_id),
    employee_reference text not null check (
        length(employee_reference) between 1 and 120
        and employee_reference glob '[a-z0-9]*'
        and employee_reference not glob '*[^a-z0-9-]*'
        and employee_reference not like '-%'
        and employee_reference not like '%-'
        and employee_reference not like '%--%'
    ),
    payroll_month text not null check (
        payroll_month glob '2026-[0-1][0-9]'
        and payroll_month between '2026-01' and '2026-12'
    ),
    effective_date text not null check (
        effective_date = date(payroll_month || '-01', '+1 month', '-1 day')
    ),
    wage_expense_account_code text not null references account (account_code),
    employer_social_expense_account_code text not null references account (account_code),
    net_wages_payable_account_code text not null references account (account_code),
    employee_social_payable_account_code text not null references account (account_code),
    employer_social_payable_account_code text not null references account (account_code),
    personal_income_tax_payable_account_code text not null references account (account_code),
    currency_code text not null check (currency_code = 'EUR'),
    gross_wages_minor integer not null check (gross_wages_minor between 1 and 877500),
    employee_social_contribution_minor integer not null check (employee_social_contribution_minor >= 0),
    employer_social_contribution_minor integer not null check (employer_social_contribution_minor >= 0),
    non_taxable_minimum_minor integer not null check (non_taxable_minimum_minor >= 0),
    personal_income_tax_minor integer not null check (personal_income_tax_minor >= 0),
    net_wages_minor integer not null check (net_wages_minor > 0),
    check (
        employee_social_contribution_minor = (gross_wages_minor * 105000 + 500000) / 1000000
        and employer_social_contribution_minor = (gross_wages_minor * 235900 + 500000) / 1000000
        and non_taxable_minimum_minor = min(55000, gross_wages_minor - employee_social_contribution_minor)
        and personal_income_tax_minor = (
            ((gross_wages_minor - employee_social_contribution_minor - non_taxable_minimum_minor) * 255000 + 500000) / 1000000
        )
        and net_wages_minor = gross_wages_minor - employee_social_contribution_minor - personal_income_tax_minor
    ),
    check (
        wage_expense_account_code <> employer_social_expense_account_code
        and wage_expense_account_code <> net_wages_payable_account_code
        and wage_expense_account_code <> employee_social_payable_account_code
        and wage_expense_account_code <> employer_social_payable_account_code
        and wage_expense_account_code <> personal_income_tax_payable_account_code
        and employer_social_expense_account_code <> net_wages_payable_account_code
        and employer_social_expense_account_code <> employee_social_payable_account_code
        and employer_social_expense_account_code <> employer_social_payable_account_code
        and employer_social_expense_account_code <> personal_income_tax_payable_account_code
        and net_wages_payable_account_code <> employee_social_payable_account_code
        and net_wages_payable_account_code <> employer_social_payable_account_code
        and net_wages_payable_account_code <> personal_income_tax_payable_account_code
        and employee_social_payable_account_code <> employer_social_payable_account_code
        and employee_social_payable_account_code <> personal_income_tax_payable_account_code
        and employer_social_payable_account_code <> personal_income_tax_payable_account_code
    )
) strict;

create table if not exists latvian_payroll_run_reversal (
    reversal_posting_id text primary key references posting_fact (posting_id),
    payroll_run_id text not null unique references latvian_payroll_run (payroll_run_id)
) strict;

create trigger if not exists latvian_payroll_run_validate_on_insert
before insert on latvian_payroll_run
begin
    select raise(fail, 'latvian_payroll_run requires an EUR functional-currency book.')
    where not exists (
        select 1 from book_identity where functional_currency_code = 'EUR'
    );
    select raise(fail, 'latvian_payroll_run origin must be the matching typed payroll posting.')
    where not exists (
        select 1
        from posting_fact
        where posting_id = new.origin_posting_id
            and posting_origin_kind = 'LATVIAN_MONTHLY_PAYROLL'
            and effective_date = new.effective_date
    );
    select raise(fail, 'latvian_payroll_run may have only one active run per employee and month.')
    where exists (
        select 1
        from latvian_payroll_run existing_run
        where existing_run.employee_reference = new.employee_reference
            and existing_run.payroll_month = new.payroll_month
            and not exists (
                select 1
                from latvian_payroll_run_reversal existing_reversal
                where existing_reversal.payroll_run_id = existing_run.payroll_run_id
            )
    );
    select raise(fail, 'latvian_payroll_run requires two expense accounts and four current liabilities.')
    where
        not exists (
            select 1 from account
            where account_code = new.wage_expense_account_code and account_type = 'EXPENSE'
        )
        or not exists (
            select 1 from account
            where account_code = new.employer_social_expense_account_code and account_type = 'EXPENSE'
        )
        or exists (
            select 1
            from account
            where account_code in (
                new.net_wages_payable_account_code,
                new.employee_social_payable_account_code,
                new.employer_social_payable_account_code,
                new.personal_income_tax_payable_account_code
            )
            and (
                account_type <> 'LIABILITY'
                or financial_position_line_classification <> 'CURRENT_LIABILITY'
            )
        )
        or 4 <> (
            select count(*)
            from account
            where account_code in (
                new.net_wages_payable_account_code,
                new.employee_social_payable_account_code,
                new.employer_social_payable_account_code,
                new.personal_income_tax_payable_account_code
            )
                and account_type = 'LIABILITY'
                and financial_position_line_classification = 'CURRENT_LIABILITY'
        );
    select raise(fail, 'latvian_payroll_run journal must exactly match its resolved component facts.')
    where exists (
        select 1
        from journal_line
        where posting_id = new.origin_posting_id
            and not (
                (account_code = new.wage_expense_account_code and entry_side = 'DEBIT' and currency_code = new.currency_code and amount_minor = new.gross_wages_minor)
                or (new.employer_social_contribution_minor > 0 and account_code = new.employer_social_expense_account_code and entry_side = 'DEBIT' and currency_code = new.currency_code and amount_minor = new.employer_social_contribution_minor)
                or (account_code = new.net_wages_payable_account_code and entry_side = 'CREDIT' and currency_code = new.currency_code and amount_minor = new.net_wages_minor)
                or (new.employee_social_contribution_minor > 0 and account_code = new.employee_social_payable_account_code and entry_side = 'CREDIT' and currency_code = new.currency_code and amount_minor = new.employee_social_contribution_minor)
                or (new.employer_social_contribution_minor > 0 and account_code = new.employer_social_payable_account_code and entry_side = 'CREDIT' and currency_code = new.currency_code and amount_minor = new.employer_social_contribution_minor)
                or (new.personal_income_tax_minor > 0 and account_code = new.personal_income_tax_payable_account_code and entry_side = 'CREDIT' and currency_code = new.currency_code and amount_minor = new.personal_income_tax_minor)
            )
    )
    or not exists (
        select 1 from journal_line
        where posting_id = new.origin_posting_id
            and account_code = new.wage_expense_account_code
            and entry_side = 'DEBIT'
            and currency_code = new.currency_code
            and amount_minor = new.gross_wages_minor
    )
    or not exists (
        select 1 from journal_line
        where posting_id = new.origin_posting_id
            and account_code = new.net_wages_payable_account_code
            and entry_side = 'CREDIT'
            and currency_code = new.currency_code
            and amount_minor = new.net_wages_minor
    )
    or (
        new.employee_social_contribution_minor > 0
        and not exists (
            select 1 from journal_line
            where posting_id = new.origin_posting_id
                and account_code = new.employee_social_payable_account_code
                and entry_side = 'CREDIT'
                and currency_code = new.currency_code
                and amount_minor = new.employee_social_contribution_minor
        )
    )
    or (
        new.employer_social_contribution_minor > 0
        and (
            not exists (
                select 1 from journal_line
                where posting_id = new.origin_posting_id
                    and account_code = new.employer_social_expense_account_code
                    and entry_side = 'DEBIT'
                    and currency_code = new.currency_code
                    and amount_minor = new.employer_social_contribution_minor
            )
            or not exists (
                select 1 from journal_line
                where posting_id = new.origin_posting_id
                    and account_code = new.employer_social_payable_account_code
                    and entry_side = 'CREDIT'
                    and currency_code = new.currency_code
                    and amount_minor = new.employer_social_contribution_minor
            )
        )
    )
    or (
        new.personal_income_tax_minor > 0
        and not exists (
            select 1 from journal_line
            where posting_id = new.origin_posting_id
                and account_code = new.personal_income_tax_payable_account_code
                and entry_side = 'CREDIT'
                and currency_code = new.currency_code
                and amount_minor = new.personal_income_tax_minor
        )
    );
end;

create trigger if not exists latvian_payroll_run_reversal_validate_on_insert
before insert on latvian_payroll_run_reversal
begin
    select raise(fail, 'latvian_payroll_run reversal must negate its originating payroll posting.')
    where not exists (
        select 1
        from latvian_payroll_run
        inner join posting_fact on posting_fact.posting_id = new.reversal_posting_id
        where latvian_payroll_run.payroll_run_id = new.payroll_run_id
            and posting_fact.posting_origin_kind = 'REVERSAL'
            and posting_fact.prior_posting_id = latvian_payroll_run.origin_posting_id
    );
    select raise(fail, 'latvian_payroll_run reversal requires every active payroll settlement to be reversed first.')
    where exists (
        select 1
        from latvian_payroll_settlement
        where latvian_payroll_settlement.payroll_run_id = new.payroll_run_id
            and not exists (
                select 1
                from latvian_payroll_settlement_reversal
                where latvian_payroll_settlement_reversal.origin_posting_id = latvian_payroll_settlement.origin_posting_id
            )
    );
end;

create trigger if not exists latvian_payroll_run_reject_update
before update on latvian_payroll_run
begin
    select raise(fail, 'latvian_payroll_run rows are append-only.');
end;

create trigger if not exists latvian_payroll_run_reject_delete
before delete on latvian_payroll_run
begin
    select raise(fail, 'latvian_payroll_run rows are append-only.');
end;

create trigger if not exists latvian_payroll_run_reversal_reject_update
before update on latvian_payroll_run_reversal
begin
    select raise(fail, 'latvian_payroll_run_reversal rows are append-only.');
end;

create trigger if not exists latvian_payroll_run_reversal_reject_delete
before delete on latvian_payroll_run_reversal
begin
    select raise(fail, 'latvian_payroll_run_reversal rows are append-only.');
end;

create table if not exists latvian_payroll_settlement (
    origin_posting_id text primary key references posting_fact (posting_id),
    payroll_run_id text not null references latvian_payroll_run (payroll_run_id),
    settlement_kind text not null check (settlement_kind in ('NET_WAGES', 'STATE_REMITTANCE')),
    effective_date text not null,
    cash_account_code text not null references account (account_code)
) strict;

create table if not exists latvian_payroll_settlement_reversal (
    reversal_posting_id text primary key references posting_fact (posting_id),
    origin_posting_id text not null unique references latvian_payroll_settlement (origin_posting_id)
) strict;

create trigger if not exists latvian_payroll_settlement_validate_on_insert
before insert on latvian_payroll_settlement
begin
    select raise(fail, 'latvian_payroll_settlement requires one active payroll run.')
    where not exists (
        select 1
        from latvian_payroll_run
        where payroll_run_id = new.payroll_run_id
            and not exists (
                select 1
                from latvian_payroll_run_reversal
                where latvian_payroll_run_reversal.payroll_run_id = latvian_payroll_run.payroll_run_id
            )
    );
    select raise(fail, 'latvian_payroll_settlement effective date cannot precede its payroll run.')
    where exists (
        select 1
        from latvian_payroll_run
        where payroll_run_id = new.payroll_run_id
            and new.effective_date < effective_date
    );
    select raise(fail, 'latvian_payroll_settlement origin must be the matching typed payroll-settlement posting.')
    where not exists (
        select 1
        from posting_fact
        where posting_id = new.origin_posting_id
            and effective_date = new.effective_date
            and (
                (new.settlement_kind = 'NET_WAGES' and posting_origin_kind = 'LATVIAN_PAYROLL_NET_WAGE_SETTLEMENT')
                or (new.settlement_kind = 'STATE_REMITTANCE' and posting_origin_kind = 'LATVIAN_PAYROLL_STATE_REMITTANCE')
            )
    );
    select raise(fail, 'latvian_payroll_settlement requires a cash-and-cash-equivalent asset account.')
    where not exists (
        select 1
        from account
        where account_code = new.cash_account_code
            and account_type = 'ASSET'
            and cash_flow_asset_classification = 'CASH_AND_CASH_EQUIVALENT'
    );
    select raise(fail, 'latvian_payroll_settlement may have only one active settlement per payroll obligation.')
    where exists (
        select 1
        from latvian_payroll_settlement existing_settlement
        where existing_settlement.payroll_run_id = new.payroll_run_id
            and existing_settlement.settlement_kind = new.settlement_kind
            and not exists (
                select 1
                from latvian_payroll_settlement_reversal existing_reversal
                where existing_reversal.origin_posting_id = existing_settlement.origin_posting_id
            )
    );
    select raise(fail, 'latvian_payroll_settlement journal must exactly match its immutable payroll obligation.')
    where exists (
        select 1
        from journal_line
        inner join latvian_payroll_run on latvian_payroll_run.payroll_run_id = new.payroll_run_id
        where posting_id = new.origin_posting_id
            and not (
                (new.settlement_kind = 'NET_WAGES'
                    and account_code = latvian_payroll_run.net_wages_payable_account_code
                    and entry_side = 'DEBIT'
                    and journal_line.currency_code = latvian_payroll_run.currency_code
                    and amount_minor = latvian_payroll_run.net_wages_minor)
                or (new.settlement_kind = 'NET_WAGES'
                    and account_code = new.cash_account_code
                    and entry_side = 'CREDIT'
                    and journal_line.currency_code = latvian_payroll_run.currency_code
                    and amount_minor = latvian_payroll_run.net_wages_minor)
                or (new.settlement_kind = 'STATE_REMITTANCE'
                    and latvian_payroll_run.employee_social_contribution_minor > 0
                    and account_code = latvian_payroll_run.employee_social_payable_account_code
                    and entry_side = 'DEBIT'
                    and journal_line.currency_code = latvian_payroll_run.currency_code
                    and amount_minor = latvian_payroll_run.employee_social_contribution_minor)
                or (new.settlement_kind = 'STATE_REMITTANCE'
                    and latvian_payroll_run.employer_social_contribution_minor > 0
                    and account_code = latvian_payroll_run.employer_social_payable_account_code
                    and entry_side = 'DEBIT'
                    and journal_line.currency_code = latvian_payroll_run.currency_code
                    and amount_minor = latvian_payroll_run.employer_social_contribution_minor)
                or (new.settlement_kind = 'STATE_REMITTANCE'
                    and latvian_payroll_run.personal_income_tax_minor > 0
                    and account_code = latvian_payroll_run.personal_income_tax_payable_account_code
                    and entry_side = 'DEBIT'
                    and journal_line.currency_code = latvian_payroll_run.currency_code
                    and amount_minor = latvian_payroll_run.personal_income_tax_minor)
                or (new.settlement_kind = 'STATE_REMITTANCE'
                    and account_code = new.cash_account_code
                    and entry_side = 'CREDIT'
                    and journal_line.currency_code = latvian_payroll_run.currency_code
                    and amount_minor = latvian_payroll_run.employee_social_contribution_minor + latvian_payroll_run.employer_social_contribution_minor + latvian_payroll_run.personal_income_tax_minor)
            )
    )
    or not exists (
        select 1
        from journal_line
        inner join latvian_payroll_run on latvian_payroll_run.payroll_run_id = new.payroll_run_id
        where posting_id = new.origin_posting_id
            and (
                (new.settlement_kind = 'NET_WAGES'
                    and account_code = latvian_payroll_run.net_wages_payable_account_code
                    and entry_side = 'DEBIT'
                    and journal_line.currency_code = latvian_payroll_run.currency_code
                    and amount_minor = latvian_payroll_run.net_wages_minor)
                or (new.settlement_kind = 'STATE_REMITTANCE'
                    and account_code = new.cash_account_code
                    and entry_side = 'CREDIT'
                    and journal_line.currency_code = latvian_payroll_run.currency_code
                    and amount_minor = latvian_payroll_run.employee_social_contribution_minor + latvian_payroll_run.employer_social_contribution_minor + latvian_payroll_run.personal_income_tax_minor)
            )
    )
    or (
        new.settlement_kind = 'NET_WAGES'
        and not exists (
            select 1
            from journal_line
            inner join latvian_payroll_run on latvian_payroll_run.payroll_run_id = new.payroll_run_id
            where posting_id = new.origin_posting_id
                and account_code = new.cash_account_code
                and entry_side = 'CREDIT'
                and journal_line.currency_code = latvian_payroll_run.currency_code
                and amount_minor = latvian_payroll_run.net_wages_minor
        )
    )
    or (
        new.settlement_kind = 'STATE_REMITTANCE'
        and exists (
            select 1
            from latvian_payroll_run
            where payroll_run_id = new.payroll_run_id
                and employee_social_contribution_minor > 0
        )
        and not exists (
            select 1
            from journal_line
            inner join latvian_payroll_run on latvian_payroll_run.payroll_run_id = new.payroll_run_id
            where posting_id = new.origin_posting_id
                and account_code = latvian_payroll_run.employee_social_payable_account_code
                and entry_side = 'DEBIT'
                and journal_line.currency_code = latvian_payroll_run.currency_code
                and amount_minor = latvian_payroll_run.employee_social_contribution_minor
        )
    )
    or (
        new.settlement_kind = 'STATE_REMITTANCE'
        and exists (
            select 1
            from latvian_payroll_run
            where payroll_run_id = new.payroll_run_id
                and employer_social_contribution_minor > 0
        )
        and not exists (
            select 1
            from journal_line
            inner join latvian_payroll_run on latvian_payroll_run.payroll_run_id = new.payroll_run_id
            where posting_id = new.origin_posting_id
                and account_code = latvian_payroll_run.employer_social_payable_account_code
                and entry_side = 'DEBIT'
                and journal_line.currency_code = latvian_payroll_run.currency_code
                and amount_minor = latvian_payroll_run.employer_social_contribution_minor
        )
    )
    or (
        new.settlement_kind = 'STATE_REMITTANCE'
        and exists (
            select 1
            from latvian_payroll_run
            where payroll_run_id = new.payroll_run_id
                and personal_income_tax_minor > 0
        )
        and not exists (
            select 1
            from journal_line
            inner join latvian_payroll_run on latvian_payroll_run.payroll_run_id = new.payroll_run_id
            where posting_id = new.origin_posting_id
                and account_code = latvian_payroll_run.personal_income_tax_payable_account_code
                and entry_side = 'DEBIT'
                and journal_line.currency_code = latvian_payroll_run.currency_code
                and amount_minor = latvian_payroll_run.personal_income_tax_minor
        )
    );
end;

create trigger if not exists latvian_payroll_settlement_reversal_validate_on_insert
before insert on latvian_payroll_settlement_reversal
begin
    select raise(fail, 'latvian_payroll_settlement reversal must negate its originating payroll settlement posting.')
    where not exists (
        select 1
        from latvian_payroll_settlement
        inner join posting_fact on posting_fact.posting_id = new.reversal_posting_id
        where latvian_payroll_settlement.origin_posting_id = new.origin_posting_id
            and posting_fact.posting_origin_kind = 'REVERSAL'
            and posting_fact.prior_posting_id = latvian_payroll_settlement.origin_posting_id
    );
end;

create trigger if not exists latvian_payroll_settlement_reject_update
before update on latvian_payroll_settlement
begin
    select raise(fail, 'latvian_payroll_settlement rows are append-only.');
end;

create trigger if not exists latvian_payroll_settlement_reject_delete
before delete on latvian_payroll_settlement
begin
    select raise(fail, 'latvian_payroll_settlement rows are append-only.');
end;

create trigger if not exists latvian_payroll_settlement_reversal_reject_update
before update on latvian_payroll_settlement_reversal
begin
    select raise(fail, 'latvian_payroll_settlement_reversal rows are append-only.');
end;

create trigger if not exists latvian_payroll_settlement_reversal_reject_delete
before delete on latvian_payroll_settlement_reversal
begin
    select raise(fail, 'latvian_payroll_settlement_reversal rows are append-only.');
end;

create index if not exists latvian_payroll_run_by_employee_month
on latvian_payroll_run (employee_reference, payroll_month, payroll_run_id);

create index if not exists latvian_payroll_settlement_by_run_kind
on latvian_payroll_settlement (payroll_run_id, settlement_kind, effective_date, origin_posting_id);

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

create index if not exists inventory_movement_by_account_replay
on inventory_movement (inventory_account, effective_date, account_sequence);

create index if not exists inventory_movement_by_posting_id
on inventory_movement (posting_id, inventory_account, account_sequence);

create index if not exists accrual_cutoff_application_by_cutoff_horizon
on accrual_cutoff_application (accrual_cutoff_id, effective_date, application_posting_id);

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

create trigger if not exists inventory_movement_reject_update
before update on inventory_movement
begin
    select raise(fail, 'inventory_movement rows are append-only.');
end;

create trigger if not exists inventory_movement_reject_delete
before delete on inventory_movement
begin
    select raise(fail, 'inventory_movement rows are append-only.');
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
