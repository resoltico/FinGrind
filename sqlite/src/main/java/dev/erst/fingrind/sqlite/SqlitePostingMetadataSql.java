package dev.erst.fingrind.sqlite;

/** Metadata and book-state SQL for the SQLite posting adapter. */
final class SqlitePostingMetadataSql {
  static final String INITIALIZED_AT_META_KEY = "initialized_at";
  static final String SCHEMA_FINGERPRINT_META_KEY = "schema_fingerprint_sha256";

  static final String USER_SCHEMA_EXISTS =
      """
      select 1
      from sqlite_schema
      where type in ('table', 'index', 'trigger', 'view')
        and name not like 'sqlite_%'
      limit 1
      """;

  static final String TABLE_EXISTS =
      """
      select 1
      from sqlite_schema
      where type = 'table'
        and name = ?
      limit 1
      """;

  static final String BOOK_INITIALIZED_EXISTS =
      """
      select 1
      from book_meta
      where meta_key = ?
      limit 1
      """;

  static final String FIND_BOOK_INITIALIZED_AT =
      """
      select value
      from book_meta
      where meta_key = ?
      limit 1
      """;

  static final String FIND_BOOK_META_VALUE =
      """
      select value
      from book_meta
      where meta_key = ?
      limit 1
      """;

  static final String FIND_BOOK_IDENTITY_CORE =
      """
      select
          entity_name,
          accounting_kernel_profile,
          accounting_basis,
          accounting_framework_position,
          entity_form,
          book_template_id,
          functional_currency_code,
          fiscal_year_start_month,
          fiscal_year_start_day
      from book_identity
      where singleton_id = 1
      limit 1
      """;

  static final String INSERT_BOOK_META_VALUE =
      """
      insert into book_meta (meta_key, value)
      values (?, ?)
      """;

  static final String INSERT_BOOK_IDENTITY =
      """
      insert into book_identity (
          singleton_id,
          entity_name,
          accounting_kernel_profile,
          accounting_basis,
          accounting_framework_position,
          entity_form,
          book_template_id,
          functional_currency_code,
          fiscal_year_start_month,
          fiscal_year_start_day
      ) values (1, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      """;

  private SqlitePostingMetadataSql() {}
}
