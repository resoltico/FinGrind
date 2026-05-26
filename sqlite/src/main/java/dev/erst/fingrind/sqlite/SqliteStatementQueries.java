package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountingEvidence;
import dev.erst.fingrind.core.BookEntityName;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.BusinessActivityTag;
import dev.erst.fingrind.core.CanonicalTemporalText;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EntityProfile;
import dev.erst.fingrind.core.FiscalYearStart;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryCursor;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryPage;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryQuery;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Shared SQLite statement helpers for single-row lookups and pragma reads. */
final class SqliteStatementQueries {
  /** Binds parameters onto a prepared SQLite statement before execution. */
  @FunctionalInterface
  interface Binder {
    /** Applies all statement bindings required by one query. */
    void bind(SqliteNativeStatement statement);
  }

  /** Loads journal lines for one posting identifier while mapping a posting row. */
  @FunctionalInterface
  interface PostingAttachmentLoader {
    /** Returns the journal lines and evidence that belong to the supplied posting. */
    PostingAttachments load(PostingId postingId);
  }

  /** Fully loaded posting attachments required to materialize one committed posting. */
  record PostingAttachments(List<JournalLine> lines, AccountingEvidence evidence) {
    PostingAttachments {
      lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
      Objects.requireNonNull(evidence, "evidence");
    }
  }

  /** Runs one mapped query against a prepared statement. */
  @FunctionalInterface
  private interface StatementQuery<T> {
    /** Executes one query body against the supplied prepared statement. */
    T query(SqliteNativeStatement statement);
  }

  /** Single-column text row shape that distinguishes empty, exact-one-row, and multi-row cases. */
  record OptionalTextRow(Optional<String> value, boolean singleRow) {
    OptionalTextRow {
      Objects.requireNonNull(value, "value");
    }
  }

  private record BookIdentityCoreRow(
      String entityName,
      String functionalCurrencyCode,
      int fiscalYearStartMonth,
      int fiscalYearStartDay) {}

  private record EntityProfileRow(String businessActivityTags) {}

  private SqliteStatementQueries() {}

  static Optional<CommittedPosting> findOneCommittedPosting(
      SqliteNativeDatabase activeDatabase,
      String sql,
      Binder binder,
      PostingAttachmentLoader loadAttachments) {
    return withStatement(
        activeDatabase,
        sql,
        statement -> {
          binder.bind(statement);
          if (statement.step() == SqliteNativeResultCodes.DONE) {
            return Optional.empty();
          }
          PostingId postingId =
              new PostingId(
                  SqlitePostingMapper.requiredText(statement, SqlitePostingSql.COL_POSTING_ID));
          PostingAttachments attachments = loadAttachments.load(postingId);
          return Optional.of(
              SqlitePostingMapper.committedPosting(
                  statement, attachments.lines(), attachments.evidence()));
        });
  }

  static Optional<RegisteredAccount> findOneAccount(
      SqliteNativeDatabase activeDatabase, AccountCode accountCode) {
    return withStatement(
        activeDatabase,
        SqlitePostingSql.FIND_ACCOUNT_BY_CODE,
        statement -> {
          statement.bindText(1, accountCode.value());
          if (statement.step() == SqliteNativeResultCodes.DONE) {
            return Optional.empty();
          }
          return Optional.of(SqlitePostingMapper.registeredAccount(statement));
        });
  }

  static Map<AccountCode, RegisteredAccount> findAccounts(
      SqliteNativeDatabase activeDatabase, Set<AccountCode> accountCodes) {
    List<AccountCode> orderedCodes = List.copyOf(accountCodes);
    return withStatement(
        activeDatabase,
        SqlitePostingSql.findAccountsByCodeCount(orderedCodes.size()),
        statement -> {
          int bindIndex = 1;
          for (AccountCode accountCode : orderedCodes) {
            statement.bindText(bindIndex, accountCode.value());
            bindIndex++;
          }
          List<RegisteredAccount> accounts = new ArrayList<>();
          while (statement.step() == SqliteNativeResultCodes.ROW) {
            accounts.add(SqlitePostingMapper.registeredAccount(statement));
          }
          return accounts.stream()
              .collect(
                  Collectors.toUnmodifiableMap(
                      RegisteredAccount::accountCode, Function.identity()));
        });
  }

  static List<RegisteredAccount> loadAllAccounts(SqliteNativeDatabase activeDatabase, String sql) {
    return withStatement(
        activeDatabase,
        sql,
        statement -> {
          List<RegisteredAccount> accounts = new ArrayList<>();
          while (statement.step() == SqliteNativeResultCodes.ROW) {
            accounts.add(SqlitePostingMapper.registeredAccount(statement));
          }
          return List.copyOf(accounts);
        });
  }

  static AccountRegistryPage loadAccountPage(
      SqliteNativeDatabase activeDatabase, AccountRegistryQuery query) {
    List<RegisteredAccount> accounts = new ArrayList<>();
    withStatement(
        activeDatabase,
        SqlitePostingSql.listAccounts(),
        statement -> {
          String cursorAccountCode =
              query
                  .cursor()
                  .map(AccountRegistryCursor::accountCode)
                  .map(AccountCode::value)
                  .orElse(null);
          statement.bindText(1, cursorAccountCode);
          statement.bindText(2, cursorAccountCode);
          statement.bindInt(3, query.limit() + 1);
          while (statement.step() == SqliteNativeResultCodes.ROW) {
            accounts.add(SqlitePostingMapper.registeredAccount(statement));
          }
          return Boolean.TRUE;
        });
    boolean hasMore = accounts.size() > query.limit();
    List<RegisteredAccount> pageItems = hasMore ? accounts.subList(0, query.limit()) : accounts;
    return new AccountRegistryPage(
        pageItems,
        query.limit(),
        hasMore
            ? Optional.of(new AccountRegistryCursor(pageItems.getLast().accountCode()))
            : Optional.empty());
  }

