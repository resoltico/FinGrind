package dev.erst.fingrind.contract.protocol;

/** Canonical FinGrind operation identifiers exposed on the public machine contract. */
public enum OperationId {
  /** Prints command usage, examples, and workflow guidance. */
  HELP("help"),
  /** Prints application identity and version information. */
  VERSION("version"),
  /** Prints the machine-readable contract catalog. */
  CAPABILITIES("capabilities"),
  /** Prints a minimal posting-request JSON document. */
  PRINT_REQUEST_TEMPLATE("print-request-template"),
  /** Prints a minimal AI-agent ledger-plan JSON document. */
  PRINT_PLAN_TEMPLATE("print-plan-template"),
  /** Creates a generated owner-only book key file. */
  GENERATE_BOOK_KEY_FILE("generate-book-key-file"),
  /** Initializes one protected book. */
  OPEN_BOOK("open-book"),
  /** Rotates the passphrase protecting one book. */
  REKEY_BOOK("rekey-book"),
  /** Declares or reactivates one account. */
  DECLARE_ACCOUNT("declare-account"),
  /** Inspects one book for lifecycle and compatibility state. */
  INSPECT_BOOK("inspect-book"),
  /** Lists the declared account registry. */
  LIST_ACCOUNTS("list-accounts"),
  /** Returns one committed posting. */
  GET_POSTING("get-posting"),
  /** Lists committed postings. */
  LIST_POSTINGS("list-postings"),
  /** Computes balances for one account. */
  ACCOUNT_BALANCE("account-balance"),
  /** Executes one ordered AI-agent ledger plan transaction. */
  EXECUTE_PLAN("execute-plan"),
  /** Validates one posting request without committing it. */
  PREFLIGHT_ENTRY("preflight-entry"),
  /** Commits one posting request. */
  POST_ENTRY("post-entry");

  private final String wireName;

  OperationId(String wireName) {
    this.wireName = wireName;
  }

  /** Returns the stable CLI and wire identifier for this operation. */
  public String wireName() {
    return wireName;
  }
}
