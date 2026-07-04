package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.AccountingBasis;
import dev.erst.fingrind.core.AccountingFrameworkPosition;
import dev.erst.fingrind.core.AccountingKernelProfileId;
import dev.erst.fingrind.core.BookDoctrine;
import dev.erst.fingrind.core.BookEntityName;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.BookTemplateId;
import dev.erst.fingrind.core.CanonicalTemporalText;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EntityForm;
import dev.erst.fingrind.core.EntityProfile;
import dev.erst.fingrind.core.FiscalYearStart;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.RequestFingerprint;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.spi.StoredRequestPosting;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Function;

/** Shared SQLite statement helpers for posting lookups, book identity, and scalar reads. */
final class SqliteStatementQueries {
  /** Runs one mapped query against a prepared statement. */
  @FunctionalInterface
  private interface StatementQuery<T> {
    /** Executes one query body against the supplied prepared statement. */
    T query(SqliteNativeStatement statement);
  }

  private record BookIdentityCoreRow(
      String entityName,
      String accountingKernelProfile,
      String accountingBasis,
      String accountingFrameworkPosition,
      String entityForm,
      String bookTemplateId,
      String functionalCurrencyCode,
      int fiscalYearStartMonth,
      int fiscalYearStartDay) {}

  private SqliteStatementQueries() {}

  static Optional<CommittedPosting> findOneCommittedPosting(
      SqliteNativeDatabase activeDatabase,
      String sql,
      SqliteStatementBinder binder,
      SqlitePostingAttachmentLoader loadAttachments) {
    return withStatement(
        activeDatabase,
        sql,
        statement -> {
          binder.bind(statement);
          if (statement.step() == SqliteNativeResultCode.code("DONE")) {
            return Optional.empty();
          }
          PostingId postingId =
              new PostingId(
                  SqlitePostingMapper.requiredText(
                      statement, SqlitePostingColumnIndexes.COL_POSTING_ID));
          SqlitePostingAttachments attachments = loadAttachments.load(postingId);
          return Optional.of(
              SqlitePostingMapper.committedPosting(
                  statement,
                  attachments.lines(),
                  attachments.evidence(),
                  attachments.appliedTax(),
                  attachments.foreignExchangeDetails()));
        });
  }

  static Optional<StoredRequestPosting> findOneStoredRequestPosting(
      SqliteNativeDatabase activeDatabase,
      String sql,
      SqliteStatementBinder binder,
      SqlitePostingAttachmentLoader loadAttachments) {
    return withStatement(
        activeDatabase,
        sql,
        statement -> {
          binder.bind(statement);
          if (statement.step() == SqliteNativeResultCode.code("DONE")) {
            return Optional.empty();
          }
          PostingId postingId =
              new PostingId(
                  SqlitePostingMapper.requiredText(
                      statement, SqlitePostingColumnIndexes.COL_POSTING_ID));
          SqlitePostingAttachments attachments = loadAttachments.load(postingId);
          return Optional.of(
              new StoredRequestPosting(
                  SqlitePostingMapper.committedPosting(
                      statement,
                      attachments.lines(),
                      attachments.evidence(),
                      attachments.appliedTax(),
                      attachments.foreignExchangeDetails()),
                  new RequestFingerprint(
                      SqlitePostingMapper.requiredInt(
                          statement, SqlitePostingColumnIndexes.COL_REQUEST_FINGERPRINT_VERSION),
                      SqlitePostingMapper.requiredText(
                          statement, SqlitePostingColumnIndexes.COL_REQUEST_FINGERPRINT_SHA256))));
        });
  }

  static boolean existsRow(
      SqliteNativeDatabase activeDatabase, String sql, SqliteStatementBinder binder) {
    return withStatement(
        activeDatabase,
        sql,
        statement -> {
          binder.bind(statement);
          return statement.step() == SqliteNativeResultCode.code("ROW");
        });
  }

  static Optional<Instant> loadInitializedAt(SqliteNativeDatabase activeDatabase) {
    return withStatement(
        activeDatabase,
        SqlitePostingSql.FIND_BOOK_INITIALIZED_AT,
        statement -> {
          statement.bindText(1, SqlitePostingSql.INITIALIZED_AT_META_KEY);
          if (statement.step() == SqliteNativeResultCode.code("DONE")) {
            return Optional.empty();
          }
          return Optional.of(
              CanonicalTemporalText.parseUtcInstant(
                  SqlitePostingMapper.requiredText(statement, 0), "book initializedAt"));
        });
  }

