package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.financialPositionTaxonomy;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.generatedEvidence;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.initializedLifecycleInspection;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.profitAndLossTaxonomy;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.registeredAccount;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.committedProvenance;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.requestProvenance;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.RequestSurfaceFacts;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.PostingOriginKind;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PostingAcceptancePolicy;
import dev.erst.fingrind.executor.bookkeeping.PostingLineageModel;
import dev.erst.fingrind.executor.bookkeeping.PostingValidationStore;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Verifies that published classification reachability matches the live posting routes. */
class PostingRouteReachabilityContractTest {
  private static final PostEntrySemanticsPolicy ENTRY_SEMANTICS =
      PostEntrySemanticsPolicy.currentKernel();
  private static final PostingAcceptancePolicy POSTING_ACCEPTANCE =
      PostingAcceptancePolicy.currentKernel();
  private static final LocalDate EFFECTIVE_DATE = LocalDate.parse("2026-04-07");
  private static final Instant DECLARED_AT = Instant.parse("2026-04-07T10:15:30Z");
  private static final AccountCode COUNTER_ACCOUNT_CODE = new AccountCode("1000");

  @Test
  void openingPositionReachabilityMatchesThePublishedMatrix() {
    for (RequestSurfaceFacts.ReachabilityCellFacts cell : reachabilityMatrix()) {
      assertOpeningPositionReachability(cell);
    }
  }

  @Test
  void directJournalReachabilityMatchesThePublishedMatrix() {
    for (RequestSurfaceFacts.ReachabilityCellFacts cell : reachabilityMatrix()) {
      assertDirectJournalReachability(cell);
    }
  }

  @Test
  void reversalReachabilityMatchesThePublishedMatrix() {
    for (RequestSurfaceFacts.ReachabilityCellFacts cell : reachabilityMatrix()) {
      assertReversalReachability(cell);
    }
  }

  private static List<RequestSurfaceFacts.ReachabilityCellFacts> reachabilityMatrix() {
    return ProtocolCatalog.domain().requestSurface().reachabilityMatrix();
  }

  private static void assertOpeningPositionReachability(
      RequestSurfaceFacts.ReachabilityCellFacts cell) {
    ReachabilityValidationBook book =
        new ReachabilityValidationBook(candidateAccount(cell), counterAssetAccount());
    PostEntryCommand command = openAccountingPositionCommand("opening-" + cell.classification());

    Optional<BookkeepingPostingRejection> rejection =
        POSTING_ACCEPTANCE.rejectionFor(PostEntryCommandTranslator.toPostingCommand(command), book);

    if (cell.openingReachable()) {
      assertEquals(Optional.empty(), rejection, "opening route should accept " + cell);
      return;
    }
    BookkeepingPostingRejection.OpenAccountingPositionTouchesNominalAccount nominalRejection =
        assertInstanceOf(
            BookkeepingPostingRejection.OpenAccountingPositionTouchesNominalAccount.class,
            rejection.orElseThrow(),
            "opening route should reject non-opening cell " + cell);
    assertEquals(candidateAccountCode().value(), nominalRejection.accountCode().value());
    assertEquals(cell.accountType(), nominalRejection.accountType());
  }

  private static void assertDirectJournalReachability(
      RequestSurfaceFacts.ReachabilityCellFacts cell) {
    ReachabilityValidationBook book =
        new ReachabilityValidationBook(candidateAccount(cell), counterAssetAccount());
    PostEntryCommand command = directJournalCommand("journal-" + cell.classification());

    assertTrue(
        ENTRY_SEMANTICS.rejectionFor(command, book).isEmpty(),
        "entry semantics should accept " + cell);
    Optional<BookkeepingPostingRejection> rejection =
        POSTING_ACCEPTANCE.rejectionFor(PostEntryCommandTranslator.toPostingCommand(command), book);

    if (cell.operationalJournalReachable()) {
      assertEquals(Optional.empty(), rejection, "direct journal route should accept " + cell);
      return;
    }
    BookkeepingPostingRejection.ResultHoldingAccountReserved reservedRejection =
        assertInstanceOf(
            BookkeepingPostingRejection.ResultHoldingAccountReserved.class,
            rejection.orElseThrow(),
            "direct journal route should reject reserved cell " + cell);
    assertEquals(candidateAccountCode(), reservedRejection.accountCode());
  }

  private static void assertReversalReachability(RequestSurfaceFacts.ReachabilityCellFacts cell) {
    CommittedPosting priorPosting = priorPosting(cell, "prior-" + cell.classification());
    ReachabilityValidationBook book =
        new ReachabilityValidationBook(candidateAccount(cell), counterAssetAccount(), priorPosting);
    PostEntryCommand command =
        reversalCommand("reversal-" + cell.classification(), priorPosting.postingId());

    assertTrue(
        ENTRY_SEMANTICS.rejectionFor(command, book).isEmpty(),
        "reversal semantics should accept " + cell);
    Optional<BookkeepingPostingRejection> rejection =
        POSTING_ACCEPTANCE.rejectionFor(PostEntryCommandTranslator.toPostingCommand(command), book);

    if (cell.reversalReachable()) {
      assertEquals(Optional.empty(), rejection, "reversal route should accept " + cell);
      return;
    }
    BookkeepingPostingRejection.ResultHoldingAccountReserved reservedRejection =
        assertInstanceOf(
            BookkeepingPostingRejection.ResultHoldingAccountReserved.class,
            rejection.orElseThrow(),
            "reversal route should reject reserved cell " + cell);
    assertEquals(candidateAccountCode(), reservedRejection.accountCode());
  }

