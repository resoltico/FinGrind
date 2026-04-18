package dev.erst.fingrind.contract.protocol;

import java.util.List;

/** Shared immutable facts published through the public protocol catalog. */
final class ProtocolCatalogFacts {
  private static final BookModelFacts BOOK_MODEL =
      new BookModelFacts(
          "one SQLite file equals one book",
          "one book belongs to one entity",
          ProtocolOptions.BOOK_FILE + " may point anywhere on the OS filesystem",
          "every book-bound command requires exactly one explicit passphrase source via "
              + ProtocolOptions.BOOK_KEY_FILE
              + ", "
              + ProtocolOptions.BOOK_PASSPHRASE_STDIN
              + ", or "
              + ProtocolOptions.BOOK_PASSPHRASE_PROMPT,
          "books must be opened explicitly before any posting or account declaration",
          "every posting line must reference a declared active account",
          "sequential in-place migration is the canonical strategy. The current public line starts at book-format version 1, so no historical upgrade steps exist yet, but "
              + OperationId.INSPECT_BOOK.wireName()
              + " exposes detected and supported book-format versions plus compatibility state before mutating commands run",
          "single-currency-per-entry");
  private static final CurrencyFacts CURRENCY =
      new CurrencyFacts(
          BOOK_MODEL.currencyScope(),
          "not-supported",
          "Every journal line inside one entry must share the same currencyCode. Mixed-currency entries are rejected and no multi-currency posting model exists yet.");
  private static final PreflightFacts PREFLIGHT =
      new PreflightFacts(
          "advisory",
          false,
          "Preflight validates the current request against the current book state, but it is not a commit guarantee because durable commit-time checks still run inside the write transaction.");
  private static final PlanExecutionFacts PLAN_EXECUTION =
      new PlanExecutionFacts(
          "atomic",
          "halt-on-first-failure",
          "complete per-step journal with canonical step kind, status, timing, typed facts, grouped observations, and structured failure",
          List.of(
              "executionPolicy is not accepted; the protocol has exactly one execution mode",
              OperationId.OPEN_BOOK.wireName()
                  + " must be the first step when a plan initializes a book",
              "one plan may contain at most "
                  + ProtocolLimits.LEDGER_PLAN_STEP_MAX
                  + " steps, which bounds the emitted execution journal",
              "a rejected or assertion-failed step rolls back the entire plan transaction",
              "preflight steps are validation-only steps and do not commit postings"));
  private static final List<String> STORAGE_ENGINES = List.of("sqlite");
  private static final List<String> SUCCESS_STATUSES =
      List.of(
          ProtocolStatuses.OK,
          ProtocolStatuses.PREFLIGHT_ACCEPTED,
          ProtocolStatuses.COMMITTED,
          ProtocolStatuses.PLAN_COMMITTED);
  private static final List<String> REJECTION_STATUSES =
      List.of(
          ProtocolStatuses.REJECTED,
          ProtocolStatuses.PLAN_REJECTED,
          ProtocolStatuses.PLAN_ASSERTION_FAILED);
  private static final PublicDistributionContract PUBLIC_DISTRIBUTION_CONTRACT =
      PublicDistributionContract.current();

  private ProtocolCatalogFacts() {}

  static BookModelFacts bookModel() {
    return BOOK_MODEL;
  }

  static CurrencyFacts currency() {
    return CURRENCY;
  }

  static PreflightFacts preflight() {
    return PREFLIGHT;
  }

  static PlanExecutionFacts planExecution() {
    return PLAN_EXECUTION;
  }

  static List<String> storageEngines() {
    return STORAGE_ENGINES;
  }

  static List<String> successStatuses() {
    return SUCCESS_STATUSES;
  }

  static List<String> rejectionStatuses() {
    return REJECTION_STATUSES;
  }

  static PublicDistributionContract publicDistributionContract() {
    return PUBLIC_DISTRIBUTION_CONTRACT;
  }
}
