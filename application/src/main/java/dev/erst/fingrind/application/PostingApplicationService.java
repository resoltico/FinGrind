package dev.erst.fingrind.application;

import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.runtime.PostingFact;
import dev.erst.fingrind.runtime.PostingFactStore;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** Application service that owns preflight and commit behavior for posting entries. */
public final class PostingApplicationService {
  private final PostingFactStore postingFactStore;
  private final Supplier<PostingId> postingIdSupplier;

  /** Creates the posting application service with its runtime seams. */
  public PostingApplicationService(
      PostingFactStore postingFactStore, Supplier<PostingId> postingIdSupplier) {
    this.postingFactStore = Objects.requireNonNull(postingFactStore, "postingFactStore");
    this.postingIdSupplier = Objects.requireNonNull(postingIdSupplier, "postingIdSupplier");
  }

  /** Validates a request and reports whether a later commit attempt is admissible. */
  public PostEntryResult preflight(PostEntryCommand command) {
    Optional<PostingFact> existingPosting = existingPosting(command);
    if (existingPosting.isPresent()) {
      return duplicateRejection(command);
    }
    return new PostEntryResult.PreflightAccepted(
        command.provenance().idempotencyKey(), command.journalEntry().effectiveDate());
  }

  /** Commits a request as one durable posting fact or returns a deterministic rejection. */
  public PostEntryResult commit(PostEntryCommand command) {
    Optional<PostingFact> existingPosting = existingPosting(command);
    if (existingPosting.isPresent()) {
      return duplicateRejection(command);
    }

    PostingFact postingFact =
        new PostingFact(postingIdSupplier.get(), command.journalEntry(), command.provenance());

    try {
      PostingFact committedPosting = postingFactStore.commit(postingFact);
      return new PostEntryResult.Committed(
          committedPosting.postingId(),
          committedPosting.provenance().idempotencyKey(),
          committedPosting.journalEntry().effectiveDate(),
          committedPosting.provenance().recordedAt());
    } catch (IllegalStateException exception) {
      return duplicateRejection(command);
    }
  }

  private Optional<PostingFact> existingPosting(PostEntryCommand command) {
    return postingFactStore.findByIdempotency(command.provenance().idempotencyKey());
  }

  private static PostEntryResult.Rejected duplicateRejection(PostEntryCommand command) {
    return new PostEntryResult.Rejected(
        PostingRejectionCode.DUPLICATE_IDEMPOTENCY_KEY,
        "A posting with the same idempotency key already exists in this book.",
        command.provenance().idempotencyKey());
  }
}
