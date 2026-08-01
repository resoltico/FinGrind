package dev.erst.fingrind.executor;

import static dev.erst.fingrind.testsupport.PostingRouteReachabilityScenarioFactory.directJournalCommand;
import static dev.erst.fingrind.testsupport.PostingRouteReachabilityScenarioFactory.openingPositionCommand;
import static dev.erst.fingrind.testsupport.PostingRouteReachabilityScenarioFactory.priorPosting;
import static dev.erst.fingrind.testsupport.PostingRouteReachabilityScenarioFactory.reversalCommand;
import static dev.erst.fingrind.testsupport.PostingRouteReachabilityTestSupport.bookIdentity;
import static dev.erst.fingrind.testsupport.PostingRouteReachabilityTestSupport.candidateAccount;
import static dev.erst.fingrind.testsupport.PostingRouteReachabilityTestSupport.cellToken;
import static dev.erst.fingrind.testsupport.PostingRouteReachabilityTestSupport.counterAuxiliaryAccount;
import static dev.erst.fingrind.testsupport.PostingRouteReachabilityTestSupport.isInventoryCell;
import static dev.erst.fingrind.testsupport.PostingRouteReachabilityTestSupport.payableAuxiliaryAccount;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.protocol.RequestSurfaceFacts;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PostingAcceptancePolicy;
import dev.erst.fingrind.executor.bookkeeping.PostingValidationStore;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.testsupport.PostingRouteReachabilityContract;
import dev.erst.fingrind.testsupport.PostingRouteReachabilityTestSupport;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Verifies that published classification reachability matches the live posting routes. */
class PostingRouteReachabilityContractTest implements PostingRouteReachabilityContract {
  private static final PostEntrySemanticsPolicy ENTRY_SEMANTICS =
      PostEntrySemanticsPolicy.currentKernel();
  private static final PostingAcceptancePolicy POSTING_ACCEPTANCE =
      PostingAcceptancePolicy.currentKernel();

  @Test
  void openingPositionReachabilityMatchesThePublishedMatrix() {
    verifyOpeningPositionReachabilityMatrix();
  }

  @Test
  void directJournalReachabilityMatchesThePublishedMatrix() {
    verifyDirectJournalReachabilityMatrix();
  }

  @Test
  void reversalReachabilityMatchesThePublishedMatrix() {
    verifyReversalReachabilityMatrix();
  }

  @Override
  public void assertOpeningPositionReachability(RequestSurfaceFacts.ReachabilityCellFacts cell) {
    ReachabilityValidationBook book =
        new ReachabilityValidationBook(
            bookIdentity(cell), candidateAccount(cell), counterAuxiliaryAccount());
    PostEntryCommand command = openingPositionCommand(cell, "opening-" + cellToken(cell));
    Optional<BookkeepingPostingRejection> semanticRejection =
        ENTRY_SEMANTICS.rejectionFor(command, book);

    if (cell.openingReachable()) {
      assertEquals(Optional.empty(), semanticRejection, "opening semantics should accept " + cell);
      Optional<BookkeepingPostingRejection> rejection =
          POSTING_ACCEPTANCE.rejectionFor(
              PostEntryCommandTranslator.toPostingCommand(command, book), book);
      assertEquals(Optional.empty(), rejection, "opening route should accept " + cell);
      return;
    }
    BookkeepingPostingRejection.EntrySemanticsViolations semanticViolations =
        assertInstanceOf(
            BookkeepingPostingRejection.EntrySemanticsViolations.class,
            semanticRejection.orElseThrow(),
            "opening semantics should reject non-opening cell " + cell);
    assertEquals(
        "opening-window-account-not-permitted", semanticViolations.violations().getFirst().code());
    assertEquals(
        "entryKind '"
            + BookkeepingEntryKind.OPENING_POSITION.wireValue()
            + "' uses openingBalances[].accountCode '"
            + PostingRouteReachabilityTestSupport.CANDIDATE_ACCOUNT_CODE.value()
            + "', which is not permitted in the adoption opening window.",
        semanticViolations.violations().getFirst().message());
  }

  @Override
  public void assertDirectJournalReachability(RequestSurfaceFacts.ReachabilityCellFacts cell) {
    ReachabilityValidationBook book =
        new ReachabilityValidationBook(
            bookIdentity(cell), candidateAccount(cell), counterAuxiliaryAccount());
    PostEntryCommand command = directJournalCommand(cell, "journal-" + cellToken(cell));
    Optional<BookkeepingPostingRejection> semanticRejection =
        ENTRY_SEMANTICS.rejectionFor(command, book);

    if (isInventoryCell(cell)) {
      BookkeepingPostingRejection.EntrySemanticsViolations semanticViolations =
          assertInstanceOf(
              BookkeepingPostingRejection.EntrySemanticsViolations.class,
              semanticRejection.orElseThrow(),
              "direct journal semantics should reject inventory cell " + cell);
      assertEquals(
          "raw-journal-touches-inventory", semanticViolations.violations().getFirst().code());
      assertEquals(
          "entryKind '"
              + BookkeepingEntryKind.DIRECT_JOURNAL.wireValue()
              + "' contains lines[].accountCode '"
              + PostingRouteReachabilityTestSupport.CANDIDATE_ACCOUNT_CODE.value()
              + "', which resolves to the inventory role. Raw direct-journal requests cannot create or change exact inventory quantity.",
          semanticViolations.violations().getFirst().message());
      return;
    }

    assertTrue(semanticRejection.isEmpty(), "entry semantics should accept " + cell);
    Optional<BookkeepingPostingRejection> rejection =
        POSTING_ACCEPTANCE.rejectionFor(
            PostEntryCommandTranslator.toPostingCommand(command, book), book);

    if (cell.operationalJournalReachable()) {
      assertEquals(Optional.empty(), rejection, "direct journal route should accept " + cell);
      return;
    }
    BookkeepingPostingRejection.ReservedResultClassification reservedRejection =
        assertInstanceOf(
            BookkeepingPostingRejection.ReservedResultClassification.class,
            rejection.orElseThrow(),
            "direct journal route should reject reserved cell " + cell);
    assertEquals(
        PostingRouteReachabilityTestSupport.CANDIDATE_ACCOUNT_CODE,
        reservedRejection.accountCode());
    assertEquals(
        reservedClassification(cell), reservedRejection.financialPositionLineClassification());
  }

