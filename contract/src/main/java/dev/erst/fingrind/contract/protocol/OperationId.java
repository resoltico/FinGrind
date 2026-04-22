package dev.erst.fingrind.contract.protocol;

/** Canonical FinGrind operation identifiers exposed on the public machine contract. */
public enum OperationId {
  /** Prints command usage, examples, and workflow guidance. */
  HELP,
  /** Prints application identity and version information. */
  VERSION,
  /** Prints the machine-readable contract catalog. */
  CAPABILITIES,
  /** Prints a minimal posting-request JSON document. */
  PRINT_REQUEST_TEMPLATE,
  /** Prints a minimal AI-agent ledger-plan JSON document. */
  PRINT_PLAN_TEMPLATE,
  /** Creates a generated owner-only book key file. */
  GENERATE_BOOK_KEY_FILE,
  /** Initializes one protected book. */
  OPEN_BOOK,
  /** Rotates the passphrase protecting one book. */
  REKEY_BOOK,
  /** Declares or reactivates one account. */
  DECLARE_ACCOUNT,
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
}
