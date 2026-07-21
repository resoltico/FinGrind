package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.core.EconomicEventClass;
import dev.erst.fingrind.core.WireValue;
import java.util.Map;
import java.util.Objects;

/** Canonical FinGrind operation identifiers exposed on the public machine contract. */
public enum OperationId implements WireValue {
  /** Prints command usage, examples, and workflow guidance. */
  HELP,
  /** Prints application identity and version information. */
  VERSION,
  /** Prints the machine-readable contract catalog. */
  CAPABILITIES,
  /** Prints live runtime, distribution, and SQLite provenance facts. */
  ENVIRONMENT,
  /** Prints the canonical minimal posting-request scaffold JSON document. */
  PRINT_REQUEST_TEMPLATE,
  /** Prints the canonical minimal AI-agent ledger-plan scaffold JSON document. */
  PRINT_PLAN_TEMPLATE,
  /** Creates a generated owner-only book key file. */
  GENERATE_BOOK_KEY_FILE,
  /** Initializes one protected book. */
  OPEN_BOOK,
  /** Rotates the passphrase protecting one book. */
  REKEY_BOOK,
  /** Exports one closed encrypted-book backup pair. */
  BACKUP_BOOK,
  /** Restores one encrypted-book backup pair onto one selected book path. */
  RESTORE_BOOK,
  /** Declares or reactivates one account. */
  DECLARE_ACCOUNT,
  /** Replaces the definition of a never-posted, unreferenced account. */
  AMEND_ACCOUNT,
  /** Retires a zero-balance account from ordinary authored posting use. */
  RETIRE_ACCOUNT,
  /** Declares or updates one owned tax registration. */
  DECLARE_TAX_REGISTRATION,
  /** Transfers one contiguous reporting period into the policy-selected result-holding account. */
  INTERIM_RESULT_SWEEP,
  /** Closes one fiscal year into capital and retained accumulated targets. */
  FISCAL_YEAR_CLOSE,
  /** Inspects one book for lifecycle and compatibility state. */
  INSPECT_BOOK,
  /** Verifies the complete immutable attestation chain from genesis. */
  VERIFY_BOOK,
  /** Reports non-persisted compromise-review findings for one verified book. */
  ATTESTATION_REVIEW,
  /** Exports one no-clobber quorum-signed receipt without mutating the book. */
  EXPORT_ATTESTATION_RECEIPT,
  /** Verifies one receipt against the immutable evidence from one selected book. */
  VERIFY_RECEIPT,
  /** Lists the declared account registry. */
  LIST_ACCOUNTS,
  /** Lists the declared tax-registration registry. */
  LIST_TAX_REGISTRATIONS,
  /** Computes one tax-obligation report for one declared tax registration. */
  TAX_OBLIGATION,
  /** Returns one committed posting. */
  GET_POSTING,
  /** Lists committed postings. */
  LIST_POSTINGS,
  /** Computes balances for one account. */
  ACCOUNT_BALANCE,
  /** Computes the trial balance for one book. */
  TRIAL_BALANCE,
  /** Computes the running ledger for one account. */
  ACCOUNT_LEDGER,
  /** Computes the bounded period summary for one book. */
  PERIOD_SUMMARY,
  /** Computes one statement of financial position. */
  FINANCIAL_POSITION,
  /** Computes exact per-account inventory carrying values from the inventory movement ledger. */
  INVENTORY_VALUATION,
  /** Computes the durable schedule of prepayments, deferred revenue, and accrued expenses. */
  ACCRUAL_CUTOFF_SCHEDULE,
  /** Computes the durable register of fixed-asset lifecycle facts. */
  FIXED_ASSET_REGISTER,
  /** Computes the durable register of financing principal and interest lifecycle facts. */
  FINANCING_REGISTER,
  /**
   * Computes the durable register of foreign-currency receivable and settlement lifecycle facts.
   */
  REALIZED_FOREIGN_EXCHANGE_REGISTER,
  /** Computes the durable register of Latvian payroll calculations and settlement lineage. */
  LATVIAN_PAYROLL_REGISTER,
  /** Computes one bounded income statement. */
  INCOME_STATEMENT,
  /** Computes one bounded cash-flow statement. */
  CASH_FLOW_STATEMENT,
  /** Computes one bounded statement of changes in equity. */
  CHANGES_IN_EQUITY,
  /** Executes one ordered AI-agent ledger plan transaction. */
  EXECUTE_PLAN,
  /** Validates one posting request without committing it. */
  PREFLIGHT_ENTRY,
  /** Commits one settled sale entry using the sale-first request language. */
  RECORD_SALE_SETTLED,
  /** Commits one sale-on-credit entry using the sale-first request language. */
  RECORD_SALE_ON_CREDIT,
  /** Commits one settled purchase entry using the purchase-first request language. */
  RECORD_PURCHASE_SETTLED,
  /** Commits one purchase-on-credit entry using the purchase-first request language. */
  RECORD_PURCHASE_ON_CREDIT,
  /** Commits one settled inventory-capitalization entry. */
  RECORD_INVENTORY_CAPITALIZATION_SETTLED,
  /** Commits one inventory-capitalization-on-credit entry. */
  RECORD_INVENTORY_CAPITALIZATION_ON_CREDIT,
  /** Commits one inventory write-down entry. */
  RECORD_INVENTORY_WRITE_DOWN,
  /** Commits one inventory shrinkage entry. */
  RECORD_INVENTORY_SHRINKAGE,
  /** Commits one inventory count-increase entry. */
  RECORD_INVENTORY_COUNT_INCREASE,
  /** Commits one cash-funded prepayment and its recognition schedule. */
  RECORD_PREPAYMENT,
  /** Commits one cash-funded deferred-revenue liability and its recognition schedule. */
  RECORD_DEFERRED_REVENUE,
  /** Commits one unpaid accrued expense. */
  RECORD_ACCRUED_EXPENSE,
  /** Recognizes one scheduled prepayment or deferred-revenue amount. */
  RECORD_ACCRUAL_CUTOFF_RECOGNITION,
  /** Settles one accrued-expense liability. */
  RECORD_ACCRUED_EXPENSE_SETTLEMENT,
  /** Commits one executor-resolved Latvian monthly payroll accrual. */
  RECORD_LATVIAN_MONTHLY_PAYROLL,
  /** Settles the exact net-wage obligation of the retained Latvian payroll run. */
  RECORD_LATVIAN_PAYROLL_NET_WAGE_SETTLEMENT,
  /** Remits the exact state obligation of the retained Latvian payroll run. */
  RECORD_LATVIAN_PAYROLL_STATE_REMITTANCE,
  /** Capitalizes one fixed asset with its owned useful-life facts. */
  RECORD_FIXED_ASSET_CAPITALIZATION,
  /** Records one periodic depreciation amount against a fixed asset. */
  RECORD_FIXED_ASSET_DEPRECIATION,
  /** Disposes one fixed asset with its retained lifecycle lineage. */
  RECORD_FIXED_ASSET_DISPOSAL,
  /** Records one borrowing into a retained financing arrangement. */
  RECORD_FINANCING_BORROWING,
  /** Records one principal repayment against a retained financing arrangement. */
  RECORD_FINANCING_PRINCIPAL_REPAYMENT,
  /** Records one interest accrual against a retained financing arrangement. */
  RECORD_FINANCING_INTEREST_ACCRUAL,
  /** Records one interest payment against a retained financing arrangement. */
  RECORD_FINANCING_INTEREST_PAYMENT,
  /** Records one foreign-currency receivable with its functional carrying amount. */
  RECORD_FOREIGN_CURRENCY_OBLIGATION,
  /** Settles one retained foreign-currency obligation and derives realized gain or loss. */
  RECORD_REALIZED_FOREIGN_EXCHANGE_SETTLEMENT,
  /** Commits one settled expense entry using the expense-first request language. */
  RECORD_EXPENSE_SETTLED,
  /** Commits one expense-on-credit entry using the expense-first request language. */
  RECORD_EXPENSE_ON_CREDIT,
  /** Commits one trade-receivable settlement entry. */
  RECORD_RECEIPT,
  /** Commits one trade-payable settlement entry. */
  RECORD_PAYMENT,
  /** Commits one owner-contribution entry using the contribution-first request language. */
  RECORD_OWNER_CONTRIBUTION,
  /** Commits one owner-withdrawal entry using the withdrawal-first request language. */
  RECORD_OWNER_WITHDRAWAL,
  /** Commits one opening-position entry using the opening-position-first request language. */
  RECORD_OPENING_POSITION,
  /** Commits one reversal entry using the reversal-first request language. */
  RECORD_REVERSAL,
  /** Commits one posting request. */
  POST_ENTRY;

