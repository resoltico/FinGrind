package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.core.AccountingKernelProfiles;
import java.util.List;

/** Shared immutable facts published through the public protocol catalog. */
final class ProtocolCatalogFacts {
  static final RuntimeSurfaceContract RUNTIME_SURFACE_CONTRACT = RuntimeSurfaceContracts.current();
  static final BookModelFacts BOOK_MODEL =
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
          "single-functional-currency-per-book");
  static final CurrencyFacts CURRENCY =
      new CurrencyFacts(
          BOOK_MODEL.currencyScope(),
          "not-supported",
          "Every posting request and every persisted journal line must match the selected book functional currency. Mixed-currency entries are rejected and no foreign-currency translation model exists yet.");
  static final BookkeepingKernelFacts BOOKKEEPING_KERNEL =
      new BookkeepingKernelFacts(
          AccountingKernelProfiles.INTERNAL_MANAGEMENT_CASH_BOOKKEEPING_KERNEL.value(),
          List.of(
              OperationId.FINANCIAL_POSITION.wireName(),
              OperationId.INCOME_STATEMENT.wireName(),
              OperationId.CHANGES_IN_EQUITY.wireName()),
          List.of(
              new ReportCapabilityFacts(
                  OperationId.FINANCIAL_POSITION.wireName(),
                  true,
                  "Built into the current kernel as one internal management statement."),
              new ReportCapabilityFacts(
                  OperationId.INCOME_STATEMENT.wireName(),
                  true,
                  "Built into the current kernel as one internal management statement."),
              new ReportCapabilityFacts(
                  OperationId.CHANGES_IN_EQUITY.wireName(),
                  true,
                  "Built into the current kernel as one internal management statement.")),
          "Current built-in cash-oriented bookkeeping kernel facts for one single-entity, single-functional-currency internal-management book with three built-in statements.");
  static final PreflightFacts PREFLIGHT =
      new PreflightFacts(
          "advisory",
          false,
          "Preflight validates the current request against the current book state, but it is not a commit guarantee because durable commit-time checks still run inside the write transaction.");
  static final PlanExecutionFacts PLAN_EXECUTION =
      new PlanExecutionFacts(
          PlanTransactionMode.ATOMIC,
          PlanFailurePolicy.HALT_ON_FIRST_FAILURE,
          "complete per-step journal with canonical step kind, status, timing, typed facts, grouped observations, and structured failure",
          List.of(
              "executionPolicy is not accepted; the protocol has exactly one execution mode",
              OperationId.OPEN_BOOK.wireName()
                  + " must be the first step when a plan initializes a book",
              "one plan may contain at most "
                  + ProtocolInteractionLimits.LEDGER_PLAN_STEP_MAX
                  + " steps, which bounds the emitted execution journal",
              "a rejected or assertion-failed step rolls back the entire plan transaction",
              "preflight steps are validation-only steps and do not commit postings"));
  static final RequestSurfaceFacts REQUEST_SURFACE = RequestSurfaceContracts.current();
  static final List<StorageEngine> STORAGE_ENGINES =
      List.of(RUNTIME_SURFACE_CONTRACT.storageEngine());
  static final List<ProtocolEnvelopeStatus> ENVELOPE_STATUSES =
      List.of(ProtocolEnvelopeStatus.values());
  static final ProtocolEnvelopeStatus SUCCESS_STATUS = ProtocolEnvelopeStatus.OK;
  static final ProtocolEnvelopeStatus REJECTION_STATUS = ProtocolEnvelopeStatus.REJECTED;
  static final ProtocolEnvelopeStatus ERROR_STATUS = ProtocolEnvelopeStatus.ERROR;
  static final RuntimeEnvironmentContract RUNTIME_ENVIRONMENT_CONTRACT =
      RuntimeEnvironmentContract.current();
  static final ProtectedBookFormatContract PROTECTED_BOOK_FORMAT_CONTRACT =
      ProtectedBookFormatContracts.current();
  static final ManagedSqliteContract MANAGED_SQLITE_CONTRACT = ManagedSqliteContracts.current();
  static final BundlePublicationContract BUNDLE_PUBLICATION_CONTRACT =
      BundlePublicationContracts.current();
  static final BundleLayoutContract BUNDLE_LAYOUT_CONTRACT = BundleLayoutContracts.current();

  private ProtocolCatalogFacts() {}
}
