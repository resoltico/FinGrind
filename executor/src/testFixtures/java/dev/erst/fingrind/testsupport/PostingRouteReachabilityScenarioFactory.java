package dev.erst.fingrind.testsupport;

import static dev.erst.fingrind.testsupport.PostingRouteReachabilityProvenanceFixtures.committedProvenance;
import static dev.erst.fingrind.testsupport.PostingRouteReachabilityProvenanceFixtures.generatedEvidence;
import static dev.erst.fingrind.testsupport.PostingRouteReachabilityProvenanceFixtures.requestProvenance;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.protocol.RequestSurfaceFacts;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.PostingOriginKind;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PostingLineageModel;

/** Shared command and persisted-posting fixtures for posting-route reachability contracts. */
public final class PostingRouteReachabilityScenarioFactory {
  private PostingRouteReachabilityScenarioFactory() {}

  /** Builds an opening-balance command for one reachability cell. */
  public static PostEntryCommand openingPositionCommand(String token) {
    return new PostEntryCommand(
        new BookkeepingEntry.OpeningPosition(
            PostingRouteReachabilityTestSupport.EFFECTIVE_DATE,
            java.util.List.of(
                new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                    PostingRouteReachabilityTestSupport.CANDIDATE_ACCOUNT_CODE,
                    JournalLine.EntrySide.DEBIT,
                    MonetaryAmount.of(Money.parse("EUR", "10.00"))),
                new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                    PostingRouteReachabilityTestSupport.COUNTER_ACCOUNT_CODE,
                    JournalLine.EntrySide.CREDIT,
                    MonetaryAmount.of(Money.parse("EUR", "10.00"))))),
        generatedEvidence(token, "opening-balance"),
        requestProvenance(token),
        SourceChannel.CLI);
  }

  /** Builds a direct raw journal command for one reachability cell. */
  public static PostEntryCommand directJournalCommand(String token) {
    return new PostEntryCommand(
        new BookkeepingEntry.DirectJournal(
            new JournalEntry(
                PostingRouteReachabilityTestSupport.EFFECTIVE_DATE,
                java.util.List.of(
                    new JournalLine(
                        PostingRouteReachabilityTestSupport.CANDIDATE_ACCOUNT_CODE,
                        JournalLine.EntrySide.DEBIT,
                        Money.parse("EUR", "10.00")),
                    new JournalLine(
                        PostingRouteReachabilityTestSupport.COUNTER_ACCOUNT_CODE,
                        JournalLine.EntrySide.CREDIT,
                        Money.parse("EUR", "10.00")))),
            null),
        generatedEvidence(token, "operator-note"),
        requestProvenance(token),
        SourceChannel.CLI);
  }

  /** Builds a reversal command that targets the supplied prior posting. */
  public static PostEntryCommand reversalCommand(String token, PostingId priorPostingId) {
    return new PostEntryCommand(
        new BookkeepingEntry.Reversal(
            new JournalEntry(
                PostingRouteReachabilityTestSupport.EFFECTIVE_DATE,
                java.util.List.of(
                    new JournalLine(
                        PostingRouteReachabilityTestSupport.CANDIDATE_ACCOUNT_CODE,
                        JournalLine.EntrySide.CREDIT,
                        Money.parse("EUR", "10.00")),
                    new JournalLine(
                        PostingRouteReachabilityTestSupport.COUNTER_ACCOUNT_CODE,
                        JournalLine.EntrySide.DEBIT,
                        Money.parse("EUR", "10.00")))),
            new dev.erst.fingrind.contract.bookkeeping.PostingLineage.Reversal(
                new ReversalReference(priorPostingId), new ReversalReason("full reversal")),
            null),
        generatedEvidence(token, "operator-annotation"),
        requestProvenance(token),
        SourceChannel.CLI);
  }

  /** Chooses the canonical prior-posting route required to make a reversal scenario valid. */
  public static PostEntryCommand priorPostingCommandForReversal(
      RequestSurfaceFacts.ReachabilityCellFacts cell, String token) {
    return cell.operationalJournalReachable()
        ? directJournalCommand(token)
        : openingPositionCommand(token);
  }

  /** Materializes the committed prior posting used by reversal reachability scenarios. */
  public static CommittedPosting priorPosting(
      RequestSurfaceFacts.ReachabilityCellFacts cell, String token, PostingId postingId) {
    PostEntryCommand seedCommand = priorPostingCommandForReversal(cell, token);
    PostingOriginKind postingOriginKind =
        switch (seedCommand.entry().entryKind()) {
          case DIRECT_JOURNAL -> PostingOriginKind.DIRECT_JOURNAL;
          case OPENING_POSITION -> PostingOriginKind.OPENING_POSITION;
          default ->
              throw new IllegalStateException(
                  "Unexpected prior-posting entry kind: " + seedCommand.entry().entryKind());
        };
    return new CommittedPosting(
        postingId,
        journalEntry(seedCommand.entry()),
        PostingLineageModel.direct(),
        postingOriginKind == PostingOriginKind.OPENING_POSITION
            ? PostingKind.OPENING_BALANCE
            : PostingKind.STANDARD,
        postingOriginKind,
        generatedEvidence(token, sourceDocumentType(seedCommand.entry())),
        committedProvenance(token));
  }

  private static JournalEntry journalEntry(BookkeepingEntry entry) {
    return switch (entry) {
      case BookkeepingEntry.DirectJournal journal -> journal.journalEntry();
      case BookkeepingEntry.Sale sale -> sale.journalEntry();
      case BookkeepingEntry.Expense expense -> expense.journalEntry();
      case BookkeepingEntry.OwnerContribution ownerContribution -> ownerContribution.journalEntry();
      case BookkeepingEntry.OwnerWithdrawal ownerWithdrawal -> ownerWithdrawal.journalEntry();
      case BookkeepingEntry.OpeningPosition openingPosition -> openingPosition.journalEntry();
      case BookkeepingEntry.Reversal reversal -> reversal.journalEntry();
    };
  }

  private static String sourceDocumentType(BookkeepingEntry entry) {
    return switch (entry.entryKind()) {
      case DIRECT_JOURNAL -> "operator-note";
      case SALE -> "cash-receipt";
      case EXPENSE -> "expense-receipt";
      case OWNER_CONTRIBUTION -> "owner-contribution";
      case OWNER_WITHDRAWAL -> "owner-withdrawal";
      case OPENING_POSITION -> "opening-balance";
      case REVERSAL -> "operator-annotation";
    };
  }
}
