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
          "single-functional-currency-per-book");
  private static final CurrencyFacts CURRENCY =
      new CurrencyFacts(
          BOOK_MODEL.currencyScope(),
          "not-supported",
          "Every posting request and every persisted journal line must match the selected book functional currency. Mixed-currency entries are rejected and no foreign-currency translation model exists yet.");
  private static final AccountingBaselineFacts ACCOUNTING_BASELINE =
      new AccountingBaselineFacts(
          "country-agnostic-bookkeeping-kernel",
          List.of(
              "IFRS Conceptual Framework general-purpose financial-reporting concepts",
              "IAS 21 functional-currency anchor concepts"),
          List.of(
              OperationId.FINANCIAL_POSITION.wireName(),
              OperationId.INCOME_STATEMENT.wireName(),
              OperationId.CHANGES_IN_EQUITY.wireName()),
          List.of(
              "statement-of-cash-flows",
              "oci-and-comprehensive-income-presentation",
              "external-note-and-disclosure-packages",
              "rich-statement-presentation-taxonomy-and-current-non-current-classification",
              "hierarchical-chart-of-accounts-and-report-taxonomy",
              "operational-subledgers-for-invoicing-receivables-payables-inventory-or-payroll",
              "jurisdiction-specific-tax-or-filing-rules",
              "group-reporting-and-consolidation",
              "foreign-currency-translation-and-exchange-difference-accounting-inside-one-book"),
          "FinGrind targets one exact-money bookkeeping kernel, not one full IFRS or local-GAAP compliance/reporting package.",
          "Built-in reporting stops at financial position, income statement, and changes in equity. Cash flows, OCI/comprehensive-income reporting, and note/disclosure packages belong to adjacent reporting contexts rather than the current kernel.",
          "The current chart of accounts is flat. No parent-child hierarchy or first-class report-taxonomy structure is built into the kernel account model.",
          "FinGrind does not yet claim IFRS for SMEs parity. The current kernel fits one single-entity, single-functional-currency internal book for sole traders and small organizations, but not one full SME reporting regime.",
          "Operational contexts such as invoicing, receivables, payables, inventory, payroll, and settlement orchestration are not modeled in the current kernel. They belong above the ledger as adjacent bounded contexts that publish postings into the book.",
          "Tax is not a first-class domain in the current kernel. Users may post tax-bearing amounts manually, but tax registrations, tax codes, rate schedules, recoverability, inclusivity, determination rules, and filing obligations are not modeled yet.",
          "FinGrind does not yet claim multi-entity organizational accounting. Group reporting, consolidations, and intercompany elimination belong to a separate future context above the current single-entity book kernel.",
          "ISO 21378 informs audit-data collection, not the substantive bookkeeping doctrine FinGrind uses as its current accounting baseline.");
  private static final ExtensionSurfaceFacts EXTENSION_SURFACE =
      new ExtensionSurfaceFacts(
          "bookkeeping-policy-pack",
          List.of("statement-comparative-policy"),
          List.of(
              "statement-presentation-policy",
              "cash-flow-and-oci-reporting",
              "notes-and-disclosures",
              "fx-accounting-and-translation",
              "tax-determination-and-filing",
              "operational-subledgers-ar-ap-invoicing",
              "group-reporting-and-consolidation"),
          "Future standards-specific or jurisdiction-specific behavior must arrive as explicit adjacent contexts or stabilized policy seams layered on top of the current kernel instead of compatibility shims inside the core model. The only published seam implemented today is the fiscal-year-aware statement-comparative-policy.");
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

  static AccountingBaselineFacts accountingBaseline() {
    return ACCOUNTING_BASELINE;
  }

  static ExtensionSurfaceFacts extensionSurface() {
    return EXTENSION_SURFACE;
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
