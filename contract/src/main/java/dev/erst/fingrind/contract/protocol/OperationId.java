package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.core.WireValue;
import java.util.List;

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
  /** Closes one contiguous reporting period into the policy-selected closing equity account. */
  CLOSE_PERIOD,
  /** Inspects one book for lifecycle and compatibility state. */
  INSPECT_BOOK,
  /** Lists the declared account registry. */
  LIST_ACCOUNTS,
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
  /** Computes one bounded income statement. */
  INCOME_STATEMENT,
  /** Computes one bounded statement of changes in equity. */
  CHANGES_IN_EQUITY,
  /** Executes one ordered AI-agent ledger plan transaction. */
  EXECUTE_PLAN,
  /** Validates one posting request without committing it. */
  PREFLIGHT_ENTRY,
  /** Commits one posting request. */
  POST_ENTRY;

  /** Returns the stable CLI and wire identifier for this operation. */
  public String wireName() {
    return OperationIdContract.current().wireName(name());
  }

  /** Returns the stable public wire value for this operation. */
  @Override
  public String wireValue() {
    return wireName();
  }

  /** Returns every stable public operation identifier in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(OperationId.class);
  }

  /** Parses one stable public operation identifier. */
  public static OperationId fromWireValue(String wireValue) {
    return WireValue.fromWireValue(OperationId.class, wireValue, "Unsupported operation id");
  }

  @Override
  public String toString() {
    return wireValue();
  }
}
