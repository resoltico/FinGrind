package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.runtime.ContractResponse;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Descriptor owner for the closed family of published book-administration rejections. */
final class BookAdministrationRejectionDescriptors {
  private static final Map<Class<? extends BookAdministrationRejection>, Descriptor>
      DESCRIPTORS_BY_REJECTION_TYPE =
          Map.ofEntries(
              Map.entry(
                  BookAdministrationRejection.BookAlreadyInitialized.class,
                  Descriptor.BOOK_ALREADY_INITIALIZED),
              Map.entry(
                  BookAdministrationRejection.BookNotInitialized.class,
                  Descriptor.BOOK_NOT_INITIALIZED),
              Map.entry(
                  BookAdministrationRejection.BookContainsSchema.class,
                  Descriptor.BOOK_CONTAINS_SCHEMA),
              Map.entry(
                  BookAdministrationRejection.AccountTypeConflict.class,
                  Descriptor.ACCOUNT_TYPE_CONFLICT),
              Map.entry(
                  BookAdministrationRejection.AccountTaxonomyConflict.class,
                  Descriptor.ACCOUNT_TAXONOMY_CONFLICT),
              Map.entry(
                  AccountRegistryLifecycleRejection.AccountNotFound.class,
                  Descriptor.ACCOUNT_NOT_FOUND),
              Map.entry(
                  AccountRegistryLifecycleRejection.AccountHasDependents.class,
                  Descriptor.ACCOUNT_HAS_DEPENDENTS),
              Map.entry(
                  AccountRegistryLifecycleRejection.AccountBalanceNotZero.class,
                  Descriptor.ACCOUNT_BALANCE_NOT_ZERO),
              Map.entry(
                  BookAdministrationRejection.ParentAccountMissing.class,
                  Descriptor.PARENT_ACCOUNT_MISSING),
              Map.entry(
                  BookAdministrationRejection.ParentAccountInactive.class,
                  Descriptor.PARENT_ACCOUNT_INACTIVE),
              Map.entry(
                  BookAdministrationRejection.ParentAccountTypeConflict.class,
                  Descriptor.PARENT_ACCOUNT_TYPE_CONFLICT),
              Map.entry(
                  BookAdministrationRejection.ParentAccountNotHeader.class,
                  Descriptor.PARENT_ACCOUNT_NOT_HEADER),
              Map.entry(
                  BookAdministrationRejection.ParentAccountTaxonomyConflict.class,
                  Descriptor.PARENT_ACCOUNT_TAXONOMY_CONFLICT),
              Map.entry(
                  BookAdministrationRejection.AccountHierarchyCycle.class,
                  Descriptor.ACCOUNT_HIERARCHY_CYCLE),
              Map.entry(
                  CloseTargetAccountCandidateMissing.class,
                  Descriptor.CLOSING_EQUITY_ACCOUNT_CANDIDATE_MISSING),
              Map.entry(
                  CloseTargetAccountCandidateAmbiguous.class,
                  Descriptor.CLOSING_EQUITY_ACCOUNT_CANDIDATE_AMBIGUOUS),
              Map.entry(
                  BookAdministrationRejection.InterimResultSweepMustStartAt.class,
                  Descriptor.INTERIM_RESULT_SWEEP_MUST_START_AT),
              Map.entry(
                  BookAdministrationRejection.InterimResultSweepFutureDate.class,
                  Descriptor.INTERIM_RESULT_SWEEP_FUTURE_DATE),
              Map.entry(
                  BookAdministrationRejection.InterimResultSweepCrossesFiscalYearBoundary.class,
                  Descriptor.INTERIM_RESULT_SWEEP_CROSSES_FISCAL_YEAR_BOUNDARY),
              Map.entry(
                  BookAdministrationRejection.FiscalYearCloseMustStartAt.class,
                  Descriptor.FISCAL_YEAR_CLOSE_MUST_START_AT),
              Map.entry(
                  BookAdministrationRejection.FiscalYearCloseMustEndAt.class,
                  Descriptor.FISCAL_YEAR_CLOSE_MUST_END_AT),
              Map.entry(
                  BookAdministrationRejection.FiscalYearClosePrecedesTransferredThroughHorizon
                      .class,
                  Descriptor.FISCAL_YEAR_CLOSE_PRECEDES_TRANSFERRED_THROUGH_HORIZON),
              Map.entry(
                  BookAdministrationRejection.FiscalYearCloseFutureDate.class,
                  Descriptor.FISCAL_YEAR_CLOSE_FUTURE_DATE));

