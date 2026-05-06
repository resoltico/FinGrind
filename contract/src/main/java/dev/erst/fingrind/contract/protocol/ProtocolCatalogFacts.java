package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.core.InteractionLimits;
import java.util.List;

/** Shared immutable facts published through the public protocol catalog. */
final class ProtocolCatalogFacts {
  private static final RuntimeSurfaceContract RUNTIME_SURFACE_CONTRACT =
      RuntimeSurfaceContracts.current();
  private static final BookModelFacts BOOK_MODEL =
      new BookModelFacts(
          "one SQLite file equals one book",
          "one book belongs to one accounting entity",
          ProtocolOptions.BOOK_FILE + " may point anywhere on the OS filesystem",
          "every book-bound command requires exactly one explicit passphrase source via "
              + ProtocolOptions.BOOK_KEY_FILE
              + ", "
              + ProtocolOptions.BOOK_PASSPHRASE_STDIN
              + ", or "
              + ProtocolOptions.BOOK_PASSPHRASE_PROMPT,
          "books must be opened explicitly before any posting or account declaration",
          "every posting line must reference a declared active account",
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
          PlanTransactionMode.ATOMIC,
          PlanFailurePolicy.HALT_ON_FIRST_FAILURE,
          "complete per-step journal with canonical step kind, status, timing, typed facts, grouped observations, and structured failure",
          List.of(
              "executionPolicy is not accepted; the protocol has exactly one execution mode",
              OperationId.OPEN_BOOK.wireName()
                  + " must be the first step when a plan initializes a book",
              "one plan may contain at most "
                  + InteractionLimits.LEDGER_PLAN_STEP_MAX
                  + " steps, which bounds the emitted execution journal",
              "a rejected or assertion-failed step rolls back the entire plan transaction",
              "preflight steps are validation-only steps and do not commit postings"));
  private static final List<StorageEngine> STORAGE_ENGINES =
      List.of(RUNTIME_SURFACE_CONTRACT.storageEngine());
  private static final List<ProtocolSuccessStatus> SUCCESS_STATUSES =
      List.of(ProtocolSuccessStatus.values());
  private static final List<ProtocolRejectionStatus> REJECTION_STATUSES =
      List.of(ProtocolRejectionStatus.values());
  private static final RuntimeEnvironmentContract RUNTIME_ENVIRONMENT_CONTRACT =
      RuntimeEnvironmentContract.current();
  private static final ProtectedBookFormatContract PROTECTED_BOOK_FORMAT_CONTRACT =
      ProtectedBookFormatContracts.current();
  private static final PublicDistributionContract PUBLIC_DISTRIBUTION_CONTRACT =
      PublicDistributionContracts.current();
  private static final ManagedSqliteContract MANAGED_SQLITE_CONTRACT =
      ManagedSqliteContracts.current();
  private static final BundleLayoutContract BUNDLE_LAYOUT_CONTRACT =
      BundleLayoutContracts.current();

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

  static List<StorageEngine> storageEngines() {
    return STORAGE_ENGINES;
  }

  static List<ProtocolSuccessStatus> successStatuses() {
    return SUCCESS_STATUSES;
  }

  static List<ProtocolRejectionStatus> rejectionStatuses() {
    return REJECTION_STATUSES;
  }

  static RuntimeEnvironmentContract runtimeEnvironmentContract() {
    return RUNTIME_ENVIRONMENT_CONTRACT;
  }

  static RuntimeSurfaceContract runtimeSurfaceContract() {
    return RUNTIME_SURFACE_CONTRACT;
  }

  static ProtectedBookFormatContract protectedBookFormatContract() {
    return PROTECTED_BOOK_FORMAT_CONTRACT;
  }

  static PublicDistributionContract publicDistributionContract() {
    return PUBLIC_DISTRIBUTION_CONTRACT;
  }

  static ManagedSqliteContract managedSqliteContract() {
    return MANAGED_SQLITE_CONTRACT;
  }

  static BundleLayoutContract bundleLayoutContract() {
    return BUNDLE_LAYOUT_CONTRACT;
  }
}
