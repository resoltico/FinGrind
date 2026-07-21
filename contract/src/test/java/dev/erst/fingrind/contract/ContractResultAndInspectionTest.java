package dev.erst.fingrind.contract;

import static dev.erst.fingrind.contract.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceResult;
import dev.erst.fingrind.contract.bookkeeping.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.bookkeeping.AccountPage;
import dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection;
import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountResult;
import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.GetPostingResult;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsQuery;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsResult;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsQuery;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsResult;
import dev.erst.fingrind.contract.bookkeeping.OpenBookResult;
import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.contract.bookkeeping.PostingPage;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.runtime.BookFormatContract;
import dev.erst.fingrind.contract.runtime.BookInspection;
import dev.erst.fingrind.contract.runtime.BookMigrationPolicyMode;
import dev.erst.fingrind.contract.workflow.LedgerAssertion;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.core.PostingId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit tests for contract result records and book-inspection models. */
class ContractResultAndInspectionTest extends ContractTestSupport {
  @Test
  void resultRecordsExposePayloadsAcrossAdministrationAndQuerySurfaces() {
    DeclaredAccount declaredAccount = declaredAccount("1000");
    PostingFact postingFact = postingFact("posting-1", "idem-1");
    AccountPage accountPage = accountPage(List.of(declaredAccount), 50, Optional.empty());
    PostingPage postingPage = postingPage(List.of(postingFact), 50, Optional.empty());
    AccountBalanceSnapshot snapshot =
        new AccountBalanceSnapshot(
            bookIdentity(),
            declaredAccount,
            Optional.of(LocalDate.parse("2026-04-01")),
            Optional.of(LocalDate.parse("2026-04-30")),
            PostingCoverage.ALL_POSTING_KINDS,
            List.of(CurrencyBalance.ofTotals(money("10.00"), money("0.00"))));
    assertEquals(
        Instant.parse("2026-04-07T10:15:30Z"),
        new OpenBookResult.Opened(Instant.parse("2026-04-07T10:15:30Z"), bookIdentity())
            .initializedAt());
    assertEquals(
        new BookAdministrationRejection.BookAlreadyInitialized(),
        new OpenBookResult.Rejected(new BookAdministrationRejection.BookAlreadyInitialized())
            .rejection());
    assertEquals(declaredAccount, new DeclareAccountResult.Declared(declaredAccount).account());
    assertEquals(declaredAccount, new DeclareAccountResult.Reactivated(declaredAccount).account());
    assertEquals(declaredAccount, new DeclareAccountResult.Renamed(declaredAccount).account());
    assertEquals(declaredAccount, new DeclareAccountResult.Unchanged(declaredAccount).account());
    assertEquals(
        new BookAdministrationRejection.BookNotInitialized(),
        new DeclareAccountResult.Rejected(new BookAdministrationRejection.BookNotInitialized())
            .rejection());
    assertEquals(
        accountPage,
        new ListAccountsResult.Listed(new ListAccountsQuery(50, Optional.empty()), accountPage)
            .page());
    assertEquals(
        new BookQueryRejection.BookNotInitialized(),
        new ListAccountsResult.Rejected(new BookQueryRejection.BookNotInitialized()).rejection());
    assertEquals(postingFact, foundPosting(postingFact).postingFact());
    assertEquals(
        new BookQueryRejection.PostingNotFound(new PostingId("41a95cd2-4a5f-3ef3-8a33-c2771905f362")),
        new GetPostingResult.Rejected(
                new BookQueryRejection.PostingNotFound(new PostingId("41a95cd2-4a5f-3ef3-8a33-c2771905f362")))
            .rejection());
    assertEquals(snapshot, new AccountBalanceResult.Reported(snapshot).snapshot());
    assertEquals(
        new BookQueryRejection.UnknownAccount(new AccountCode("9999")),
        new AccountBalanceResult.Rejected(
                new BookQueryRejection.UnknownAccount(new AccountCode("9999")))
            .rejection());
    assertEquals(
        postingPage,
        new ListPostingsResult.Listed(
                new ListPostingsQuery(Optional.empty(), null, null, 50, Optional.empty()),
                postingPage)
            .page());
    assertEquals(
        new BookQueryRejection.BookNotInitialized(),
        new ListPostingsResult.Rejected(new BookQueryRejection.BookNotInitialized()).rejection());
  }

