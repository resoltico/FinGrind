package dev.erst.fingrind.sqlite;

import static dev.erst.fingrind.sqlite.SqlitePostingFactFixtureSupport.accountTaxonomy;
import static dev.erst.fingrind.sqlite.SqlitePostingFactFixtureSupport.financialPositionTaxonomy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.PostingOriginKind;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Proves lifecycle aggregates cannot diverge from the immutable typed facts that originated them.
 */
class SqliteLifecycleFactBindingStorageContractTest extends SqlitePostingFactStoreTestSupport {
  private static final String DECLARED_AT = "2026-01-01T00:00:00Z";

  @Test
  void lifecycleApplications_rejectValuesThatDisagreeWithTheirRetainedOriginatingFacts() {
    Path bookPath = tempDirectory.resolve("lifecycle-fact-binding.sqlite");
    withStandaloneDatabase(
        bookAccess(bookPath),
        database -> {
          SqliteBookSchemaBootstrap.initializeBook(database);
          insertCanonicalInitializedBookMetadata(database);
          insertLifecycleAccounts(database);

          assertFixedAssetFactsCannotDiverge(database);
          assertFinancingFactsCannotDiverge(database);
          assertForeignExchangeSettlementFactsCannotDiverge(database);
        });
  }

  private void assertFixedAssetFactsCannotDiverge(SqliteNativeDatabase database) {
    insertTypedPostingFact(
        database,
        "fixed-asset-capitalization",
        "2026-01-01",
        PostingOriginKind.FIXED_ASSET_CAPITALIZATION,
        "fixed-asset",
        "cash",
        10_000);
    database.executeStatement(
        """
        insert into fixed_asset (
            fixed_asset_id,
            origin_posting_id,
            capitalized_on,
            asset_account_code,
            accumulated_depreciation_account_code,
            depreciation_expense_account_code,
            disposal_gain_account_code,
            disposal_loss_account_code,
            currency_code,
            cost_minor,
            residual_value_minor,
            in_service_date,
            useful_life_months
        ) values (
            'fixed-asset-001',
            'fixed-asset-capitalization',
            '2026-01-01',
            'fixed-asset',
            'accumulated-depreciation',
            'depreciation-expense',
            'disposal-gain',
            'disposal-loss',
            'EUR',
            10000,
            0,
            '2026-01-01',
            120
        )
        """);
    insertTypedPostingFact(
        database,
        "fixed-asset-depreciation",
        "2026-02-01",
        PostingOriginKind.FIXED_ASSET_DEPRECIATION,
        "depreciation-expense",
        "accumulated-depreciation",
        1_000);
    database.executeStatement(
        """
        insert into fixed_asset_application (
            application_posting_id,
            fixed_asset_id,
            application_kind,
            effective_date,
            currency_code,
            amount_minor
        ) values (
            'fixed-asset-depreciation',
            'fixed-asset-001',
            'DEPRECIATION',
            '2026-02-01',
            'EUR',
            1000
        )
        """);
    insertTypedPostingFact(
        database,
        "fixed-asset-disposal",
        "2026-03-01",
        PostingOriginKind.FIXED_ASSET_DISPOSAL,
        "cash",
        "fixed-asset",
        9_000);

    assertTriggerRejection(
        () ->
            database.executeStatement(
                """
                insert into fixed_asset_application (
                    application_posting_id,
                    fixed_asset_id,
                    application_kind,
                    effective_date,
                    currency_code,
                    amount_minor
                ) values (
                    'fixed-asset-disposal',
                    'fixed-asset-001',
                    'DISPOSAL',
                    '2026-03-01',
                    'EUR',
                    9001
                )
                """));
    assertEquals(1, countRows(database, "fixed_asset_application"));
  }

