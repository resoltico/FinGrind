package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.ListPostingsQuery;
import dev.erst.fingrind.contract.PostingPage;
import dev.erst.fingrind.contract.PostingPageCursor;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.PostingCommitResult;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.Test;

/** Unit and integration tests for {@link SqlitePostingFactStore}. */
@NullUnmarked
class SqlitePostingQueryTest extends SqlitePostingFactStoreTestSupport {

  @Test
  void listPostings_requiresInitializedBookForMissingAndBlankBooks() throws Exception {
    ListPostingsQuery firstPage =
        new ListPostingsQuery(Optional.empty(), null, null, 2, Optional.empty());

    Path missingBookPath = tempDirectory.resolve("list-postings-missing.sqlite");
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(missingBookPath))) {
      assertInitializedQueryViewFailure(() -> postingFactStore.listPostings(firstPage));
    }

    Path blankBookPath = tempDirectory.resolve("list-postings-blank.sqlite");
    createEmptySqliteFile(blankBookPath);
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(blankBookPath))) {
      assertInitializedQueryViewFailure(() -> postingFactStore.listPostings(firstPage));
    }
  }

  @Test
  void listPostings_filtersAndPaginatesCommittedPostings() {
    Path databasePath = tempDirectory.resolve("list-postings.sqlite");
    CommittedPosting postingOne =
        postingFact(
            "posting-1",
            "idem-1",
            LocalDate.parse("2026-04-07"),
            Instant.parse("2026-04-07T10:15:30Z"),
            List.of(
                line("1000", JournalLine.EntrySide.DEBIT, "EUR", "10.00"),
                line("2000", JournalLine.EntrySide.CREDIT, "EUR", "10.00")));
    CommittedPosting postingTwo =
        postingFact(
            "posting-2",
            "idem-2",
            LocalDate.parse("2026-04-08"),
            Instant.parse("2026-04-08T10:15:30Z"),
            List.of(
                line("3000", JournalLine.EntrySide.DEBIT, "EUR", "20.00"),
                line("2000", JournalLine.EntrySide.CREDIT, "EUR", "20.00")));
    CommittedPosting postingThree =
        postingFact(
            "posting-3",
            "idem-3",
            LocalDate.parse("2026-04-09"),
            Instant.parse("2026-04-09T10:15:30Z"),
            List.of(
                line("1000", JournalLine.EntrySide.DEBIT, "EUR", "30.00"),
                line("2000", JournalLine.EntrySide.CREDIT, "EUR", "30.00")));

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      initializeBookWithDefaultAccounts(postingFactStore);
      assertEquals(
          new AccountDeclarationOutcome.Declared(
              new RegisteredAccount(
                  new AccountCode("3000"),
                  new AccountName("Receivable"),
                  NormalBalance.DEBIT,
                  true,
                  Instant.parse("2026-04-07T10:15:30Z"))),
          postingFactStore.declareAccount(
              new AccountCode("3000"),
              new AccountName("Receivable"),
              NormalBalance.DEBIT,
              Instant.parse("2026-04-07T10:15:30Z")));
      assertEquals(
          new PostingCommitResult.Committed(postingOne), postingFactStore.commit(postingOne));
      assertEquals(
          new PostingCommitResult.Committed(postingTwo), postingFactStore.commit(postingTwo));
      assertEquals(
          new PostingCommitResult.Committed(postingThree), postingFactStore.commit(postingThree));

      assertEquals(
          new PostingPage(
              List.of(publishedPostingFact(postingThree), publishedPostingFact(postingTwo)),
              2,
              Optional.of(PostingPageCursor.fromPosting(publishedPostingFact(postingTwo)))),
          postingFactStore.listPostings(
              new ListPostingsQuery(Optional.empty(), null, null, 2, Optional.empty())));
      assertEquals(
          new PostingPage(List.of(publishedPostingFact(postingOne)), 2, Optional.empty()),
          postingFactStore.listPostings(
              new ListPostingsQuery(
                  Optional.empty(),
                  null,
                  null,
                  2,
                  Optional.of(PostingPageCursor.fromPosting(publishedPostingFact(postingTwo))))));
      assertEquals(
          new PostingPage(List.of(publishedPostingFact(postingOne)), 50, Optional.empty()),
          postingFactStore.listPostings(
              new ListPostingsQuery(
                  Optional.of(new AccountCode("1000")),
                  LocalDate.parse("2026-04-07"),
                  LocalDate.parse("2026-04-08"),
                  50,
                  Optional.empty())));
    }
  }

  @Test
  void findByPostingId_returnsEmptyWhenExistingBookHasNoMatchingPosting() {
    Path databasePath = tempDirectory.resolve("books").resolve("entity-a.sqlite");
    CommittedPosting postingFact =
        postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty());

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      initializeBookWithDefaultAccounts(postingFactStore);
      postingFactStore.commit(postingFact);

      assertEquals(
          Optional.empty(), postingFactStore.findPosting(new PostingId("posting-missing")));
    }
  }

  @Test
  void findByIdempotency_rejectsForeignSqliteFileWithPostingLikeSchema() {
    Path bookPath = tempDirectory.resolve("missing-line-table.sqlite");
    createPostingFactOnlyBook(bookPath);

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(bookPath))) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.findExistingPosting(new IdempotencyKey("idem-partial")));

      assertEquals("The selected SQLite file is not a FinGrind book.", exception.getMessage());
    }
  }

  @Test
  void executeFindOnePosting_closesStatementWhenRowMappingFails() throws Exception {
    Path bookPath = tempDirectory.resolve("row-mapping-failure.sqlite");
    createEmptySqliteFile(bookPath);
    assertDoesNotThrow(
        () ->
            withStandaloneDatabase(
                bookAccess(bookPath),
                database -> {
                  assertThrows(
                      NullPointerException.class,
                      () ->
                          SqliteStatementQueries.findOneCommittedPosting(
                              database,
                              "select null as posting_id",
                              statement -> {},
                              postingId -> List.of()));
                }));
  }
}
