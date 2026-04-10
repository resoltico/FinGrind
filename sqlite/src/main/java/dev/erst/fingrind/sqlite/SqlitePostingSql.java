package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.CorrectionReason;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.runtime.PostingFact;
import java.util.List;

/** Produces SQLite statements for FinGrind posting storage. */
final class SqlitePostingSql {
  private SqlitePostingSql() {}

  static String findPostingByIdempotency(IdempotencyKey idempotencyKey) {
    return basePostingSelect()
        + " where idempotency_key = %s limit 1\n".formatted(sqlLiteral(idempotencyKey.value()));
  }

  static String findPostingById(PostingId postingId) {
    return basePostingSelect()
        + " where posting_id = %s limit 1\n".formatted(sqlLiteral(postingId.value()));
  }

  static String findReversalFor(PostingId priorPostingId) {
    return basePostingSelect()
        + " where correction_kind = 'REVERSAL' and prior_posting_id = %s limit 1\n"
            .formatted(sqlLiteral(priorPostingId.value()));
  }

  static String loadLines(PostingId postingId) {
    return """
            select account_code, entry_side, currency_code, amount
            from journal_line
            where posting_id = %s
            order by line_order
            """
        .formatted(sqlLiteral(postingId.value()));
  }

  static String commitScript(PostingFact postingFact) {
    RequestProvenance requestProvenance = postingFact.provenance().requestProvenance();
    StringBuilder script =
        new StringBuilder(2048)
            .append("begin immediate;\n")
            .append(
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
                correction_kind,
                prior_posting_id
            ) values (
                %s,
                %s,
                %s,
                %s,
                %s,
                %s,
                %s,
                %s,
                %s,
                %s,
                %s,
                %s,
                %s
            );
            """
                    .formatted(
                        sqlLiteral(postingFact.postingId().value()),
                        sqlLiteral(postingFact.journalEntry().effectiveDate().toString()),
                        sqlLiteral(postingFact.provenance().recordedAt().toString()),
                        sqlLiteral(requestProvenance.actorId().value()),
                        sqlLiteral(requestProvenance.actorType().name()),
                        sqlLiteral(requestProvenance.commandId().value()),
                        sqlLiteral(requestProvenance.idempotencyKey().value()),
                        sqlLiteral(requestProvenance.causationId().value()),
                        sqlLiteral(
                            requestProvenance
                                .correlationId()
                                .map(CorrelationId::value)
                                .orElse(null)),
                        sqlLiteral(
                            requestProvenance.reason().map(CorrectionReason::value).orElse(null)),
                        sqlLiteral(postingFact.provenance().sourceChannel().name()),
                        sqlLiteral(
                            postingFact
                                .correctionReference()
                                .map(reference -> reference.kind().name())
                                .orElse(null)),
                        sqlLiteral(
                            postingFact
                                .correctionReference()
                                .map(reference -> reference.priorPostingId().value())
                                .orElse(null))));
    List<JournalLine> lines = postingFact.journalEntry().lines();
    for (int index = 0; index < lines.size(); index++) {
      JournalLine line = lines.get(index);
      script.append(
          """
                insert into journal_line (
                    posting_id,
                    line_order,
                    account_code,
                    entry_side,
                    currency_code,
                    amount
                ) values (
                    %s,
                    %s,
                    %s,
                    %s,
                    %s,
                    %s
                );
                """
              .formatted(
                  sqlLiteral(postingFact.postingId().value()),
                  Integer.toString(index),
                  sqlLiteral(line.accountCode().value()),
                  sqlLiteral(line.side().name()),
                  sqlLiteral(line.amount().currencyCode().value()),
                  sqlLiteral(line.amount().amount().toPlainString())));
    }
    script.append("commit;\n");
    return script.toString();
  }

  static String sqlLiteral(String value) {
    if (value == null) {
      return "null";
    }
    return "'" + value.replace("'", "''") + "'";
  }

  private static String basePostingSelect() {
    return """
            select
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
                correction_kind,
                prior_posting_id
            from posting_fact
            """;
  }
}
