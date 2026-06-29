package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.OpenBookResult;
import dev.erst.fingrind.contract.runtime.BookInspection;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteRoundTripWorkflowInvalidExistingBookCoverageTest {
  @TempDir Path tempDirectory;

  @Test
  void inspection_open_and_commit_guards_cover_accepted_rejected_and_runtime_shapes()
      throws Exception {
    Path bookPath = tempDirectory.resolve("entity.sqlite");

    assertDoesNotThrow(
        () ->
            SqliteRoundTripWorkflowInvalidExistingBookCoverage.assertNonInitializedInspection(
                () -> ContractDecision.accepted(new BookInspection.Missing(1)), bookPath));
    IllegalStateException initializedInspection =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteRoundTripWorkflowInvalidExistingBookCoverage.assertNonInitializedInspection(
                    () ->
                        ContractDecision.accepted(
                            new BookInspection.Initialized(
                                1,
                                1,
                                1,
                                CliFuzzFixtures.fixedClock().instant(),
                                CliFuzzWorkflowFixtures.bookIdentity(),
                                resultTransferReadyInspection())),
                    bookPath));
    SqliteRoundTripWorkflowTestSupport.assertMessageContains(
        initializedInspection, "unexpectedly inspected as initialized");
    assertDoesNotThrow(
        () ->
            SqliteRoundTripWorkflowInvalidExistingBookCoverage.assertNonInitializedInspection(
                () ->
                    ContractDecision.rejected(
                        SqliteRoundTripWorkflowTestSupport.contractFailure("inspection rejected")),
                bookPath));
    assertDoesNotThrow(
        () ->
            SqliteRoundTripWorkflowInvalidExistingBookCoverage.assertNonInitializedInspection(
                () -> {
                  throw new IllegalStateException("inspection runtime");
                },
                bookPath));

    assertDoesNotThrow(
        () ->
            SqliteRoundTripWorkflowInvalidExistingBookCoverage.assertNotOpened(
                () ->
                    ContractDecision.accepted(
                        new OpenBookResult.Rejected(
                            new dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection
                                .BookContainsSchema())),
                bookPath));
    IllegalStateException openedBook =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteRoundTripWorkflowInvalidExistingBookCoverage.assertNotOpened(
                    () ->
                        ContractDecision.accepted(
                            new OpenBookResult.Opened(
                                CliFuzzFixtures.fixedClock().instant(),
                                CliFuzzWorkflowFixtures.bookIdentity())),
                    bookPath));
    SqliteRoundTripWorkflowTestSupport.assertMessageContains(
        openedBook, "unexpectedly opened as a valid book");
    assertDoesNotThrow(
        () ->
            SqliteRoundTripWorkflowInvalidExistingBookCoverage.assertNotOpened(
                () ->
                    ContractDecision.rejected(
                        SqliteRoundTripWorkflowTestSupport.contractFailure("open rejected")),
                bookPath));
    assertDoesNotThrow(
        () ->
            SqliteRoundTripWorkflowInvalidExistingBookCoverage.assertNotOpened(
                () -> {
                  throw new IllegalStateException("open runtime");
                },
                bookPath));

    assertDoesNotThrow(
        () ->
            SqliteRoundTripWorkflowInvalidExistingBookCoverage.assertNotCommitted(
                () ->
                    ContractDecision.accepted(
                        SqliteRoundTripWorkflowTestSupport.commitRejected(
                            new dev.erst.fingrind.contract.bookkeeping.PostingRejection
                                .BookNotInitialized())),
                "book-not-initialized"));
    IllegalStateException committedPosting =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteRoundTripWorkflowInvalidExistingBookCoverage.assertNotCommitted(
                    () ->
                        ContractDecision.accepted(
                            SqliteRoundTripWorkflowTestSupport.committed("posting-1")),
                    null));
    SqliteRoundTripWorkflowTestSupport.assertMessageContains(
        committedPosting, "unexpectedly committed a posting fact");
    assertDoesNotThrow(
        () ->
            SqliteRoundTripWorkflowInvalidExistingBookCoverage.assertNotCommitted(
                () ->
                    ContractDecision.rejected(
                        SqliteRoundTripWorkflowTestSupport.contractFailure("commit rejected")),
                null));
    assertDoesNotThrow(
        () ->
            SqliteRoundTripWorkflowInvalidExistingBookCoverage.assertNotCommitted(
                () -> {
                  throw new IllegalStateException("commit runtime");
                },
                null));
  }

  @Test
  void exerciseInvalidExistingBookCoverage_covers_directory_and_plaintext_existing_book_paths()
      throws Exception {
    assertDoesNotThrow(
        () ->
            SqliteRoundTripWorkflowInvalidExistingBookCoverage.exerciseInvalidExistingBookCoverage(
                SqliteRoundTripWorkflowTestSupport.basicValidCommand(),
                tempDirectory.resolve("invalid-existing-book")));
  }

  private static BookInspection.CloseReadiness resultTransferReadyInspection() {
    return new BookInspection.CloseReadiness(
        readyTarget(FinancialPositionLineClassification.RESULT_HOLDING, new AccountCode("3200")),
        readyTarget(
            FinancialPositionLineClassification.RETAINED_ACCUMULATED, new AccountCode("3300")));
  }

  private static BookInspection.CloseTargetReadiness readyTarget(
      FinancialPositionLineClassification classification, AccountCode accountCode) {
    return new BookInspection.CloseTargetReadiness(
        true, classification, accountCode, null, null, List.of());
  }
}
