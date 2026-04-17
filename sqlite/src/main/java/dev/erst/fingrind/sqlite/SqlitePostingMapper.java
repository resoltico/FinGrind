package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.PostingFact;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Maps SQLite native statement rows into FinGrind posting domain objects. */
final class SqlitePostingMapper {
  private SqlitePostingMapper() {}

  static DeclaredAccount declaredAccount(SqliteNativeStatement accountRow) {
    return new DeclaredAccount(
        new AccountCode(requiredText(accountRow, SqlitePostingSql.COL_ACCOUNT_CODE)),
        new AccountName(requiredText(accountRow, SqlitePostingSql.COL_ACCOUNT_NAME)),
        NormalBalance.valueOf(
            requiredText(accountRow, SqlitePostingSql.COL_ACCOUNT_NORMAL_BALANCE)),
        requiredInt(accountRow, SqlitePostingSql.COL_ACCOUNT_ACTIVE) == 1,
        Instant.parse(requiredText(accountRow, SqlitePostingSql.COL_ACCOUNT_DECLARED_AT)));
  }

  static PostingFact postingFact(SqliteNativeStatement postingRow, List<JournalLine> lines) {
    PostingId postingId = new PostingId(requiredText(postingRow, SqlitePostingSql.COL_POSTING_ID));
    JournalEntry journalEntry =
        new JournalEntry(
            LocalDate.parse(requiredText(postingRow, SqlitePostingSql.COL_EFFECTIVE_DATE)), lines);
    RequestProvenance requestProvenance =
        new RequestProvenance(
            new ActorId(requiredText(postingRow, SqlitePostingSql.COL_ACTOR_ID)),
            ActorType.valueOf(requiredText(postingRow, SqlitePostingSql.COL_ACTOR_TYPE)),
            new CommandId(requiredText(postingRow, SqlitePostingSql.COL_COMMAND_ID)),
            new IdempotencyKey(requiredText(postingRow, SqlitePostingSql.COL_IDEMPOTENCY_KEY)),
            new CausationId(requiredText(postingRow, SqlitePostingSql.COL_CAUSATION_ID)),
            optionalText(postingRow, SqlitePostingSql.COL_CORRELATION_ID).map(CorrelationId::new),
            optionalText(postingRow, SqlitePostingSql.COL_REASON).map(ReversalReason::new));
    CommittedProvenance provenance =
        new CommittedProvenance(
            requestProvenance,
            Instant.parse(requiredText(postingRow, SqlitePostingSql.COL_RECORDED_AT)),
            SourceChannel.valueOf(requiredText(postingRow, SqlitePostingSql.COL_SOURCE_CHANNEL)));
    return new PostingFact(postingId, journalEntry, readReversalReference(postingRow), provenance);
  }

  static List<JournalLine> journalLines(SqliteNativeStatement lineRows)
      throws SqliteNativeException {
    List<JournalLine> lines = new ArrayList<>();
    while (lineRows.step() == SqliteNativeLibrary.SQLITE_ROW) {
      lines.add(
          new JournalLine(
              new AccountCode(requiredText(lineRows, SqlitePostingSql.COL_LINE_ACCOUNT_CODE)),
              JournalLine.EntrySide.valueOf(
                  requiredText(lineRows, SqlitePostingSql.COL_LINE_ENTRY_SIDE)),
              new Money(
                  new CurrencyCode(requiredText(lineRows, SqlitePostingSql.COL_LINE_CURRENCY_CODE)),
                  new BigDecimal(requiredText(lineRows, SqlitePostingSql.COL_LINE_AMOUNT)))));
    }
    return lines;
  }

  static Optional<ReversalReference> readReversalReference(SqliteNativeStatement postingRow) {
    Optional<String> priorPostingId =
        optionalText(postingRow, SqlitePostingSql.COL_PRIOR_POSTING_ID);
    if (priorPostingId.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new ReversalReference(new PostingId(priorPostingId.orElseThrow())));
  }

  static String requiredText(SqliteNativeStatement row, int columnIndex) {
    String value = row.columnText(columnIndex);
    return Objects.requireNonNull(value, "Null SQLite column index: " + columnIndex);
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
