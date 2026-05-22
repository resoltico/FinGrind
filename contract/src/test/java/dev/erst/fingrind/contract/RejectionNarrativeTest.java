package dev.erst.fingrind.contract;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceRejection;
import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.bookkeeping.RejectionNarrative;
import dev.erst.fingrind.contract.runtime.PublicPathHint;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.PostingId;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link RejectionNarrative}. */
class RejectionNarrativeTest {
  @Test
  void administrationMessagesCoverEveryRejection() {
    assertTrue(
        RejectionNarrative.message(new BookAdministrationRejection.BookAlreadyInitialized())
            .contains("already initialized"));
    assertTrue(
        RejectionNarrative.message(new BookAdministrationRejection.BookNotInitialized())
            .contains("open-book"));
    assertTrue(
        RejectionNarrative.message(new BookAdministrationRejection.BookContainsSchema())
            .contains("schema objects"));
    assertTrue(
        RejectionNarrative.message(
                new BookAdministrationRejection.AccountTypeConflict(
                    new AccountCode("1000"), AccountType.ASSET, AccountType.LIABILITY))
            .contains("account type"));
    assertTrue(
        RejectionNarrative.message(
                new BookAdministrationRejection.AccountRoleConflict(
                    new AccountCode("1000"), AccountRole.ORDINARY, AccountRole.CONTRA))
            .contains("1000"));
    assertTrue(
        RejectionNarrative.message(
                new BookAdministrationRejection.AccountTaxonomyConflict(
                    new AccountCode("1000"),
                    new AccountTaxonomy(
                        dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
                        Optional.of(new AccountCode("3000")),
                        Optional.of(FinancialPositionLineClassification.OTHER_EQUITY),
                        Optional.empty()),
                    new AccountTaxonomy(
                        dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
                        Optional.of(new AccountCode("3010")),
                        Optional.of(FinancialPositionLineClassification.ACCUMULATED_RESULT),
                        Optional.empty())))
            .contains("immutable hierarchy or statement taxonomy"));
    assertTrue(
        RejectionNarrative.message(
                new BookAdministrationRejection.ParentAccountMissing(
                    new AccountCode("1010"), new AccountCode("1000")))
            .contains("parent account '1000'"));
    assertTrue(
        RejectionNarrative.message(
                new BookAdministrationRejection.ParentAccountInactive(
                    new AccountCode("1010"), new AccountCode("1000")))
            .contains("inactive"));
    assertTrue(
        RejectionNarrative.message(
                new BookAdministrationRejection.ParentAccountTypeConflict(
                    new AccountCode("1010"),
                    AccountType.ASSET,
                    new AccountCode("2000"),
                    AccountType.LIABILITY))
            .contains("Parent and child must share one account type"));
    assertTrue(
        RejectionNarrative.message(
                new BookAdministrationRejection.ParentAccountRoleConflict(
                    new AccountCode("1010"),
                    AccountRole.ORDINARY,
                    new AccountCode("1000"),
                    AccountRole.CONTRA))
            .contains("Parent and child must share one account role"));
    assertTrue(
        RejectionNarrative.message(
                new BookAdministrationRejection.ParentAccountNotHeader(
                    new AccountCode("1010"),
                    new AccountCode("1000"),
                    dev.erst.fingrind.core.AccountNodeKind.POSTABLE))
            .contains("cannot own child accounts"));
    assertTrue(
        RejectionNarrative.message(
                new BookAdministrationRejection.ParentAccountTaxonomyConflict(
                    new AccountCode("1010"),
                    new AccountTaxonomy(
                        dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
                        Optional.of(new AccountCode("1000")),
                        Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                        Optional.empty()),
                    new AccountCode("1000"),
                    new AccountTaxonomy(
                        dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
                        Optional.empty(),
                        Optional.of(FinancialPositionLineClassification.NONCURRENT_ASSET),
                        Optional.empty())))
            .contains("statement-classification family"));
    assertTrue(
        RejectionNarrative.message(
                new BookAdministrationRejection.AccountHierarchyCycle(
                    new AccountCode("1000"), new AccountCode("1010")))
            .contains("chart hierarchy cycle"));
    assertTrue(
        RejectionNarrative.message(
                new BookAdministrationRejection.ClosingEquityAccountCandidateMissing(
                    FinancialPositionLineClassification.ACCUMULATED_RESULT, List.of()))
            .contains("required classification"));
    assertTrue(
        RejectionNarrative.message(
                new BookAdministrationRejection.ClosingEquityAccountCandidateMissing(
                    FinancialPositionLineClassification.ACCUMULATED_RESULT,
                    List.of(new AccountCode("3200"))))
            .contains("inactive candidates: 3200"));
    assertTrue(
        RejectionNarrative.message(
                new BookAdministrationRejection.ClosingEquityAccountCandidateAmbiguous(
                    FinancialPositionLineClassification.OTHER_EQUITY,
                    List.of(new AccountCode("3200"), new AccountCode("3210"))))
            .contains("3200, 3210"));
    assertTrue(
        RejectionNarrative.message(
                new BookAdministrationRejection.PeriodCloseMustStartAt(
                    LocalDate.parse("2026-01-01")))
            .contains("2026-01-01"));
    assertTrue(
        RejectionNarrative.message(
                new BookAdministrationRejection.PeriodCloseFutureDate(
                    LocalDate.parse("2026-12-31")))
            .contains("2026-12-31"));
    assertTrue(
        RejectionNarrative.message(
                new BookAdministrationRejection.PeriodCloseCrossesFiscalYearBoundary(
                    LocalDate.parse("2026-12-15"),
                    LocalDate.parse("2027-01-15"),
                    dev.erst.fingrind.core.FiscalYearStart.parse("01-01")))
            .contains("01-01"));
  }

