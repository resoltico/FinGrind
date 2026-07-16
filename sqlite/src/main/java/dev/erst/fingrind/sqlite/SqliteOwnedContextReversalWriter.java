package dev.erst.fingrind.sqlite;

import java.util.List;

/** Persists compensation links from a reversal posting to the lifecycle facts it negates. */
final class SqliteOwnedContextReversalWriter {
  private static final List<ReversalTarget> TARGETS =
      List.of(
          new ReversalTarget(
              "fixed_asset_reversal", "fixed_asset_id", "fixed_asset", "origin_posting_id"),
          new ReversalTarget(
              "fixed_asset_application_reversal",
              "application_posting_id",
              "fixed_asset_application",
              "application_posting_id"),
          new ReversalTarget(
              "financing_arrangement_reversal",
              "financing_arrangement_id",
              "financing_arrangement",
              "origin_posting_id"),
          new ReversalTarget(
              "financing_application_reversal",
              "application_posting_id",
              "financing_application",
              "application_posting_id"),
          new ReversalTarget(
              "foreign_currency_obligation_reversal",
              "foreign_currency_obligation_id",
              "foreign_currency_obligation",
              "origin_posting_id"),
          new ReversalTarget(
              "foreign_currency_obligation_settlement_reversal",
              "settlement_posting_id",
              "foreign_currency_obligation_settlement",
              "settlement_posting_id"));

  private SqliteOwnedContextReversalWriter() {}

  static void persist(
      SqliteNativeDatabase database, String reversalPostingId, String priorPostingId) {
    for (ReversalTarget target : TARGETS) {
      String sql =
          """
          insert into %s (reversal_posting_id, %s)
          select ?, %s
          from %s
          where %s = ?
          """
              .formatted(
                  target.reversalTable(),
                  target.targetColumn(),
                  target.targetColumn(),
                  target.targetTable(),
                  target.targetPostingColumn());
      try (var statement = database.prepare(sql)) {
        statement.bindText(1, reversalPostingId);
        statement.bindText(2, priorPostingId);
        statement.step();
      }
    }
  }

  private record ReversalTarget(
      String reversalTable, String targetColumn, String targetTable, String targetPostingColumn) {}
}
