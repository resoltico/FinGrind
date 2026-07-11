package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.RequestFingerprint;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.executor.PostEntryResolutionSupport;
import dev.erst.fingrind.executor.bookkeeping.policy.KernelAccountingRulesResolver;
import dev.erst.fingrind.executor.spi.PostingDraft;
import dev.erst.fingrind.executor.spi.StoredRequestPosting;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Bookkeeping acceptance policy shared by preflight and durable commit paths. */
public final class PostingAcceptancePolicy {
  private static final PostingAcceptancePolicy CURRENT_KERNEL = new PostingAcceptancePolicy();
  private final PostingCurrencyAcceptancePolicy postingCurrencyAcceptancePolicy =
      new PostingCurrencyAcceptancePolicy();
  private final PostingSweptInterimResultPolicy postingSweptInterimResultPolicy =
      new PostingSweptInterimResultPolicy();
  private final OpeningPositionAcceptancePolicy openingPositionAcceptancePolicy =
      new OpeningPositionAcceptancePolicy();
  private final PostingAccountStatePolicy postingAccountStatePolicy =
      new PostingAccountStatePolicy();

  /** Returns the built-in posting-acceptance policy for the current FinGrind kernel. */
  public static PostingAcceptancePolicy currentKernel() {
    return CURRENT_KERNEL;
  }

  /** Acceptance decision distinguishing fresh acceptance, idempotent replay, and rejection. */
  public sealed interface Decision permits Decision.Accepted, Decision.Replay, Decision.Rejected {
    /** Accepted request with its computed semantic fingerprint. */
    record Accepted(RequestFingerprint requestFingerprint, AcceptedPosting acceptedPosting)
        implements Decision {
      public Accepted {
        Objects.requireNonNull(requestFingerprint, "requestFingerprint");
        Objects.requireNonNull(acceptedPosting, "acceptedPosting");
      }
    }

    /** Idempotent replay of one already committed posting. */
    record Replay(CommittedPosting postingFact) implements Decision {
      public Replay {
        Objects.requireNonNull(postingFact, "postingFact");
      }
    }

    /** Rejected request carrying the first deterministic refusal. */
    record Rejected(BookkeepingPostingRejection rejection) implements Decision {
      public Rejected {
        Objects.requireNonNull(rejection, "rejection");
      }
    }
  }

  /** Returns the first deterministic rejection for the supplied posting attempt, if any. */
  public Optional<BookkeepingPostingRejection> rejectionFor(
      PostingRequestModel postingRequest, PostingValidationStore book) {
    Decision decision = decisionFor(postingRequest, book);
    if (decision instanceof Decision.Rejected rejected) {
      return Optional.of(rejected.rejection());
    }
    return Optional.empty();
  }

  /** Returns the full acceptance decision for the supplied posting attempt. */
  public Decision decisionFor(PostingRequestModel postingRequest, PostingValidationStore book) {
    Objects.requireNonNull(postingRequest, "postingRequest");
    Objects.requireNonNull(book, "book");
    if (!book.allowsInitializedWorkflow()) {
      return new Decision.Rejected(new BookkeepingPostingRejection.BookNotInitialized());
    }
    RequestFingerprint requestFingerprint = RequestFingerprintOwner.fingerprint(postingRequest);
    Optional<StoredRequestPosting> existingPosting =
        book.findExistingPosting(postingRequest.requestProvenance().idempotencyKey());
    if (existingPosting.isPresent()) {
      StoredRequestPosting storedRequestPosting = existingPosting.orElseThrow();
      if (storedRequestPosting.requestFingerprint().equals(requestFingerprint)) {
        return new Decision.Replay(storedRequestPosting.postingFact());
      }
      return new Decision.Rejected(new BookkeepingPostingRejection.IdempotencyKeyConflict());
    }
    BookIdentity bookIdentity = initializedBookIdentity(book);
    AcceptedPosting acceptedPosting;
    try {
      acceptedPosting = resolveAcceptedPosting(postingRequest, book);
    } catch (AcceptedPostingResolutionFailure failure) {
      return new Decision.Rejected(failure.rejection());
    }
    Optional<BookkeepingPostingRejection> currencyRejection =
        postingCurrencyAcceptancePolicy.rejectionFor(acceptedPosting, bookIdentity);
    if (currencyRejection.isPresent()) {
      return new Decision.Rejected(currencyRejection.orElseThrow());
    }
    Optional<BookkeepingPostingRejection> sweptInterimResultRejection =
        postingSweptInterimResultPolicy.rejectionFor(acceptedPosting, book);
    if (sweptInterimResultRejection.isPresent()) {
      return new Decision.Rejected(sweptInterimResultRejection.orElseThrow());
    }
    Optional<BookkeepingPostingRejection> openingPositionWindowRejection =
        openingPositionAcceptancePolicy.windowRejection(acceptedPosting, book);
    if (openingPositionWindowRejection.isPresent()) {
      return new Decision.Rejected(openingPositionWindowRejection.orElseThrow());
    }
    Optional<BookkeepingPostingRejection> accountRejection =
        postingAccountStatePolicy.rejectionFor(acceptedPosting, book);
    if (accountRejection.isPresent()) {
      return new Decision.Rejected(accountRejection.orElseThrow());
    }
    Optional<BookkeepingPostingRejection> openingPositionNominalRejection =
        openingPositionAcceptancePolicy.nominalAccountRejection(acceptedPosting, book);
    if (openingPositionNominalRejection.isPresent()) {
      return new Decision.Rejected(openingPositionNominalRejection.orElseThrow());
    }
    Optional<BookkeepingPostingRejection> resultHoldingReservationRejection =
        new ReservedResultClassificationPolicy(
                KernelAccountingRulesResolver.forBookIdentity(bookIdentity).closePostingPolicy())
            .rejectionFor(acceptedPosting, bookIdentity, book);
    if (resultHoldingReservationRejection.isPresent()) {
      return new Decision.Rejected(resultHoldingReservationRejection.orElseThrow());
    }
    Optional<BookkeepingPostingRejection> reversalRejection =
        ReversalAcceptancePolicy.rejectionFor(acceptedPosting, book);
    if (reversalRejection.isPresent()) {
      return new Decision.Rejected(reversalRejection.orElseThrow());
    }
    return new Decision.Accepted(requestFingerprint, acceptedPosting);
  }