  private void assertFinancingFactsCannotDiverge(SqliteNativeDatabase database) {
    insertTypedPostingFact(
        database,
        "financing-borrowing",
        "2026-01-01",
        PostingOriginKind.FINANCING_BORROWING,
        "cash",
        "financing-principal",
        10_000);
    database.executeStatement(
        """
        insert into financing_arrangement (
            financing_arrangement_id,
            origin_posting_id,
            originated_on,
            principal_liability_account_code,
            interest_payable_account_code,
            currency_code,
            original_principal_minor
        ) values (
            'financing-001',
            'financing-borrowing',
            '2026-01-01',
            'financing-principal',
            'financing-interest-payable',
            'EUR',
            10000
        )
        """);
    insertTypedPostingFact(
        database,
        "financing-principal-repayment",
        "2026-02-01",
        PostingOriginKind.FINANCING_PRINCIPAL_REPAYMENT,
        "financing-principal",
        "cash",
        2_500);

    assertTriggerRejection(
        () ->
            database.executeStatement(
                """
                insert into financing_application (
                    application_posting_id,
                    financing_arrangement_id,
                    application_kind,
                    effective_date,
                    currency_code,
                    amount_minor
                ) values (
                    'financing-principal-repayment',
                    'financing-001',
                    'PRINCIPAL_REPAYMENT',
                    '2026-02-01',
                    'EUR',
                    2501
                )
                """));
    assertEquals(0, countRows(database, "financing_application"));
  }

  private void assertForeignExchangeSettlementFactsCannotDiverge(SqliteNativeDatabase database) {
    insertTypedPostingFact(
        database,
        "foreign-currency-obligation",
        "2026-01-01",
        PostingOriginKind.FOREIGN_CURRENCY_OBLIGATION,
        "foreign-receivable",
        "foreign-revenue",
        10_000);
    insertForeignExchangeFacts(database, "foreign-currency-obligation", 1_000, 10_000);
    database.executeStatement(
        """
        insert into foreign_currency_obligation (
            foreign_currency_obligation_id,
            origin_posting_id,
            originated_on,
            receivable_account_code,
            realized_gain_account_code,
            realized_loss_account_code,
            transaction_currency_code,
            transaction_amount_minor,
            functional_currency_code,
            functional_carrying_amount_minor
        ) values (
            'foreign-currency-obligation-001',
            'foreign-currency-obligation',
            '2026-01-01',
            'foreign-receivable',
            'foreign-exchange-gain',
            'foreign-exchange-loss',
            'USD',
            1000,
            'EUR',
            10000
        )
        """);
    insertTypedPostingFact(
        database,
        "foreign-currency-obligation-loss",
        "2026-01-02",
        PostingOriginKind.FOREIGN_CURRENCY_OBLIGATION,
        "foreign-receivable",
        "foreign-revenue",
        10_000);
    insertForeignExchangeFacts(database, "foreign-currency-obligation-loss", 1_000, 10_000);
    database.executeStatement(
        """
        insert into foreign_currency_obligation (
            foreign_currency_obligation_id,
            origin_posting_id,
            originated_on,
            receivable_account_code,
            realized_gain_account_code,
            realized_loss_account_code,
            transaction_currency_code,
            transaction_amount_minor,
            functional_currency_code,
            functional_carrying_amount_minor
        ) values (
            'foreign-currency-obligation-002',
            'foreign-currency-obligation-loss',
            '2026-01-02',
            'foreign-receivable',
            'foreign-exchange-gain',
            'foreign-exchange-loss',
            'USD',
            1000,
            'EUR',
            10000
        )
        """);
    insertTypedPostingFact(
        database,
        "foreign-exchange-realized-gain",
        "2026-02-01",
        PostingOriginKind.REALIZED_FOREIGN_EXCHANGE_SETTLEMENT,
        "cash",
        "foreign-receivable",
        10_500);
    insertForeignExchangeFacts(database, "foreign-exchange-realized-gain", 1_000, 10_500);
    insertForeignExchangeSettlement(
        database,
        "foreign-exchange-realized-gain",
        "foreign-currency-obligation-001",
        "2026-02-01",
        10_500);
    insertTypedPostingFact(
        database,
        "foreign-exchange-realized-loss",
        "2026-02-02",
        PostingOriginKind.REALIZED_FOREIGN_EXCHANGE_SETTLEMENT,
        "cash",
        "foreign-receivable",
        9_500);
    insertForeignExchangeFacts(database, "foreign-exchange-realized-loss", 1_000, 9_500);
    insertForeignExchangeSettlement(
        database,
        "foreign-exchange-realized-loss",
        "foreign-currency-obligation-002",
        "2026-02-02",
        9_500);
    assertEquals(
        List.of(Optional.of(true), Optional.of(false)),
        SqliteRealizedForeignExchangeStatementQueries.load(database).stream()
            .map(
                dev.erst.fingrind.executor.bookkeeping.ForeignCurrencyObligationRecord
                    ::realizedGain)
            .toList());
    insertTypedPostingFact(
        database,
        "foreign-exchange-transaction-mismatch",
        "2026-02-01",
        PostingOriginKind.REALIZED_FOREIGN_EXCHANGE_SETTLEMENT,
        "cash",
        "foreign-receivable",
        9_500);
    insertForeignExchangeFacts(database, "foreign-exchange-transaction-mismatch", 999, 9_500);
    insertTypedPostingFact(
        database,
        "foreign-exchange-functional-mismatch",
        "2026-02-02",
        PostingOriginKind.REALIZED_FOREIGN_EXCHANGE_SETTLEMENT,
        "cash",
        "foreign-receivable",
        9_000);
    insertForeignExchangeFacts(database, "foreign-exchange-functional-mismatch", 1_000, 9_000);

    assertTriggerRejection(
        () ->
            insertForeignExchangeSettlement(
                database,
                "foreign-exchange-transaction-mismatch",
                "foreign-currency-obligation-001",
                "2026-02-01",
                9_500));
    assertTriggerRejection(
        () ->
            insertForeignExchangeSettlement(
                database,
                "foreign-exchange-functional-mismatch",
                "foreign-currency-obligation-001",
                "2026-02-02",
                9_500));
    assertEquals(2, countRows(database, "foreign_currency_obligation_settlement"));
  }

