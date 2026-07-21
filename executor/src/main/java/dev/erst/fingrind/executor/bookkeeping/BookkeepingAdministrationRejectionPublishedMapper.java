package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection;
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
    return publishFiscalWindowOrAccountRegistryRejection(rejection);
  }

  private static BookAdministrationRejection publishFiscalWindowOrAccountRegistryRejection(
      BookkeepingAdministrationRejection rejection) {
    if (rejection instanceof CloseTargetAccountCandidateMissing
        || rejection instanceof CloseTargetAccountCandidateAmbiguous) {
      return CloseTargetRejectionPublishedMapper.toPublished(rejection);
    }
    return switch (rejection) {
      case BookkeepingAdministrationRejection.InterimResultSweepMustStartAt conflict ->
          new BookAdministrationRejection.InterimResultSweepMustStartAt(
              conflict.requiredEffectiveDateFrom());
      case BookkeepingAdministrationRejection.InterimResultSweepFutureDate conflict ->
          new BookAdministrationRejection.InterimResultSweepFutureDate(
              conflict.attemptedEffectiveDateTo());
      case BookkeepingAdministrationRejection.InterimResultSweepCrossesFiscalYearBoundary
              conflict ->
          new BookAdministrationRejection.InterimResultSweepCrossesFiscalYearBoundary(
              conflict.attemptedEffectiveDateFrom(),
              conflict.attemptedEffectiveDateTo(),
              conflict.fiscalYearStart());
      case BookkeepingAdministrationRejection.FiscalYearCloseMustStartAt conflict ->
          new BookAdministrationRejection.FiscalYearCloseMustStartAt(
              conflict.requiredEffectiveDateFrom());
      case BookkeepingAdministrationRejection.FiscalYearCloseMustEndAt conflict ->
          new BookAdministrationRejection.FiscalYearCloseMustEndAt(
              conflict.requiredEffectiveDateTo());
      case BookkeepingAdministrationRejection.FiscalYearClosePrecedesTransferredThroughHorizon
              conflict ->
          new BookAdministrationRejection.FiscalYearClosePrecedesTransferredThroughHorizon(
              conflict.attemptedEffectiveDateTo(), conflict.transferredThroughEffectiveDate());
      case BookkeepingAdministrationRejection.FiscalYearCloseFutureDate conflict ->
          new BookAdministrationRejection.FiscalYearCloseFutureDate(
              conflict.attemptedEffectiveDateTo());
      case FiscalYearCloseRequiresGeneratedPostings _ ->
          new dev.erst.fingrind.contract.bookkeeping.FiscalYearCloseRequiresGeneratedPostings();
      default -> AccountRegistryRejectionPublishedMapper.toPublished(rejection);
    };
  }
}
