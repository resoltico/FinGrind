package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection;

/** Maps close-target selection rejections into the published administration contract. */
final class CloseTargetRejectionPublishedMapper {
  private CloseTargetRejectionPublishedMapper() {}

  static BookAdministrationRejection toPublished(BookkeepingAdministrationRejection rejection) {
    return switch (rejection) {
      case CloseTargetAccountCandidateMissing conflict ->
          new dev.erst.fingrind.contract.bookkeeping.CloseTargetAccountCandidateMissing(
              conflict.requiredFinancialPositionLineClassification(),
              conflict.inactiveCandidateAccountCodes());
      case CloseTargetAccountCandidateAmbiguous conflict ->
          new dev.erst.fingrind.contract.bookkeeping.CloseTargetAccountCandidateAmbiguous(
              conflict.requiredFinancialPositionLineClassification(),
              conflict.candidateAccountCodes());
      default ->
          throw new IllegalArgumentException(
              "Expected a close-target rejection but received "
                  + rejection.getClass().getName()
                  + ".");
    };
  }
}
