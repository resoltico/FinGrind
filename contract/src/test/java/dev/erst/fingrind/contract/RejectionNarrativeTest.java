package dev.erst.fingrind.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.AccountRegistryLifecycleRejection;
import dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceArtifactRole;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenancePathFailure;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceRejection;
import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.contract.bookkeeping.CloseTargetAccountCandidateAmbiguous;
import dev.erst.fingrind.contract.bookkeeping.CloseTargetAccountCandidateMissing;
import dev.erst.fingrind.contract.bookkeeping.ContraAccountInvalid;
import dev.erst.fingrind.contract.bookkeeping.PostingEffectiveDateBeforeBookStart;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.bookkeeping.PostingRejectionSemantics;
import dev.erst.fingrind.contract.bookkeeping.RejectionNarrative;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountRegistryDependency;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.ContraAccountRelationshipViolation;
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
                new PostingEffectiveDateBeforeBookStart(
                    LocalDate.parse("2025-12-31"), LocalDate.parse("2026-01-01")))
            .contains("immutable accounting start"));
    assertTrue(
        RejectionNarrative.message(
                new BookAdministrationRejection.AccountTypeConflict(
                    new AccountCode("1000"), AccountType.ASSET, AccountType.LIABILITY))
            .contains("account type"));
    assertTrue(
        RejectionNarrative.message(
                new BookAdministrationRejection.AccountTaxonomyConflict(
                    new AccountCode("1000"),
                    new AccountTaxonomy(
                        dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
                        Optional.of(new AccountCode("3000")),
                        Optional.empty(),
                        Optional.of(FinancialPositionLineClassification.OTHER_EQUITY),
                        Optional.empty(),
                        Optional.empty()),
                    new AccountTaxonomy(
                        dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
                        Optional.of(new AccountCode("3010")),
                        Optional.empty(),
                        Optional.of(FinancialPositionLineClassification.RESULT_HOLDING),
                        Optional.empty(),
                        Optional.empty())))
            .contains("immutable hierarchy or statement taxonomy"));
    assertTrue(
        RejectionNarrative.message(
                new ContraAccountInvalid(
                    new AccountCode("4010"),
                    new AccountCode("4000"),
                    ContraAccountRelationshipViolation.ACCOUNT_TYPE_MISMATCH))
            .contains("account-type-mismatch"));
    assertTrue(
        RejectionNarrative.message(
                new AccountRegistryLifecycleRejection.AccountNotFound(new AccountCode("9999")))
            .contains("9999"));
    assertTrue(
        RejectionNarrative.message(
                new AccountRegistryLifecycleRejection.AccountHasDependents(
                    new AccountCode("1000"), List.of(AccountRegistryDependency.POSTINGS)))
            .contains("postings"));
    assertTrue(
        RejectionNarrative.message(
                new AccountRegistryLifecycleRejection.AccountBalanceNotZero(
                    new AccountCode("1000")))
            .contains("not zero"));
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
                        Optional.empty(),
                        Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                        Optional.empty(),
                        Optional.empty()),
                    new AccountCode("1000"),
                    new AccountTaxonomy(
                        dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of(FinancialPositionLineClassification.NONCURRENT_ASSET),
                        Optional.empty(),
                        Optional.empty())))
            .contains("statement-classification family"));
    assertTrue(
        RejectionNarrative.message(
                new BookAdministrationRejection.AccountHierarchyCycle(
                    new AccountCode("1000"), new AccountCode("1010")))
            .contains("chart hierarchy cycle"));
    assertTrue(
        RejectionNarrative.message(
                new CloseTargetAccountCandidateMissing(
                    FinancialPositionLineClassification.RESULT_HOLDING, List.of()))
            .contains("required classification"));
    assertTrue(
        RejectionNarrative.message(
                new CloseTargetAccountCandidateMissing(
                    FinancialPositionLineClassification.RESULT_HOLDING,
                    List.of(new AccountCode("3200"))))
            .contains("inactive candidates: 3200"));
    assertTrue(
        RejectionNarrative.message(
                new CloseTargetAccountCandidateAmbiguous(
                    FinancialPositionLineClassification.OTHER_EQUITY,
                    List.of(new AccountCode("3200"), new AccountCode("3210"))))
            .contains("3200, 3210"));
    assertTrue(
        RejectionNarrative.message(
                new BookAdministrationRejection.InterimResultSweepMustStartAt(
                    LocalDate.parse("2026-01-01")))
            .contains("2026-01-01"));
    assertTrue(
        RejectionNarrative.message(
                new BookAdministrationRejection.InterimResultSweepFutureDate(
                    LocalDate.parse("2026-12-31")))
            .contains("2026-12-31"));
    assertTrue(
        RejectionNarrative.message(
                new BookAdministrationRejection.InterimResultSweepCrossesFiscalYearBoundary(
                    LocalDate.parse("2026-12-15"),
                    LocalDate.parse("2027-01-15"),
                    dev.erst.fingrind.core.FiscalYearStart.parse("01-01")))
            .contains("01-01"));
    assertTrue(
        RejectionNarrative.message(
                new BookAdministrationRejection.FiscalYearCloseMustStartAt(
                    LocalDate.parse("2026-01-01")))
            .contains("2026-01-01"));
    assertTrue(
        RejectionNarrative.message(
                new BookAdministrationRejection.FiscalYearCloseMustEndAt(
                    LocalDate.parse("2026-12-31")))
            .contains("2026-12-31"));
    assertTrue(
        RejectionNarrative.message(
                new BookAdministrationRejection.FiscalYearClosePrecedesTransferredThroughHorizon(
                    LocalDate.parse("2025-12-31"), LocalDate.parse("2026-03-31")))
            .contains("2026-03-31"));
    assertTrue(
        RejectionNarrative.message(
                new BookAdministrationRejection.FiscalYearCloseFutureDate(
                    LocalDate.parse("2027-01-01")))
            .contains("2027-01-01"));
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
                new BookQueryRejection.PostingNotFound(
                    new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69")))
            .contains("bdc03c47-a16c-3688-a18f-2445894bbc69"));
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
                new BookMaintenanceRejection.ArtifactPathInvalid(
                    BookMaintenanceArtifactRole.BACKUP_TARGET,
                    hint(java.nio.file.Path.of("backup/acme.sqlite")),
                    BookMaintenancePathFailure.PARENT_PATH_COLLISION))
            .contains("violates the filesystem contract"));
    assertTrue(
        RejectionNarrative.message(
                new BookMaintenanceRejection.ArtifactBusy(
                    BookMaintenanceArtifactRole.LIVE_BOOK,
                    hint(java.nio.file.Path.of("books/acme.sqlite"))))
            .contains("actively in use"));
    assertTrue(
        RejectionNarrative.message(
                new BookMaintenanceRejection.BackupDestinationAlreadyExists(
                    hint(java.nio.file.Path.of("backup/acme.sqlite"))))
            .contains("will not overwrite"));
    assertTrue(
        RejectionNarrative.message(
                new BookMaintenanceRejection.SecretTargetOccupied(
                    hint(java.nio.file.Path.of("backup/acme.book-key"))))
            .contains("generated-secret target"));
    assertTrue(
        RejectionNarrative.message(
                new BookMaintenanceRejection.BookDestinationOccupied(
                    hint(java.nio.file.Path.of("books/acme.sqlite"))))
            .contains("will not replace it"));
    assertTrue(
        RejectionNarrative.message(
                new BookMaintenanceRejection.ArtifactVerificationFailed(
                    dev.erst.fingrind.contract.bookkeeping.BookMaintenanceArtifactRole
                        .RESTORED_TARGET,
                    hint(java.nio.file.Path.of("books/acme.sqlite")),
                    dev.erst.fingrind.contract.bookkeeping.BookMaintenanceVerificationFailure
                        .PROTECTED_BOOK_VERIFICATION_FAILED))
            .contains("failed verification"));
  }

  private static java.nio.file.Path hint(java.nio.file.Path path) {
    return path.toAbsolutePath().normalize();
  }

  @Test
  void postingMessagesCoverEveryRejection() {
    PostingRejection.AccountStateViolations accountStateViolations =
        new PostingRejection.AccountStateViolations(
            List.of(
                new PostingRejection.UnknownAccount(new AccountCode("9999")),
                new PostingRejection.InactiveAccount(new AccountCode("1000"))));
    PostingRejection.EntrySemanticsViolations entrySemanticsViolations =
        new PostingRejection.EntrySemanticsViolations(
            List.of(
                PostingRejectionSemantics.sourceDocumentTypeNotAccepted(
                    "SALE",
                    new dev.erst.fingrind.core.SourceDocumentType("invoice"),
                    List.of("cash-receipt", "bank-deposit"))));

    assertTrue(
        RejectionNarrative.message(new PostingRejection.BookNotInitialized())
            .contains("open-book"));
    assertEquals(
        "Posting rejected with 2 account-state issues.",
        RejectionNarrative.message(accountStateViolations));
    assertEquals(
        "Posting rejected with 1 entry-semantics issue.",
        RejectionNarrative.message(entrySemanticsViolations));
    assertFalse(
        RejectionNarrative.message(entrySemanticsViolations).contains("published semantics"));
    PostingRejection.EntrySemanticsViolations multipleEntrySemanticsViolations =
        new PostingRejection.EntrySemanticsViolations(
            List.of(
                PostingRejectionSemantics.sourceDocumentTypeNotAccepted(
                    "SALE",
                    new dev.erst.fingrind.core.SourceDocumentType("invoice"),
                    List.of("cash-receipt", "bank-deposit")),
                PostingRejectionSemantics.accountTypeMismatch(
                    "SALE",
                    "revenueAccountCode",
                    new AccountCode("1000"),
                    AccountType.REVENUE,
                    AccountType.ASSET),
                PostingRejectionSemantics.distinctRoleAccountsRequired(
                    "SALE", "cashAccountCode", "revenueAccountCode", new AccountCode("1000"))));
    String multipleEntrySemanticsMessage =
        RejectionNarrative.message(multipleEntrySemanticsViolations);
    assertEquals("Posting rejected with 3 entry-semantics issues.", multipleEntrySemanticsMessage);
    assertTrue(
        RejectionNarrative.message(new PostingRejection.IdempotencyKeyConflict())
            .contains("different committed posting request"));
    assertTrue(
        RejectionNarrative.message(
                new PostingRejection.PostingEffectiveDateInFuture(
                    LocalDate.parse("2026-05-02"), LocalDate.parse("2026-05-01")))
            .contains("current UTC date"));
    assertTrue(
        RejectionNarrative.message(
                new PostingRejection.BookFunctionalCurrencyMismatch(
                    dev.erst.fingrind.core.CurrencyUnit.of("EUR"),
                    dev.erst.fingrind.core.CurrencyUnit.of("USD")))
            .contains("EUR"));
    assertTrue(
        RejectionNarrative.message(
                new PostingRejection.SweptInterimResultViolation(
                    LocalDate.parse("2026-05-01"), LocalDate.parse("2026-04-30")))
            .contains("transferred-through horizon"));
    assertTrue(
        RejectionNarrative.message(
                new PostingRejection.OpeningPositionWindowClosed(
                    dev.erst.fingrind.core.PostingKind.STANDARD, LocalDate.parse("2026-05-02")))
            .contains("first blocking posting"));
    assertTrue(
        RejectionNarrative.message(
                new PostingRejection.OpeningPositionTouchesNominalAccount(
                    new AccountCode("4000"), AccountType.REVENUE))
            .contains("4000"));
    String resultHoldingMessage =
        RejectionNarrative.message(
            new PostingRejection.ReservedResultClassification(
                new AccountCode("3900"), FinancialPositionLineClassification.RESULT_HOLDING));
    assertTrue(resultHoldingMessage.contains("3900"));
    assertTrue(resultHoldingMessage.contains("RESULT_HOLDING"));
    String retainedAccumulatedMessage =
        RejectionNarrative.message(
            new PostingRejection.ReservedResultClassification(
                new AccountCode("3950"), FinancialPositionLineClassification.RETAINED_ACCUMULATED));
    assertTrue(retainedAccumulatedMessage.contains("3950"));
    assertTrue(retainedAccumulatedMessage.contains("RETAINED_ACCUMULATED"));
    assertTrue(
        RejectionNarrative.message(
                new PostingRejection.ReversalTargetNotFound(
                    new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69")))
            .contains("bdc03c47-a16c-3688-a18f-2445894bbc69"));
    assertTrue(
        RejectionNarrative.message(
                new dev.erst.fingrind.contract.bookkeeping.ReversalTargetIsReversal(
                    new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69")))
            .contains("cannot be reversed"));
    assertTrue(
        RejectionNarrative.message(
                new PostingRejection.ReversalAlreadyExists(
                    new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69")))
            .contains("full reversal"));
    assertTrue(
        RejectionNarrative.message(
                new PostingRejection.ReversalDoesNotNegateTarget(
                    new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69")))
            .contains("does not negate"));
  }

  @Test
  void postingHintsCoverEveryRejection() {
    PostingRejection.EntrySemanticsViolations singleEntrySemanticsViolation =
        new PostingRejection.EntrySemanticsViolations(
            List.of(PostingRejectionSemantics.economicNullJournal("DIRECT_JOURNAL")));
    PostingRejection.EntrySemanticsViolations multipleEntrySemanticsViolations =
        new PostingRejection.EntrySemanticsViolations(
            List.of(
                PostingRejectionSemantics.sourceDocumentTypeNotAccepted(
                    "SALE",
                    new dev.erst.fingrind.core.SourceDocumentType("invoice"),
                    List.of("cash-receipt", "bank-deposit")),
                PostingRejectionSemantics.accountTypeMismatch(
                    "SALE",
                    "revenueAccountCode",
                    new AccountCode("1000"),
                    AccountType.REVENUE,
                    AccountType.ASSET),
                PostingRejectionSemantics.distinctRoleAccountsRequired(
                    "SALE", "cashAccountCode", "revenueAccountCode", new AccountCode("1000"))));

    assertTrue(
        java.util.Objects.requireNonNull(
                RejectionNarrative.hint(new PostingRejection.BookNotInitialized()))
            .contains("open-book"));
    assertNull(
        RejectionNarrative.hint(
            new PostingRejection.AccountStateViolations(
                List.of(new PostingRejection.UnknownAccount(new AccountCode("9999"))))));
    assertNull(RejectionNarrative.hint(singleEntrySemanticsViolation));
    assertNull(RejectionNarrative.hint(multipleEntrySemanticsViolations));
    assertTrue(
        java.util.Objects.requireNonNull(
                RejectionNarrative.hint(new PostingRejection.IdempotencyKeyConflict()))
            .contains("exact same normalized request"));
    assertEquals(
        "Use an effective date on or after this book's immutable accounting start date '2026-01-01'.",
        RejectionNarrative.hint(
            new PostingEffectiveDateBeforeBookStart(
                LocalDate.parse("2025-12-31"), LocalDate.parse("2026-01-01"))));
    assertEquals(
        "Use an effective date on or before the current UTC date.",
        RejectionNarrative.hint(
            new PostingRejection.PostingEffectiveDateInFuture(
                LocalDate.parse("2026-05-02"), LocalDate.parse("2026-05-01"))));
    assertTrue(
        java.util.Objects.requireNonNull(
                RejectionNarrative.hint(
                    new PostingRejection.BookFunctionalCurrencyMismatch(
                        dev.erst.fingrind.core.CurrencyUnit.of("EUR"),
                        dev.erst.fingrind.core.CurrencyUnit.of("USD"))))
            .contains("functional currency"));
    assertTrue(
        java.util.Objects.requireNonNull(
                RejectionNarrative.hint(
                    new PostingRejection.SweptInterimResultViolation(
                        LocalDate.parse("2026-05-01"), LocalDate.parse("2026-04-30"))))
            .contains("transferred-through horizon"));
    assertTrue(
        java.util.Objects.requireNonNull(
                RejectionNarrative.hint(
                    new PostingRejection.OpeningPositionWindowClosed(
                        dev.erst.fingrind.core.PostingKind.STANDARD,
                        LocalDate.parse("2026-05-02"))))
            .contains("first committed posting"));
    assertTrue(
        java.util.Objects.requireNonNull(
                RejectionNarrative.hint(
                    new PostingRejection.OpeningPositionTouchesNominalAccount(
                        new AccountCode("4000"), AccountType.REVENUE)))
            .contains("asset, liability, or equity"));
    String resultHoldingHint =
        java.util.Objects.requireNonNull(
            RejectionNarrative.hint(
                new PostingRejection.ReservedResultClassification(
                    new AccountCode("3000"), FinancialPositionLineClassification.RESULT_HOLDING)));
    assertTrue(resultHoldingHint.contains("RESULT_HOLDING"));
    String retainedAccumulatedHint =
        java.util.Objects.requireNonNull(
            RejectionNarrative.hint(
                new PostingRejection.ReservedResultClassification(
                    new AccountCode("3001"),
                    FinancialPositionLineClassification.RETAINED_ACCUMULATED)));
    assertTrue(retainedAccumulatedHint.contains("RETAINED_ACCUMULATED"));
    assertTrue(
        java.util.Objects.requireNonNull(
                RejectionNarrative.hint(
                    new PostingRejection.ReversalTargetNotFound(
                        new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"))))
            .contains("get-posting"));
    assertTrue(
        java.util.Objects.requireNonNull(
                RejectionNarrative.hint(
                    new dev.erst.fingrind.contract.bookkeeping.ReversalTargetIsReversal(
                        new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"))))
            .contains("fresh operational entry"));
    assertTrue(
        java.util.Objects.requireNonNull(
                RejectionNarrative.hint(
                    new PostingRejection.ReversalAlreadyExists(
                        new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"))))
            .contains("existing reversal"));
    assertTrue(
        java.util.Objects.requireNonNull(
                RejectionNarrative.hint(
                    new PostingRejection.ReversalDoesNotNegateTarget(
                        new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"))))
            .contains("full negating journal entry"));
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
