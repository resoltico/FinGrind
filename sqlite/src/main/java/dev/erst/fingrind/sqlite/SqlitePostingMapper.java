package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.CorrectionReason;
import dev.erst.fingrind.core.CorrectionReference;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.CurrencyCode;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.runtime.PostingFact;
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

  static PostingFact postingFact(SqliteNativeStatement postingRow, List<JournalLine> lines) {
    PostingId postingId = new PostingId(requiredText(postingRow, 0));
    JournalEntry journalEntry =
        new JournalEntry(LocalDate.parse(requiredText(postingRow, 1)), lines);
    RequestProvenance requestProvenance =
        new RequestProvenance(
            new ActorId(requiredText(postingRow, 3)),
            ActorType.valueOf(requiredText(postingRow, 4)),
            new CommandId(requiredText(postingRow, 5)),
            new IdempotencyKey(requiredText(postingRow, 6)),
            new CausationId(requiredText(postingRow, 7)),
            optionalText(postingRow, 8).map(CorrelationId::new),
            optionalText(postingRow, 9).map(CorrectionReason::new));
    CommittedProvenance provenance =
        new CommittedProvenance(
            requestProvenance,
            Instant.parse(requiredText(postingRow, 2)),
            SourceChannel.valueOf(requiredText(postingRow, 10)));
    return new PostingFact(
        postingId, journalEntry, readCorrectionReference(postingRow), provenance);
  }

  static List<JournalLine> journalLines(SqliteNativeStatement lineRows)
      throws SqliteNativeException {
    List<JournalLine> lines = new ArrayList<>();
    while (lineRows.step() == SqliteNativeLibrary.SQLITE_ROW) {
      lines.add(
          new JournalLine(
              new AccountCode(requiredText(lineRows, 0)),
              JournalLine.EntrySide.valueOf(requiredText(lineRows, 1)),
              new Money(
                  new CurrencyCode(requiredText(lineRows, 2)),
                  new BigDecimal(requiredText(lineRows, 3)))));
    }
    return lines;
  }

  static Optional<CorrectionReference> readCorrectionReference(SqliteNativeStatement postingRow) {
    Optional<String> correctionKind = optionalText(postingRow, 11);
    Optional<String> priorPostingId = optionalText(postingRow, 12);
    if (correctionKind.isEmpty() || priorPostingId.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        new CorrectionReference(
            CorrectionReference.CorrectionKind.valueOf(correctionKind.orElseThrow()),
            new PostingId(priorPostingId.orElseThrow())));
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
}
