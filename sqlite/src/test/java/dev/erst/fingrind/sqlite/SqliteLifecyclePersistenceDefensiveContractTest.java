package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.FinancingArrangementId;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetId;
import dev.erst.fingrind.contract.bookkeeping.ForeignCurrencyObligationId;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.RealizedForeignExchangeBookkeepingEntryVariants;
import dev.erst.fingrind.contract.fx.ForeignExchangeDetails;
import dev.erst.fingrind.contract.fx.ForeignExchangeTreatmentKind;
import dev.erst.fingrind.contract.fx.QuotedExchangeRate;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingOriginKind;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Proves that corrupted or absent lifecycle persistence fails deterministically at the adapter
 * boundary.
 */
class SqliteLifecyclePersistenceDefensiveContractTest {
  private static final String TEST_BOOK_KEY = "lifecycle-persistence-test-book-key";
  private static final JournalEntry JOURNAL_ENTRY =
      new JournalEntry(
          LocalDate.parse("2026-07-15"),
          List.of(
              new JournalLine(
                  new AccountCode("debit"),
                  JournalLine.EntrySide.DEBIT,
                  Money.parse("EUR", "1.00")),
              new JournalLine(
                  new AccountCode("credit"),
                  JournalLine.EntrySide.CREDIT,
                  Money.parse("EUR", "1.00"))));

  @TempDir Path tempDirectory;

  @BeforeEach
  void hardenTempDirectory() {
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(tempDirectory);
  }

  @Test
  void lifecycleStatementHelpers_distinguishNoRowOneRowAndCorruptDuplicateRows() {
    try (SqliteNativeDatabase database = openNativeDatabase("statement-helpers.sqlite")) {
      database.executeScript(
          """
          create table item (id text not null, value text not null);
          insert into item values ('one', 'value-one');
          insert into item values ('duplicate', 'value-one');
          insert into item values ('duplicate', 'value-two');
          """);

      assertEquals(
          Optional.empty(),
          SqliteLifecycleStatementQuerySupport.findOne(
              database,
              "select value from item where id = ?",
              "missing",
              statement -> statement.bindText(1, "missing"),
              statement -> SqlitePostingMapper.requiredText(statement, 0),
              "test aggregate"));
      assertEquals(
          Optional.of("value-one"),
          SqliteLifecycleStatementQuerySupport.findOne(
              database,
              "select value from item where id = ?",
              "one",
              statement -> statement.bindText(1, "one"),
              statement -> SqlitePostingMapper.requiredText(statement, 0),
              "test aggregate"));
      assertThrows(
          IllegalStateException.class,
          () ->
              SqliteLifecycleStatementQuerySupport.findOne(
                  database,
                  "select value from item where id = ?",
                  "duplicate",
                  statement -> statement.bindText(1, "duplicate"),
                  statement -> SqlitePostingMapper.requiredText(statement, 0),
                  "test aggregate"));
      assertFalse(
          SqliteLifecycleStatementQuerySupport.exists(
              database,
              "select 1 from item where id = ?",
              "missing",
              statement -> statement.bindText(1, "missing")));
      assertTrue(
          SqliteLifecycleStatementQuerySupport.exists(
              database,
              "select 1 from item where id = ?",
              "one",
              statement -> statement.bindText(1, "one")));
    }
  }

  @Test
  void lifecycleMappers_rejectMissingAndWrongDurableAggregateFacts() {
    try (SqliteNativeDatabase database = openNativeDatabase("mapper-defenses.sqlite")) {
      createLifecycleMapperTables(database);
      try (SqliteNativeStatement postingRow = postingRow(database)) {
        assertEquals(SqliteNativeResultCode.code("ROW"), postingRow.step());
        assertMissingFixedAssetFacts(database, postingRow);
        assertWrongFixedAssetApplicationKinds(database, postingRow);
        assertMissingFinancingFacts(database, postingRow);
        assertWrongFinancingApplicationKind(database, postingRow);
        assertMissingForeignExchangeFacts(database, postingRow);
      }
    }
  }

  @Test
  void transactionLifecycleQueries_translateSQLiteFailuresAtTheAdmissionBoundary() {
    try (SqliteNativeDatabase database = openNativeDatabase("admission-query-failure.sqlite")) {
      SqliteTransactionLifecycleValidationQueries queries =
          new SqliteTransactionLifecycleValidationQueries(database);

      assertThrows(
          IllegalStateException.class,
          () -> queries.findFixedAsset(new FixedAssetId("asset-missing")));
      assertThrows(
          IllegalStateException.class,
          () -> queries.hasFixedAsset(new FixedAssetId("asset-missing")));
      assertThrows(
          IllegalStateException.class,
          () -> queries.findFinancingArrangement(new FinancingArrangementId("financing-missing")));
      assertThrows(
          IllegalStateException.class,
          () -> queries.hasFinancingArrangement(new FinancingArrangementId("financing-missing")));
      assertThrows(
          IllegalStateException.class,
          () ->
              queries.findForeignCurrencyObligation(
                  new ForeignCurrencyObligationId("foreign-currency-obligation-missing")));
      assertThrows(
          IllegalStateException.class,
          () ->
              queries.hasForeignCurrencyObligation(
                  new ForeignCurrencyObligationId("foreign-currency-obligation-missing")));
    }
  }