  @Test
  void queryMessagesCoverEveryRejection() {
    assertTrue(
        RejectionNarrative.message(new BookQueryRejection.BookNotInitialized())
            .contains("open-book"));
    assertTrue(
        RejectionNarrative.message(new BookQueryRejection.UnknownAccount(new AccountCode("9999")))
            .contains("9999"));
    assertTrue(
        RejectionNarrative.message(
                new BookQueryRejection.PostingNotFound(new PostingId("posting-1")))
            .contains("posting-1"));
  }

  @Test
  void maintenanceMessagesCoverEveryRejection() {
    assertTrue(
        RejectionNarrative.message(
                new BookMaintenanceRejection.BookHasBlockingArtifacts(
                    hint(java.nio.file.Path.of("books/acme.sqlite")),
                    List.of(hint(java.nio.file.Path.of("books/acme.sqlite-wal")))))
            .contains("blocking sibling artifacts"));
    assertTrue(
        RejectionNarrative.message(
                new BookMaintenanceRejection.BackupSourceHasBlockingArtifacts(
                    hint(java.nio.file.Path.of("backup/acme.sqlite")),
                    List.of(hint(java.nio.file.Path.of("backup/acme.sqlite-wal")))))
            .contains("safe to restore"));
    assertTrue(
        RejectionNarrative.message(
                new BookMaintenanceRejection.BackupSourceMatchesLiveBook(
                    hint(java.nio.file.Path.of("books/acme.sqlite")),
                    hint(java.nio.file.Path.of("books/acme.sqlite"))))
            .contains("will not restore a book from itself"));
    assertTrue(
        RejectionNarrative.message(
                new BookMaintenanceRejection.ArtifactBusy(
                    dev.erst.fingrind.contract.bookkeeping.BookMaintenanceArtifactRole.LIVE_BOOK,
                    hint(java.nio.file.Path.of("books/acme.sqlite"))))
            .contains("actively in use"));
    assertTrue(
        RejectionNarrative.message(
                new BookMaintenanceRejection.BackupDestinationAlreadyExists(
                    hint(java.nio.file.Path.of("backup/acme.sqlite"))))
            .contains("will not overwrite"));
    assertTrue(
        RejectionNarrative.message(
                new BookMaintenanceRejection.BackupKeyFileAlreadyExists(
                    hint(java.nio.file.Path.of("backup/acme.book-key"))))
            .contains("key file"));
    assertTrue(
        RejectionNarrative.message(
                new BookMaintenanceRejection.ArtifactVerificationFailed(
                    dev.erst.fingrind.contract.bookkeeping.BookMaintenanceArtifactRole
                        .RESTORED_TARGET,
                    hint(java.nio.file.Path.of("books/acme.sqlite")),
                    dev.erst.fingrind.contract.bookkeeping.BookMaintenanceVerificationFailure
                        .PROTECTED_BOOK_VERIFICATION_FAILED))
            .contains("failed verification"));
    assertTrue(
        RejectionNarrative.message(
                new BookMaintenanceRejection.NoRollbackArtifactsFound(
                    hint(java.nio.file.Path.of("books/acme.sqlite"))))
            .contains("No sibling rekey rollback artifacts"));
    assertTrue(
        RejectionNarrative.message(
                new BookMaintenanceRejection.RollbackArtifactSelectionRequired(
                    hint(java.nio.file.Path.of("books/acme.sqlite")),
                    List.of(
                        hint(java.nio.file.Path.of("books/acme.rekey-rollback-1.sqlite")),
                        hint(java.nio.file.Path.of("books/acme.rekey-rollback-2.sqlite")))))
            .contains("choose one explicit rollback artifact path"));
    assertTrue(
        RejectionNarrative.message(
                new BookMaintenanceRejection.RollbackArtifactNotFound(
                    hint(java.nio.file.Path.of("books/acme.rekey-rollback-1.sqlite"))))
            .contains("does not exist"));
    assertTrue(
        RejectionNarrative.message(
                new BookMaintenanceRejection.RollbackArtifactNotForBook(
                    hint(java.nio.file.Path.of("books/acme.sqlite")),
                    hint(java.nio.file.Path.of("books/other.rekey-rollback-1.sqlite"))))
            .contains("does not belong"));
  }