  private static final Map<EconomicEventClass, OperationId> ECONOMIC_EVENT_CLASS_OPERATIONS =
      Map.ofEntries(
          Map.entry(EconomicEventClass.SETTLED_SALE, RECORD_SALE_SETTLED),
          Map.entry(EconomicEventClass.CREDIT_SALE, RECORD_SALE_ON_CREDIT),
          Map.entry(EconomicEventClass.SETTLED_PURCHASE, RECORD_PURCHASE_SETTLED),
          Map.entry(EconomicEventClass.CREDIT_PURCHASE, RECORD_PURCHASE_ON_CREDIT),
          Map.entry(
              EconomicEventClass.INVENTORY_CAPITALIZATION, RECORD_INVENTORY_CAPITALIZATION_SETTLED),
          Map.entry(EconomicEventClass.INVENTORY_WRITE_DOWN, RECORD_INVENTORY_WRITE_DOWN),
          Map.entry(EconomicEventClass.INVENTORY_SHRINKAGE, RECORD_INVENTORY_SHRINKAGE),
          Map.entry(EconomicEventClass.INVENTORY_COUNT_INCREASE, RECORD_INVENTORY_COUNT_INCREASE),
          Map.entry(EconomicEventClass.PREPAYMENT, RECORD_PREPAYMENT),
          Map.entry(EconomicEventClass.DEFERRED_REVENUE, RECORD_DEFERRED_REVENUE),
          Map.entry(EconomicEventClass.ACCRUED_EXPENSE, RECORD_ACCRUED_EXPENSE),
          Map.entry(
              EconomicEventClass.ACCRUAL_CUTOFF_RECOGNITION, RECORD_ACCRUAL_CUTOFF_RECOGNITION),
          Map.entry(
              EconomicEventClass.ACCRUED_EXPENSE_SETTLEMENT, RECORD_ACCRUED_EXPENSE_SETTLEMENT),
          Map.entry(EconomicEventClass.LATVIAN_MONTHLY_PAYROLL, RECORD_LATVIAN_MONTHLY_PAYROLL),
          Map.entry(
              EconomicEventClass.LATVIAN_PAYROLL_NET_WAGE_SETTLEMENT,
              RECORD_LATVIAN_PAYROLL_NET_WAGE_SETTLEMENT),
          Map.entry(
              EconomicEventClass.LATVIAN_PAYROLL_STATE_REMITTANCE,
              RECORD_LATVIAN_PAYROLL_STATE_REMITTANCE),
          Map.entry(
              EconomicEventClass.FIXED_ASSET_CAPITALIZATION, RECORD_FIXED_ASSET_CAPITALIZATION),
          Map.entry(EconomicEventClass.FIXED_ASSET_DEPRECIATION, RECORD_FIXED_ASSET_DEPRECIATION),
          Map.entry(EconomicEventClass.FIXED_ASSET_DISPOSAL, RECORD_FIXED_ASSET_DISPOSAL),
          Map.entry(EconomicEventClass.FINANCING_BORROWING, RECORD_FINANCING_BORROWING),
          Map.entry(
              EconomicEventClass.FINANCING_PRINCIPAL_REPAYMENT,
              RECORD_FINANCING_PRINCIPAL_REPAYMENT),
          Map.entry(
              EconomicEventClass.FINANCING_INTEREST_ACCRUAL, RECORD_FINANCING_INTEREST_ACCRUAL),
          Map.entry(
              EconomicEventClass.FINANCING_INTEREST_PAYMENT, RECORD_FINANCING_INTEREST_PAYMENT),
          Map.entry(
              EconomicEventClass.FOREIGN_CURRENCY_OBLIGATION, RECORD_FOREIGN_CURRENCY_OBLIGATION),
          Map.entry(
              EconomicEventClass.REALIZED_FOREIGN_EXCHANGE_SETTLEMENT,
              RECORD_REALIZED_FOREIGN_EXCHANGE_SETTLEMENT),
          Map.entry(EconomicEventClass.SETTLED_EXPENSE, RECORD_EXPENSE_SETTLED),
          Map.entry(EconomicEventClass.CREDIT_EXPENSE, RECORD_EXPENSE_ON_CREDIT),
          Map.entry(EconomicEventClass.AR_SETTLEMENT, RECORD_RECEIPT),
          Map.entry(EconomicEventClass.AP_SETTLEMENT, RECORD_PAYMENT),
          Map.entry(EconomicEventClass.OWNER_CONTRIBUTION, RECORD_OWNER_CONTRIBUTION),
          Map.entry(EconomicEventClass.OWNER_WITHDRAWAL, RECORD_OWNER_WITHDRAWAL),
          Map.entry(EconomicEventClass.OPENING, RECORD_OPENING_POSITION),
          Map.entry(EconomicEventClass.REVERSAL, RECORD_REVERSAL));

  /** Returns the stable CLI and wire identifier for this operation. */
  public String wireName() {
    return OperationIdContract.current().wireName(name());
  }

  /** Returns the canonical typed write operation for one singleton economic event class. */
  public static OperationId forEconomicEventClass(EconomicEventClass eventClass) {
    EconomicEventClass requiredEventClass = Objects.requireNonNull(eventClass, "eventClass");
    OperationId operationId = ECONOMIC_EVENT_CLASS_OPERATIONS.get(requiredEventClass);
    if (operationId != null) {
      return operationId;
    }
    throw new IllegalArgumentException(
        "No typed write operation exists for economicEventClass '%s'."
            .formatted(requiredEventClass.wireValue()));
  }

  /** Returns the stable public wire value for this operation. */
  @Override
  public String wireValue() {
    return wireName();
  }

  @Override
  public String toString() {
    return wireValue();
  }
}
