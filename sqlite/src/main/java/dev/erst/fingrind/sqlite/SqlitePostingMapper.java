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
import tools.jackson.databind.JsonNode;

/** Maps SQLite JSON row payloads into FinGrind posting domain objects. */
final class SqlitePostingMapper {
  private SqlitePostingMapper() {}

  static PostingFact postingFact(JsonNode postingRow, List<JournalLine> lines) {
    PostingId postingId = new PostingId(requiredText(postingRow, "posting_id"));
    JournalEntry journalEntry =
        new JournalEntry(LocalDate.parse(requiredText(postingRow, "effective_date")), lines);
    RequestProvenance requestProvenance =
        new RequestProvenance(
            new ActorId(requiredText(postingRow, "actor_id")),
            ActorType.valueOf(requiredText(postingRow, "actor_type")),
            new CommandId(requiredText(postingRow, "command_id")),
            new IdempotencyKey(requiredText(postingRow, "idempotency_key")),
            new CausationId(requiredText(postingRow, "causation_id")),
            optionalText(postingRow, "correlation_id").map(CorrelationId::new),
            optionalText(postingRow, "reason").map(CorrectionReason::new));
    CommittedProvenance provenance =
        new CommittedProvenance(
            requestProvenance,
            Instant.parse(requiredText(postingRow, "recorded_at")),
            SourceChannel.valueOf(requiredText(postingRow, "source_channel")));
    return new PostingFact(
        postingId, journalEntry, readCorrectionReference(postingRow), provenance);
  }

  static List<JournalLine> journalLines(JsonNode lineRows) {
    List<JournalLine> lines = new ArrayList<>();
    for (JsonNode lineRow : lineRows) {
      lines.add(
          new JournalLine(
              new AccountCode(requiredText(lineRow, "account_code")),
              JournalLine.EntrySide.valueOf(requiredText(lineRow, "entry_side")),
              new Money(
                  new CurrencyCode(requiredText(lineRow, "currency_code")),
                  new BigDecimal(requiredText(lineRow, "amount")))));
    }
    return lines;
  }

  static Optional<CorrectionReference> readCorrectionReference(JsonNode postingRow) {
    Optional<String> correctionKind = optionalText(postingRow, "correction_kind");
    Optional<String> priorPostingId = optionalText(postingRow, "prior_posting_id");
    if (correctionKind.isEmpty() || priorPostingId.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        new CorrectionReference(
            CorrectionReference.CorrectionKind.valueOf(correctionKind.orElseThrow()),
            new PostingId(priorPostingId.orElseThrow())));
  }

  static String requiredText(JsonNode node, String fieldName) {
    JsonNode fieldNode =
        Objects.requireNonNull(node.get(fieldName), "Missing sqlite3 field: " + fieldName);
    return Objects.requireNonNull(fieldNode.textValue(), "Null sqlite3 field: " + fieldName);
  }

  static Optional<String> optionalText(JsonNode node, String fieldName) {
    JsonNode fieldNode = node.get(fieldName);
    if (fieldNode == null || fieldNode.isNull()) {
      return Optional.empty();
    }
    return Optional.of(fieldNode.textValue());
  }
}
