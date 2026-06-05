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
                  BookAdministrationRejection.AccountRoleConflict.class,
                  Descriptor.ACCOUNT_ROLE_CONFLICT),
              Map.entry(
                  BookAdministrationRejection.AccountTaxonomyConflict.class,
                  Descriptor.ACCOUNT_TAXONOMY_CONFLICT),
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
                  BookAdministrationRejection.ParentAccountRoleConflict.class,
                  Descriptor.PARENT_ACCOUNT_ROLE_CONFLICT),
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
                  BookAdministrationRejection.ResultHoldingAccountCandidateMissing.class,
                  Descriptor.CLOSING_EQUITY_ACCOUNT_CANDIDATE_MISSING),
              Map.entry(
                  BookAdministrationRejection.ResultHoldingAccountCandidateAmbiguous.class,
                  Descriptor.CLOSING_EQUITY_ACCOUNT_CANDIDATE_AMBIGUOUS),
              Map.entry(
                  BookAdministrationRejection.PeriodResultTransferMustStartAt.class,
                  Descriptor.PERIOD_RESULT_TRANSFER_MUST_START_AT),
              Map.entry(
                  BookAdministrationRejection.PeriodResultTransferFutureDate.class,
                  Descriptor.PERIOD_RESULT_TRANSFER_FUTURE_DATE),
              Map.entry(
                  BookAdministrationRejection.PeriodResultTransferCrossesFiscalYearBoundary.class,
                  Descriptor.PERIOD_RESULT_TRANSFER_CROSSES_FISCAL_YEAR_BOUNDARY));

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
    /** Descriptor for conflicting immutable account role declarations. */
    ACCOUNT_ROLE_CONFLICT,
    /** Descriptor for conflicting immutable account taxonomy declarations. */
    ACCOUNT_TAXONOMY_CONFLICT,
    /** Descriptor for missing requested parent accounts. */
    PARENT_ACCOUNT_MISSING,
    /** Descriptor for inactive requested parent accounts. */
    PARENT_ACCOUNT_INACTIVE,
    /** Descriptor for parent-child account type mismatches. */
    PARENT_ACCOUNT_TYPE_CONFLICT,
    /** Descriptor for parent-child account role mismatches. */
    PARENT_ACCOUNT_ROLE_CONFLICT,
    /** Descriptor for parent accounts that are not header nodes. */
    PARENT_ACCOUNT_NOT_HEADER,
    /** Descriptor for parent-child taxonomy family mismatches. */
    PARENT_ACCOUNT_TAXONOMY_CONFLICT,
    /** Descriptor for parent-child hierarchy cycles. */
    ACCOUNT_HIERARCHY_CYCLE,
    /** Descriptor for missing result-holding account candidates. */
    CLOSING_EQUITY_ACCOUNT_CANDIDATE_MISSING,
    /** Descriptor for ambiguous result-holding account candidates. */
    CLOSING_EQUITY_ACCOUNT_CANDIDATE_AMBIGUOUS,
    /** Descriptor for non-contiguous period result transfer starts. */
    PERIOD_RESULT_TRANSFER_MUST_START_AT,
    /** Descriptor for period result transfers that target a future date. */
    PERIOD_RESULT_TRANSFER_FUTURE_DATE,
    /** Descriptor for period result transfers that cross a fiscal-year boundary. */
    PERIOD_RESULT_TRANSFER_CROSSES_FISCAL_YEAR_BOUNDARY;

    String code() {
      return BookAdministrationRejectionDescriptorCatalog.code(this);
    }
  }
}
