package dev.erst.fingrind.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.PostEntryCommand;
import dev.erst.fingrind.contract.PostEntryResult;
import dev.erst.fingrind.contract.PostingFact;
import dev.erst.fingrind.contract.PostingLineage;
import dev.erst.fingrind.contract.PostingRejection;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.CurrencyCode;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.core.SourceChannel;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link PostingApplicationService}. */
class PostingApplicationServiceTest {
  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-04-07T10:15:30Z"), ZoneOffset.UTC);

  @Test
  void preflight_rejectsBookNotInitialized() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      PostingApplicationService applicationService = applicationService(bookSession);

      PostEntryResult result = applicationService.preflight(command("idem-1"));

      assertEquals(
          preflightRejected(
              new IdempotencyKey("idem-1"), new PostingRejection.BookNotInitialized()),
          result);
    }
  }

  @Test
  void preflight_rejectsUnknownAndInactiveAccountsBeforeOtherChecks() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      PostingApplicationService applicationService = applicationService(bookSession);

      PostEntryResult unknownAccountResult = applicationService.preflight(command("idem-1"));
      assertEquals(
          preflightRejected(
              new IdempotencyKey("idem-1"),
              new PostingRejection.AccountStateViolations(
                  List.of(
                      new PostingRejection.UnknownAccount(new AccountCode("1000")),
                      new PostingRejection.UnknownAccount(new AccountCode("2000"))))),
          unknownAccountResult);

      declareDefaultAccounts(bookSession);
      bookSession.deactivateAccount(new AccountCode("1000"));

      PostEntryResult inactiveAccountResult = applicationService.preflight(command("idem-2"));
      assertEquals(
          preflightRejected(
              new IdempotencyKey("idem-2"),
              new PostingRejection.AccountStateViolations(
                  List.of(new PostingRejection.InactiveAccount(new AccountCode("1000"))))),
          inactiveAccountResult);
    }
  }

  @Test
  void preflight_returnsAcceptedWhenRequestIsAdmissible() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      declareDefaultAccounts(bookSession);
      PostingApplicationService applicationService = applicationService(bookSession);

      PostEntryResult result = applicationService.preflight(command("idem-1"));

      assertEquals(
          new PostEntryResult.PreflightAccepted(
              new IdempotencyKey("idem-1"), LocalDate.parse("2026-04-07")),
          result);
    }
  }

  @Test
  void preflight_rejectsDuplicateIdempotencyKey() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      declareDefaultAccounts(bookSession);
      bookSession.commit(existingPosting("posting-existing", "idem-1"));
      PostingApplicationService applicationService = applicationService(bookSession);

      PostEntryResult result = applicationService.preflight(command("idem-1"));

      assertEquals(
          preflightRejected(
              new IdempotencyKey("idem-1"), new PostingRejection.DuplicateIdempotencyKey()),
          result);
    }
  }

  @Test
  void preflight_rejectsMissingReversalTarget() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      declareDefaultAccounts(bookSession);
      PostingApplicationService applicationService = applicationService(bookSession);

      PostEntryResult result =
          applicationService.preflight(
              command(
                  "idem-1",
                  Optional.of(new ReversalReference(new PostingId("posting-missing"))),
                  Optional.of(new ReversalReason("operator reversal"))));

      assertEquals(
          preflightRejected(
              new IdempotencyKey("idem-1"),
              new PostingRejection.ReversalTargetNotFound(new PostingId("posting-missing"))),
          result);
    }
  }

  @Test
  void preflight_acceptsReversalWhenTargetExistsAndReasonIsPresent() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      declareDefaultAccounts(bookSession);
      bookSession.commit(existingPosting("posting-1", "idem-existing"));
      PostingApplicationService applicationService = applicationService(bookSession);

      PostEntryResult result =
          applicationService.preflight(
              command(
                  "idem-1",
                  reversalReference("posting-1"),
                  Optional.of(new ReversalReason("full reversal")),
                  reversalJournalEntry()));

      assertEquals(
          new PostEntryResult.PreflightAccepted(
              new IdempotencyKey("idem-1"), LocalDate.parse("2026-04-07")),
          result);
    }
  }

  @Test
  void preflight_rejectsReversalThatDoesNotNegateTarget() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      declareDefaultAccounts(bookSession);
      bookSession.commit(existingPosting("posting-1", "idem-existing"));
      PostingApplicationService applicationService = applicationService(bookSession);

      PostEntryResult result =
          applicationService.preflight(
              command(
                  "idem-1",
                  reversalReference("posting-1"),
                  Optional.of(new ReversalReason("full reversal")),
                  mismatchedReversalJournalEntry()));

      assertEquals(
          preflightRejected(
              new IdempotencyKey("idem-1"),
              new PostingRejection.ReversalDoesNotNegateTarget(new PostingId("posting-1"))),
          result);
    }
  }

  @Test
  void commit_returnsCommittedWhenRequestIsAdmissible() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      declareDefaultAccounts(bookSession);
      PostingApplicationService applicationService = applicationService(bookSession);

      PostEntryResult result = applicationService.commit(command("idem-1"));

      assertEquals(
          new PostEntryResult.Committed(
              new PostingId("posting-new"),
              new IdempotencyKey("idem-1"),
              LocalDate.parse("2026-04-07"),
              FIXED_CLOCK.instant()),
          result);
    }
  }

  @Test
  void commit_rejectsBookNotInitializedBeforeGeneratingPostingId() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      PostingApplicationService applicationService =
          new PostingApplicationService(
              bookSession,
              () -> {
                throw new AssertionError("postingIdGenerator should not be called");
              },
              FIXED_CLOCK);

      PostEntryResult result = applicationService.commit(command("idem-1"));

      assertEquals(
          commitRejected(new IdempotencyKey("idem-1"), new PostingRejection.BookNotInitialized()),
          result);
    }
  }

  @Test
  void commit_returnsCommittedForValidReversal() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      declareDefaultAccounts(bookSession);
      bookSession.commit(existingPosting("posting-1", "idem-original"));
      PostingApplicationService applicationService = applicationService(bookSession);

      PostEntryResult result =
          applicationService.commit(
              command(
                  "idem-1",
                  reversalReference("posting-1"),
                  Optional.of(new ReversalReason("full reversal")),
                  reversalJournalEntry()));

      assertEquals(
          new PostEntryResult.Committed(
              new PostingId("posting-new"),
              new IdempotencyKey("idem-1"),
              LocalDate.parse("2026-04-07"),
              FIXED_CLOCK.instant()),
          result);
    }
  }

  @Test
  void preflight_rejectsReversalWhenTargetAlreadyHasReversal() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      declareDefaultAccounts(bookSession);
      bookSession.commit(existingPosting("posting-1", "idem-original"));
      PostingApplicationService applicationService = applicationService(bookSession);
      applicationService.commit(
          command(
              "idem-existing-reversal",
              reversalReference("posting-1"),
              Optional.of(new ReversalReason("full reversal")),
              reversalJournalEntry()));

      PostEntryResult result =
          applicationService.preflight(
              command(
                  "idem-1",
                  reversalReference("posting-1"),
                  Optional.of(new ReversalReason("full reversal")),
                  reversalJournalEntry()));

      assertEquals(
          preflightRejected(
              new IdempotencyKey("idem-1"),
              new PostingRejection.ReversalAlreadyExists(new PostingId("posting-1"))),
          result);
    }
  }

  @Test
  void commit_mapsOrdinaryBookSessionOutcomes() {
    PostingBookSession bookSession = mappedOutcomeBookSession();
    try (bookSession) {
      PostingApplicationService applicationService = applicationService(bookSession);

      assertEquals(
          commitRejected(
              new IdempotencyKey("idem-book-not-initialized"),
              new PostingRejection.BookNotInitialized()),
          applicationService.commit(command("idem-book-not-initialized")));
      assertEquals(
          commitRejected(
              new IdempotencyKey("idem-unknown-account"),
              new PostingRejection.AccountStateViolations(
                  List.of(new PostingRejection.UnknownAccount(new AccountCode("1000"))))),
          applicationService.commit(command("idem-unknown-account")));
      assertEquals(
          commitRejected(
              new IdempotencyKey("idem-inactive-account"),
              new PostingRejection.AccountStateViolations(
                  List.of(new PostingRejection.InactiveAccount(new AccountCode("1000"))))),
          applicationService.commit(command("idem-inactive-account")));
      assertEquals(
          commitRejected(
              new IdempotencyKey("idem-duplicate"), new PostingRejection.DuplicateIdempotencyKey()),
          applicationService.commit(command("idem-duplicate")));
      assertEquals(
          commitRejected(
              new IdempotencyKey("idem-reversal-duplicate"),
              new PostingRejection.ReversalAlreadyExists(new PostingId("posting-1"))),
          applicationService.commit(
              command(
                  "idem-reversal-duplicate",
                  reversalReference("posting-1"),
                  Optional.of(new ReversalReason("full reversal")),
                  reversalJournalEntry())));
    }
  }

  @Test
  void commit_propagatesUnexpectedBookSessionFailure() {
    PostingBookSession bookSession =
        new DelegatingPostingBookSession() {
          @Override
          public boolean isInitialized() {
            return true;
          }

          @Override
          public Optional<DeclaredAccount> findAccount(AccountCode accountCode) {
            return Optional.of(
                new DeclaredAccount(
                    accountCode,
                    new AccountName("Synthetic"),
                    "1000".equals(accountCode.value()) ? NormalBalance.DEBIT : NormalBalance.CREDIT,
                    true,
                    FIXED_CLOCK.instant()));
          }

          @Override
          public PostingCommitResult commit(PostingFact postingFact) {
            throw new IllegalStateException("boom");
          }
        };
    try (bookSession) {
      PostingApplicationService applicationService = applicationService(bookSession);

      IllegalStateException thrown =
          assertThrows(
              IllegalStateException.class, () -> applicationService.commit(command("idem-1")));

      assertEquals("boom", thrown.getMessage());
    }
  }

  private static InMemoryBookSession initializedBook() {
    InMemoryBookSession bookSession = new InMemoryBookSession();
    bookSession.openBook(FIXED_CLOCK.instant());
    return bookSession;
  }

  private static void declareDefaultAccounts(InMemoryBookSession bookSession) {
    bookSession.declareAccount(
        new AccountCode("1000"),
        new AccountName("Cash"),
        NormalBalance.DEBIT,
        FIXED_CLOCK.instant());
    bookSession.declareAccount(
        new AccountCode("2000"),
        new AccountName("Revenue"),
        NormalBalance.CREDIT,
        FIXED_CLOCK.instant());
  }

  private static PostingApplicationService applicationService(PostingBookSession bookSession) {
    return new PostingApplicationService(
        bookSession, () -> new PostingId("posting-new"), FIXED_CLOCK);
  }

  private static PostEntryCommand command(String idempotencyKey) {
    return command(idempotencyKey, Optional.empty(), Optional.empty(), journalEntry());
  }

  private static PostEntryResult.PreflightRejected preflightRejected(
      IdempotencyKey idempotencyKey, PostingRejection rejection) {
    return new PostEntryResult.PreflightRejected(idempotencyKey, rejection);
  }

  private static PostEntryResult.CommitRejected commitRejected(
      IdempotencyKey idempotencyKey, PostingRejection rejection) {
    return new PostEntryResult.CommitRejected(idempotencyKey, rejection);
  }

  private static PostEntryCommand command(
      String idempotencyKey,
      Optional<ReversalReference> reversalReference,
      Optional<ReversalReason> reason) {
    return command(idempotencyKey, reversalReference, reason, journalEntry());
  }

  private static PostEntryCommand command(
      String idempotencyKey,
      Optional<ReversalReference> reversalReference,
      Optional<ReversalReason> reason,
      JournalEntry journalEntry) {
    return new PostEntryCommand(
        journalEntry,
        postingLineage(reversalReference, reason),
        requestProvenance(idempotencyKey),
        SourceChannel.CLI);
  }

  private static Optional<ReversalReference> reversalReference(String priorPostingId) {
    return Optional.of(new ReversalReference(new PostingId(priorPostingId)));
  }

  private static PostingFact existingPosting(String postingId, String idempotencyKey) {
    return new PostingFact(
        new PostingId(postingId),
        journalEntry(),
        PostingLineage.direct(),
        committedProvenance(idempotencyKey));
  }

  private static CommittedProvenance committedProvenance(String idempotencyKey) {
    return new CommittedProvenance(
        requestProvenance(idempotencyKey), FIXED_CLOCK.instant(), SourceChannel.CLI);
  }

  private static RequestProvenance requestProvenance(String idempotencyKey) {
    return new RequestProvenance(
        new ActorId("actor-1"),
        ActorType.AGENT,
        new CommandId("command-1"),
        new IdempotencyKey(idempotencyKey),
        new CausationId("cause-1"),
        Optional.of(new CorrelationId("corr-1")));
  }

  private static PostingLineage postingLineage(
      Optional<ReversalReference> reversalReference, Optional<ReversalReason> reason) {
    if (reversalReference.isEmpty()) {
      return PostingLineage.direct();
    }
    return PostingLineage.reversal(reversalReference.orElseThrow(), reason.orElseThrow());
  }

  private static JournalEntry journalEntry() {
    return new JournalEntry(
        LocalDate.parse("2026-04-07"),
        List.of(
            line("1000", JournalLine.EntrySide.DEBIT, "10.00"),
            line("2000", JournalLine.EntrySide.CREDIT, "10.00")));
  }

  private static JournalEntry reversalJournalEntry() {
    return new JournalEntry(
        LocalDate.parse("2026-04-07"),
        List.of(
            line("1000", JournalLine.EntrySide.CREDIT, "10.00"),
            line("2000", JournalLine.EntrySide.DEBIT, "10.00")));
  }

  private static JournalEntry mismatchedReversalJournalEntry() {
    return new JournalEntry(
        LocalDate.parse("2026-04-07"),
        List.of(
            line("1000", JournalLine.EntrySide.CREDIT, "5.00"),
            line("2000", JournalLine.EntrySide.DEBIT, "5.00")));
  }

  private static JournalLine line(String accountCode, JournalLine.EntrySide side, String amount) {
    return new JournalLine(
        new AccountCode(accountCode),
        side,
        new Money(new CurrencyCode("EUR"), new BigDecimal(amount)));
  }

  private static PostingBookSession mappedOutcomeBookSession() {
    return new DelegatingPostingBookSession() {
      @Override
      public boolean isInitialized() {
        return true;
      }

      @Override
      public Optional<DeclaredAccount> findAccount(AccountCode accountCode) {
        return Optional.of(
            new DeclaredAccount(
                accountCode,
                new AccountName("Synthetic"),
                "1000".equals(accountCode.value()) ? NormalBalance.DEBIT : NormalBalance.CREDIT,
                true,
                FIXED_CLOCK.instant()));
      }

      @Override
      public Optional<PostingFact> findPosting(PostingId postingId) {
        return Optional.of(existingPosting(postingId.value(), "idem-existing"));
      }

      @Override
      public PostingCommitResult commit(PostingFact postingFact) {
        String idempotencyKey =
            postingFact.provenance().requestProvenance().idempotencyKey().value();
        return switch (idempotencyKey) {
          case "idem-book-not-initialized" ->
              new PostingCommitResult.Rejected(new PostingRejection.BookNotInitialized());
          case "idem-unknown-account" ->
              new PostingCommitResult.Rejected(
                  new PostingRejection.AccountStateViolations(
                      List.of(new PostingRejection.UnknownAccount(new AccountCode("1000")))));
          case "idem-inactive-account" ->
              new PostingCommitResult.Rejected(
                  new PostingRejection.AccountStateViolations(
                      List.of(new PostingRejection.InactiveAccount(new AccountCode("1000")))));
          case "idem-duplicate" ->
              new PostingCommitResult.Rejected(new PostingRejection.DuplicateIdempotencyKey());
          case "idem-reversal-duplicate" ->
              new PostingCommitResult.Rejected(
                  new PostingRejection.ReversalAlreadyExists(new PostingId("posting-1")));
          default -> throw new AssertionError("Unexpected test idempotency key: " + idempotencyKey);
        };
      }
    };
  }

  /** Minimal book-session stub whose methods fail unless a test overrides them. */
  private abstract static class DelegatingPostingBookSession implements PostingBookSession {
    @Override
    public boolean isInitialized() {
      throw new AssertionError("isInitialized should not be called in this test");
    }

    @Override
    public Optional<DeclaredAccount> findAccount(AccountCode accountCode) {
      throw new AssertionError("findAccount should not be called in this test");
    }

    @Override
    public Optional<PostingFact> findExistingPosting(IdempotencyKey idempotencyKey) {
      return Optional.empty();
    }

    @Override
    public Optional<PostingFact> findPosting(PostingId postingId) {
      return Optional.empty();
    }

    @Override
    public Optional<PostingFact> findReversalFor(PostingId priorPostingId) {
      return Optional.empty();
    }

    @Override
    public PostingCommitResult commit(
        PostingDraft postingDraft, PostingIdGenerator postingIdGenerator) {
      return commit(postingDraft.materialize(postingIdGenerator.nextPostingId()));
    }

    @Override
    public void close() {}
  }
}