  private static void assertMissingFixedAssetFacts(
      SqliteNativeDatabase database, SqliteNativeStatement postingRow) {
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteFixedAssetOriginatingEntryMapper.originatingEntry(
                database,
                new PostingId("081e5c62-f606-3670-851a-6514409d074a"),
                postingRow,
                JOURNAL_ENTRY,
                PostingOriginKind.FIXED_ASSET_CAPITALIZATION));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteFixedAssetOriginatingEntryMapper.originatingEntry(
                database,
                new PostingId("081e5c62-f606-3670-851a-6514409d074a"),
                postingRow,
                JOURNAL_ENTRY,
                PostingOriginKind.FIXED_ASSET_DEPRECIATION));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteFixedAssetOriginatingEntryMapper.originatingEntry(
                database,
                new PostingId("081e5c62-f606-3670-851a-6514409d074a"),
                postingRow,
                JOURNAL_ENTRY,
                PostingOriginKind.FIXED_ASSET_DISPOSAL));
  }

  private static void assertWrongFixedAssetApplicationKinds(
      SqliteNativeDatabase database, SqliteNativeStatement postingRow) {
    assertFalse(SqliteFixedAssetStatementQueries.exists(database, new FixedAssetId("asset-1")));
    database.executeStatement(
        """
        insert into fixed_asset values (
            'asset-1', 'asset', 'accumulated-depreciation', 'depreciation-expense',
            'disposal-gain', 'disposal-loss', 'EUR', 12000, 0, '2026-01-01', 12, '%s')
        """
            .formatted(SqliteTestPostingIds.valueForLabel("fixed-origin")));
    assertTrue(SqliteFixedAssetStatementQueries.exists(database, new FixedAssetId("asset-1")));
    database.executeStatement(
        "insert into fixed_asset_application values ('DISPOSAL', 'asset-1', 'EUR', 10000, '%s')"
            .formatted(SqliteTestPostingIds.valueForLabel("fixed-application")));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteFixedAssetOriginatingEntryMapper.originatingEntry(
                database,
                new PostingId("dcb44d0b-2f05-3a1a-97f7-bd20ac65aa05"),
                postingRow,
                JOURNAL_ENTRY,
                PostingOriginKind.FIXED_ASSET_DEPRECIATION));
    database.executeStatement(
        "update fixed_asset_application set application_kind = 'DEPRECIATION'");
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteFixedAssetOriginatingEntryMapper.originatingEntry(
                database,
                new PostingId("dcb44d0b-2f05-3a1a-97f7-bd20ac65aa05"),
                postingRow,
                JOURNAL_ENTRY,
                PostingOriginKind.FIXED_ASSET_DISPOSAL));
    database.executeStatement(
        "update fixed_asset_application set application_kind = 'DISPOSAL', amount_minor = 12000");
    FixedAssetBookkeepingEntryVariants.Disposal loss =
        assertInstanceOf(
            FixedAssetBookkeepingEntryVariants.Disposal.class,
            SqliteFixedAssetOriginatingEntryMapper.originatingEntry(
                database,
                new PostingId("dcb44d0b-2f05-3a1a-97f7-bd20ac65aa05"),
                postingRow,
                JOURNAL_ENTRY,
                PostingOriginKind.FIXED_ASSET_DISPOSAL));
    assertFalse(Objects.requireNonNull(loss.resolvedDisposal()).gain());
  }

  private static void assertMissingFinancingFacts(
      SqliteNativeDatabase database, SqliteNativeStatement postingRow) {
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteFinancingOriginatingEntryMapper.originatingEntry(
                database,
                new PostingId("28ebe8f7-b11b-3df0-9a2c-824ad5ca05a5"),
                postingRow,
                JOURNAL_ENTRY,
                PostingOriginKind.FINANCING_BORROWING));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteFinancingOriginatingEntryMapper.originatingEntry(
                database,
                new PostingId("28ebe8f7-b11b-3df0-9a2c-824ad5ca05a5"),
                postingRow,
                JOURNAL_ENTRY,
                PostingOriginKind.FINANCING_PRINCIPAL_REPAYMENT));
  }

  private static void assertWrongFinancingApplicationKind(
      SqliteNativeDatabase database, SqliteNativeStatement postingRow) {
    database.executeStatement(
        "insert into financing_arrangement values ('arrangement-1', 'principal', 'interest-payable', '%s')"
            .formatted(SqliteTestPostingIds.valueForLabel("financing-origin")));
    database.executeStatement(
        "insert into financing_application values ('INTEREST_ACCRUAL', 'arrangement-1', 'EUR', 100, '%s')"
            .formatted(SqliteTestPostingIds.valueForLabel("financing-application")));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteFinancingOriginatingEntryMapper.originatingEntry(
                database,
                new PostingId("edd155bd-c189-3610-8b56-8579b27f5d02"),
                postingRow,
                JOURNAL_ENTRY,
                PostingOriginKind.FINANCING_PRINCIPAL_REPAYMENT));
  }

  private static void assertMissingForeignExchangeFacts(
      SqliteNativeDatabase database, SqliteNativeStatement postingRow) {
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRealizedForeignExchangeOriginatingEntryMapper.originatingEntry(
                database,
                new PostingId("fc48ea89-a9d6-39dd-8045-c4d898b83328"),
                postingRow,
                JOURNAL_ENTRY,
                PostingOriginKind.FOREIGN_CURRENCY_OBLIGATION,
                null));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRealizedForeignExchangeOriginatingEntryMapper.originatingEntry(
                database,
                new PostingId("fc48ea89-a9d6-39dd-8045-c4d898b83328"),
                postingRow,
                JOURNAL_ENTRY,
                PostingOriginKind.REALIZED_FOREIGN_EXCHANGE_SETTLEMENT,
                null));
    database.executeStatement(
        """
        insert into foreign_currency_obligation values (
            'obligation-1', 'receivable', 'foreign-exchange-gain', 'foreign-exchange-loss',
            'EUR', 10000, '%s')
        """
            .formatted(SqliteTestPostingIds.valueForLabel("foreign-exchange-origin")));
    database.executeStatement(
        "insert into foreign_currency_obligation_settlement values ('obligation-1', '%s')"
            .formatted(SqliteTestPostingIds.valueForLabel("foreign-exchange-settlement")));
    RealizedForeignExchangeBookkeepingEntryVariants.Settlement loss =
        assertInstanceOf(
            RealizedForeignExchangeBookkeepingEntryVariants.Settlement.class,
            SqliteRealizedForeignExchangeOriginatingEntryMapper.originatingEntry(
                database,
                new PostingId("455cb4e4-449a-3750-8119-1a95e46be2f5"),
                postingRow,
                JOURNAL_ENTRY,
                PostingOriginKind.REALIZED_FOREIGN_EXCHANGE_SETTLEMENT,
                foreignExchangeDetails("90.00")));
    assertFalse(Objects.requireNonNull(loss.resolvedSettlement()).gain());
  }

  private static ForeignExchangeDetails foreignExchangeDetails(String functionalAmount) {
    MonetaryAmount transaction = MonetaryAmount.of(Money.parse("USD", "100.00"));
    MonetaryAmount functional = MonetaryAmount.of(Money.parse("EUR", functionalAmount));
    return new ForeignExchangeDetails(
        transaction,
        functional,
        new QuotedExchangeRate(transaction, functional, LocalDate.parse("2026-07-15"), "ecb-spot"),
        ForeignExchangeTreatmentKind.SPOT_TRANSACTION);
  }

  private static void createLifecycleMapperTables(SqliteNativeDatabase database) {
    database.executeScript(
        """
        create table fixed_asset (
            fixed_asset_id text, asset_account_code text, accumulated_depreciation_account_code text,
            depreciation_expense_account_code text, disposal_gain_account_code text,
            disposal_loss_account_code text, currency_code text, cost_minor integer,
            residual_value_minor integer, in_service_date text, useful_life_months integer,
            origin_posting_id text
        );
        create table fixed_asset_application (
            application_kind text, fixed_asset_id text, currency_code text, amount_minor integer,
            application_posting_id text
        );
        create table financing_arrangement (
            financing_arrangement_id text, principal_liability_account_code text,
            interest_payable_account_code text, origin_posting_id text
        );
        create table financing_application (
            application_kind text, financing_arrangement_id text, currency_code text,
            amount_minor integer, application_posting_id text
        );
        create table foreign_currency_obligation (
            foreign_currency_obligation_id text, receivable_account_code text,
            realized_gain_account_code text, realized_loss_account_code text,
            functional_currency_code text, functional_carrying_amount_minor integer,
            origin_posting_id text
        );
        create table foreign_currency_obligation_settlement (
            foreign_currency_obligation_id text, settlement_posting_id text
        );
        """);
  }

  private static SqliteNativeStatement postingRow(SqliteNativeDatabase database) {
    return SqliteNativeStatements.prepare(
        database,
        """
        select
            'posting-1', 'STANDARD', 'FIXED_ASSET_CAPITALIZATION', 'debit', 'credit', null,
            'EUR', 100, null, null, null, null, '2026-07-15', '2026-07-15T00:00:00Z',
            'actor', 'AGENT', 'command', 'idempotency', 'causation', 'correlation', null,
            'CLI', null, null, null
        """);
  }

  private SqliteNativeDatabase openNativeDatabase(String fileName) {
    return SqliteNativeKeyFileAccess.open(
        tempDirectory.resolve(fileName), keyFilePath(tempDirectory.resolve(fileName)));
  }

  private static Path keyFilePath(Path bookPath) {
    try {
      Path keyPath = bookPath.resolveSibling(bookPath.getFileName() + ".key");
      SqliteBookKeyFileGenerator.generate(keyPath);
      Files.writeString(keyPath, TEST_BOOK_KEY);
      return keyPath;
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }
}
