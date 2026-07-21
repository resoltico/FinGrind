package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.spi.PostingCommitResult;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Field-tests durable retention of approval evidence attached to a caller-authored posting. */
class SqlitePostingApprovalPersistenceFieldTest extends SqlitePostingFactStoreTestSupport {
  @Test
  void committedPosting_persistsEveryApprovalAlongsideItsSourceEvidence() {
    Path bookPath = tempDirectory.resolve("approval-evidence.sqlite");
    try (SqlitePostingFactStore store = openStore(bookAccess(bookPath))) {
      initializeBookWithMinimalNumericAccounts(store);
      dev.erst.fingrind.executor.bookkeeping.CommittedPosting posting =
          SqlitePostingFactFixtureSupport.postingFactWithEvidence(
              "approved-posting",
              "approved-idempotency-key",
              Optional.empty(),
              Optional.empty(),
              SqlitePostingFactFixtureSupport.accountingEvidenceWithApproval("approved-posting"));

      assertEquals(
          posting,
          assertInstanceOf(PostingCommitResult.Committed.class, commitPosting(store, posting))
              .postingFact());
      assertEquals(
          1,
          queryInt(
              requireStoreDatabase(store),
              "select count(*) from posting_approval where posting_id = '%s'"
                  .formatted(posting.postingId().value())));
      assertEquals(
          Optional.of(posting), store.findPosting(new PostingId(posting.postingId().value())));
    }
  }
}