  private static void insertLifecycleAccounts(SqliteNativeDatabase database) {
    insertAccountRow(
        database,
        "cash",
        "Cash",
        AccountType.ASSET.wireValue(),
        financialPositionTaxonomy(FinancialPositionLineClassification.CURRENT_ASSET),
        1,
        DECLARED_AT);
    insertAccountRow(
        database,
        "fixed-asset",
        "Fixed Asset",
        AccountType.ASSET.wireValue(),
        financialPositionTaxonomy(FinancialPositionLineClassification.NONCURRENT_ASSET),
        1,
        DECLARED_AT);
    insertAccountRow(
        database,
        "accumulated-depreciation",
        "Accumulated Depreciation",
        AccountType.ASSET.wireValue(),
        financialPositionTaxonomy(FinancialPositionLineClassification.NONCURRENT_ASSET),
        1,
        DECLARED_AT);
    insertAccountRow(
        database,
        "depreciation-expense",
        "Depreciation Expense",
        AccountType.EXPENSE.wireValue(),
        accountTaxonomy(AccountType.EXPENSE),
        1,
        DECLARED_AT);
    insertAccountRow(
        database,
        "disposal-gain",
        "Disposal Gain",
        AccountType.REVENUE.wireValue(),
        accountTaxonomy(AccountType.REVENUE),
        1,
        DECLARED_AT);
    insertAccountRow(
        database,
        "disposal-loss",
        "Disposal Loss",
        AccountType.EXPENSE.wireValue(),
        accountTaxonomy(AccountType.EXPENSE),
        1,
        DECLARED_AT);
    insertAccountRow(
        database,
        "financing-principal",
        "Financing Principal",
        AccountType.LIABILITY.wireValue(),
        financialPositionTaxonomy(FinancialPositionLineClassification.NONCURRENT_LIABILITY),
        1,
        DECLARED_AT);
    insertAccountRow(
        database,
        "financing-interest-payable",
        "Financing Interest Payable",
        AccountType.LIABILITY.wireValue(),
        financialPositionTaxonomy(FinancialPositionLineClassification.CURRENT_LIABILITY),
        1,
        DECLARED_AT);
    insertAccountRow(
        database,
        "foreign-receivable",
        "Foreign Receivable",
        AccountType.ASSET.wireValue(),
        financialPositionTaxonomy(FinancialPositionLineClassification.TRADE_RECEIVABLE),
        1,
        DECLARED_AT);
    insertAccountRow(
        database,
        "foreign-revenue",
        "Foreign Revenue",
        AccountType.REVENUE.wireValue(),
        accountTaxonomy(AccountType.REVENUE),
        1,
        DECLARED_AT);
    insertAccountRow(
        database,
        "foreign-exchange-gain",
        "Foreign Exchange Gain",
        AccountType.REVENUE.wireValue(),
        accountTaxonomy(AccountType.REVENUE),
        1,
        DECLARED_AT);
    insertAccountRow(
        database,
        "foreign-exchange-loss",
        "Foreign Exchange Loss",
        AccountType.EXPENSE.wireValue(),
        accountTaxonomy(AccountType.EXPENSE),
        1,
        DECLARED_AT);
  }