  private BookAdministrationRejectionDescriptors() {}

  static String wireCode(BookAdministrationRejection rejection) {
    return descriptorFor(rejection).code();
  }

  static String bookNotInitializedCode() {
    return Descriptor.BOOK_NOT_INITIALIZED.code();
  }

  static List<ContractResponse.RejectionDescriptor> descriptors() {
    return BookAdministrationRejectionDescriptorCatalog.descriptors();
  }

  private static Descriptor descriptorFor(BookAdministrationRejection rejection) {
    Descriptor descriptor =
        DESCRIPTORS_BY_REJECTION_TYPE.get(
            Objects.requireNonNull(rejection, "rejection").getClass());
    return Objects.requireNonNull(
        descriptor,
        "Unsupported book-administration rejection type: " + rejection.getClass().getName());
  }

  /** Stable descriptor keys for the published book-administration rejection catalog. */
  enum Descriptor {
    /** Descriptor for refusing an already initialized book. */
    BOOK_ALREADY_INITIALIZED,
    /** Descriptor for refusing administration on an uninitialized book. */
    BOOK_NOT_INITIALIZED,
    /** Descriptor for refusing initialization on a file that already contains schema. */
    BOOK_CONTAINS_SCHEMA,
    /** Descriptor for conflicting immutable account type declarations. */
    ACCOUNT_TYPE_CONFLICT,
    /** Descriptor for conflicting immutable account taxonomy declarations. */
    ACCOUNT_TAXONOMY_CONFLICT,
    /** Descriptor for an account lifecycle request naming no declared account. */
    ACCOUNT_NOT_FOUND,
    /** Descriptor for lifecycle changes blocked by durable account relationships. */
    ACCOUNT_HAS_DEPENDENTS,
    /** Descriptor for account retirement blocked by a non-zero balance. */
    ACCOUNT_BALANCE_NOT_ZERO,
    /** Descriptor for missing requested parent accounts. */
    PARENT_ACCOUNT_MISSING,
    /** Descriptor for inactive requested parent accounts. */
    PARENT_ACCOUNT_INACTIVE,
    /** Descriptor for parent-child account type mismatches. */
    PARENT_ACCOUNT_TYPE_CONFLICT,
    /** Descriptor for parent accounts that are not header nodes. */
    PARENT_ACCOUNT_NOT_HEADER,
    /** Descriptor for parent-child taxonomy family mismatches. */
    PARENT_ACCOUNT_TAXONOMY_CONFLICT,
    /** Descriptor for parent-child hierarchy cycles. */
    ACCOUNT_HIERARCHY_CYCLE,
    /** Descriptor for missing close-target account candidates. */
    CLOSING_EQUITY_ACCOUNT_CANDIDATE_MISSING,
    /** Descriptor for ambiguous close-target account candidates. */
    CLOSING_EQUITY_ACCOUNT_CANDIDATE_AMBIGUOUS,
    /** Descriptor for non-contiguous interim-result sweep starts. */
    INTERIM_RESULT_SWEEP_MUST_START_AT,
    /** Descriptor for interim-result sweeps that target a future date. */
    INTERIM_RESULT_SWEEP_FUTURE_DATE,
    /** Descriptor for interim-result sweeps that cross a fiscal-year boundary. */
    INTERIM_RESULT_SWEEP_CROSSES_FISCAL_YEAR_BOUNDARY,
    /** Descriptor for fiscal-year closes that miss the required year start. */
    FISCAL_YEAR_CLOSE_MUST_START_AT,
    /** Descriptor for fiscal-year closes that miss the required year end. */
    FISCAL_YEAR_CLOSE_MUST_END_AT,
    /** Descriptor for fiscal-year closes that precede the live transferred-through horizon. */
    FISCAL_YEAR_CLOSE_PRECEDES_TRANSFERRED_THROUGH_HORIZON,
    /** Descriptor for fiscal-year closes that target a future date. */
    FISCAL_YEAR_CLOSE_FUTURE_DATE;

    String code() {
      return BookAdministrationRejectionDescriptorCatalog.code(this);
    }
  }
}