  private static PublicPathHint hint(java.nio.file.Path path) {
    return PublicPathHint.fromPath(path);
  }

  @Test
  void postingMessagesCoverEveryRejection() {
    PostingRejection.AccountStateViolations accountStateViolations =
        new PostingRejection.AccountStateViolations(
            List.of(
                new PostingRejection.UnknownAccount(new AccountCode("9999")),
                new PostingRejection.InactiveAccount(new AccountCode("1000"))));

    assertTrue(
        RejectionNarrative.message(new PostingRejection.BookNotInitialized())
            .contains("open-book"));
    assertTrue(RejectionNarrative.message(accountStateViolations).contains("Reported issues: 2"));
    assertTrue(
        RejectionNarrative.message(new PostingRejection.DuplicateIdempotencyKey())
            .contains("same idempotency key"));
    assertTrue(
        RejectionNarrative.message(
                new PostingRejection.BookFunctionalCurrencyMismatch(
                    dev.erst.fingrind.core.CurrencyUnit.of("EUR"),
                    dev.erst.fingrind.core.CurrencyUnit.of("USD")))
            .contains("EUR"));
    assertTrue(
        RejectionNarrative.message(
                new PostingRejection.ClosedPeriodViolation(
                    LocalDate.parse("2026-05-01"), LocalDate.parse("2026-04-30")))
            .contains("closed-through horizon"));
    assertTrue(
        RejectionNarrative.message(
                new PostingRejection.OpeningBalanceWindowClosed(
                    dev.erst.fingrind.core.PostingKind.STANDARD, LocalDate.parse("2026-05-02")))
            .contains("first blocking posting"));
    assertTrue(
        RejectionNarrative.message(
                new PostingRejection.OpeningBalanceTouchesNominalAccount(
                    new AccountCode("4000"), AccountType.REVENUE))
            .contains("4000"));
    assertTrue(
        RejectionNarrative.message(
                new PostingRejection.ClosingEquityAccountReserved(new AccountCode("3900")))
            .contains("3900"));
    assertTrue(
        RejectionNarrative.message(
                new PostingRejection.ReversalTargetNotFound(new PostingId("posting-1")))
            .contains("posting-1"));
    assertTrue(
        RejectionNarrative.message(
                new PostingRejection.ReversalAlreadyExists(new PostingId("posting-1")))
            .contains("full reversal"));
    assertTrue(
        RejectionNarrative.message(
                new PostingRejection.ReversalDoesNotNegateTarget(new PostingId("posting-1")))
            .contains("does not negate"));
  }

  @Test
  void nullRejectionsAreRejected() {
    assertThrows(
        NullPointerException.class,
        () -> RejectionNarrative.message(NullTestSupport.<BookAdministrationRejection>nullOf()));
    assertThrows(
        NullPointerException.class,
        () -> RejectionNarrative.message(NullTestSupport.<BookQueryRejection>nullOf()));
    assertThrows(
        NullPointerException.class,
        () -> RejectionNarrative.message(NullTestSupport.<BookMaintenanceRejection>nullOf()));
    assertThrows(
        NullPointerException.class,
        () -> RejectionNarrative.message(NullTestSupport.<PostingRejection>nullOf()));
  }
}
