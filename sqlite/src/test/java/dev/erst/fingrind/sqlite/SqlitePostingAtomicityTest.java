package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.executor.spi.PostingCommitResult;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Proves SQLite posting commits roll back fully when failures interrupt one transaction. */
class SqlitePostingAtomicityTest extends SqlitePostingFactStoreTestSupport {
  @Test
  void ordinaryPosting_failureAfterPostingFactInsert_leavesNoDurableResidue_andRetrySucceeds()
      throws Exception {
    Path bookPath = tempDirectory.resolve("atomicity-after-posting-fact.sqlite");
    OneShotCommitFaultHook faultHook =
        OneShotCommitFaultHook.afterPostingFactInsert(new PostingId("posting-1"));
    try (SqliteBookPassphrase bookPassphrase =
            SqliteBookPassphrase.fromCharacters("atomicity ordinary", TEST_BOOK_KEY.toCharArray());
        SqlitePostingFactStore postingFactStore =
            new SqlitePostingFactStore(
                bookPath,
                bookPassphrase,
                SqliteStoreAccessMode.READ_WRITE_CREATE,
                SqliteNativeBootstrap::api,
                faultHook)) {
      initializeBookWithMinimalNumericAccounts(postingFactStore);

      IllegalStateException failure =
          assertThrows(
              IllegalStateException.class,
              () ->
                  commitPosting(
                      postingFactStore,
                      postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty())));
      assertEquals("forced failure after posting_fact insert", failure.getMessage());
      assertEquals(
          0,
          queryInt(
              requireStoreDatabase(postingFactStore),
              "select count(*) from posting_fact where posting_id = 'posting-1'"));
      assertEquals(
          0,
          queryInt(
              requireStoreDatabase(postingFactStore),
              "select count(*) from journal_line where posting_id = 'posting-1'"));
      assertEquals(
          0,
          queryInt(
              requireStoreDatabase(postingFactStore),
              "select count(*) from audit_event where posting_id = 'posting-1'"));

      assertEquals(
          new PostingCommitResult.Committed(
              postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty()), false),
          commitPosting(
              postingFactStore,
              postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty())));
    }
  }

  @Test
  void reversalPosting_failureBeforeJournalLinePersistence_rollsBackFully_andRetrySucceeds()
      throws Exception {
    Path bookPath = tempDirectory.resolve("atomicity-before-persist-journal.sqlite");
    OneShotCommitFaultHook faultHook =
        OneShotCommitFaultHook.beforePersistJournalLines(new PostingId("posting-2"));
    try (SqliteBookPassphrase bookPassphrase =
            SqliteBookPassphrase.fromCharacters("atomicity reversal", TEST_BOOK_KEY.toCharArray());
        SqlitePostingFactStore postingFactStore =
            new SqlitePostingFactStore(
                bookPath,
                bookPassphrase,
                SqliteStoreAccessMode.READ_WRITE_CREATE,
                SqliteNativeBootstrap::api,
                faultHook)) {
      initializeBookWithMinimalNumericAccounts(postingFactStore);
      assertEquals(
          new PostingCommitResult.Committed(
              postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty()), false),
          commitPosting(
              postingFactStore,
              postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty())));

      IllegalStateException failure =
          assertThrows(
              IllegalStateException.class,
              () ->
                  commitPosting(
                      postingFactStore,
                      postingFact(
                          "posting-2",
                          "idem-2",
                          Optional.of(new ReversalReference(new PostingId("posting-1"))),
                          Optional.of(new ReversalReason("full reversal")))));
      assertEquals("forced failure before journal_line persistence", failure.getMessage());
      assertEquals(
          0,
          queryInt(
              requireStoreDatabase(postingFactStore),
              "select count(*) from posting_fact where posting_id = 'posting-2'"));
      assertEquals(
          0,
          queryInt(
              requireStoreDatabase(postingFactStore),
              "select count(*) from journal_line where posting_id = 'posting-2'"));
      assertEquals(
          0,
          queryInt(
              requireStoreDatabase(postingFactStore),
              "select count(*) from audit_event where posting_id = 'posting-2'"));

      assertEquals(
          new PostingCommitResult.Committed(
              postingFact(
                  "posting-2",
                  "idem-2",
                  Optional.of(new ReversalReference(new PostingId("posting-1"))),
                  Optional.of(new ReversalReason("full reversal"))),
              false),
          commitPosting(
              postingFactStore,
              postingFact(
                  "posting-2",
                  "idem-2",
                  Optional.of(new ReversalReference(new PostingId("posting-1"))),
                  Optional.of(new ReversalReason("full reversal")))));
    }
  }

  /**
   * One deterministic failure hook that trips once for one selected posting and injection point.
   */
  private static final class OneShotCommitFaultHook implements SqliteCommitFaultHook {
    private final PostingId targetPostingId;
    private final InjectionPoint injectionPoint;
    private boolean tripped;

    private OneShotCommitFaultHook(PostingId targetPostingId, InjectionPoint injectionPoint) {
      this.targetPostingId = targetPostingId;
      this.injectionPoint = injectionPoint;
    }

    static OneShotCommitFaultHook afterPostingFactInsert(PostingId postingId) {
      return new OneShotCommitFaultHook(postingId, InjectionPoint.AFTER_POSTING_FACT_INSERT);
    }

    static OneShotCommitFaultHook beforePersistJournalLines(PostingId postingId) {
      return new OneShotCommitFaultHook(postingId, InjectionPoint.BEFORE_PERSIST_JOURNAL_LINES);
    }

    @Override
    public void afterPostingFactInserted(
        dev.erst.fingrind.executor.bookkeeping.CommittedPosting posting) {
      maybeFail(posting, InjectionPoint.AFTER_POSTING_FACT_INSERT);
    }

    @Override
    public void beforePersistJournalLines(
        dev.erst.fingrind.executor.bookkeeping.CommittedPosting posting) {
      maybeFail(posting, InjectionPoint.BEFORE_PERSIST_JOURNAL_LINES);
    }

    private void maybeFail(
        dev.erst.fingrind.executor.bookkeeping.CommittedPosting posting,
        InjectionPoint currentInjectionPoint) {
      if (!tripped
          && injectionPoint == currentInjectionPoint
          && posting.postingId().equals(targetPostingId)) {
        tripped = true;
        throw new IllegalStateException(
            switch (currentInjectionPoint) {
              case AFTER_POSTING_FACT_INSERT -> "forced failure after posting_fact insert";
              case BEFORE_PERSIST_JOURNAL_LINES -> "forced failure before journal_line persistence";
            });
      }
    }

    /** The precise injected-commit point where one deterministic failure should trip. */
    private enum InjectionPoint {
      AFTER_POSTING_FACT_INSERT,
      BEFORE_PERSIST_JOURNAL_LINES
    }
  }
}
