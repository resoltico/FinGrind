package dev.erst.fingrind.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.CorrectionReason;
import dev.erst.fingrind.core.CorrectionReference;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.CurrencyCode;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.runtime.InMemoryPostingFactStore;
import dev.erst.fingrind.runtime.PostingCommitResult;
import dev.erst.fingrind.runtime.PostingFact;
import dev.erst.fingrind.runtime.PostingFactStore;
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
  void preflight_returnsAcceptedWhenRequestIsAdmissible() {
    PostingApplicationService applicationService =
        applicationService(new InMemoryPostingFactStore());

    PostEntryResult result = applicationService.preflight(command("idem-1"));

    assertEquals(
        new PostEntryResult.PreflightAccepted(
            new IdempotencyKey("idem-1"), LocalDate.parse("2026-04-07")),
        result);
  }

  @Test
  void preflight_rejectsDuplicateIdempotencyKey() {
    InMemoryPostingFactStore postingFactStore = new InMemoryPostingFactStore();
    postingFactStore.commit(existingPosting("posting-existing", "idem-1"));
    PostingApplicationService applicationService = applicationService(postingFactStore);

    PostEntryResult result = applicationService.preflight(command("idem-1"));

    assertEquals(
        new PostEntryResult.Rejected(
            new IdempotencyKey("idem-1"), new PostingRejection.DuplicateIdempotencyKey()),
        result);
  }

  @Test
  void preflight_rejectsCorrectionWithoutReason() {
    PostingApplicationService applicationService =
        applicationService(new InMemoryPostingFactStore());

    PostEntryResult result =
        applicationService.preflight(
            command(
                "idem-1",
                Optional.of(
                    new CorrectionReference(
                        CorrectionReference.CorrectionKind.AMENDMENT, new PostingId("posting-1"))),
                Optional.empty()));

    assertEquals(
        new PostEntryResult.Rejected(
            new IdempotencyKey("idem-1"), new PostingRejection.CorrectionReasonRequired()),
        result);
  }

  @Test
  void preflight_rejectsReasonWithoutCorrection() {
    PostingApplicationService applicationService =
        applicationService(new InMemoryPostingFactStore());

    PostEntryResult result =
        applicationService.preflight(
            command(
                "idem-1",
                Optional.empty(),
                Optional.of(new CorrectionReason("operator correction"))));

    assertEquals(
        new PostEntryResult.Rejected(
            new IdempotencyKey("idem-1"), new PostingRejection.CorrectionReasonForbidden()),
        result);
  }

  @Test
  void preflight_rejectsMissingCorrectionTarget() {
    PostingApplicationService applicationService =
        applicationService(new InMemoryPostingFactStore());

    PostEntryResult result =
        applicationService.preflight(
            command(
                "idem-1",
                Optional.of(
                    new CorrectionReference(
                        CorrectionReference.CorrectionKind.AMENDMENT,
                        new PostingId("posting-missing"))),
                Optional.of(new CorrectionReason("operator correction"))));

    assertEquals(
        new PostEntryResult.Rejected(
            new IdempotencyKey("idem-1"),
            new PostingRejection.CorrectionTargetNotFound(new PostingId("posting-missing"))),
        result);
  }

  @Test
  void preflight_acceptsAmendmentWhenTargetExistsAndReasonIsPresent() {
    InMemoryPostingFactStore postingFactStore = new InMemoryPostingFactStore();
    postingFactStore.commit(existingPosting("posting-1", "idem-existing"));
    PostingApplicationService applicationService = applicationService(postingFactStore);

    PostEntryResult result =
        applicationService.preflight(
            command(
                "idem-1",
                Optional.of(
                    new CorrectionReference(
                        CorrectionReference.CorrectionKind.AMENDMENT, new PostingId("posting-1"))),
                Optional.of(new CorrectionReason("operator correction"))));

    assertEquals(
        new PostEntryResult.PreflightAccepted(
            new IdempotencyKey("idem-1"), LocalDate.parse("2026-04-07")),
        result);
  }

  @Test
  void preflight_rejectsReversalThatDoesNotNegateTarget() {
    InMemoryPostingFactStore postingFactStore = new InMemoryPostingFactStore();
    postingFactStore.commit(existingPosting("posting-1", "idem-existing"));
    PostingApplicationService applicationService = applicationService(postingFactStore);

    PostEntryResult result =
        applicationService.preflight(
            command(
                "idem-1",
                reversalReference("posting-1"),
                Optional.of(new CorrectionReason("full reversal")),
                mismatchedReversalJournalEntry()));

    assertEquals(
        new PostEntryResult.Rejected(
            new IdempotencyKey("idem-1"),
            new PostingRejection.ReversalDoesNotNegateTarget(new PostingId("posting-1"))),
        result);
  }

  @Test
  void commit_returnsCommittedForValidReversal() {
    InMemoryPostingFactStore postingFactStore = new InMemoryPostingFactStore();
    postingFactStore.commit(existingPosting("posting-1", "idem-original"));
    PostingApplicationService applicationService = applicationService(postingFactStore);

    PostEntryResult result =
        applicationService.commit(
            command(
                "idem-1",
                reversalReference("posting-1"),
                Optional.of(new CorrectionReason("full reversal")),
                reversalJournalEntry()));

    assertEquals(
        new PostEntryResult.Committed(
            new PostingId("posting-new"),
            new IdempotencyKey("idem-1"),
            LocalDate.parse("2026-04-07"),
            FIXED_CLOCK.instant()),
        result);
  }

  @Test
  void commit_returnsCommittedWhenRequestIsAdmissible() {
    PostingApplicationService applicationService =
        applicationService(new InMemoryPostingFactStore());

    PostEntryResult result = applicationService.commit(command("idem-1"));

    assertEquals(
        new PostEntryResult.Committed(
            new PostingId("posting-new"),
            new IdempotencyKey("idem-1"),
            LocalDate.parse("2026-04-07"),
            FIXED_CLOCK.instant()),
        result);
  }

  @Test
  void commit_rejectsDuplicateIdempotencyKey() {
    InMemoryPostingFactStore postingFactStore = new InMemoryPostingFactStore();
    postingFactStore.commit(existingPosting("posting-existing", "idem-1"));
    PostingApplicationService applicationService = applicationService(postingFactStore);

    PostEntryResult result = applicationService.commit(command("idem-1"));

    assertEquals(
        new PostEntryResult.Rejected(
            new IdempotencyKey("idem-1"), new PostingRejection.DuplicateIdempotencyKey()),
        result);
  }

  @Test
  void commit_mapsRuntimeDuplicateIdempotencyOutcome() {
    PostingFactStore postingFactStore =
        new PostingFactStore() {
          @Override
          public Optional<PostingFact> findByIdempotency(IdempotencyKey idempotencyKey) {
            return Optional.empty();
          }

          @Override
          public Optional<PostingFact> findByPostingId(PostingId postingId) {
            return Optional.empty();
          }

          @Override
          public Optional<PostingFact> findReversalFor(PostingId priorPostingId) {
            return Optional.empty();
          }

          @Override
          public PostingCommitResult commit(PostingFact postingFact) {
            return new PostingCommitResult.DuplicateIdempotency(
                postingFact.provenance().requestProvenance().idempotencyKey());
          }
        };
    PostingApplicationService applicationService = applicationService(postingFactStore);

    PostEntryResult result = applicationService.commit(command("idem-1"));

    assertEquals(
        new PostEntryResult.Rejected(
            new IdempotencyKey("idem-1"), new PostingRejection.DuplicateIdempotencyKey()),
        result);
  }

  @Test
  void commit_rejectsSecondReversalForSameTarget() {
    InMemoryPostingFactStore postingFactStore = new InMemoryPostingFactStore();
    postingFactStore.commit(existingPosting("posting-1", "idem-original"));
    postingFactStore.commit(existingReversal("posting-2", "idem-reversal", "posting-1"));
    PostingApplicationService applicationService = applicationService(postingFactStore);

    PostEntryResult result =
        applicationService.commit(
            command(
                "idem-1",
                reversalReference("posting-1"),
                Optional.of(new CorrectionReason("full reversal")),
                reversalJournalEntry()));

    assertEquals(
        new PostEntryResult.Rejected(
            new IdempotencyKey("idem-1"),
            new PostingRejection.ReversalAlreadyExists(new PostingId("posting-1"))),
        result);
  }

  @Test
  void commit_mapsRuntimeDuplicateReversalTargetOutcome() {
    PostingFact priorPosting = existingPosting("posting-1", "idem-existing");
    PostingFactStore postingFactStore =
        new PostingFactStore() {
          @Override
          public Optional<PostingFact> findByIdempotency(IdempotencyKey idempotencyKey) {
            return Optional.empty();
          }

          @Override
          public Optional<PostingFact> findByPostingId(PostingId postingId) {
            return Optional.of(priorPosting);
          }

          @Override
          public Optional<PostingFact> findReversalFor(PostingId priorPostingId) {
            return Optional.empty();
          }

          @Override
          public PostingCommitResult commit(PostingFact postingFact) {
            return new PostingCommitResult.DuplicateReversalTarget(new PostingId("posting-1"));
          }
        };
    PostingApplicationService applicationService = applicationService(postingFactStore);

    PostEntryResult result =
        applicationService.commit(
            command(
                "idem-1",
                reversalReference("posting-1"),
                Optional.of(new CorrectionReason("full reversal")),
                reversalJournalEntry()));

    assertEquals(
        new PostEntryResult.Rejected(
            new IdempotencyKey("idem-1"),
            new PostingRejection.ReversalAlreadyExists(new PostingId("posting-1"))),
        result);
  }

  @Test
  void commit_propagatesUnexpectedRuntimeFailure() {
    PostingFactStore postingFactStore =
        new PostingFactStore() {
          @Override
          public Optional<PostingFact> findByIdempotency(IdempotencyKey idempotencyKey) {
            return Optional.empty();
          }

          @Override
          public Optional<PostingFact> findByPostingId(PostingId postingId) {
            return Optional.empty();
          }

          @Override
          public Optional<PostingFact> findReversalFor(PostingId priorPostingId) {
            return Optional.empty();
          }

          @Override
          public PostingCommitResult commit(PostingFact postingFact) {
            throw new IllegalStateException("boom");
          }
        };
    PostingApplicationService applicationService = applicationService(postingFactStore);

    IllegalStateException thrown =
        assertThrows(
            IllegalStateException.class, () -> applicationService.commit(command("idem-1")));

    assertEquals("boom", thrown.getMessage());
  }

  private static PostingApplicationService applicationService(PostingFactStore postingFactStore) {
    return new PostingApplicationService(
        postingFactStore, () -> new PostingId("posting-new"), FIXED_CLOCK);
  }

  private static PostEntryCommand command(String idempotencyKey) {
    return command(idempotencyKey, Optional.empty(), Optional.empty(), journalEntry());
  }

  private static PostEntryCommand command(
      String idempotencyKey,
      Optional<CorrectionReference> correctionReference,
      Optional<CorrectionReason> reason) {
    return command(idempotencyKey, correctionReference, reason, journalEntry());
  }

  private static PostEntryCommand command(
      String idempotencyKey,
      Optional<CorrectionReference> correctionReference,
      Optional<CorrectionReason> reason,
      JournalEntry journalEntry) {
    return new PostEntryCommand(
        journalEntry,
        correctionReference,
        requestProvenance(idempotencyKey, reason),
        SourceChannel.CLI);
  }

  private static PostingFact existingPosting(String postingId, String idempotencyKey) {
    return new PostingFact(
        new PostingId(postingId),
        journalEntry(),
        Optional.empty(),
        committedProvenance(idempotencyKey, Optional.empty()));
  }

  private static PostingFact existingReversal(
      String postingId, String idempotencyKey, String priorPostingId) {
    return new PostingFact(
        new PostingId(postingId),
        reversalJournalEntry(),
        reversalReference(priorPostingId),
        committedProvenance(
            idempotencyKey, Optional.of(new CorrectionReason("historical full reversal"))));
  }

  private static CommittedProvenance committedProvenance(
      String idempotencyKey, Optional<CorrectionReason> reason) {
    return new CommittedProvenance(
        requestProvenance(idempotencyKey, reason), FIXED_CLOCK.instant(), SourceChannel.CLI);
  }

  private static RequestProvenance requestProvenance(
      String idempotencyKey, Optional<CorrectionReason> reason) {
    return new RequestProvenance(
        new ActorId("actor-1"),
        ActorType.AGENT,
        new CommandId("command-1"),
        new IdempotencyKey(idempotencyKey),
        new CausationId("cause-1"),
        Optional.of(new CorrelationId("corr-1")),
        reason);
  }

  private static Optional<CorrectionReference> reversalReference(String priorPostingId) {
    return Optional.of(
        new CorrectionReference(
            CorrectionReference.CorrectionKind.REVERSAL, new PostingId(priorPostingId)));
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
            line("1000", JournalLine.EntrySide.CREDIT, "10.00"),
            line("2000", JournalLine.EntrySide.DEBIT, "9.00"),
            line("9999", JournalLine.EntrySide.DEBIT, "1.00")));
  }

  private static JournalLine line(String accountCode, JournalLine.EntrySide side, String amount) {
    return new JournalLine(
        new AccountCode(accountCode),
        side,
        new Money(new CurrencyCode("EUR"), new BigDecimal(amount)));
  }
}
