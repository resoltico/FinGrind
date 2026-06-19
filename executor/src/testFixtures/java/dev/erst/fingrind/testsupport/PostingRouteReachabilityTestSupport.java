package dev.erst.fingrind.testsupport;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.RequestSurfaceFacts;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.ContentSha256;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.PostingOriginKind;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.core.SourceDocumentId;
import dev.erst.fingrind.core.SourceDocumentReference;
import dev.erst.fingrind.core.SourceDocumentType;
import dev.erst.fingrind.core.StorageLocator;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PostingLineageModel;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Shared reachability scenarios reused by in-memory and SQLite-backed reachability contracts. */
public final class PostingRouteReachabilityTestSupport {
  public static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-04-07T10:15:30Z"), ZoneOffset.UTC);
  public static final LocalDate EFFECTIVE_DATE = LocalDate.parse("2026-04-07");
  public static final Instant DECLARED_AT = Instant.parse("2026-04-07T10:15:30Z");
  public static final AccountCode COUNTER_ACCOUNT_CODE = new AccountCode("1000");
  public static final AccountCode CANDIDATE_ACCOUNT_CODE = new AccountCode("9000");

  private PostingRouteReachabilityTestSupport() {}

  /** Returns the published reachability matrix that every executable contract must honor. */
  public static List<RequestSurfaceFacts.ReachabilityCellFacts> reachabilityMatrix() {
    return ProtocolCatalog.domain().requestSurface().reachabilityMatrix();
  }

  /** Produces a stable scenario token for one matrix cell. */
  public static String cellToken(RequestSurfaceFacts.ReachabilityCellFacts cell) {
    return cell.classificationFamily()
        + "-"
        + cell.accountType().wireValue().toLowerCase(Locale.ROOT)
        + "-"
        + cell.classification().toLowerCase(Locale.ROOT);
  }

