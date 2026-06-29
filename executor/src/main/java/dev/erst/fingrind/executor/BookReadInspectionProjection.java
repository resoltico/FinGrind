package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection;
import dev.erst.fingrind.contract.bookkeeping.RejectionNarrative;
import dev.erst.fingrind.contract.runtime.BookInspection;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPublishedLanguageTranslator;
import dev.erst.fingrind.executor.bookkeeping.CloseTargetAccountSelector;
import dev.erst.fingrind.executor.bookkeeping.CloseTargetSelection;
import dev.erst.fingrind.executor.bookkeeping.policy.KernelAccountingRulesResolver;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.BookkeepingReadStore;
import java.util.List;
import java.util.Objects;

/** Published inspection projection plus close-readiness enrichment for initialized books. */
final class BookReadInspectionProjection {
  private BookReadInspectionProjection() {}

  static BookInspection project(
      BookkeepingReadStore bookStore, BookLifecycleInspection inspection) {
    Objects.requireNonNull(bookStore, "bookStore");
    Objects.requireNonNull(inspection, "inspection");
    if (inspection instanceof BookLifecycleInspection.Initialized initialized) {
      return new BookInspection.Initialized(
          initialized.applicationId(),
          initialized.detectedBookFormatVersion(),
          initialized.supportedBookFormatVersion(),
          initialized.initializedAt(),
          initialized.bookIdentity(),
          new BookInspection.CloseReadiness(
              closeTargetReadiness(
                  bookStore,
                  KernelAccountingRulesResolver.forBookIdentity(initialized.bookIdentity())
                      .closePostingPolicy()
                      .resultHoldingLineClassification(initialized.bookIdentity())),
              closeTargetReadiness(
                  bookStore, FinancialPositionLineClassification.RETAINED_ACCUMULATED)));
    }
    return BookInspectionPublishedLanguageTranslator.toPublished(inspection);
  }

  private static BookInspection.CloseTargetReadiness closeTargetReadiness(
      BookkeepingReadStore bookStore, FinancialPositionLineClassification requiredClassification) {
    CloseTargetSelection selection =
        CloseTargetAccountSelector.select(requiredClassification, bookStore.allAccounts());
    return switch (selection) {
      case dev.erst.fingrind.executor.bookkeeping.AcceptedCloseTargetSelection accepted ->
          new BookInspection.CloseTargetReadiness(
              true,
              requiredClassification,
              accepted.account().accountCode(),
              null,
              null,
              List.of());
      case dev.erst.fingrind.executor.bookkeeping.RejectedCloseTargetSelection rejected -> {
        BookAdministrationRejection published =
            BookkeepingPublishedLanguageTranslator.toPublished(rejected.rejection());
        yield new BookInspection.CloseTargetReadiness(
            false,
            requiredClassification,
            null,
            BookAdministrationRejection.wireCode(published),
            RejectionNarrative.message(published),
            rejected.candidateAccountCodes());
      }
    };
  }
}
