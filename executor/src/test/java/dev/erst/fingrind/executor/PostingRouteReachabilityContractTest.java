package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.initializedLifecycleInspection;
import static dev.erst.fingrind.testsupport.PostingRouteReachabilityScenarioFactory.directJournalCommand;
import static dev.erst.fingrind.testsupport.PostingRouteReachabilityScenarioFactory.openingPositionCommand;
import static dev.erst.fingrind.testsupport.PostingRouteReachabilityScenarioFactory.priorPosting;
import static dev.erst.fingrind.testsupport.PostingRouteReachabilityScenarioFactory.reversalCommand;
import static dev.erst.fingrind.testsupport.PostingRouteReachabilityTestSupport.candidateAccount;
import static dev.erst.fingrind.testsupport.PostingRouteReachabilityTestSupport.cellToken;
import static dev.erst.fingrind.testsupport.PostingRouteReachabilityTestSupport.counterAssetAccount;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.protocol.RequestSurfaceFacts;
import dev.erst.fingrind.core.AccountCode;
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
        new ReachabilityValidationBook(candidateAccount(cell), counterAssetAccount());
    PostEntryCommand command = openingPositionCommand("opening-" + cellToken(cell));

    Optional<BookkeepingPostingRejection> rejection =
        POSTING_ACCEPTANCE.rejectionFor(
            PostEntryCommandTranslator.toPostingCommand(command, book), book);

    if (cell.openingReachable()) {
      assertEquals(Optional.empty(), rejection, "opening route should accept " + cell);
      return;
    }
    BookkeepingPostingRejection.OpeningPositionTouchesNominalAccount nominalRejection =
        assertInstanceOf(
            BookkeepingPostingRejection.OpeningPositionTouchesNominalAccount.class,
            rejection.orElseThrow(),
            "opening route should reject non-opening cell " + cell);
    assertEquals(
        PostingRouteReachabilityTestSupport.CANDIDATE_ACCOUNT_CODE.value(),
        nominalRejection.accountCode().value());
    assertEquals(cell.accountType(), nominalRejection.accountType());
  }

  @Override
  public void assertDirectJournalReachability(RequestSurfaceFacts.ReachabilityCellFacts cell) {
    ReachabilityValidationBook book =
        new ReachabilityValidationBook(candidateAccount(cell), counterAssetAccount());
    PostEntryCommand command = directJournalCommand("journal-" + cellToken(cell));

    assertTrue(
        ENTRY_SEMANTICS.rejectionFor(command, book).isEmpty(),
        "entry semantics should accept " + cell);
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
            new PostingId("posting-" + cellToken(cell).toLowerCase(java.util.Locale.ROOT)));
    ReachabilityValidationBook book =
        new ReachabilityValidationBook(candidateAccount(cell), counterAssetAccount(), priorPosting);
    PostEntryCommand command =
        reversalCommand("reversal-" + cellToken(cell), priorPosting.postingId());

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
    private final Map<AccountCode, RegisteredAccount> accounts;
    private final Optional<CommittedPosting> priorPosting;

    private ReachabilityValidationBook(
        RegisteredAccount candidateAccount, RegisteredAccount counterAccount) {
      this.accounts =
          Map.of(
              candidateAccount.accountCode(), candidateAccount,
              counterAccount.accountCode(), counterAccount);
      this.priorPosting = Optional.empty();
    }

    private ReachabilityValidationBook(
        RegisteredAccount candidateAccount,
        RegisteredAccount counterAccount,
        CommittedPosting priorPosting) {
      this.accounts =
          Map.of(
              candidateAccount.accountCode(), candidateAccount,
              counterAccount.accountCode(), counterAccount);
      this.priorPosting = Optional.ofNullable(priorPosting);
    }

    @Override
    public BookLifecycleInspection inspectBook() {
      return initializedLifecycleInspection(
          1001, 1, 1, PostingRouteReachabilityTestSupport.DECLARED_AT);
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