  private static void insertTypedPostingFact(
      SqliteNativeDatabase database,
      String postingId,
      String effectiveDate,
      PostingOriginKind originKind,
      String debitAccountCode,
      String creditAccountCode,
      long amountMinor) {
    database.executeStatement(
        """
        insert into posting_fact (
            posting_id,
            posting_kind,
            posting_origin_kind,
            entry_primary_debit_account_code,
            entry_primary_credit_account_code,
            entry_adjunct_account_code,
            entry_amount_currency_code,
            entry_amount_minor,
            entry_adjunct_amount_minor,
            entry_quantity,
            entry_unit_cost_currency_code,
            entry_unit_cost_minor,
            effective_date,
            recorded_at,
            command_id,
            idempotency_key,
            causation_id,
            correlation_id,
            reason,
            source_channel,
            prior_posting_id,
            request_fingerprint_version,
            request_fingerprint_sha256
        ) values (
            '%s',
            'STANDARD',
            '%s',
            '%s',
            '%s',
            null,
            'EUR',
            %d,
            null,
            null,
            null,
            null,
            '%s',
            '%sT00:00:00Z',
            '019e26ff-0000-7002-8000-000000000001',
            'idempotency-%s',
            'causation-%s',
            null,
            null,
            'SYSTEM',
            null,
            1,
            '%s'
        )
        """
            .formatted(
                postingId,
                originKind.wireValue(),
                debitAccountCode,
                creditAccountCode,
                amountMinor,
                effectiveDate,
                effectiveDate,
                postingId,
                postingId,
                "0".repeat(64)));
  }

  private static void insertForeignExchangeFacts(
      SqliteNativeDatabase database,
      String postingId,
      long transactionAmountMinor,
      long functionalAmountMinor) {
    database.executeStatement(
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
        ) values (
            '%s',
            'SPOT_TRANSACTION',
            'USD',
            %d,
            'EUR',
            %d,
            %d,
            %d,
            '2026-01-01',
            'test quote'
        )
        """
            .formatted(
                postingId,
                transactionAmountMinor,
                functionalAmountMinor,
                transactionAmountMinor,
                functionalAmountMinor));
  }

  private static void insertForeignExchangeSettlement(
      SqliteNativeDatabase database,
      String postingId,
      String foreignCurrencyObligationId,
      String effectiveDate,
      long functionalSettlementAmountMinor) {
    database.executeStatement(
        """
        insert into foreign_currency_obligation_settlement (
            settlement_posting_id,
            foreign_currency_obligation_id,
            effective_date,
            functional_currency_code,
            functional_settlement_amount_minor
        ) values (
            '%s',
            '%s',
            '%s',
            'EUR',
            %d
        )
        """
            .formatted(
                postingId,
                foreignCurrencyObligationId,
                effectiveDate,
                functionalSettlementAmountMinor));
  }

  private static void assertTriggerRejection(Runnable operation) {
    SqliteNativeException exception = assertThrows(SqliteNativeException.class, operation::run);
    assertEquals(SqliteNativeResultCode.code("CONSTRAINT_TRIGGER"), exception.resultCode());
  }
}
