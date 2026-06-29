package dev.erst.fingrind.sqlite;

/** Canonical SQLite SQL literals for retained foreign-exchange posting attachments. */
final class SqliteForeignExchangeSql {
  static final String LOAD_POSTING_FOREIGN_EXCHANGE =
      """
      select
          transaction_currency_code,
          transaction_amount_minor,
          functional_currency_code,
          functional_amount_minor,
          quoted_transaction_amount_minor,
          quoted_functional_amount_minor,
          quoted_on,
          quote_source,
          treatment_kind
      from posting_foreign_exchange
      where posting_id = ?
      limit 1
      """;

  static final String INSERT_POSTING_FOREIGN_EXCHANGE =
      """
      insert into posting_foreign_exchange (
          posting_id,
          treatment_kind,
          transaction_currency_code,
          transaction_amount_minor,
          functional_currency_code,
          functional_amount_minor,
          quoted_transaction_amount_minor,
          quoted_functional_amount_minor,
          quoted_on,
          quote_source
      ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      """;

  private SqliteForeignExchangeSql() {}
}
