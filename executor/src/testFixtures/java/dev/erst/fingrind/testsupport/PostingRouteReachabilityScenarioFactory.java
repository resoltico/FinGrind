package dev.erst.fingrind.testsupport;

import static dev.erst.fingrind.testsupport.PostingRouteReachabilityProvenanceFixtures.committedProvenance;
import static dev.erst.fingrind.testsupport.PostingRouteReachabilityProvenanceFixtures.generatedEvidence;
import static dev.erst.fingrind.testsupport.PostingRouteReachabilityProvenanceFixtures.requestProvenance;
import static dev.erst.fingrind.testsupport.PostingRouteReachabilityTestSupport.CANDIDATE_ACCOUNT_CODE;
import static dev.erst.fingrind.testsupport.PostingRouteReachabilityTestSupport.EFFECTIVE_DATE;
import static dev.erst.fingrind.testsupport.PostingRouteReachabilityTestSupport.PAYABLE_ACCOUNT_CODE;
import static dev.erst.fingrind.testsupport.PostingRouteReachabilityTestSupport.isInventoryCell;
import static dev.erst.fingrind.testsupport.PostingRouteReachabilityTestSupport.taxonomy;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.bookkeeping.QuantityText;
import dev.erst.fingrind.contract.bookkeeping.ResolvedInventoryAcquisition;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.RequestSurfaceFacts;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.Quantity;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PostingLineageModel;

/** Shared command and persisted-posting fixtures for posting-route reachability contracts. */
public final class PostingRouteReachabilityScenarioFactory {
  private PostingRouteReachabilityScenarioFactory() {}

