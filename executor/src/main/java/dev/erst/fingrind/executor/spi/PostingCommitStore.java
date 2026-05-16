package dev.erst.fingrind.executor.spi;

/** Commits one admissible posting into durable storage. */
@FunctionalInterface
public interface PostingCommitStore {
  /** Attempts one durable commit and returns the ordinary application outcome explicitly. */
  PostingCommitResult commit(PostingDraft postingDraft, PostingIdGenerator postingIdGenerator);
}
