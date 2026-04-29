package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.contract.DeclareAccountResult;
import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.PostingFact;
import dev.erst.fingrind.contract.PostingLineage;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.CurrencyCode;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.core.SourceChannel;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.NullUnmarked;

/** Shared SQLite posting/book fixtures and native-handle doubles for split store tests. */
@NullUnmarked
class SqlitePostingFactFixtureSupport extends SqliteStoreFixtureSupport {
  static PostingFact postingFact(
      String postingId,
      String idempotencyKey,
      Optional<ReversalReference> reversalReference,
      Optional<ReversalReason> reason) {
    return new PostingFact(
        new PostingId(postingId),
        journalEntry(reversalReference),
        postingLineage(reversalReference, reason),
        new CommittedProvenance(
            new RequestProvenance(
                new ActorId("actor-1"),
                ActorType.AGENT,
                new CommandId("command-" + postingId),
                new IdempotencyKey(idempotencyKey),
                new CausationId("cause-1"),
                Optional.of(new CorrelationId("corr-1"))),
            Instant.parse("2026-04-07T10:15:30Z"),
            SourceChannel.CLI));
  }

  static PostingFact postingFact(
      String postingId,
      String idempotencyKey,
      LocalDate effectiveDate,
      Instant recordedAt,
      List<JournalLine> lines) {
    return new PostingFact(
        new PostingId(postingId),
        new JournalEntry(effectiveDate, lines),
        PostingLineage.direct(),
        new CommittedProvenance(
            new RequestProvenance(
                new ActorId("actor-1"),
                ActorType.AGENT,
                new CommandId("command-" + postingId),
                new IdempotencyKey(idempotencyKey),
                new CausationId("cause-1"),
                Optional.of(new CorrelationId("corr-1"))),
            recordedAt,
            SourceChannel.CLI));
  }

  static PostingLineage postingLineage(
      Optional<ReversalReference> reversalReference, Optional<ReversalReason> reason) {
    if (reversalReference.isEmpty()) {
      return PostingLineage.direct();
    }
    return PostingLineage.reversal(reversalReference.orElseThrow(), reason.orElseThrow());
  }

  static void initializeBookWithDefaultAccounts(SqlitePostingFactStore postingFactStore) {
    postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z"));
    declareDefaultAccounts(postingFactStore);
  }

  static void declareDefaultAccounts(SqlitePostingFactStore postingFactStore) {
    assertEquals(
        new DeclareAccountResult.Declared(
            new DeclaredAccount(
                new AccountCode("1000"),
                new AccountName("Cash"),
                NormalBalance.DEBIT,
                true,
                Instant.parse("2026-04-07T10:15:30Z"))),
        postingFactStore.declareAccount(
            new AccountCode("1000"),
            new AccountName("Cash"),
            NormalBalance.DEBIT,
            Instant.parse("2026-04-07T10:15:30Z")));
    assertEquals(
        new DeclareAccountResult.Declared(
            new DeclaredAccount(
                new AccountCode("2000"),
                new AccountName("Revenue"),
                NormalBalance.CREDIT,
                true,
                Instant.parse("2026-04-07T10:15:30Z"))),
        postingFactStore.declareAccount(
            new AccountCode("2000"),
            new AccountName("Revenue"),
            NormalBalance.CREDIT,
            Instant.parse("2026-04-07T10:15:30Z")));
  }

  static JournalEntry journalEntry(Optional<ReversalReference> reversalReference) {
    if (reversalReference.isPresent()) {
      return new JournalEntry(
          LocalDate.parse("2026-04-07"),
          List.of(
              line("1000", JournalLine.EntrySide.CREDIT, "10.00"),
              line("2000", JournalLine.EntrySide.DEBIT, "10.00")));
    }
    return new JournalEntry(
        LocalDate.parse("2026-04-07"),
        List.of(
            line("1000", JournalLine.EntrySide.DEBIT, "10.00"),
            line("2000", JournalLine.EntrySide.CREDIT, "10.00")));
  }

  static JournalLine line(String accountCode, JournalLine.EntrySide side, String amount) {
    return new JournalLine(
        new AccountCode(accountCode),
        side,
        new Money(new CurrencyCode("EUR"), new java.math.BigDecimal(amount)));
  }

  static JournalLine line(
      String accountCode, JournalLine.EntrySide side, String currencyCode, String amount) {
    return new JournalLine(new AccountCode(accountCode), side, money(currencyCode, amount));
  }

  static Money money(String currencyCode, String amount) {
    return new Money(new CurrencyCode(currencyCode), new java.math.BigDecimal(amount));
  }

  static void insertPostingFactRow(
      SqliteNativeDatabase database, String postingId, String idempotencyKey) {
    database.executeStatement(
        """
        insert into posting_fact (
            posting_id,
            effective_date,
            recorded_at,
            actor_id,
            actor_type,
            command_id,
            idempotency_key,
            causation_id,
            correlation_id,
            reason,
            source_channel,
            prior_posting_id
        ) values (
            '%s',
            '2026-04-07',
            '2026-04-07T10:15:30Z',
            'actor-1',
            'AGENT',
            'command-%s',
            '%s',
            'cause-1',
            null,
            null,
            '%s',
            null
        )
        """
            .formatted(postingId, postingId, idempotencyKey, SourceChannel.CLI.wireValue()));
  }
}