  static BookIdentity initializedBookIdentity(PostingValidationStore book) {
    return Objects.requireNonNull(book, "book").requireInitializedBookIdentity();
  }

  static boolean isInternalSystemPosting(PostingRequestModel postingRequest) {
    return switch (postingRequest) {
      case AcceptedPosting acceptedPosting ->
          acceptedPosting.sourceChannel() == SourceChannel.SYSTEM;
      case PostingCommand command -> command.sourceChannel() == SourceChannel.SYSTEM;
      case PostingDraft draft -> draft.provenance().sourceChannel() == SourceChannel.SYSTEM;
    };
  }

  private static AcceptedPosting resolveAcceptedPosting(
      PostingRequestModel postingRequest, PostingValidationStore book) {
    if (postingRequest.callerAuthoredEntry().isPresent()) {
      BookkeepingEntry callerAuthoredEntry = postingRequest.callerAuthoredEntry().orElseThrow();
      PostEntryResolutionSupport.ResolutionOutcome resolutionOutcome =
          PostEntryResolutionSupport.resolve(callerAuthoredEntry, book);
      if (resolutionOutcome.rejection().isPresent()) {
        throw new AcceptedPostingResolutionFailure(resolutionOutcome.rejection().orElseThrow());
      }
      BookkeepingEntry resolvedEntry = resolutionOutcome.entry();
      return new AcceptedPosting(
          resolvedEntry.journalEntry(),
          postingLineage(resolvedEntry),
          resolvedEntry.postingKind(),
          resolvedEntry.postingOriginKind(),
          postingRequest.evidence(),
          postingRequest.requestProvenance(),
          postingRequest.sourceChannel(),
          callerAuthoredEntry,
          resolvedEntry,
          resolutionOutcome.resolution().inventoryMovements(),
          resolutionOutcome.resolution().resultingInventoryStates());
    }
    return new AcceptedPosting(
        postingRequest.journalEntry(),
        postingRequest.postingLineage(),
        postingRequest.postingKind(),
        postingRequest.postingOriginKind(),
        postingRequest.evidence(),
        postingRequest.requestProvenance(),
        postingRequest.sourceChannel(),
        null,
        postingRequest.resolvedOriginatingEntry().orElse(null),
        List.of(),
        Map.of());
  }

  private static PostingLineageModel postingLineage(BookkeepingEntry entry) {
    return switch (entry.postingLineage()) {
      case dev.erst.fingrind.contract.bookkeeping.PostingLineage.Direct _ ->
          PostingLineageModel.direct();
      case dev.erst.fingrind.contract.bookkeeping.PostingLineage.Reversal reversal ->
          PostingLineageModel.reversal(reversal.reference(), reversal.reason());
    };
  }

  /** Signals that accepted-posting resolution produced a deterministic published rejection. */
  private static final class AcceptedPostingResolutionFailure extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final transient BookkeepingPostingRejection rejection;

    private AcceptedPostingResolutionFailure(BookkeepingPostingRejection rejection) {
      super("Accepted posting resolution failed.");
      this.rejection = Objects.requireNonNull(rejection, "rejection");
    }

    private BookkeepingPostingRejection rejection() {
      return rejection;
    }
  }
}