  @Test
  void inspectionAssertionsAndViolationsCoverOptionalAndPositiveBranches() {
    BookInspection inspection =
        new BookInspection.Initialized(
            BookFormatContract.APPLICATION_ID,
            BookFormatContract.FORMAT_VERSION,
            BookFormatContract.FORMAT_VERSION,
            Instant.parse("2026-04-07T10:15:30Z"),
            bookIdentity(),
            closeReadiness());
    AccountBalanceSnapshot snapshot =
        new AccountBalanceSnapshot(
            bookIdentity(),
            declaredAccount("1000"),
            Optional.of(LocalDate.parse("2026-04-01")),
            Optional.of(LocalDate.parse("2026-04-30")),
            PostingCoverage.ALL_POSTING_KINDS,
            List.of(CurrencyBalance.ofTotals(money("10.00"), money("0.00"))));
    LedgerAssertion.AccountActive active =
        new LedgerAssertion.AccountActive(new AccountCode("1000"));
    LedgerAssertion.PostingExists postingExists =
        new LedgerAssertion.PostingExists(new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"));
    LedgerAssertion.AccountBalanceEquals assertion =
        new LedgerAssertion.AccountBalanceEquals(
            new AccountCode("1000"),
            null,
            LocalDate.parse("2026-04-30"),
            money("10.00"),
            BalanceSide.DEBIT);
    LedgerAssertion.AccountBalanceEquals lowerBoundOnlyAssertion =
        new LedgerAssertion.AccountBalanceEquals(
            new AccountCode("1000"),
            LocalDate.parse("2026-04-01"),
            null,
            money("10.00"),
            BalanceSide.DEBIT);
    PostingRejection.InactiveAccount inactive =
        new PostingRejection.InactiveAccount(new AccountCode("2000"));
    assertEquals(BookInspection.Status.INITIALIZED, inspection.status());
    assertEquals(
        BookFormatContract.APPLICATION_ID,
        ((BookInspection.Initialized) inspection).applicationId());
    assertEquals(Optional.of(LocalDate.parse("2026-04-01")), snapshot.effectiveDateFrom());
    assertEquals(Optional.of(LocalDate.parse("2026-04-30")), snapshot.effectiveDateTo());
    assertEquals(new AccountCode("1000"), active.accountCode());
    assertEquals(new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"), postingExists.postingId());
    assertTrue(assertion.query().effectiveDateFrom().isEmpty());
    assertEquals(Optional.of(LocalDate.parse("2026-04-30")), assertion.query().effectiveDateTo());
    assertEquals(
        Optional.of(LocalDate.parse("2026-04-01")),
        lowerBoundOnlyAssertion.query().effectiveDateFrom());
    assertTrue(lowerBoundOnlyAssertion.query().effectiveDateTo().isEmpty());
    assertEquals(new AccountCode("2000"), inactive.accountCode());
  }

  @Test
  void inspectionVariantsExposeTheirStructuralStateAndRejectInvalidMetadata() {
    List<BookInspection> inspections =
        List.of(
            new BookInspection.Missing(BookFormatContract.FORMAT_VERSION),
            new BookInspection.Existing(
                BookInspection.Status.BLANK_SQLITE,
                BookFormatContract.APPLICATION_ID,
                0,
                BookFormatContract.FORMAT_VERSION),
            new BookInspection.Initialized(
                BookFormatContract.APPLICATION_ID,
                BookFormatContract.FORMAT_VERSION,
                BookFormatContract.FORMAT_VERSION,
                Instant.parse("2026-04-07T10:15:30Z"),
                bookIdentity(),
                closeReadiness()),
            new BookInspection.Existing(
                BookInspection.Status.FOREIGN_SQLITE,
                0x12345678,
                0,
                BookFormatContract.FORMAT_VERSION),
            new BookInspection.Existing(
                BookInspection.Status.UNSUPPORTED_FORMAT_VERSION,
                BookFormatContract.APPLICATION_ID,
                2,
                BookFormatContract.FORMAT_VERSION),
            new BookInspection.Existing(
                BookInspection.Status.INCOMPLETE_FINGRIND,
                BookFormatContract.APPLICATION_ID,
                BookFormatContract.FORMAT_VERSION,
                BookFormatContract.FORMAT_VERSION));
    List<BookInspection.Status> statuses =
        List.of(
            BookInspection.Status.MISSING,
            BookInspection.Status.BLANK_SQLITE,
            BookInspection.Status.INITIALIZED,
            BookInspection.Status.FOREIGN_SQLITE,
            BookInspection.Status.UNSUPPORTED_FORMAT_VERSION,
            BookInspection.Status.INCOMPLETE_FINGRIND);
    List<Boolean> initialized = List.of(false, false, true, false, false, false);
    List<Boolean> compatibleWithCurrentBinary = List.of(false, false, true, false, false, false);
    List<Boolean> canInitializeWithOpenBook = List.of(true, true, false, false, false, false);
    for (int index = 0; index < inspections.size(); index++) {
      assertEquals(statuses.get(index), inspections.get(index).status());
      BookInspection.Status status = inspections.get(index).status();
      assertEquals(initialized.get(index), status.initialized());
      assertEquals(compatibleWithCurrentBinary.get(index), status.compatibleWithCurrentBinary());
      assertEquals(canInitializeWithOpenBook.get(index), status.canInitializeWithOpenBook());
      assertEquals(
          BookFormatContract.FORMAT_VERSION, inspections.get(index).supportedBookFormatVersion());
      assertEquals(
          BookMigrationPolicyMode.HARD_BREAK_REJECT_OLDER_FORMATS,
          inspections.get(index).migrationPolicy().mode());
      assertEquals(
          BookFormatContract.FORMAT_VERSION,
          inspections.get(index).migrationPolicy().supportedBookFormatVersion());
      assertFalse(inspections.get(index).migrationPolicy().inPlaceUpgradeSupported());
      assertFalse(inspections.get(index).migrationPolicy().olderFormatsAccepted());
      assertFalse(inspections.get(index).migrationPolicy().newerFormatsAccepted());
    }
    assertEquals(
        BookInspection.Status.BLANK_SQLITE, BookInspection.Status.fromWireValue("blank-sqlite"));
    assertEquals(
        BookMigrationPolicyMode.HARD_BREAK_REJECT_OLDER_FORMATS,
        BookMigrationPolicyMode.fromWireValue("hard-break-reject-older-formats"));
    assertThrows(IllegalArgumentException.class, () -> new BookInspection.Missing(0));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BookInspection.Existing(
                BookInspection.Status.BLANK_SQLITE,
                BookFormatContract.APPLICATION_ID,
                -1,
                BookFormatContract.FORMAT_VERSION));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BookInspection.Existing(
                BookInspection.Status.BLANK_SQLITE, -1, 0, BookFormatContract.FORMAT_VERSION));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BookInspection.Existing(
                BookInspection.Status.INITIALIZED,
                BookFormatContract.APPLICATION_ID,
                BookFormatContract.FORMAT_VERSION,
                BookFormatContract.FORMAT_VERSION));
    assertThrows(
        NullPointerException.class,
        () ->
            new BookInspection.Initialized(
                BookFormatContract.APPLICATION_ID,
                1,
                1,
                nullOf(),
                bookIdentity(),
                closeReadiness()));
  }

  @Test
  void closeTargetReadiness_readyStateRejectsBlockingMetadata() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BookInspection.CloseTargetReadiness(
                true,
                FinancialPositionLineClassification.RESULT_HOLDING,
                new AccountCode("3200"),
                "close-target-account-candidate-missing",
                "Blocked",
                List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BookInspection.CloseTargetReadiness(
                true,
                FinancialPositionLineClassification.RESULT_HOLDING,
                new AccountCode("3200"),
                null,
                "Blocked",
                List.of()));
  }

  @Test
  void closeTargetReadiness_blockedStateRejectsAccountCode() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BookInspection.CloseTargetReadiness(
                false,
                FinancialPositionLineClassification.RESULT_HOLDING,
                new AccountCode("3200"),
                "close-target-account-candidate-missing",
                "Missing result-holding account.",
                List.of()));
  }

  @Test
  void closeTargetReadiness_blockedStateRequiresBlockingMetadata() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BookInspection.CloseTargetReadiness(
                false,
                FinancialPositionLineClassification.RESULT_HOLDING,
                null,
                " ",
                "Missing result-holding account.",
                List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BookInspection.CloseTargetReadiness(
                false,
                FinancialPositionLineClassification.RESULT_HOLDING,
                null,
                "close-target-account-candidate-missing",
                " ",
                List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BookInspection.CloseTargetReadiness(
                false,
                FinancialPositionLineClassification.RESULT_HOLDING,
                null,
                null,
                "Missing result-holding account.",
                List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BookInspection.CloseTargetReadiness(
                false,
                FinancialPositionLineClassification.RESULT_HOLDING,
                null,
                "close-target-account-candidate-missing",
                null,
                List.of()));
  }

  @Test
  void closeTargetReadiness_copiesCandidateAccountCodes() {
    List<AccountCode> candidates = new ArrayList<>(List.of(new AccountCode("3200")));
    BookInspection.CloseTargetReadiness readiness =
        new BookInspection.CloseTargetReadiness(
            false,
            FinancialPositionLineClassification.RESULT_HOLDING,
            null,
            "close-target-account-candidate-ambiguous",
            "Multiple result-holding candidates are active.",
            candidates);
    candidates.clear();
    assertEquals(List.of(new AccountCode("3200")), readiness.candidateAccountCodes());
  }

  private static BookInspection.CloseReadiness closeReadiness() {
    return new BookInspection.CloseReadiness(
        readyCloseTarget(
            FinancialPositionLineClassification.RESULT_HOLDING, new AccountCode("3200")),
        readyCloseTarget(
            FinancialPositionLineClassification.RETAINED_ACCUMULATED, new AccountCode("3300")));
  }

  private static BookInspection.CloseTargetReadiness readyCloseTarget(
      FinancialPositionLineClassification classification, AccountCode accountCode) {
    return new BookInspection.CloseTargetReadiness(
        true, classification, accountCode, null, null, List.of());
  }
}
