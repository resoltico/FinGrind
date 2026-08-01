package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PostingAcceptancePolicy;
import dev.erst.fingrind.executor.bookkeeping.PostingAcceptancePolicy.Decision;
import dev.erst.fingrind.executor.spi.PostingDraft;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import java.util.List;
import java.util.Objects;

/** Admits and persists generated period-close postings against one SQLite transaction view. */
final class SqliteGeneratedClosePostingPersistence {
  private final SqliteStoreContext context;
  private final PostingAcceptancePolicy postingAcceptancePolicy;
  private final SqliteAcceptedPostingPersistence acceptedPostings;

  SqliteGeneratedClosePostingPersistence(
      SqliteStoreContext context,
      PostingAcceptancePolicy postingAcceptancePolicy,
      SqliteAcceptedPostingPersistence acceptedPostings) {
    this.context = Objects.requireNonNull(context, "context");
    this.postingAcceptancePolicy =
        Objects.requireNonNull(postingAcceptancePolicy, "postingAcceptancePolicy");
    this.acceptedPostings = Objects.requireNonNull(acceptedPostings, "acceptedPostings");
  }

  List<CommittedPosting> persistGeneratedPostings(
      SqliteNativeDatabase activeDatabase,
      List<PostingDraft> postingDrafts,
      PostingIdGenerator postingIdGenerator,
      String closeOperation) {
    SqliteTransactionValidationBook validationBook =
        new SqliteTransactionValidationBook(activeDatabase, context.postingReader(), true);
    PostingIdGenerator requiredPostingIdGenerator =
        Objects.requireNonNull(postingIdGenerator, "postingIdGenerator");
    List<CommittedPosting> postings = new java.util.ArrayList<>();
    for (PostingDraft postingDraft : postingDrafts) {
      switch (postingAcceptancePolicy.decisionFor(postingDraft, validationBook)) {
        case Decision.Replay replay -> postings.add(replay.postingFact());
        case Decision.Rejected rejected ->
            throw new IllegalStateException(
                "Generated "
                    + closeOperation
                    + " posting failed bookkeeping acceptance: "
                    + rejected.rejection());
        case Decision.Accepted accepted ->
            postings.add(
                acceptedPostings.persistAcceptedPosting(
                    activeDatabase,
                    accepted.acceptedPosting(),
                    accepted.requestFingerprint(),
                    postingDraft.provenance(),
                    requiredPostingIdGenerator));
      }
    }
    return postings;
  }
}
