package dev.erst.fingrind.executor.bookkeeping;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountingEvidence;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.bookIdentity;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.core.AccountCode;
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
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.PostingOriginKind;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.StoredRequestPosting;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Direct branch coverage for reversal acceptance before durable commit. */
class ReversalAcceptancePolicyTest {
  private static final Instant DECLARED_AT = Instant.parse("2026-04-07T10:15:30Z");

  @Test
  void rejectionFor_reportsMissingTargetAndNonNegatingCandidate() {
    PostingRequestModel missingTargetRequest =
        reversalRequest("idem-missing", "posting-missing", reversalJournalEntry());
    PostingRequestModel mismatchedReversalRequest =
        reversalRequest("idem-mismatch", "posting-1", originalJournalEntry());
    PostingValidationStore missingTargetStore = new PostingValidationStoreStub(Map.of());
    PostingValidationStore existingPostingStore =
        new PostingValidationStoreStub(
            Map.of(
                new PostingId("posting-1"), committedPosting("posting-1", originalJournalEntry())));

    assertEquals(
        Optional.of(
            new BookkeepingPostingRejection.ReversalTargetNotFound(
                new PostingId("posting-missing"))),
        ReversalAcceptancePolicy.rejectionFor(missingTargetRequest, missingTargetStore));
    assertEquals(
        Optional.of(
            new BookkeepingPostingRejection.ReversalDoesNotNegateTarget(
                new PostingId("posting-1"))),
        ReversalAcceptancePolicy.rejectionFor(mismatchedReversalRequest, existingPostingStore));
  }

  @Test
  void rejectionFor_rejectsTargetsThatAreAlreadyReversals() {
    PostingRequestModel reversalOfReversalRequest =
        reversalRequest("idem-reroll", "posting-reversal", originalJournalEntry());
    PostingValidationStore reversalTargetStore =
        new PostingValidationStoreStub(
            Map.of(
                new PostingId("posting-reversal"),
                reversalPosting("posting-reversal", "posting-original")));

    assertEquals(
        Optional.of(new ReversalTargetIsReversal(new PostingId("posting-reversal"))),
        ReversalAcceptancePolicy.rejectionFor(reversalOfReversalRequest, reversalTargetStore));
  }

  private static PostingRequestModel reversalRequest(
      String idempotencyKey, String priorPostingId, JournalEntry candidateJournalEntry) {
    ReversalReference reversalReference = new ReversalReference(new PostingId(priorPostingId));
    ReversalReason reversalReason = new ReversalReason("operator reversal");
    return new PostingCommand(
        PostingKind.STANDARD,
        PostingOriginKind.REVERSAL,
        candidateJournalEntry,
        PostingLineageModel.reversal(reversalReference, reversalReason),
        accountingEvidence(idempotencyKey),
        requestProvenance(idempotencyKey),
        SourceChannel.CLI);
  }

  private static CommittedPosting committedPosting(String postingId, JournalEntry journalEntry) {
    return new CommittedPosting(
        new PostingId(postingId),
        journalEntry,
        PostingLineageModel.direct(),
        PostingKind.STANDARD,
        PostingOriginKind.DIRECT_JOURNAL,
        accountingEvidence("prior-" + postingId),
        new CommittedProvenance(
            requestProvenance("prior-" + postingId),
            Instant.parse("2026-04-07T10:15:30Z"),
            SourceChannel.CLI));
  }

  private static CommittedPosting reversalPosting(String postingId, String priorPostingId) {
    return new CommittedPosting(
        new PostingId(postingId),
        reversalJournalEntry(),
        PostingLineageModel.reversal(
            new ReversalReference(new PostingId(priorPostingId)),
            new ReversalReason("historical full reversal")),
        PostingKind.STANDARD,
        PostingOriginKind.REVERSAL,
        accountingEvidence("prior-" + postingId),
        new CommittedProvenance(
            requestProvenance("prior-" + postingId),
            Instant.parse("2026-04-07T10:15:30Z"),
            SourceChannel.CLI));
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

  private static JournalEntry originalJournalEntry() {
    return new JournalEntry(
        LocalDate.parse("2026-04-07"),
        List.of(
            new JournalLine(
                new AccountCode("1000"), JournalLine.EntrySide.DEBIT, Money.parse("EUR", "10.00")),
            new JournalLine(
                new AccountCode("2000"),
                JournalLine.EntrySide.CREDIT,
                Money.parse("EUR", "10.00"))));
  }

  private static JournalEntry reversalJournalEntry() {
    return new JournalEntry(
        LocalDate.parse("2026-04-07"),
        List.of(
            new JournalLine(
                new AccountCode("1000"), JournalLine.EntrySide.CREDIT, Money.parse("EUR", "10.00")),
            new JournalLine(
                new AccountCode("2000"),
                JournalLine.EntrySide.DEBIT,
                Money.parse("EUR", "10.00"))));
  }

  /** Minimal validation-store stub for targeted reversal-acceptance branch coverage. */
  private static final class PostingValidationStoreStub implements PostingValidationStore {
    private final Map<PostingId, CommittedPosting> postingsById;

    private PostingValidationStoreStub(Map<PostingId, CommittedPosting> postingsById) {
      this.postingsById = postingsById;
    }

    @Override
    public BookLifecycleInspection inspectBook() {
      return new BookLifecycleInspection.Initialized(1001, 1, 1, DECLARED_AT, bookIdentity());
    }

    @Override
    public Optional<RegisteredAccount> findAccount(AccountCode accountCode) {
      return Optional.empty();
    }

    @Override
    public Map<AccountCode, RegisteredAccount> findAccounts(Set<AccountCode> accountCodes) {
      return Map.of();
    }

    @Override
    public Optional<dev.erst.fingrind.contract.tax.DeclaredTaxRegistration> findTaxRegistration(
        dev.erst.fingrind.contract.tax.TaxRegistrationId taxRegistrationId) {
      return Optional.empty();
    }

    @Override
    public Optional<StoredRequestPosting> findExistingPosting(IdempotencyKey idempotencyKey) {
      return Optional.empty();
    }

    @Override
    public Optional<CommittedPosting> findPosting(PostingId postingId) {
      return Optional.ofNullable(postingsById.get(postingId));
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
  }
}