  @Override
  public void assertReversalReachability(RequestSurfaceFacts.ReachabilityCellFacts cell) {
    CommittedPosting priorPosting =
        priorPosting(
            cell,
            "prior-" + cellToken(cell),
            dev.erst.fingrind.executor.ScenarioPostingIdentifiers.fromLabel(
                "posting-" + cellToken(cell).toLowerCase(java.util.Locale.ROOT)));
    ReachabilityValidationBook book =
        new ReachabilityValidationBook(
            bookIdentity(cell), candidateAccount(cell), counterAuxiliaryAccount(), priorPosting);
    PostEntryCommand command =
        reversalCommand(cell, "reversal-" + cellToken(cell), priorPosting.postingId());

    assertTrue(
        ENTRY_SEMANTICS.rejectionFor(command, book).isEmpty(),
        "reversal semantics should accept " + cell);
    Optional<BookkeepingPostingRejection> rejection =
        POSTING_ACCEPTANCE.rejectionFor(
            PostEntryCommandTranslator.toPostingCommand(command, book), book);

    if (cell.reversalReachable()) {
      assertEquals(Optional.empty(), rejection, "reversal route should accept " + cell);
      return;
    }
    BookkeepingPostingRejection.ReservedResultClassification reservedRejection =
        assertInstanceOf(
            BookkeepingPostingRejection.ReservedResultClassification.class,
            rejection.orElseThrow(),
            "reversal route should reject reserved cell " + cell);
    assertEquals(
        PostingRouteReachabilityTestSupport.CANDIDATE_ACCOUNT_CODE,
        reservedRejection.accountCode());
    assertEquals(
        reservedClassification(cell), reservedRejection.financialPositionLineClassification());
  }

  private static FinancialPositionLineClassification reservedClassification(
      RequestSurfaceFacts.ReachabilityCellFacts cell) {
    return PostingRouteReachabilityTestSupport.taxonomy(cell)
        .financialPositionLineClassification()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Reserved direct-posting cells must declare one financial-position classification."));
  }

  /** Minimal posting-validation store tailored to one reachability scenario. */
  private static final class ReachabilityValidationBook implements PostingValidationStore {
    private final BookIdentity bookIdentity;
    private final Map<AccountCode, RegisteredAccount> accounts;
    private final Optional<CommittedPosting> priorPosting;

    private ReachabilityValidationBook(
        BookIdentity bookIdentity,
        RegisteredAccount candidateAccount,
        RegisteredAccount counterAccount) {
      this.bookIdentity = bookIdentity;
      this.accounts =
          Map.of(
              candidateAccount.accountCode(), candidateAccount,
              counterAccount.accountCode(), counterAccount,
              payableAuxiliaryAccount().accountCode(), payableAuxiliaryAccount());
      this.priorPosting = Optional.empty();
    }

    private ReachabilityValidationBook(
        BookIdentity bookIdentity,
        RegisteredAccount candidateAccount,
        RegisteredAccount counterAccount,
        CommittedPosting priorPosting) {
      this.bookIdentity = bookIdentity;
      this.accounts =
          Map.of(
              candidateAccount.accountCode(), candidateAccount,
              counterAccount.accountCode(), counterAccount,
              payableAuxiliaryAccount().accountCode(), payableAuxiliaryAccount());
      this.priorPosting = Optional.ofNullable(priorPosting);
    }

    @Override
    public BookLifecycleInspection inspectBook() {
      return new BookLifecycleInspection.Initialized(
          1001, 1, 1, PostingRouteReachabilityTestSupport.DECLARED_AT, bookIdentity);
    }

    @Override
    public Optional<RegisteredAccount> findAccount(AccountCode accountCode) {
      return Optional.ofNullable(accounts.get(accountCode));
    }

    @Override
    public Optional<dev.erst.fingrind.contract.tax.DeclaredTaxRegistration> findTaxRegistration(
        dev.erst.fingrind.contract.tax.TaxRegistrationId taxRegistrationId) {
      return Optional.empty();
    }

    @Override
    public Optional<dev.erst.fingrind.executor.spi.StoredRequestPosting> findExistingPosting(
        dev.erst.fingrind.core.IdempotencyKey idempotencyKey) {
      return Optional.empty();
    }

    @Override
    public Optional<CommittedPosting> findPosting(PostingId postingId) {
      return priorPosting.filter(posting -> posting.postingId().equals(postingId));
    }

    @Override
    public Optional<CommittedPosting> findReversalFor(PostingId priorPostingId) {
      return Optional.empty();
    }

    @Override
    public List<CommittedPosting> postings(EffectiveDateRange effectiveDateRange) {
      return priorPosting.stream().toList();
    }

    @Override
    public Optional<LocalDate> earliestPostingEffectiveDate() {
      return priorPosting.map(posting -> posting.journalEntry().effectiveDate());
    }

    @Override
    public Optional<LocalDate> transferredThroughEffectiveDate() {
      return Optional.empty();
    }
  }
}
