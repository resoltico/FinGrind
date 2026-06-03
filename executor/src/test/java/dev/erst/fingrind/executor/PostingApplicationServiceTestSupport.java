package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountRole;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountTaxonomy;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountingEvidence;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.bookIdentity;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.initializedLifecycleInspection;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.registeredAccount;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult;
import dev.erst.fingrind.contract.bookkeeping.PostingLineage;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PostingLineageModel;
import dev.erst.fingrind.executor.bookkeeping.PostingValidationStore;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.PostingCommitResult;
import dev.erst.fingrind.executor.spi.PostingCommitStore;
import dev.erst.fingrind.executor.spi.PostingDraft;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Shared fixtures and seam doubles for split {@link PostingApplicationService} tests. */
final class PostingApplicationServiceTestSupport {
  static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-04-07T10:15:30Z"), ZoneOffset.UTC);

  private PostingApplicationServiceTestSupport() {}

  static InMemoryBookSession initializedBook() {
    InMemoryBookSession bookSession = new InMemoryBookSession();
    bookSession.openBook(FIXED_CLOCK.instant(), bookIdentity(), List.of());
    return bookSession;
  }

  static void declareDefaultAccounts(InMemoryBookSession bookSession) {
    bookSession.declareAccount(
        new AccountCode("1000"),
        new AccountName("Cash"),
        AccountType.ASSET,
        accountRole(AccountType.ASSET, NormalBalance.DEBIT),
        accountTaxonomy(AccountType.ASSET),
        FIXED_CLOCK.instant());
    bookSession.declareAccount(
        new AccountCode("2000"),
        new AccountName("Revenue"),
        AccountType.REVENUE,
        accountRole(AccountType.REVENUE, NormalBalance.CREDIT),
        accountTaxonomy(AccountType.REVENUE),
        FIXED_CLOCK.instant());
  }

  static <T extends PostingValidationStore & PostingCommitStore>
      PostingApplicationService applicationService(T bookSession) {
    return new PostingApplicationService(
        bookSession, bookSession, () -> new PostingId("posting-new"), FIXED_CLOCK);
  }

  static PostEntryCommand command(String idempotencyKey) {
    return new PostEntryCommand(
        new BookkeepingEntry.CashRevenue(
            LocalDate.parse("2026-04-07"),
            new AccountCode("1000"),
            new AccountCode("2000"),
            MonetaryAmount.of(Money.parse("EUR", "10.00"))),
        accountingEvidence(idempotencyKey),
        requestProvenance(idempotencyKey),
        SourceChannel.CLI);
  }

  static PostEntryResult.PreflightRejected preflightRejected(
      IdempotencyKey idempotencyKey, PostingRejection rejection) {
    return new PostEntryResult.PreflightRejected(idempotencyKey, rejection);
  }

  static PostEntryResult.CommitRejected commitRejected(
      IdempotencyKey idempotencyKey, PostingRejection rejection) {
    return new PostEntryResult.CommitRejected(idempotencyKey, rejection);
  }

  static PostEntryCommand command(
      String idempotencyKey,
      Optional<ReversalReference> reversalReference,
      Optional<ReversalReason> reason) {
    return command(idempotencyKey, reversalReference, reason, journalEntry());
  }

  static PostEntryCommand command(
      String idempotencyKey,
      Optional<ReversalReference> reversalReference,
      Optional<ReversalReason> reason,
      JournalEntry journalEntry) {
    BookkeepingEntry entry =
        reversalReference.isPresent()
            ? new BookkeepingEntry.ReversalAdjustment(
                journalEntry,
                new PostingLineage.Reversal(reversalReference.orElseThrow(), reason.orElseThrow()))
            : new BookkeepingEntry.OpenAccountingPosition(
                journalEntry.effectiveDate(), openingBalances(journalEntry));
    return new PostEntryCommand(
        entry,
        accountingEvidence(idempotencyKey),
        requestProvenance(idempotencyKey),
        SourceChannel.CLI);
  }

  private static List<BookkeepingEntry.OpenAccountingPosition.OpeningAccountBalance>
      openingBalances(JournalEntry journalEntry) {
    List<BookkeepingEntry.OpenAccountingPosition.OpeningAccountBalance> balances =
        new ArrayList<>(journalEntry.lines().size());
    for (JournalLine line : journalEntry.lines()) {
      balances.add(
          new BookkeepingEntry.OpenAccountingPosition.OpeningAccountBalance(
              line.accountCode(), line.side(), MonetaryAmount.of(line.amount().money())));
    }
    return List.copyOf(balances);
  }

  static Optional<ReversalReference> reversalReference(String priorPostingId) {
    return Optional.of(new ReversalReference(new PostingId(priorPostingId)));
  }

  static CommittedPosting existingPosting(String postingId, String idempotencyKey) {
    return new CommittedPosting(
        new PostingId(postingId),
        journalEntry(),
        PostingLineageModel.direct(),
        PostingKind.STANDARD,
        dev.erst.fingrind.core.PostingOriginKind.REVERSAL_ADJUSTMENT,
        accountingEvidence(idempotencyKey),
        committedProvenance(idempotencyKey));
  }

  static CommittedProvenance committedProvenance(String idempotencyKey) {
    return new CommittedProvenance(
        requestProvenance(idempotencyKey), FIXED_CLOCK.instant(), SourceChannel.CLI);
  }

