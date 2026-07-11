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
  /** Inspects stale sibling rekey rollback artifacts for one selected book path. */
  INSPECT_REKEY_ROLLBACK,
  /** Deletes one selected stale sibling rekey rollback artifact. */
  DELETE_REKEY_ROLLBACK,
  /** Restores one selected stale sibling rekey rollback artifact onto the live book path. */
  RESTORE_REKEY_ROLLBACK,
  /** Declares or reactivates one account. */
  DECLARE_ACCOUNT,
  /** Declares or updates one owned tax registration. */
  DECLARE_TAX_REGISTRATION,
  /** Transfers one contiguous reporting period into the policy-selected result-holding account. */
  INTERIM_RESULT_SWEEP,
  /** Closes one fiscal year into capital and retained accumulated targets. */
  FISCAL_YEAR_CLOSE,
  /** Inspects one book for lifecycle and compatibility state. */
  INSPECT_BOOK,
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
