package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection;
import dev.erst.fingrind.contract.bookkeeping.FiscalYearCloseRequiresGeneratedPostings;
import java.util.Objects;

/** Maps local bookkeeping-administration rejections into the published contract. */
public final class BookkeepingAdministrationRejectionPublishedMapper {
  private BookkeepingAdministrationRejectionPublishedMapper() {}

  /** Translates one local bookkeeping-administration rejection into the published contract. */
  public static BookAdministrationRejection toPublished(
      BookkeepingAdministrationRejection rejection) {
    Objects.requireNonNull(rejection, "rejection");
    if (rejection instanceof BookkeepingAdministrationRejection.BookAlreadyInitialized) {
      return new BookAdministrationRejection.BookAlreadyInitialized();
    }
    if (rejection instanceof BookkeepingAdministrationRejection.BookNotInitialized) {
      return new BookAdministrationRejection.BookNotInitialized();
    }
    if (rejection instanceof BookkeepingAdministrationRejection.BookContainsSchema) {
      return new BookAdministrationRejection.BookContainsSchema();
    }
    if (rejection
        instanceof BookkeepingAdministrationRejection.InterimResultSweepMustStartAt conflict) {
      return new BookAdministrationRejection.InterimResultSweepMustStartAt(
          conflict.requiredEffectiveDateFrom());
    }
    if (rejection
        instanceof BookkeepingAdministrationRejection.InterimResultSweepFutureDate conflict) {
      return new BookAdministrationRejection.InterimResultSweepFutureDate(
          conflict.attemptedEffectiveDateTo());
    }
    if (rejection
        instanceof
        BookkeepingAdministrationRejection.InterimResultSweepCrossesFiscalYearBoundary conflict) {
      return new BookAdministrationRejection.InterimResultSweepCrossesFiscalYearBoundary(
          conflict.attemptedEffectiveDateFrom(),
          conflict.attemptedEffectiveDateTo(),
          conflict.fiscalYearStart());
    }
    if (rejection
        instanceof BookkeepingAdministrationRejection.FiscalYearCloseMustStartAt conflict) {
      return new BookAdministrationRejection.FiscalYearCloseMustStartAt(
          conflict.requiredEffectiveDateFrom());
    }
    if (rejection instanceof BookkeepingAdministrationRejection.FiscalYearCloseMustEndAt conflict) {
      return new BookAdministrationRejection.FiscalYearCloseMustEndAt(
          conflict.requiredEffectiveDateTo());
    }
    if (rejection
        instanceof
        BookkeepingAdministrationRejection.FiscalYearClosePrecedesTransferredThroughHorizon
            conflict) {
      return new BookAdministrationRejection.FiscalYearClosePrecedesTransferredThroughHorizon(
          conflict.attemptedEffectiveDateTo(), conflict.transferredThroughEffectiveDate());
    }
    if (rejection
        instanceof BookkeepingAdministrationRejection.FiscalYearCloseFutureDate conflict) {
      return new BookAdministrationRejection.FiscalYearCloseFutureDate(
          conflict.attemptedEffectiveDateTo());
    }
    if (rejection
        instanceof BookkeepingAdministrationRejection.FiscalYearCloseRequiresGeneratedPostings) {
      return new FiscalYearCloseRequiresGeneratedPostings();
    }
    if (rejection instanceof CloseTargetAccountCandidateMissing
        || rejection instanceof CloseTargetAccountCandidateAmbiguous) {
      return CloseTargetRejectionPublishedMapper.toPublished(rejection);
    }
    return AccountRegistryRejectionPublishedMapper.toPublished(rejection);
  }
}
