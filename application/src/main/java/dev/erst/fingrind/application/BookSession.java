package dev.erst.fingrind.application;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Application-owned book session seam for committed posting facts. */
public interface BookSession extends AutoCloseable {
  /** Reports whether the selected book already carries the explicit initialization marker. */
  boolean isInitialized();

  /** Explicitly initializes one new book if the selected path is currently empty. */
  OpenBookResult openBook(Instant initializedAt);

  /** Looks up one declared account in the selected book. */
  Optional<DeclaredAccount> findAccount(AccountCode accountCode);

  /** Declares or reactivates one account in the selected book. */
  DeclareAccountResult declareAccount(
      AccountCode accountCode,
      AccountName accountName,
      NormalBalance normalBalance,
      Instant declaredAt);

  /** Returns the full account registry for the selected book. */
  List<DeclaredAccount> listAccounts();

  /** Looks up one existing posting fact by book-local idempotency identity. */
  Optional<PostingFact> findByIdempotency(IdempotencyKey idempotencyKey);

  /** Looks up one existing posting fact by its durable posting identity. */
  Optional<PostingFact> findByPostingId(PostingId postingId);

  /** Looks up an existing full reversal for one prior posting, if such a reversal exists. */
  Optional<PostingFact> findReversalFor(PostingId priorPostingId);

  /** Attempts one durable commit and returns the ordinary application outcome explicitly. */
  PostingCommitResult commit(PostingFact postingFact);

  @Override
  void close();
}
