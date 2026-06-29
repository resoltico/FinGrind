package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.tax.DeclaredTaxRegistration;
import dev.erst.fingrind.contract.tax.ListTaxRegistrationsQuery;
import dev.erst.fingrind.contract.tax.TaxCodeDefinition;
import dev.erst.fingrind.contract.tax.TaxRegistrationId;
import dev.erst.fingrind.contract.tax.TaxRegistrationPage;
import dev.erst.fingrind.contract.tax.TaxRegistrationPageCursor;
import dev.erst.fingrind.core.BookIdentity;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Shared SQLite statement helpers for tax-registration lookups and pages. */
final class SqliteTaxStatementQueries {
  /** Runs one mapped tax query against a prepared statement. */
  @FunctionalInterface
  private interface StatementQuery<T> {
    /** Executes one tax query body against the supplied prepared statement. */
    T query(SqliteNativeStatement statement);
  }

  private SqliteTaxStatementQueries() {}

  static Optional<DeclaredTaxRegistration> findOneTaxRegistration(
      SqliteNativeDatabase activeDatabase, TaxRegistrationId taxRegistrationId) {
    Objects.requireNonNull(taxRegistrationId, "taxRegistrationId");
    return withStatement(
        activeDatabase,
        SqliteTaxSql.FIND_TAX_REGISTRATION_BY_ID,
        statement -> {
          statement.bindText(1, taxRegistrationId.value());
          if (statement.step() != SqliteNativeResultCode.code("ROW")) {
            return Optional.empty();
          }
          SqliteTaxMapper.TaxRegistrationCoreRow coreRow = taxRegistrationCoreRow(statement);
          if (statement.step() != SqliteNativeResultCode.code("DONE")) {
            throw new IllegalStateException(
                "SQLite tax-registration query returned more than one row for "
                    + taxRegistrationId.value()
                    + ".");
          }
          return Optional.of(
              SqliteTaxMapper.declaredTaxRegistration(
                  coreRow, loadTaxCodesForRegistration(activeDatabase, taxRegistrationId)));
        });
  }

  static List<DeclaredTaxRegistration> loadAllTaxRegistrations(
      SqliteNativeDatabase activeDatabase) {
    return withStatement(
        activeDatabase,
        SqliteTaxSql.LOAD_ALL_TAX_REGISTRATIONS,
        statement -> {
          List<DeclaredTaxRegistration> registrations = new ArrayList<>();
          while (statement.step() == SqliteNativeResultCode.code("ROW")) {
            SqliteTaxMapper.TaxRegistrationCoreRow coreRow = taxRegistrationCoreRow(statement);
            registrations.add(
                SqliteTaxMapper.declaredTaxRegistration(
                    coreRow,
                    loadTaxCodesForRegistration(
                        activeDatabase, new TaxRegistrationId(coreRow.taxRegistrationId()))));
          }
          return List.copyOf(registrations);
        });
  }

  static TaxRegistrationPage loadTaxRegistrationPage(
      SqliteNativeDatabase activeDatabase, ListTaxRegistrationsQuery query) {
    Objects.requireNonNull(query, "query");
    List<DeclaredTaxRegistration> registrations = new ArrayList<>();
    withStatement(
        activeDatabase,
        SqliteTaxSql.LIST_TAX_REGISTRATIONS,
        statement -> {
          String cursorTaxRegistrationId =
              query
                  .cursor()
                  .map(TaxRegistrationPageCursor::taxRegistrationId)
                  .map(TaxRegistrationId::value)
                  .orElse(null);
          statement.bindText(1, cursorTaxRegistrationId);
          statement.bindText(2, cursorTaxRegistrationId);
          statement.bindInt(3, query.limit() + 1);
          while (statement.step() == SqliteNativeResultCode.code("ROW")) {
            SqliteTaxMapper.TaxRegistrationCoreRow coreRow = taxRegistrationCoreRow(statement);
            registrations.add(
                SqliteTaxMapper.declaredTaxRegistration(
                    coreRow,
                    loadTaxCodesForRegistration(
                        activeDatabase, new TaxRegistrationId(coreRow.taxRegistrationId()))));
          }
          return Boolean.TRUE;
        });
    boolean hasMore = registrations.size() > query.limit();
    List<DeclaredTaxRegistration> pageItems =
        hasMore ? registrations.subList(0, query.limit()) : registrations;
    BookIdentity bookIdentity =
        SqliteStatementQueries.loadBookIdentity(activeDatabase)
            .orElseThrow(
                () ->
                    new IllegalStateException("Initialized SQLite book is missing book identity."));
    return new TaxRegistrationPage(
        bookIdentity,
        pageItems,
        query.limit(),
        hasMore
            ? Optional.of(TaxRegistrationPageCursor.fromRegistration(pageItems.getLast()))
            : Optional.empty());
  }

  private static SqliteTaxMapper.TaxRegistrationCoreRow taxRegistrationCoreRow(
      SqliteNativeStatement statement) {
    return new SqliteTaxMapper.TaxRegistrationCoreRow(
        SqlitePostingMapper.requiredText(statement, 0),
        SqlitePostingMapper.requiredText(statement, 1),
        SqlitePostingMapper.requiredText(statement, 2),
        SqliteTaxMapper.optionalText(statement, 3),
        SqlitePostingMapper.requiredText(statement, 4),
        SqlitePostingMapper.requiredText(statement, 5),
        SqlitePostingMapper.requiredText(statement, 6),
        SqlitePostingMapper.requiredInt(statement, 7),
        SqlitePostingMapper.requiredText(statement, 8));
  }

  private static List<TaxCodeDefinition> loadTaxCodesForRegistration(
      SqliteNativeDatabase activeDatabase, TaxRegistrationId taxRegistrationId) {
    return withStatement(
        activeDatabase,
        SqliteTaxSql.LOAD_TAX_CODES_FOR_REGISTRATION,
        statement -> {
          statement.bindText(1, taxRegistrationId.value());
          List<TaxCodeDefinition> taxCodes = new ArrayList<>();
          while (statement.step() == SqliteNativeResultCode.code("ROW")) {
            taxCodes.add(SqliteTaxMapper.taxCodeDefinition(statement));
          }
          return List.copyOf(taxCodes);
        });
  }

  private static <T> T withStatement(
      SqliteNativeDatabase activeDatabase, String sql, StatementQuery<T> query) {
    try (SqliteNativeStatement statement = activeDatabase.prepare(sql)) {
      return query.query(statement);
    }
  }
}
