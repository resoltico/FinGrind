package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Verifies the durable append-only bookkeeping audit stream emitted by SQLite mutations. */
class SqliteAuditEventStreamTest extends SqlitePostingFactStoreTestSupport {
  @Test
  void mutationPaths_appendExpectedAuditEvents() {
    Path bookPath = tempDirectory.resolve("audit-event-sequence.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath))) {
      openBookWithNoDeclaredAccounts(postingFactStore);
      declareAccount(
          postingFactStore,
          new AccountCode("1000"),
          new AccountName("Cash"),
          AccountType.ASSET,
          NormalBalance.DEBIT,
          Instant.parse("2026-04-07T10:15:30Z"));
      declareAccount(
          postingFactStore,
          new AccountCode("2000"),
          new AccountName("Revenue"),
          AccountType.REVENUE,
          NormalBalance.CREDIT,
          Instant.parse("2026-04-07T10:15:30Z"));
      deactivateAccount(bookPath, "1000");
      declareAccount(
          postingFactStore,
          new AccountCode("1000"),
          new AccountName("Cash main"),
          AccountType.ASSET,
          NormalBalance.DEBIT,
          Instant.parse("2026-04-08T10:15:30Z"));
      commitPosting(
          postingFactStore, postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty()));
      commitPosting(
          postingFactStore,
          postingFact(
              "posting-2",
              "idem-2",
              Optional.of(new ReversalReference(new PostingId("posting-1"))),
              Optional.of(new ReversalReason("full reversal"))));

      assertEquals(
          "BOOK_OPENED:-:-|ACCOUNT_DECLARED:1000:-|ACCOUNT_DECLARED:2000:-|ACCOUNT_REACTIVATED:1000:-|POSTING_COMMITTED:-:posting-1|POSTING_REVERSED:-:posting-2",
          queryText(
              requireStoreDatabase(postingFactStore),
              """
              select group_concat(
                  event_kind || ':' || coalesce(account_code, '-') || ':' || coalesce(posting_id, '-'),
                  '|'
              )
              from (
                  select event_kind, account_code, posting_id
                  from audit_event
                  order by audit_event_order
              )
              """));
    }
  }
}