  /** Builds an opening-balance command for one reachability cell. */
  public static PostEntryCommand openingPositionCommand(
      RequestSurfaceFacts.ReachabilityCellFacts cell, String token) {
    return new PostEntryCommand(
        new BookkeepingEntry.OpeningPosition(
            PostingRouteReachabilityTestSupport.EFFECTIVE_DATE,
            java.util.List.of(
                new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                    PostingRouteReachabilityTestSupport.CANDIDATE_ACCOUNT_CODE,
                    JournalLine.EntrySide.DEBIT,
                    MonetaryAmount.of(Money.parse("EUR", "10.00")),
                    isInventoryCell(cell) ? new QuantityText("1") : null),
                new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                    PostingRouteReachabilityTestSupport.COUNTER_ACCOUNT_CODE,
                    JournalLine.EntrySide.CREDIT,
                    MonetaryAmount.of(Money.parse("EUR", "10.00")),
                    null))),
        generatedEvidence(token, "opening-balance"),
        requestProvenance(token),
        SourceChannel.CLI);
  }

  /** Builds a direct raw journal command for one reachability cell. */
  public static PostEntryCommand directJournalCommand(
      RequestSurfaceFacts.ReachabilityCellFacts cell, String token) {
    JournalLine.EntrySide candidateSide = candidateSideForOperationalAdjustment(cell);
    JournalLine.EntrySide counterSide =
        candidateSide == JournalLine.EntrySide.DEBIT
            ? JournalLine.EntrySide.CREDIT
            : JournalLine.EntrySide.DEBIT;
    return new PostEntryCommand(
        new BookkeepingEntry.DirectJournal(
            new JournalEntry(
                PostingRouteReachabilityTestSupport.EFFECTIVE_DATE,
                java.util.List.of(
                    new JournalLine(
                        PostingRouteReachabilityTestSupport.CANDIDATE_ACCOUNT_CODE,
                        candidateSide,
                        Money.parse("EUR", "10.00")),
                    new JournalLine(
                        PostingRouteReachabilityTestSupport.COUNTER_ACCOUNT_CODE,
                        counterSide,
                        Money.parse("EUR", "10.00")))),
            null),
        generatedEvidence(token, "operator-note"),
        requestProvenance(token),
        SourceChannel.CLI);
  }

  /** Builds a reversal command that targets the supplied prior posting. */
  public static PostEntryCommand reversalCommand(
      RequestSurfaceFacts.ReachabilityCellFacts cell, String token, PostingId priorPostingId) {
    return new PostEntryCommand(
        new BookkeepingEntry.Reversal(
            PostingRouteReachabilityTestSupport.EFFECTIVE_DATE,
            new dev.erst.fingrind.contract.bookkeeping.PostingLineage.Reversal(
                new ReversalReference(priorPostingId), new ReversalReason("full reversal")),
            null,
            null),
        generatedEvidence(token, "operator-annotation"),
        requestProvenance(token),
        SourceChannel.CLI);
  }

  /** Chooses the canonical prior-posting route required to make a reversal scenario valid. */
  public static PostEntryCommand priorPostingCommandForReversal(
      RequestSurfaceFacts.ReachabilityCellFacts cell, String token) {
    if (isInventoryCell(cell)) {
      return inventoryPurchaseOnCreditCommand(token);
    }
    return cell.operationalJournalReachable()
        ? directJournalCommand(cell, token)
        : openingPositionCommand(cell, token);
  }

  /** Materializes the committed prior posting used by reversal reachability scenarios. */
  public static CommittedPosting priorPosting(
      RequestSurfaceFacts.ReachabilityCellFacts cell, String token, PostingId postingId) {
    PostEntryCommand seedCommand = priorPostingCommandForReversal(cell, token);
    BookkeepingEntry callerEntry = seedCommand.entry();
    BookkeepingEntry resolvedEntry = resolvedPriorPostingEntry(callerEntry);
    return new CommittedPosting(
        postingId,
        journalEntry(resolvedEntry),
        PostingLineageModel.direct(),
        resolvedEntry.postingKind(),
        resolvedEntry.postingOriginKind(),
        generatedEvidence(token, sourceDocumentType(callerEntry)),
        committedProvenance(token),
        callerEntry,
        resolvedEntry);
  }

  private static PostEntryCommand inventoryPurchaseOnCreditCommand(String token) {
    return new PostEntryCommand(
        new BookkeepingEntry.PurchaseOnCredit(
            EFFECTIVE_DATE,
            CANDIDATE_ACCOUNT_CODE,
            PAYABLE_ACCOUNT_CODE,
            new dev.erst.fingrind.contract.bookkeeping.QuantityText("1"),
            MonetaryAmount.of(Money.parse("EUR", "10.00")),
            null,
            null,
            null,
            null),
        generatedEvidence(token, "supplier-invoice"),
        requestProvenance(token),
        SourceChannel.CLI);
  }

  private static BookkeepingEntry resolvedPriorPostingEntry(BookkeepingEntry callerEntry) {
    return switch (callerEntry) {
      case BookkeepingEntry.PurchaseSettled purchase ->
          new BookkeepingEntry.PurchaseSettled(
              purchase.effectiveDate(),
              purchase.inventoryAccountCode(),
              purchase.cashAccountCode(),
              purchase.quantity(),
              purchase.unitCost(),
              resolvedInventoryAcquisition(),
              purchase.foreignExchangeDetails(),
              purchase.taxSelection(),
              purchase.appliedTax());
      case BookkeepingEntry.PurchaseOnCredit purchase ->
          new BookkeepingEntry.PurchaseOnCredit(
              purchase.effectiveDate(),
              purchase.inventoryAccountCode(),
              purchase.payableAccountCode(),
              purchase.quantity(),
              purchase.unitCost(),
              resolvedInventoryAcquisition(),
              null,
              purchase.taxSelection(),
              purchase.appliedTax());
      default -> callerEntry;
    };
  }

  private static ResolvedInventoryAcquisition resolvedInventoryAcquisition() {
    return new ResolvedInventoryAcquisition(
        Quantity.ofScaledUnits(0, 1),
        MonetaryAmount.of(Money.parse("EUR", "10.00")),
        MonetaryAmount.of(Money.parse("EUR", "10.00")));
  }

  private static JournalEntry journalEntry(BookkeepingEntry entry) {
    return entry.journalEntry();
  }

  private static String sourceDocumentType(BookkeepingEntry entry) {
    return ProtocolCatalog.domain()
        .requestSurface()
        .bookkeepingEntryKind(entry.entryKind())
        .sourceDocumentTypes()
        .scaffoldValue();
  }

  private static JournalLine.EntrySide candidateSideForOperationalAdjustment(
      RequestSurfaceFacts.ReachabilityCellFacts cell) {
    return switch (AccountRole.from(cell.accountType(), taxonomy(cell))) {
      case PAYABLE, EXPENSE, EQUITY_DRAWS -> JournalLine.EntrySide.CREDIT;
      case AUX, CASH, INVENTORY, RECEIVABLE, REVENUE, EQUITY_CONTRIBUTED, SETTLEMENT_ADJUNCT ->
          JournalLine.EntrySide.DEBIT;
    };
  }
}
