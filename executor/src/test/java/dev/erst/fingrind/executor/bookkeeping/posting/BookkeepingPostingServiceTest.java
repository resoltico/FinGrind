package dev.erst.fingrind.executor.bookkeeping.posting;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountTaxonomy;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountingEvidence;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.bookIdentity;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.executor.InMemoryBookSession;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclaration;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PostingCommand;
import dev.erst.fingrind.executor.bookkeeping.PostingLineageModel;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.bookkeeping.RequestFingerprintTestSupport;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.PostingCommitResult;
import dev.erst.fingrind.executor.spi.StoredRequestPosting;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Coverage for local posting preflight and commit outcomes. */
class BookkeepingPostingServiceTest {
  private static final Instant FIXED_INSTANT = Instant.parse("2026-04-07T10:15:30Z");
  private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

  @Test
  void preflightAndCommit_acceptThenReplayMatchingRequests() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      BookkeepingPostingService service =
          new BookkeepingPostingService(
              bookSession, bookSession, () -> new PostingId("posting-new"), FIXED_CLOCK);
      PostingCommand command = command("idem-1");

      assertEquals(
          new PostingPreflightOutcome.Accepted(
              new IdempotencyKey("idem-1"), LocalDate.parse("2026-04-07")),
          service.preflight(command));
      PostingCommitResult.Committed firstCommit =
          assertInstanceOf(PostingCommitResult.Committed.class, service.commit(command));
      assertEquals(
          new PostingCommitResult.Committed(firstCommit.postingFact(), false), firstCommit);
      assertEquals(
          new PostingPreflightOutcome.Accepted(
              new IdempotencyKey("idem-1"), LocalDate.parse("2026-04-07")),
          service.preflight(command));
      assertTrue(
          assertInstanceOf(PostingCommitResult.Committed.class, service.commit(command))
              .idempotentReplay());
    }
  }

  @Test
  void preflight_acceptsMatchingReplayFromStoredFingerprint() {
    PostingCommand command = command("idem-replay");
    BookkeepingPostingService service =
        new BookkeepingPostingService(
            new ReplayValidationStore(
                new StoredRequestPosting(
                    committedPosting("posting-replay", "idem-replay"),
                    RequestFingerprintTestSupport.fingerprint(command))),
            (postingDraft, postingIdGenerator) -> {
              throw new AssertionError("commitStore should not be called during preflight");
            },
            () -> new PostingId("posting-new"),
            FIXED_CLOCK);

    assertEquals(
        new PostingPreflightOutcome.Accepted(
            new IdempotencyKey("idem-replay"), LocalDate.parse("2026-04-07")),
        service.preflight(command));
  }

  @Test
  void preflightAndCommit_rejectWhenBookIsNotInitialized() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      BookkeepingPostingService service =
          new BookkeepingPostingService(
              bookSession, bookSession, () -> new PostingId("posting-new"), FIXED_CLOCK);
      PostingCommand command = command("idem-missing");

      assertEquals(
          new PostingPreflightOutcome.Rejected(
              new BookkeepingPostingRejection.BookNotInitialized()),
          service.preflight(command));
      assertEquals(
          new PostingCommitResult.Rejected(new BookkeepingPostingRejection.BookNotInitialized()),
          service.commit(command));
    }
  }

  private static InMemoryBookSession initializedBook() {
    InMemoryBookSession bookSession = new InMemoryBookSession();
    bookSession.openBook(FIXED_INSTANT, bookIdentity(), List.of());
    assertInstanceOf(
        AccountDeclarationOutcome.Declared.class,
        bookSession.declareAccount(
            new AccountDeclaration(
                new AccountCode("1000"),
                new AccountName("Cash"),
                AccountType.ASSET,
                accountTaxonomy(AccountType.ASSET)),
            FIXED_INSTANT));
    assertInstanceOf(
        AccountDeclarationOutcome.Declared.class,
        bookSession.declareAccount(
            new AccountDeclaration(
                new AccountCode("4000"),
                new AccountName("Revenue"),
                AccountType.REVENUE,
                accountTaxonomy(AccountType.REVENUE)),
            FIXED_INSTANT));
    return bookSession;
  }

  private static PostingCommand command(String idempotencyKey) {
    return new PostingCommand(
        dev.erst.fingrind.core.PostingKind.STANDARD,
        dev.erst.fingrind.core.PostingOriginKind.REVERSAL,
        new JournalEntry(
            LocalDate.parse("2026-04-07"),
            List.of(
                line("1000", JournalLine.EntrySide.DEBIT, "10.00"),
                line("4000", JournalLine.EntrySide.CREDIT, "10.00"))),
        PostingLineageModel.direct(),
        accountingEvidence(idempotencyKey),
        new RequestProvenance(
            new CommandId("command-" + idempotencyKey),
            new IdempotencyKey(idempotencyKey),
            new CausationId("cause-" + idempotencyKey),
            Optional.of(new CorrelationId("corr-" + idempotencyKey))),
        SourceChannel.CLI);
  }

  private static JournalLine line(String accountCode, JournalLine.EntrySide side, String amount) {
    return new JournalLine(new AccountCode(accountCode), side, Money.parse("EUR", amount));
  }

  private static CommittedPosting committedPosting(String postingId, String idempotencyKey) {
    return new CommittedPosting(
        new PostingId(postingId),
        new JournalEntry(
            LocalDate.parse("2026-04-07"),
            List.of(
                line("1000", JournalLine.EntrySide.DEBIT, "10.00"),
                line("4000", JournalLine.EntrySide.CREDIT, "10.00"))),
        PostingLineageModel.direct(),
        dev.erst.fingrind.core.PostingKind.STANDARD,
        dev.erst.fingrind.core.PostingOriginKind.REVERSAL,
        accountingEvidence(idempotencyKey),
        new CommittedProvenance(
            new RequestProvenance(
                new CommandId("command-" + postingId),
                new IdempotencyKey(idempotencyKey),
                new CausationId("cause-" + postingId),
                Optional.of(new CorrelationId("corr-" + postingId))),
            FIXED_INSTANT,
            SourceChannel.CLI));
  }

  /** Validation-store double that forces the replay path before any account lookup occurs. */
  private static final class ReplayValidationStore
      implements dev.erst.fingrind.executor.bookkeeping.PostingValidationStore {
    private final StoredRequestPosting storedRequestPosting;

    private ReplayValidationStore(StoredRequestPosting storedRequestPosting) {
      this.storedRequestPosting = storedRequestPosting;
    }

    @Override
    public BookLifecycleInspection inspectBook() {
      return new BookLifecycleInspection.Initialized(1001, 1, 1, FIXED_INSTANT, bookIdentity());
    }

    @Override
    public Optional<RegisteredAccount> findAccount(AccountCode accountCode) {
      return Optional.empty();
    }

    @Override
    public Optional<dev.erst.fingrind.contract.tax.DeclaredTaxRegistration> findTaxRegistration(
        dev.erst.fingrind.contract.tax.TaxRegistrationId taxRegistrationId) {
      return Optional.empty();
    }

    @Override
    public Map<AccountCode, RegisteredAccount> findAccounts(
        java.util.Set<AccountCode> accountCodes) {
      return Map.of();
    }

    @Override
    public Optional<StoredRequestPosting> findExistingPosting(IdempotencyKey idempotencyKey) {
      return Optional.of(storedRequestPosting);
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
    public List<CommittedPosting> postings(
        dev.erst.fingrind.core.EffectiveDateRange effectiveDateRange) {
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
  }
}