  /** Builds an opening-balance command for one reachability cell. */
  public static PostEntryCommand openAccountingPositionCommand(String token) {
    return new PostEntryCommand(
        new BookkeepingEntry.OpenAccountingPosition(
            EFFECTIVE_DATE,
            List.of(
                new BookkeepingEntry.OpenAccountingPosition.OpeningAccountBalance(
                    CANDIDATE_ACCOUNT_CODE,
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

  /** Builds a direct raw journal command for one reachability cell. */
  public static PostEntryCommand directJournalCommand(String token) {
    return new PostEntryCommand(
        new BookkeepingEntry.Journal(
            new JournalEntry(
                EFFECTIVE_DATE,
                List.of(
                    new JournalLine(
                        CANDIDATE_ACCOUNT_CODE,
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

  /** Builds a reversal command that targets the supplied prior posting. */
  public static PostEntryCommand reversalCommand(String token, PostingId priorPostingId) {
    return new PostEntryCommand(
        new BookkeepingEntry.ReversalAdjustment(
            new JournalEntry(
                EFFECTIVE_DATE,
                List.of(
                    new JournalLine(
                        CANDIDATE_ACCOUNT_CODE,
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

  /** Chooses the canonical prior-posting route required to make a reversal scenario valid. */
  public static PostEntryCommand priorPostingCommandForReversal(
      RequestSurfaceFacts.ReachabilityCellFacts cell, String token) {
    return cell.operationalJournalReachable()
        ? directJournalCommand(token)
        : openAccountingPositionCommand(token);
  }

  /** Materializes the committed prior posting used by reversal reachability scenarios. */
  public static CommittedPosting priorPosting(
      RequestSurfaceFacts.ReachabilityCellFacts cell, String token, PostingId postingId) {
    PostEntryCommand seedCommand = priorPostingCommandForReversal(cell, token);
    PostingOriginKind postingOriginKind =
        seedCommand.entry().entryKind() == dev.erst.fingrind.core.BookkeepingEntryKind.JOURNAL
            ? PostingOriginKind.JOURNAL
            : PostingOriginKind.OPEN_ACCOUNTING_POSITION;
    PostingKind postingKind =
        postingOriginKind == PostingOriginKind.OPEN_ACCOUNTING_POSITION
            ? PostingKind.OPENING_BALANCE
            : PostingKind.STANDARD;
    return new CommittedPosting(
        postingId,
        journalEntry(seedCommand.entry()),
        PostingLineageModel.direct(),
        postingKind,
        postingOriginKind,
        generatedEvidence(token, sourceDocumentType(seedCommand.entry())),
        committedProvenance(token));
  }

  /** Declares the scenario's candidate account using the matrix cell taxonomy. */
  public static RegisteredAccount candidateAccount(RequestSurfaceFacts.ReachabilityCellFacts cell) {
    return new RegisteredAccount(
        CANDIDATE_ACCOUNT_CODE,
        new AccountName("Candidate"),
        cell.accountType(),
        AccountRole.ORDINARY,
        taxonomy(cell),
        true,
        DECLARED_AT);
  }

  /** Declares the balancing asset account shared by every reachability scenario. */
  public static RegisteredAccount counterAssetAccount() {
    return new RegisteredAccount(
        COUNTER_ACCOUNT_CODE,
        new AccountName("Cash"),
        dev.erst.fingrind.core.AccountType.ASSET,
        AccountRole.ORDINARY,
        new AccountTaxonomy(
            dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
            Optional.empty(),
            Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
            Optional.empty()),
        true,
        DECLARED_AT);
  }

  /** Translates one matrix cell into the corresponding executable account taxonomy. */
  public static AccountTaxonomy taxonomy(RequestSurfaceFacts.ReachabilityCellFacts cell) {
    return switch (cell.classificationFamily()) {
      case "financial-position" ->
          new AccountTaxonomy(
              dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
              Optional.empty(),
              Optional.of(FinancialPositionLineClassification.fromWireValue(cell.classification())),
              Optional.empty());
      case "profit-and-loss" ->
          new AccountTaxonomy(
              dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
              Optional.empty(),
              Optional.empty(),
              Optional.of(ProfitAndLossLineClassification.fromWireValue(cell.classification())));
      default ->
          throw new IllegalArgumentException(
              "Unsupported classification family " + cell.classificationFamily());
    };
  }

  /** Builds the operator provenance used by shared reachability scenarios. */
  public static RequestProvenance requestProvenance(String token) {
    return new RequestProvenance(
        new ActorId("actor-" + token),
        ActorType.AGENT,
        new CommandId("command-" + token),
        new IdempotencyKey("idem-" + token),
        new CausationId("cause-" + token),
        Optional.of(new CorrelationId("corr-" + token)));
  }

  /** Builds the evidence bundle that matches the command category for a scenario. */
  public static dev.erst.fingrind.core.AccountingEvidence generatedEvidence(
      String token, String sourceDocumentType) {
    return new dev.erst.fingrind.core.AccountingEvidence(
        List.of(
            new SourceDocumentReference(
                new SourceDocumentId("document-" + token),
                new SourceDocumentType(sourceDocumentType),
                EFFECTIVE_DATE,
                DECLARED_AT,
                new StorageLocator("vault://fixtures/" + token),
                new ContentSha256(
                    "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"))),
        List.of());
  }

  /** Builds committed provenance for fixtures that need a persisted posting. */
  public static CommittedProvenance committedProvenance(String token) {
    return new CommittedProvenance(
        requestProvenance(token), FIXED_CLOCK.instant(), SourceChannel.CLI);
  }

  private static JournalEntry journalEntry(BookkeepingEntry entry) {
    return switch (entry) {
      case BookkeepingEntry.Journal journal -> journal.journalEntry();
      case BookkeepingEntry.OpenAccountingPosition openingPosition ->
          new JournalEntry(
              openingPosition.effectiveDate(),
              openingPosition.lines().stream()
                  .map(
                      line ->
                          new JournalLine(line.accountCode(), line.side(), line.amount().money()))
                  .toList());
      case BookkeepingEntry.ReversalAdjustment reversal -> reversal.journalEntry();
    };
  }

  private static String sourceDocumentType(BookkeepingEntry entry) {
    return switch (entry.entryKind()) {
      case JOURNAL -> "operator-note";
      case OPEN_ACCOUNTING_POSITION -> "opening-balance";
      case REVERSAL_ADJUSTMENT -> "operator-annotation";
    };
  }
}