  static RequestProvenance requestProvenance(String idempotencyKey) {
    return new RequestProvenance(
        new ActorId("actor-1"),
        ActorType.AGENT,
        new CommandId("command-1"),
        new IdempotencyKey(idempotencyKey),
        new CausationId("cause-1"),
        Optional.of(new CorrelationId("corr-1")));
  }

  static JournalEntry journalEntry() {
    return new JournalEntry(
        LocalDate.parse("2026-04-07"),
        List.of(
            line("1000", JournalLine.EntrySide.DEBIT, "10.00"),
            line("2000", JournalLine.EntrySide.CREDIT, "10.00")));
  }

  static JournalEntry reversalJournalEntry() {
    return new JournalEntry(
        LocalDate.parse("2026-04-07"),
        List.of(
            line("1000", JournalLine.EntrySide.CREDIT, "10.00"),
            line("2000", JournalLine.EntrySide.DEBIT, "10.00")));
  }

  static JournalEntry mismatchedReversalJournalEntry() {
    return new JournalEntry(
        LocalDate.parse("2026-04-07"),
        List.of(
            line("1000", JournalLine.EntrySide.CREDIT, "5.00"),
            line("2000", JournalLine.EntrySide.DEBIT, "5.00")));
  }

  static JournalLine line(String accountCode, JournalLine.EntrySide side, String amount) {
    return new JournalLine(new AccountCode(accountCode), side, Money.parse("EUR", amount));
  }

  static PostingBookSession mappedOutcomeBookSession() {
    return new DelegatingPostingBookSession() {
      @Override
      public BookLifecycleInspection inspectBook() {
        return initializedLifecycleInspection(1001, 1, 1, FIXED_CLOCK.instant());
      }

      @Override
      public Optional<RegisteredAccount> findAccount(AccountCode accountCode) {
        return Optional.of(
            registeredAccount(
                accountCode,
                new AccountName("Synthetic"),
                "1000".equals(accountCode.value()) ? AccountType.ASSET : AccountType.REVENUE,
                "1000".equals(accountCode.value()) ? NormalBalance.DEBIT : NormalBalance.CREDIT,
                true,
                FIXED_CLOCK.instant()));
      }

      @Override
      public Optional<CommittedPosting> findPosting(PostingId postingId) {
        return Optional.of(existingPosting(postingId.value(), "idem-existing"));
      }

      @Override
      public PostingCommitResult commit(
          PostingDraft postingDraft, PostingIdGenerator postingIdGenerator) {
        CommittedPosting postingFact = postingDraft.materialize(postingIdGenerator.nextPostingId());
        String idempotencyKey =
            postingFact.provenance().requestProvenance().idempotencyKey().value();
        return switch (idempotencyKey) {
          case "idem-book-not-initialized" ->
              new PostingCommitResult.Rejected(
                  new BookkeepingPostingRejection.BookNotInitialized());
          case "idem-unknown-account" ->
              new PostingCommitResult.Rejected(
                  new BookkeepingPostingRejection.AccountStateViolations(
                      List.of(
                          new BookkeepingPostingRejection.UnknownAccount(
                              new AccountCode("1000")))));
          case "idem-inactive-account" ->
              new PostingCommitResult.Rejected(
                  new BookkeepingPostingRejection.AccountStateViolations(
                      List.of(
                          new BookkeepingPostingRejection.InactiveAccount(
                              new AccountCode("1000")))));
          case "idem-duplicate" ->
              new PostingCommitResult.Rejected(
                  new BookkeepingPostingRejection.DuplicateIdempotencyKey());
          case "idem-reversal-duplicate" ->
              new PostingCommitResult.Rejected(
                  new BookkeepingPostingRejection.ReversalAlreadyExists(
                      new PostingId("posting-1")));
          default -> throw new AssertionError("Unexpected test idempotency key: " + idempotencyKey);
        };
      }
    };
  }

  /** Minimal book-session stub whose methods fail unless a test overrides them. */
  interface PostingBookSession extends PostingValidationStore, PostingCommitStore {}

  /** Minimal posting-session stub whose methods fail unless a test overrides them. */
  abstract static class DelegatingPostingBookSession implements PostingBookSession {
    @Override
    public BookLifecycleInspection inspectBook() {
      throw new AssertionError("inspectBook should not be called in this test");
    }

    @Override
    public Optional<RegisteredAccount> findAccount(AccountCode accountCode) {
      throw new AssertionError("findAccount should not be called in this test");
    }

    @Override
    public Optional<CommittedPosting> findExistingPosting(IdempotencyKey idempotencyKey) {
      return Optional.empty();
    }

    @Override
    public Optional<CommittedPosting> findPosting(PostingId postingId) {
      return Optional.empty();
    }

    @Override
    public Optional<CommittedPosting> findReversalFor(PostingId priorPostingId) {
      return Optional.empty();
    }

    @Override
    public List<CommittedPosting> postings(EffectiveDateRange effectiveDateRange) {
      return List.of();
    }

    @Override
    public Optional<LocalDate> earliestPostingEffectiveDate() {
      return Optional.empty();
    }

    @Override
    public Optional<LocalDate> transferredThroughEffectiveDate() {
      return Optional.empty();
    }

    @Override
    public PostingCommitResult commit(
        PostingDraft postingDraft, PostingIdGenerator postingIdGenerator) {
      throw new AssertionError("commit should not be called in this test");
    }
  }
}