  static boolean existsRow(SqliteNativeDatabase activeDatabase, String sql, Binder binder) {
    return withStatement(
        activeDatabase,
        sql,
        statement -> {
          binder.bind(statement);
          return statement.step() == SqliteNativeResultCodes.ROW;
        });
  }

  static Optional<Instant> loadInitializedAt(SqliteNativeDatabase activeDatabase) {
    return withStatement(
        activeDatabase,
        SqlitePostingSql.FIND_BOOK_INITIALIZED_AT,
        statement -> {
          statement.bindText(1, SqlitePostingSql.INITIALIZED_AT_META_KEY);
          if (statement.step() == SqliteNativeResultCodes.DONE) {
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
    EntityProfileRow entityProfileRow =
        loadRequiredEntityProfile(
            activeDatabase, "Initialized SQLite book is missing entity profile.");
    BookIdentityCoreRow coreRow = identityCoreRow.orElseThrow();
    return Optional.of(
        new BookIdentity(
            new EntityProfile(
                new BookEntityName(coreRow.entityName()),
                decodeBusinessActivityTags(entityProfileRow.businessActivityTags())),
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
          if (statement.step() != SqliteNativeResultCodes.ROW) {
            return OptionalInt.empty();
          }
          int value = statement.columnInt(0);
          if (statement.step() != SqliteNativeResultCodes.DONE) {
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
          if (statement.step() != SqliteNativeResultCodes.ROW) {
            throw new IllegalStateException("SQLite text query returned no rows: " + sql);
          }
          String value =
              Objects.requireNonNull(
                  statement.columnText(0), "SQLite text query returned NULL: " + sql);
          if (statement.step() != SqliteNativeResultCodes.DONE) {
            throw new IllegalStateException("SQLite text query returned more than one row: " + sql);
          }
          return value;
        });
  }

  static Optional<String> loadOptionalText(
      SqliteNativeDatabase activeDatabase, String sql, Binder binder) {
    OptionalTextRow row = loadOptionalTextRow(activeDatabase, sql, binder);
    if (!row.singleRow()) {
      throw new IllegalStateException("SQLite text query returned more than one row: " + sql);
    }
    return row.value();
  }

  static OptionalTextRow loadOptionalTextRow(
      SqliteNativeDatabase activeDatabase, String sql, Binder binder) {
    return withStatement(
        activeDatabase,
        sql,
        statement -> {
          binder.bind(statement);
          if (statement.step() != SqliteNativeResultCodes.ROW) {
            return new OptionalTextRow(Optional.empty(), true);
          }
          String value = statement.columnText(0);
          return new OptionalTextRow(
              Optional.ofNullable(value), statement.step() == SqliteNativeResultCodes.DONE);
        });
  }

  private static Optional<BookIdentityCoreRow> loadBookIdentityCore(
      SqliteNativeDatabase activeDatabase) {
    return withStatement(
        activeDatabase,
        SqlitePostingSql.FIND_BOOK_IDENTITY_CORE,
        statement -> {
          if (statement.step() != SqliteNativeResultCodes.ROW) {
            return Optional.empty();
          }
          BookIdentityCoreRow row =
              new BookIdentityCoreRow(
                  SqlitePostingMapper.requiredText(statement, 0),
                  SqlitePostingMapper.requiredText(statement, 1),
                  SqlitePostingMapper.requiredInt(statement, 2),
                  SqlitePostingMapper.requiredInt(statement, 3));
          if (statement.step() != SqliteNativeResultCodes.DONE) {
            throw new IllegalStateException(
                "SQLite book identity core query returned more than one row.");
          }
          return Optional.of(row);
        });
  }

  private static EntityProfileRow loadRequiredEntityProfile(
      SqliteNativeDatabase activeDatabase, String missingMessage) {
    return withStatement(
        activeDatabase,
        SqlitePostingSql.FIND_ENTITY_PROFILE,
        statement -> {
          if (statement.step() != SqliteNativeResultCodes.ROW) {
            throw new IllegalStateException(missingMessage);
          }
          EntityProfileRow row =
              new EntityProfileRow(SqlitePostingMapper.requiredText(statement, 0));
          if (statement.step() != SqliteNativeResultCodes.DONE) {
            throw new IllegalStateException(
                "SQLite entity profile query returned more than one row.");
          }
          return row;
        });
  }

  private static List<BusinessActivityTag> decodeBusinessActivityTags(String encodedTags) {
    if (encodedTags.isBlank()) {
      return List.of();
    }
    return Stream.of(encodedTags.split(",", -1))
        .map(SqliteStatementQueries::decodeBookMetaValue)
        .map(BusinessActivityTag::new)
        .toList();
  }

  private static String decodeBookMetaValue(String encodedValue) {
    return new String(Base64.getUrlDecoder().decode(encodedValue), StandardCharsets.UTF_8);
  }

  private static <T> T withStatement(
      SqliteNativeDatabase activeDatabase, String sql, StatementQuery<T> query) {
    try (SqliteNativeStatement statement = activeDatabase.prepare(sql)) {
      return query.query(statement);
    }
  }
}
