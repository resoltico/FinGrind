package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountSemantics;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPublishedLanguageTranslator;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PostingLineageModel;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Shared SQLite posting/book fixtures and native-handle doubles for split store tests. */
class SqlitePostingFactFixtureSupport extends SqliteStoreFixtureSupport {
  static CommittedPosting postingFact(
      String postingId,
      String idempotencyKey,
      Optional<ReversalReference> reversalReference,
      Optional<ReversalReason> reason) {
    return new CommittedPosting(
        new PostingId(postingId),
        journalEntry(reversalReference),
        postingLineage(reversalReference, reason),
        PostingKind.STANDARD,
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

  static CommittedPosting postingFact(
      String postingId,
      String idempotencyKey,
      LocalDate effectiveDate,
      Instant recordedAt,
      List<JournalLine> lines) {
    return new CommittedPosting(
        new PostingId(postingId),
        new JournalEntry(effectiveDate, lines),
        PostingLineageModel.direct(),
        PostingKind.STANDARD,
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

  static PostingLineageModel postingLineage(
      Optional<ReversalReference> reversalReference, Optional<ReversalReason> reason) {
    if (reversalReference.isEmpty()) {
      return PostingLineageModel.direct();
    }
    return PostingLineageModel.reversal(reversalReference.orElseThrow(), reason.orElseThrow());
  }

  static dev.erst.fingrind.contract.bookkeeping.PostingFact publishedPostingFact(
      CommittedPosting postingFact) {
    return BookkeepingPublishedLanguageTranslator.toPublished(postingFact);
  }

  static DeclaredAccount publishedAccount(RegisteredAccount account) {
    return BookkeepingPublishedLanguageTranslator.toPublished(account);
  }

  static AccountRole accountRole(AccountType accountType, NormalBalance normalBalance) {
    Objects.requireNonNull(accountType, "accountType");
    Objects.requireNonNull(normalBalance, "normalBalance");
    return AccountSemantics.normalBalance(accountType, AccountRole.ORDINARY) == normalBalance
        ? AccountRole.ORDINARY
        : AccountRole.CONTRA;
  }

  static RegisteredAccount registeredAccount(
      AccountCode accountCode,
      AccountName accountName,
      AccountType accountType,
      NormalBalance normalBalance,
      boolean active,
      Instant declaredAt) {
    return new RegisteredAccount(
        accountCode,
        accountName,
        accountType,
        accountRole(accountType, normalBalance),
        active,
        declaredAt);
  }

  static DeclaredAccount declaredAccount(
      AccountCode accountCode,
      AccountName accountName,
      AccountType accountType,
      NormalBalance normalBalance,
      boolean active,
      Instant declaredAt) {
    return publishedAccount(
        registeredAccount(
            accountCode, accountName, accountType, normalBalance, active, declaredAt));
  }

  static AccountDeclarationOutcome declareAccount(
      SqlitePostingFactStore postingFactStore,
      AccountCode accountCode,
      AccountName accountName,
      AccountType accountType,
      NormalBalance normalBalance,
      Instant declaredAt) {
    return postingFactStore.declareAccount(
        accountCode, accountName, accountType, accountRole(accountType, normalBalance), declaredAt);
  }

  static void initializeBookWithDefaultAccounts(SqlitePostingFactStore postingFactStore) {
    postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z"));
    declareDefaultAccounts(postingFactStore);
  }

  static void declareDefaultAccounts(SqlitePostingFactStore postingFactStore) {
    assertEquals(
        new AccountDeclarationOutcome.Declared(
            registeredAccount(
                new AccountCode("1000"),
                new AccountName("Cash"),
                dev.erst.fingrind.core.AccountType.ASSET,
                NormalBalance.DEBIT,
                true,
                Instant.parse("2026-04-07T10:15:30Z"))),
        declareAccount(
            postingFactStore,
            new AccountCode("1000"),
            new AccountName("Cash"),
            dev.erst.fingrind.core.AccountType.ASSET,
            NormalBalance.DEBIT,
            Instant.parse("2026-04-07T10:15:30Z")));
    assertEquals(
        new AccountDeclarationOutcome.Declared(
            registeredAccount(
                new AccountCode("2000"),
                new AccountName("Revenue"),
                dev.erst.fingrind.core.AccountType.REVENUE,
                NormalBalance.CREDIT,
                true,
                Instant.parse("2026-04-07T10:15:30Z"))),
        declareAccount(
            postingFactStore,
            new AccountCode("2000"),
            new AccountName("Revenue"),
            dev.erst.fingrind.core.AccountType.REVENUE,
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
    return new JournalLine(new AccountCode(accountCode), side, Money.parse("EUR", amount));
  }

  static JournalLine line(
      String accountCode, JournalLine.EntrySide side, String currencyCode, String amount) {
    return new JournalLine(new AccountCode(accountCode), side, money(currencyCode, amount));
  }

  static Money money(String currencyCode, String amount) {
    return Money.parse(currencyCode, amount);
  }

  static void insertPostingFactRow(
      SqliteNativeDatabase database, String postingId, String idempotencyKey) {
    database.executeStatement(
        """
        insert into posting_fact (
            posting_id,
            posting_kind,
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
            'STANDARD',
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
