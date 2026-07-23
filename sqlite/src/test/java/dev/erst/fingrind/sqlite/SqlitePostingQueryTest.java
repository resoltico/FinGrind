package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.PostingPage;
import dev.erst.fingrind.contract.bookkeeping.PostingPageCursor;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.attestation.AttestationVerification;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryCursor;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryQuery;
import dev.erst.fingrind.executor.spi.PostingCommitResult;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Unit and integration tests for {@link SqlitePostingFactStore}. */
class SqlitePostingQueryTest extends SqlitePostingFactStoreTestSupport {
  @Test
  void postingCommitmentProjection_verifiesTheCompleteEvidenceChainBeforeReturningALink() {
    Path databasePath = tempDirectory.resolve("posting-attestation-commitment.sqlite");
    CommittedPosting posting =
        postingFact(
            "posting-attested",
            "idem-attested",
            LocalDate.parse("2026-04-07"),
            Instant.parse("2026-04-07T10:15:30Z"),
            List.of(
                line("1000", JournalLine.EntrySide.DEBIT, "EUR", "10.00"),
                line("2000", JournalLine.EntrySide.CREDIT, "EUR", "10.00")));
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(databasePath));
        SqliteReadSession readSession = SqliteCapabilitySessions.read(postingFactStore)) {
      initializeBookWithMinimalNumericAccounts(postingFactStore);
      PostingCommitResult.Committed committed =
          assertInstanceOf(
              PostingCommitResult.Committed.class, commitPosting(postingFactStore, posting));
      AttestationVerification verification =
          Objects.requireNonNull(committed.attestationVerification());

      assertEquals(
          Map.of(
              posting.postingId(),
              new dev.erst.fingrind.contract.bookkeeping.AttestationCommit(
                  verification.headOrder(),
                  HexFormat.of().formatHex(verification.operationHead()))),
          readSession.attestationCommitsFor(
              Set.of(posting.postingId(), new PostingId("00000000-0000-0000-0000-000000000001"))));
    }
  }

  @Test
  void postingCommitmentProjection_returnsEmptyForAnEmptyRequestWithoutReadingEvidence() {
    Path databasePath = tempDirectory.resolve("posting-attestation-empty-request.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(databasePath));
        SqliteReadSession readSession = SqliteCapabilitySessions.read(postingFactStore)) {
      initializeBookWithMinimalNumericAccounts(postingFactStore);

      assertEquals(Map.of(), readSession.attestationCommitsFor(Set.of()));
    }
  }

  @Test
  void postingCommitmentProjection_rejectsANonEmptyRequestWhenEvidenceCannotBeVerified() {
    Path databasePath = tempDirectory.resolve("posting-attestation-unverified.sqlite");
    BookAccess access = bookAccess(databasePath);
    try (SqlitePostingFactStore postingFactStore = openStore(access);
        SqliteReadSession readSession = SqliteCapabilitySessions.read(postingFactStore)) {
      initializeBookWithMinimalNumericAccounts(postingFactStore);
      requireStoreDatabase(postingFactStore)
          .executeStatement("drop trigger attestation_operation_reject_update");
      requireStoreDatabase(postingFactStore)
          .executeStatement(
              """
          update attestation_operation
          set effect_preimage_base64 = 'AA=='
          where operation_order_hex = '0000000000000000'
          """);

      SqliteProtectedBookVerificationException exception =
          assertThrows(
              SqliteProtectedBookVerificationException.class,
              () ->
                  readSession.attestationCommitsFor(
                      Set.of(new PostingId("00000000-0000-0000-0000-000000000002"))));

      assertEquals(
          "Protected-book authentication or integrity verification failed.",
          exception.getMessage());
    }
  }

  @Test
  void listPostings_requiresInitializedBookForMissingAndBlankBooks() throws Exception {
    PostingHistoryQuery firstPage =
        new PostingHistoryQuery(Optional.empty(), null, null, 2, Optional.empty());
    Path missingBookPath = tempDirectory.resolve("list-postings-missing.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(missingBookPath))) {
      assertInitializedQueryViewFailure(() -> postingFactStore.listPostings(firstPage));
    }
    Path blankBookPath = tempDirectory.resolve("list-postings-blank.sqlite");
    createEmptySqliteFile(blankBookPath);
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(blankBookPath))) {
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
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(databasePath))) {
      initializeBookWithMinimalNumericAccounts(postingFactStore);
      assertDeclaredWithAttestation(
          registeredAccount(
              new AccountCode("3000"),
              new AccountName("Receivable"),
              dev.erst.fingrind.core.AccountType.ASSET,
              NormalBalance.DEBIT,
              true,
              Instant.parse("2026-04-07T10:15:30Z")),
          declareAccount(
              postingFactStore,
              new AccountCode("3000"),
              new AccountName("Receivable"),
              dev.erst.fingrind.core.AccountType.ASSET,
              NormalBalance.DEBIT,
              Instant.parse("2026-04-07T10:15:30Z")));
      assertFreshCommittedPosting(postingOne, commitPosting(postingFactStore, postingOne));
      assertFreshCommittedPosting(postingTwo, commitPosting(postingFactStore, postingTwo));
      assertFreshCommittedPosting(postingThree, commitPosting(postingFactStore, postingThree));
      PostingHistoryQuery firstPageQuery =
          new PostingHistoryQuery(Optional.empty(), null, null, 2, Optional.empty());
      assertEquals(
          new PostingPage(
              bookIdentity(),
              Optional.empty(),
              EffectiveDateRange.unbounded(),
              List.of(publishedPostingFact(postingThree), publishedPostingFact(postingTwo)),
              2,
              Optional.of(PostingPageCursor.fromPosting(publishedPostingFact(postingTwo))),
              Map.of(),
              Map.of()),
          published(firstPageQuery, postingFactStore.listPostings(firstPageQuery)));
      PostingHistoryQuery secondPageQuery =
          new PostingHistoryQuery(
              Optional.empty(),
              null,
              null,
              2,
              Optional.of(PostingHistoryCursor.fromPosting(postingTwo)));
      assertEquals(
          new PostingPage(
              bookIdentity(),
              Optional.empty(),
              EffectiveDateRange.unbounded(),
              List.of(publishedPostingFact(postingOne)),
              2,
              Optional.empty(),
              Map.of(),
              Map.of()),
          published(secondPageQuery, postingFactStore.listPostings(secondPageQuery)));
      PostingHistoryQuery filteredQuery =
          new PostingHistoryQuery(
              Optional.of(new AccountCode("1000")),
              LocalDate.parse("2026-04-07"),
              LocalDate.parse("2026-04-08"),
              50,
              Optional.empty());
      assertEquals(
          new PostingPage(
              bookIdentity(),
              Optional.of(new AccountCode("1000")),
              new EffectiveDateRange.Bounded(
                  LocalDate.parse("2026-04-07"), LocalDate.parse("2026-04-08")),
              List.of(publishedPostingFact(postingOne)),
              50,
              Optional.empty(),
              Map.of(),
              Map.of()),
          published(filteredQuery, postingFactStore.listPostings(filteredQuery)));
    }
  }

  @Test
  void findByPostingId_returnsEmptyWhenExistingBookHasNoMatchingPosting() {
    Path databasePath = tempDirectory.resolve("books").resolve("entity-a.sqlite");
    CommittedPosting postingFact =
        postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty());
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(databasePath))) {
      initializeBookWithMinimalNumericAccounts(postingFactStore);
      commitPosting(postingFactStore, postingFact);
      assertEquals(
          Optional.empty(),
          postingFactStore.findPosting(new PostingId("6045a122-24d5-3839-bfbe-fd3f0590e5b6")));
    }
  }

  @Test
  void findByIdempotency_rejectsForeignSqliteFileWithPostingLikeSchema() {
    Path bookPath = tempDirectory.resolve("missing-line-table.sqlite");
    createPostingFactOnlyBook(bookPath);
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath))) {
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
                              postingId ->
                                  new SqlitePostingAttachments(
                                      List.of(),
                                      SqlitePostingFactFixtureSupport.accountingEvidence("idem-1"),
                                      null,
                                      null)));
                }));
  }
}
