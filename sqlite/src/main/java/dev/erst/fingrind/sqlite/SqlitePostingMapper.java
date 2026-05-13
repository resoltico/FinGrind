package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountRole;
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
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPublishedLanguageTranslator;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PostingLineageModel;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Maps SQLite native statement rows into FinGrind posting domain objects. */
final class SqlitePostingMapper {
  private SqlitePostingMapper() {}

  static RegisteredAccount registeredAccount(SqliteNativeStatement accountRow) {
    return new RegisteredAccount(
        new AccountCode(requiredText(accountRow, SqlitePostingSql.COL_ACCOUNT_CODE)),
        new AccountName(requiredText(accountRow, SqlitePostingSql.COL_ACCOUNT_NAME)),
        AccountType.fromWireValue(requiredText(accountRow, SqlitePostingSql.COL_ACCOUNT_TYPE)),
        AccountRole.fromWireValue(requiredText(accountRow, SqlitePostingSql.COL_ACCOUNT_ROLE)),
        requiredInt(accountRow, SqlitePostingSql.COL_ACCOUNT_ACTIVE) == 1,
        Instant.parse(requiredText(accountRow, SqlitePostingSql.COL_ACCOUNT_DECLARED_AT)));
  }

  static CommittedPosting committedPosting(
      SqliteNativeStatement postingRow, List<JournalLine> lines) {
    PostingId postingId = new PostingId(requiredText(postingRow, SqlitePostingSql.COL_POSTING_ID));
    JournalEntry journalEntry =
        new JournalEntry(
            LocalDate.parse(requiredText(postingRow, SqlitePostingSql.COL_EFFECTIVE_DATE)), lines);
    RequestProvenance requestProvenance =
        new RequestProvenance(
            new ActorId(requiredText(postingRow, SqlitePostingSql.COL_ACTOR_ID)),
            ActorType.fromWireValue(requiredText(postingRow, SqlitePostingSql.COL_ACTOR_TYPE)),
            new CommandId(requiredText(postingRow, SqlitePostingSql.COL_COMMAND_ID)),
            new IdempotencyKey(requiredText(postingRow, SqlitePostingSql.COL_IDEMPOTENCY_KEY)),
            new CausationId(requiredText(postingRow, SqlitePostingSql.COL_CAUSATION_ID)),
            optionalText(postingRow, SqlitePostingSql.COL_CORRELATION_ID).map(CorrelationId::new));
    CommittedProvenance provenance =
        new CommittedProvenance(
            requestProvenance,
            Instant.parse(requiredText(postingRow, SqlitePostingSql.COL_RECORDED_AT)),
            SourceChannel.fromWireValue(
                requiredText(postingRow, SqlitePostingSql.COL_SOURCE_CHANNEL)));
    return new CommittedPosting(
        postingId,
        journalEntry,
        readPostingLineageModel(postingRow),
        PostingKind.fromWireValue(requiredText(postingRow, SqlitePostingSql.COL_POSTING_KIND)),
        provenance);
  }

  static List<JournalLine> journalLines(SqliteNativeStatement lineRows) {
    List<JournalLine> lines = new ArrayList<>();
    while (lineRows.step() == SqliteNativeResultCodes.ROW) {
      lines.add(
          new JournalLine(
              new AccountCode(requiredText(lineRows, SqlitePostingSql.COL_LINE_ACCOUNT_CODE)),
              JournalLine.EntrySide.fromWireValue(
                  requiredText(lineRows, SqlitePostingSql.COL_LINE_ENTRY_SIDE)),
              SqlitePersistedMoneyCodec.readMoney(
                  lineRows,
                  SqlitePostingSql.COL_LINE_CURRENCY_CODE,
                  SqlitePostingSql.COL_LINE_AMOUNT_MINOR)));
    }
    return lines;
  }

  static PostingLineageModel readPostingLineageModel(SqliteNativeStatement postingRow) {
    Optional<String> priorPostingId =
        optionalText(postingRow, SqlitePostingSql.COL_PRIOR_POSTING_ID);
    Optional<String> reason = optionalText(postingRow, SqlitePostingSql.COL_REASON);
    if (priorPostingId.isEmpty() && reason.isEmpty()) {
      return PostingLineageModel.direct();
    }
    if (priorPostingId.isEmpty() || reason.isEmpty()) {
      throw new IllegalStateException(
          "Persisted posting lineage is inconsistent: reversal reference and reason must be present together.");
    }
    return PostingLineageModel.reversal(
        new ReversalReference(new PostingId(priorPostingId.orElseThrow())),
        new ReversalReason(reason.orElseThrow()));
  }

  static dev.erst.fingrind.contract.bookkeeping.PostingLineage readPostingLineage(
      SqliteNativeStatement postingRow) {
    return BookkeepingPublishedLanguageTranslator.toPublished(readPostingLineageModel(postingRow));
  }

  static Optional<ReversalReference> readReversalReference(SqliteNativeStatement postingRow) {
    return readPostingLineageModel(postingRow).reversalReference();
  }

  static String requiredText(SqliteNativeStatement row, int columnIndex) {
    String value = row.columnText(columnIndex);
    return Objects.requireNonNull(
        value, "Required value at SQLite column index " + columnIndex + " is null.");
  }

  static Optional<String> optionalText(SqliteNativeStatement row, int columnIndex) {
    String value = row.columnText(columnIndex);
    if (value == null) {
      return Optional.empty();
    }
    return Optional.of(value);
  }

  static int requiredInt(SqliteNativeStatement row, int columnIndex) {
    return row.columnInt(columnIndex);
  }
}