  static Optional<BookIdentity> loadBookIdentity(SqliteNativeDatabase activeDatabase) {
    Optional<BookIdentityCoreRow> identityCoreRow = loadBookIdentityCore(activeDatabase);
    if (identityCoreRow.isEmpty()) {
      return Optional.empty();
    }
    BookIdentityCoreRow coreRow = identityCoreRow.orElseThrow();
    return Optional.of(
        new BookIdentity(
            new EntityProfile(new BookEntityName(coreRow.entityName())),
            new BookDoctrine(
                new AccountingKernelProfileId(coreRow.accountingKernelProfile()),
                AccountingBasis.fromWireValue(coreRow.accountingBasis()),
                AccountingFrameworkPosition.fromWireValue(coreRow.accountingFrameworkPosition()),
                EntityForm.fromWireValue(coreRow.entityForm()),
                BookTemplateId.fromWireValue(coreRow.bookTemplateId())),
            CurrencyUnit.of(coreRow.functionalCurrencyCode()),
            new FiscalYearStart(coreRow.fiscalYearStartMonth(), coreRow.fiscalYearStartDay())));
  }

  static int querySingleInt(SqliteNativeDatabase activeDatabase, String sql) {
    OptionalInt value = queryOptionalInt(activeDatabase, sql);
    if (value.isEmpty()) {
      throw new IllegalStateException("SQLite integer query returned no rows: " + sql);
    }
    return value.orElseThrow();
  }

  static OptionalInt queryOptionalInt(SqliteNativeDatabase activeDatabase, String sql) {
    return withStatement(
        activeDatabase,
        sql,
        statement -> {
          if (statement.step() != SqliteNativeResultCode.code("ROW")) {
            return OptionalInt.empty();
          }
          int value = statement.columnInt(0);
          if (statement.step() != SqliteNativeResultCode.code("DONE")) {
            throw new IllegalStateException(
                "SQLite integer query returned more than one row: " + sql);
          }
          return OptionalInt.of(value);
        });
  }

  static String querySingleText(SqliteNativeDatabase activeDatabase, String sql) {
    return withStatement(
        activeDatabase,
        sql,
        statement -> {
          if (statement.step() != SqliteNativeResultCode.code("ROW")) {
            throw new IllegalStateException("SQLite text query returned no rows: " + sql);
          }
          String value =
              Objects.requireNonNull(
                  statement.columnText(0), "SQLite text query returned NULL: " + sql);
          if (statement.step() != SqliteNativeResultCode.code("DONE")) {
            throw new IllegalStateException("SQLite text query returned more than one row: " + sql);
          }
          return value;
        });
  }

  static Optional<String> loadOptionalText(
      SqliteNativeDatabase activeDatabase, String sql, SqliteStatementBinder binder) {
    SqliteOptionalTextRow row = loadOptionalTextRow(activeDatabase, sql, binder);
    if (!row.singleRow()) {
      throw new IllegalStateException("SQLite text query returned more than one row: " + sql);
    }
    return row.value();
  }

  static SqliteOptionalTextRow loadOptionalTextRow(
      SqliteNativeDatabase activeDatabase, String sql, SqliteStatementBinder binder) {
    return withStatement(
        activeDatabase,
        sql,
        statement -> {
          binder.bind(statement);
          if (statement.step() != SqliteNativeResultCode.code("ROW")) {
            return new SqliteOptionalTextRow(Optional.empty(), true);
          }
          String value = statement.columnText(0);
          return new SqliteOptionalTextRow(
              Optional.ofNullable(value), statement.step() == SqliteNativeResultCode.code("DONE"));
        });
  }

  static <T> T queryWithStatement(
      SqliteNativeDatabase activeDatabase, String sql, Function<SqliteNativeStatement, T> query) {
    Objects.requireNonNull(query, "query");
    return withStatement(activeDatabase, sql, query::apply);
  }

  private static Optional<BookIdentityCoreRow> loadBookIdentityCore(
      SqliteNativeDatabase activeDatabase) {
    return withStatement(
        activeDatabase,
        SqlitePostingSql.FIND_BOOK_IDENTITY_CORE,
        statement -> {
          if (statement.step() != SqliteNativeResultCode.code("ROW")) {
            return Optional.empty();
          }
          BookIdentityCoreRow row =
              new BookIdentityCoreRow(
                  SqlitePostingMapper.requiredText(statement, 0),
                  SqlitePostingMapper.requiredText(statement, 1),
                  SqlitePostingMapper.requiredText(statement, 2),
                  SqlitePostingMapper.requiredText(statement, 3),
                  SqlitePostingMapper.requiredText(statement, 4),
                  SqlitePostingMapper.requiredText(statement, 5),
                  SqlitePostingMapper.requiredText(statement, 6),
                  SqlitePostingMapper.requiredInt(statement, 7),
                  SqlitePostingMapper.requiredInt(statement, 8));
          if (statement.step() != SqliteNativeResultCode.code("DONE")) {
            throw new IllegalStateException(
                "SQLite book identity core query returned more than one row.");
          }
          return Optional.of(row);
        });
  }

  private static <T> T withStatement(
      SqliteNativeDatabase activeDatabase, String sql, StatementQuery<T> query) {
    try (SqliteNativeStatement statement = activeDatabase.prepare(sql)) {
      return query.query(statement);
    }
  }
}