  private static PostEntryCommand openAccountingPositionCommand(String token) {
    return new PostEntryCommand(
        new BookkeepingEntry.OpenAccountingPosition(
            EFFECTIVE_DATE,
            List.of(
                new BookkeepingEntry.OpenAccountingPosition.OpeningAccountBalance(
                    candidateAccountCode(),
                    JournalLine.EntrySide.DEBIT,
                    MonetaryAmount.of(Money.parse("EUR", "10.00"))),
                new BookkeepingEntry.OpenAccountingPosition.OpeningAccountBalance(
                    COUNTER_ACCOUNT_CODE,
                    JournalLine.EntrySide.CREDIT,
                    MonetaryAmount.of(Money.parse("EUR", "10.00"))))),
        generatedEvidence(token, "opening-balance"),
        requestProvenance(token),
        SourceChannel.CLI);
  }

  private static PostEntryCommand directJournalCommand(String token) {
    return new PostEntryCommand(
        new BookkeepingEntry.Journal(
            new JournalEntry(
                EFFECTIVE_DATE,
                List.of(
                    new JournalLine(
                        candidateAccountCode(),
                        JournalLine.EntrySide.DEBIT,
                        Money.parse("EUR", "10.00")),
                    new JournalLine(
                        COUNTER_ACCOUNT_CODE,
                        JournalLine.EntrySide.CREDIT,
                        Money.parse("EUR", "10.00")))),
            null),
        generatedEvidence(token, "operator-note"),
        requestProvenance(token),
        SourceChannel.CLI);
  }

  private static PostEntryCommand reversalCommand(String token, PostingId priorPostingId) {
    return new PostEntryCommand(
        new BookkeepingEntry.ReversalAdjustment(
            new JournalEntry(
                EFFECTIVE_DATE,
                List.of(
                    new JournalLine(
                        candidateAccountCode(),
                        JournalLine.EntrySide.CREDIT,
                        Money.parse("EUR", "10.00")),
                    new JournalLine(
                        COUNTER_ACCOUNT_CODE,
                        JournalLine.EntrySide.DEBIT,
                        Money.parse("EUR", "10.00")))),
            new dev.erst.fingrind.contract.bookkeeping.PostingLineage.Reversal(
                new ReversalReference(priorPostingId), new ReversalReason("full reversal"))),
        generatedEvidence(token, "operator-annotation"),
        requestProvenance(token),
        SourceChannel.CLI);
  }

  private static CommittedPosting priorPosting(
      RequestSurfaceFacts.ReachabilityCellFacts cell, String token) {
    return new CommittedPosting(
        new PostingId("posting-" + cell.classification().toLowerCase(Locale.ROOT)),
        new JournalEntry(
            EFFECTIVE_DATE,
            List.of(
                new JournalLine(
                    candidateAccountCode(),
                    JournalLine.EntrySide.DEBIT,
                    Money.parse("EUR", "10.00")),
                new JournalLine(
                    COUNTER_ACCOUNT_CODE,
                    JournalLine.EntrySide.CREDIT,
                    Money.parse("EUR", "10.00")))),
        PostingLineageModel.direct(),
        PostingKind.OPENING_BALANCE,
        PostingOriginKind.OPEN_ACCOUNTING_POSITION,
        generatedEvidence(token, "opening-balance"),
        committedProvenance(token));
  }

  private static RegisteredAccount candidateAccount(
      RequestSurfaceFacts.ReachabilityCellFacts cell) {
    return registeredAccount(
        candidateAccountCode(),
        new AccountName("Candidate"),
        cell.accountType(),
        AccountRole.ORDINARY,
        taxonomy(cell),
        true,
        DECLARED_AT);
  }

  private static RegisteredAccount counterAssetAccount() {
    return registeredAccount(
        COUNTER_ACCOUNT_CODE,
        new AccountName("Cash"),
        AccountType.ASSET,
        AccountRole.ORDINARY,
        financialPositionTaxonomy(FinancialPositionLineClassification.CURRENT_ASSET),
        true,
        DECLARED_AT);
  }

  private static AccountTaxonomy taxonomy(RequestSurfaceFacts.ReachabilityCellFacts cell) {
    return switch (cell.classificationFamily()) {
      case "financial-position" ->
          financialPositionTaxonomy(
              FinancialPositionLineClassification.fromWireValue(cell.classification()));
      case "profit-and-loss" ->
          profitAndLossTaxonomy(
              ProfitAndLossLineClassification.fromWireValue(cell.classification()));
      default ->
          throw new IllegalArgumentException(
              "Unsupported classification family " + cell.classificationFamily());
    };
  }

  private static AccountCode candidateAccountCode() {
    return new AccountCode("2000");
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
      return initializedLifecycleInspection(1001, 1, 1, DECLARED_AT);
    }

    @Override
    public Optional<RegisteredAccount> findAccount(AccountCode accountCode) {
      return Optional.ofNullable(accounts.get(accountCode));
    }

    @Override
    public Optional<CommittedPosting> findExistingPosting(
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
