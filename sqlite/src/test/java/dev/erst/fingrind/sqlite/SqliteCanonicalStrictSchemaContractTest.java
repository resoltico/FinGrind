package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.RequestFingerprint;
import dev.erst.fingrind.core.SourceChannel;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Strict schema constraint tests for the canonical protected-book schema. */
class SqliteCanonicalStrictSchemaContractTest extends SqlitePostingFactStoreTestSupport {
  @Test
  void canonicalStrictSchema_rejectsNonLosslessTypeMismatches() {
    Path bookPath = tempDirectory.resolve("strict-datatype.sqlite");
    assertDoesNotThrow(
        () ->
            withStandaloneDatabase(
                bookAccess(bookPath),
                database -> {
                  SqliteBookSchemaBootstrap.initializeBook(database);
                  insertCanonicalInitializedBookMetadata(database);
                  insertAccountRow(database, "1000", "Cash", "DEBIT", 1, "2026-04-07T10:15:30Z");
                  insertPostingFactRow(database, "posting-1", "idem-1");
                  SqliteNativeException exception =
                      assertThrows(
                          SqliteNativeException.class,
                          () ->
                              database.executeStatement(
                                  """
                                  insert into journal_line (
                                      posting_id,
                                      line_order,
                                      account_code,
                                      entry_side,
                                      currency_code,
                                      amount_minor
                                  ) values (
                                      'posting-1',
                                      'not-an-integer',
                                      '1000',
                                      'DEBIT',
                                      'EUR',
                                      1000
                                  )
                                  """));
                  assertEquals(
                      SqliteNativeResultCode.code("CONSTRAINT_DATATYPE"), exception.resultCode());
                  assertEquals("SQLITE_CONSTRAINT_DATATYPE", exception.resultName());
                  assertEquals(0, queryInt(database, "select count(*) from journal_line"));
                }));
  }

  @Test
  void canonicalStrictSchema_rejectsPersistedIdentifierValuesOutsideTheDomainContract() {
    Path bookPath = tempDirectory.resolve("identifier-contract.sqlite");
    assertDoesNotThrow(
        () ->
            withStandaloneDatabase(
                bookAccess(bookPath),
                database -> {
                  SqliteBookSchemaBootstrap.initializeBook(database);
                  insertCanonicalInitializedBookMetadata(database);
                  SqliteNativeException invalidAccountCode =
                      assertThrows(
                          SqliteNativeException.class,
                          () ->
                              insertAccountRow(
                                  database, "_1000", "Cash", "DEBIT", 1, "2026-04-07T10:15:30Z"));
                  assertEquals(
                      SqliteNativeResultCode.code("CONSTRAINT_CHECK"),
                      invalidAccountCode.resultCode());
                  assertEquals("SQLITE_CONSTRAINT_CHECK", invalidAccountCode.resultName());
                  assertEquals(0, queryInt(database, "select count(*) from account"));
                  SqliteNativeException invalidAccountName =
                      assertThrows(
                          SqliteNativeException.class,
                          () ->
                              insertAccountRow(
                                  database, "1000", "   ", "DEBIT", 1, "2026-04-07T10:15:30Z"));
                  assertEquals(
                      SqliteNativeResultCode.code("CONSTRAINT_CHECK"),
                      invalidAccountName.resultCode());
                  assertEquals("SQLITE_CONSTRAINT_CHECK", invalidAccountName.resultName());
                  assertEquals(0, queryInt(database, "select count(*) from account"));
                  insertAccountRow(database, "1000", "Cash", "DEBIT", 1, "2026-04-07T10:15:30Z");
                  SqliteNativeException invalidIdempotencyKey =
                      assertThrows(
                          SqliteNativeException.class,
                          () -> insertPostingFactRow(database, "posting-1", "idem key"));
                  assertEquals(
                      SqliteNativeResultCode.code("CONSTRAINT_CHECK"),
                      invalidIdempotencyKey.resultCode());
                  assertEquals("SQLITE_CONSTRAINT_CHECK", invalidIdempotencyKey.resultName());
                  assertEquals(0, queryInt(database, "select count(*) from posting_fact"));
                  SqliteNativeException invalidCommandId =
                      assertThrows(
                          SqliteNativeException.class,
                          () ->
                              insertPostingFactRow(
                                  database,
                                  "posting-command",
                                  "actor-command",
                                  "   ",
                                  "idem-command",
                                  "cause-command",
                                  "null"));
                  assertEquals(
                      SqliteNativeResultCode.code("CONSTRAINT_CHECK"),
                      invalidCommandId.resultCode());
                  assertEquals("SQLITE_CONSTRAINT_CHECK", invalidCommandId.resultName());
                  assertEquals(0, queryInt(database, "select count(*) from posting_fact"));
                  SqliteNativeException invalidCausationId =
                      assertThrows(
                          SqliteNativeException.class,
                          () ->
                              insertPostingFactRow(
                                  database,
                                  "posting-cause",
                                  "actor-cause",
                                  "command-cause",
                                  "idem-cause",
                                  "   ",
                                  "null"));
                  assertEquals(
                      SqliteNativeResultCode.code("CONSTRAINT_CHECK"),
                      invalidCausationId.resultCode());
                  assertEquals("SQLITE_CONSTRAINT_CHECK", invalidCausationId.resultName());
                  assertEquals(0, queryInt(database, "select count(*) from posting_fact"));
                  SqliteNativeException invalidCorrelationId =
                      assertThrows(
                          SqliteNativeException.class,
                          () ->
                              insertPostingFactRow(
                                  database,
                                  "posting-correlation",
                                  "actor-correlation",
                                  "command-correlation",
                                  "idem-correlation",
                                  "cause-correlation",
                                  "'   '"));
                  assertEquals(
                      SqliteNativeResultCode.code("CONSTRAINT_CHECK"),
                      invalidCorrelationId.resultCode());
                  assertEquals("SQLITE_CONSTRAINT_CHECK", invalidCorrelationId.resultName());
                  assertEquals(0, queryInt(database, "select count(*) from posting_fact"));
                }));
  }

  @Test
  void canonicalStrictSchema_rejectsImpossibleFiscalYearAnchors() {
    Path bookPath = tempDirectory.resolve("invalid-fiscal-year-anchor.sqlite");
    assertDoesNotThrow(
        () ->
            withStandaloneDatabase(
                bookAccess(bookPath),
                database -> {
                  SqliteBookSchemaBootstrap.initializeBook(database);
                  SqliteNativeException invalidAnchor =
                      assertThrows(
                          SqliteNativeException.class,
                          () ->
                              database.executeStatement(
                                  """
                                  insert into book_identity (
                                      singleton_id,
                                      entity_name,
                                      accounting_kernel_profile,
                                      accounting_basis,
                                      accounting_framework_position,
                                      entity_form,
                                      book_template_id,
                                      costing_doctrine,
                                      functional_currency_code,
                                      fiscal_year_start_month,
                                      fiscal_year_start_day,
                                      book_start_effective_date
                                  ) values (
                                      1,
                                      'Acme Studio',
                                      'internal-management-bookkeeping-kernel',
                                      'CASH',
                                      'NON_STATUTORY_INTERNAL_MANAGEMENT',
                                      'OWNER_MANAGED_SINGLE_ENTITY',
                                      'OWNER_MANAGED_SERVICE',
                                      null,
                                      'EUR',
                                      2,
                                      30,
                                      '2026-01-01'
                                  )
                                  """));
                  assertEquals(
                      SqliteNativeResultCode.code("CONSTRAINT_CHECK"), invalidAnchor.resultCode());
                  assertEquals("SQLITE_CONSTRAINT_CHECK", invalidAnchor.resultName());
                  assertEquals(0, queryInt(database, "select count(*) from book_identity"));
                }));
  }

  @Test
  void canonicalStrictSchema_rejectsJournalLineCurrencyOutsideBookFunctionalCurrency() {
    Path bookPath = tempDirectory.resolve("journal-line-functional-currency.sqlite");
    assertDoesNotThrow(
        () ->
            withStandaloneDatabase(
                bookAccess(bookPath),
                database -> {
                  SqliteBookSchemaBootstrap.initializeBook(database);
                  insertCanonicalInitializedBookMetadata(database);
                  insertAccountRow(database, "1000", "Cash", "DEBIT", 1, "2026-04-07T10:15:30Z");
                  insertAccountRow(
                      database, "4000", "Revenue", "CREDIT", 1, "2026-04-07T10:15:30Z");
                  insertPostingFactRow(database, "posting-1", "idem-1");

                  SqliteNativeException mismatchedCurrency =
                      assertThrows(
                          SqliteNativeException.class,
                          () ->
                              insertJournalLineRow(
                                  database, "posting-1", 0, "1000", "DEBIT", "USD", 1000));
                  assertEquals(
                      SqliteNativeResultCode.code("CONSTRAINT_TRIGGER"),
                      mismatchedCurrency.resultCode());
                  assertEquals("SQLITE_CONSTRAINT_TRIGGER", mismatchedCurrency.resultName());
                  assertEquals(0, queryInt(database, "select count(*) from journal_line"));
                }));
  }

  @Test
  void canonicalStrictSchema_rejectsNonHeaderAndTaxonomyMismatchedParents() {
    Path bookPath = tempDirectory.resolve("account-parent-contract.sqlite");
    assertDoesNotThrow(
        () ->
            withStandaloneDatabase(
                bookAccess(bookPath),
                database -> {
                  SqliteBookSchemaBootstrap.initializeBook(database);
                  insertCanonicalInitializedBookMetadata(database);
                  insertAccountRow(database, "1000", "Cash", "DEBIT", 1, "2026-04-07T10:15:30Z");

                  SqliteNativeException nonHeaderParent =
                      assertThrows(
                          SqliteNativeException.class,
                          () ->
                              database.executeStatement(
                                  """
                                  insert into account (
                                      account_code,
                                      account_name,
                                      account_type,
                                      account_node_kind,
                                      parent_account_code,
                                      financial_position_line_classification,
                                      cash_flow_asset_classification,
                                      profit_and_loss_line_classification,
                                      unit_of_measure,
                                      quantity_scale,
                                      active,
                                      declared_at
                                  ) values (
                                      '1010',
                                      'Petty Cash',
                                      'ASSET',
                                      'POSTABLE',
                                      '1000',
                                      'CURRENT_ASSET',
                                      'CASH_AND_CASH_EQUIVALENT',
                                      null,
                                      null,
                                      null,
                                      1,
                                      '2026-04-07T10:15:30Z'
                                  )
                                  """));
                  assertEquals(
                      SqliteNativeResultCode.code("CONSTRAINT_TRIGGER"),
                      nonHeaderParent.resultCode());
                  assertEquals("SQLITE_CONSTRAINT_TRIGGER", nonHeaderParent.resultName());

                  database.executeStatement(
                      """
                      insert into account (
                          account_code,
                          account_name,
                          account_type,
                          account_node_kind,
                          parent_account_code,
                          financial_position_line_classification,
                          cash_flow_asset_classification,
                          profit_and_loss_line_classification,
                          unit_of_measure,
                          quantity_scale,
                          active,
                          declared_at
                      ) values (
                          '1100',
                          'Cash Header',
                          'ASSET',
                          'HEADER',
                          null,
                          'CURRENT_ASSET',
                          'CASH_AND_CASH_EQUIVALENT',
                          null,
                          null,
                          null,
                          1,
                          '2026-04-07T10:15:30Z'
                      )
                      """);

                  SqliteNativeException taxonomyMismatch =
                      assertThrows(
                          SqliteNativeException.class,
                          () ->
                              database.executeStatement(
                                  """
                                  insert into account (
                                      account_code,
                                      account_name,
                                      account_type,
                                      account_node_kind,
                                      parent_account_code,
                                      financial_position_line_classification,
                                      cash_flow_asset_classification,
                                      profit_and_loss_line_classification,
                                      unit_of_measure,
                                      quantity_scale,
                                      active,
                                      declared_at
                                  ) values (
                                      '1110',
                                      'Equipment',
                                      'ASSET',
                                      'POSTABLE',
                                      '1100',
                                      'NONCURRENT_ASSET',
                                      'NON_CASH',
                                      null,
                                      null,
                                      null,
                                      1,
                                      '2026-04-07T10:15:30Z'
                                  )
                                  """));
                  assertEquals(
                      SqliteNativeResultCode.code("CONSTRAINT_TRIGGER"),
                      taxonomyMismatch.resultCode());
                  assertEquals("SQLITE_CONSTRAINT_TRIGGER", taxonomyMismatch.resultName());
                  assertEquals(2, queryInt(database, "select count(*) from account"));
                }));
  }

  @Test
  void canonicalStrictSchema_rejectsLateOpeningBalanceInsertions() {
    Path bookPath = tempDirectory.resolve("late-opening-balance.sqlite");
    assertDoesNotThrow(
        () ->
            withStandaloneDatabase(
                bookAccess(bookPath),
                database -> {
                  SqliteBookSchemaBootstrap.initializeBook(database);
                  insertCanonicalInitializedBookMetadata(database);
                  insertAccountRow(
                      database, "1000", "Cash", "ASSET", "DEBIT", 1, "2026-04-07T10:15:30Z");
                  insertAccountRow(
                      database,
                      "3000",
                      "Opening Equity",
                      "EQUITY",
                      "CREDIT",
                      1,
                      "2026-04-07T10:15:30Z");
                  insertPostingFactRow(
                      database,
                      "posting-standard",
                      "STANDARD",
                      "2026-04-07",
                      "2026-04-07T10:15:30Z",
                      new PostingFactSqlLiterals(
                          "actor-standard",
                          "AGENT",
                          "command-standard",
                          "idem-standard",
                          "cause-standard",
                          "null",
                          "null",
                          SourceChannel.CLI.wireValue(),
                          "null"));
                  insertJournalLineRow(
                      database, "posting-standard", 0, "1000", "DEBIT", "EUR", 1000);
                  insertJournalLineRow(
                      database, "posting-standard", 1, "3000", "CREDIT", "EUR", 1000);

                  SqliteNativeException lateOpeningBalance =
                      assertThrows(
                          SqliteNativeException.class,
                          () ->
                              insertPostingFactRow(
                                  database,
                                  "posting-opening-balance",
                                  "OPENING_BALANCE",
                                  "2026-04-01",
                                  "2026-04-07T10:15:31Z",
                                  new PostingFactSqlLiterals(
                                      "actor-opening",
                                      "AGENT",
                                      "command-opening",
                                      "idem-opening",
                                      "cause-opening",
                                      "null",
                                      "null",
                                      SourceChannel.CLI.wireValue(),
                                      "null")));
                  assertEquals(
                      SqliteNativeResultCode.code("CONSTRAINT_TRIGGER"),
                      lateOpeningBalance.resultCode());
                  assertEquals("SQLITE_CONSTRAINT_TRIGGER", lateOpeningBalance.resultName());
                  assertEquals(1, queryInt(database, "select count(*) from posting_fact"));
                }));
  }

  @Test
  void canonicalStrictSchema_preservesAccountDefinitionsAndIdentities() {
    Path databasePath = tempDirectory.resolve("posted-account-definition.sqlite");
    assertDoesNotThrow(
        () ->
            withStandaloneDatabase(
                bookAccess(databasePath),
                database -> {
                  SqliteBookSchemaBootstrap.initializeBook(database);
                  insertCanonicalInitializedBookMetadata(database);
                  insertAccountRow(
                      database, "1000", "Cash", "ASSET", "DEBIT", 1, "2026-04-07T10:15:30Z");
                  insertAccountRow(
                      database,
                      "3000",
                      "Owner Capital",
                      "EQUITY",
                      "CREDIT",
                      1,
                      "2026-04-07T10:15:30Z");
                  insertAccountRow(
                      database,
                      "1100",
                      "Unposted Cash Reserve",
                      "ASSET",
                      "DEBIT",
                      1,
                      "2026-04-07T10:15:30Z");
                  insertPostingFactRow(
                      database, "posting-account-lifecycle", "idem-account-lifecycle");
                  insertJournalLineRow(
                      database, "posting-account-lifecycle", 0, "1000", "DEBIT", "EUR", 1000);
                  insertJournalLineRow(
                      database, "posting-account-lifecycle", 1, "3000", "CREDIT", "EUR", 1000);

                  SqliteNativeException postedAccountAmendment =
                      assertThrows(
                          SqliteNativeException.class,
                          () ->
                              database.executeStatement(
                                  "update account set account_name = 'Renamed cash' where account_code = '1000'"));
                  assertEquals(
                      SqliteNativeResultCode.code("CONSTRAINT_TRIGGER"),
                      postedAccountAmendment.resultCode());
                  assertEquals(
                      "Cash",
                      queryText(
                          database,
                          "select account_name from account where account_code = '1000'"));

                  SqliteNativeException accountDeletion =
                      assertThrows(
                          SqliteNativeException.class,
                          () ->
                              database.executeStatement(
                                  "delete from account where account_code = '1100'"));
                  assertEquals(
                      SqliteNativeResultCode.code("CONSTRAINT_TRIGGER"),
                      accountDeletion.resultCode());
                  assertEquals(
                      "Unposted Cash Reserve",
                      queryText(
                          database,
                          "select account_name from account where account_code = '1100'"));
                }));
  }

  @Test
  void canonicalStrictSchema_enforcesInactiveAccountAndOpeningBalanceJournalLineRules() {
    Path inactiveAccountPath = tempDirectory.resolve("inactive-account-journal-line.sqlite");
    assertDoesNotThrow(
        () ->
            withStandaloneDatabase(
                bookAccess(inactiveAccountPath),
                database -> {
                  SqliteBookSchemaBootstrap.initializeBook(database);
                  insertCanonicalInitializedBookMetadata(database);
                  insertAccountRow(
                      database, "1000", "Cash", "ASSET", "DEBIT", 1, "2026-04-07T10:15:30Z");
                  insertAccountRow(
                      database,
                      "1100",
                      "Dormant Cash",
                      "ASSET",
                      "DEBIT",
                      0,
                      "2026-04-07T10:15:30Z");
                  insertPostingFactRow(database, "posting-inactive", "idem-inactive");

                  SqliteNativeException inactiveAccountLine =
                      assertThrows(
                          SqliteNativeException.class,
                          () ->
                              insertJournalLineRow(
                                  database, "posting-inactive", 0, "1100", "DEBIT", "EUR", 1000));
                  assertEquals(
                      SqliteNativeResultCode.code("CONSTRAINT_TRIGGER"),
                      inactiveAccountLine.resultCode());
                  assertEquals("SQLITE_CONSTRAINT_TRIGGER", inactiveAccountLine.resultName());
                  assertEquals(0, queryInt(database, "select count(*) from journal_line"));

                  insertPostingFactRow(database, "posting-prior", "idem-prior");
                  insertPostingFactRow(
                      database,
                      "posting-historical-reversal",
                      "STANDARD",
                      "2026-04-07",
                      "2026-04-07T10:15:31Z",
                      new PostingFactSqlLiterals(
                          "actor-reversal",
                          "AGENT",
                          "command-reversal",
                          "idem-reversal",
                          "cause-reversal",
                          "null",
                          "'historical correction'",
                          SourceChannel.CLI.wireValue(),
                          "'posting-prior'"));
                  insertJournalLineRow(
                      database, "posting-historical-reversal", 0, "1100", "CREDIT", "EUR", 1000);
                  assertEquals(
                      1,
                      queryInt(
                          database,
                          "select count(*) from journal_line where posting_id = 'posting-historical-reversal'"));
                }));

    Path nominalOpeningBalancePath = tempDirectory.resolve("opening-balance-nominal.sqlite");
    assertDoesNotThrow(
        () ->
            withStandaloneDatabase(
                bookAccess(nominalOpeningBalancePath),
                database -> {
                  SqliteBookSchemaBootstrap.initializeBook(database);
                  insertCanonicalInitializedBookMetadata(database);
                  insertAccountRow(
                      database, "1000", "Cash", "ASSET", "DEBIT", 1, "2026-04-07T10:15:30Z");
                  insertAccountRow(
                      database, "4000", "Sales", "REVENUE", "CREDIT", 1, "2026-04-07T10:15:30Z");
                  insertPostingFactRow(
                      database,
                      "posting-opening-balance",
                      "OPENING_BALANCE",
                      "2026-04-01",
                      "2026-04-07T10:15:30Z",
                      new PostingFactSqlLiterals(
                          "actor-opening",
                          "AGENT",
                          "command-opening",
                          "idem-opening",
                          "cause-opening",
                          "null",
                          "null",
                          SourceChannel.CLI.wireValue(),
                          "null"));

                  SqliteNativeException nominalOpeningBalanceLine =
                      assertThrows(
                          SqliteNativeException.class,
                          () ->
                              insertJournalLineRow(
                                  database,
                                  "posting-opening-balance",
                                  0,
                                  "4000",
                                  "CREDIT",
                                  "EUR",
                                  1000));
                  assertEquals(
                      SqliteNativeResultCode.code("CONSTRAINT_TRIGGER"),
                      nominalOpeningBalanceLine.resultCode());
                  assertEquals("SQLITE_CONSTRAINT_TRIGGER", nominalOpeningBalanceLine.resultName());
                  assertEquals(0, queryInt(database, "select count(*) from journal_line"));
                }));
  }

  @Test
  void canonicalStrictSchema_rejectsSweptInterimResultBackfillAndBrokenInterimResultSweepLinks() {
    Path invalidCloseTargetPath =
        tempDirectory.resolve("invalid-interim-result-sweep-target.sqlite");
    assertDoesNotThrow(
        () ->
            withStandaloneDatabase(
                bookAccess(invalidCloseTargetPath),
                database -> {
                  SqliteBookSchemaBootstrap.initializeBook(database);
                  insertCanonicalInitializedBookMetadata(database);
                  insertAccountRow(
                      database, "4000", "Sales", "REVENUE", "CREDIT", 1, "2026-04-07T10:15:30Z");

                  SqliteNativeException invalidCloseTarget =
                      assertThrows(
                          SqliteNativeException.class,
                          () ->
                              database.executeStatement(
                                  """
                                  insert into interim_result_sweep (
                                      interim_result_sweep_order,
                                      effective_date_from,
                                      effective_date_to,
                                      result_holding_account_code,
                                      swept_at
                                  ) values (
                                      1,
                                      '2026-04-01',
                                      '2026-04-30',
                                      '4000',
                                      '2026-04-30T23:59:59Z'
                                  )
                                  """));
                  assertEquals(
                      SqliteNativeResultCode.code("CONSTRAINT_TRIGGER"),
                      invalidCloseTarget.resultCode());
                  assertEquals("SQLITE_CONSTRAINT_TRIGGER", invalidCloseTarget.resultName());
                }));

    Path transferredPeriodResultPath = tempDirectory.resolve("closed-period-backfill.sqlite");
    assertDoesNotThrow(
        () ->
            withStandaloneDatabase(
                bookAccess(transferredPeriodResultPath),
                database -> {
                  SqliteBookSchemaBootstrap.initializeBook(database);
                  insertCanonicalInitializedBookMetadata(database);
                  insertAccountRow(
                      database, "1000", "Cash", "ASSET", "DEBIT", 1, "2026-04-07T10:15:30Z");
                  insertAccountRow(
                      database,
                      "3000",
                      "Result holding",
                      "EQUITY",
                      SqlitePostingFactFixtureSupport.financialPositionTaxonomy(
                          FinancialPositionLineClassification.RESULT_HOLDING),
                      1,
                      "2026-04-07T10:15:30Z");
                  insertAccountRow(
                      database, "4000", "Sales", "REVENUE", "CREDIT", 1, "2026-04-07T10:15:30Z");
                  insertPostingFactRow(
                      database,
                      "posting-interim-result-sweep",
                      "INTERIM_RESULT_SWEEP",
                      "2026-04-30",
                      "2026-04-30T23:59:59Z",
                      new PostingFactSqlLiterals(
                          "system:interimResultSweep",
                          "SYSTEM",
                          "interimResultSweep:2026-04",
                          "interimResultSweep:2026-04",
                          "interimResultSweep:2026-04",
                          "'interimResultSweep:2026-04'",
                          "null",
                          SourceChannel.SYSTEM.wireValue(),
                          "null"));
                  insertJournalLineRow(
                      database, "posting-interim-result-sweep", 0, "4000", "DEBIT", "EUR", 1000);
                  insertJournalLineRow(
                      database, "posting-interim-result-sweep", 1, "3000", "CREDIT", "EUR", 1000);
                  database.executeStatement(
                      """
                      insert into interim_result_sweep (
                          interim_result_sweep_order,
                          effective_date_from,
                          effective_date_to,
                          result_holding_account_code,
                          swept_at
                      ) values (
                          1,
                          '2026-04-01',
                          '2026-04-30',
                          '3000',
                          '2026-04-30T23:59:59Z'
                      )
                      """);
                  database.executeStatement(
                      """
                      insert into interim_result_sweep_posting (
                          interim_result_sweep_order,
                          posting_id
                      ) values (
                          1,
                          'posting-interim-result-sweep'
                      )
                      """);
                  insertPostingFactRow(
                      database,
                      "posting-post-close",
                      "STANDARD",
                      "2026-05-01",
                      "2026-05-01T10:15:30Z",
                      new PostingFactSqlLiterals(
                          "actor-post-close",
                          "AGENT",
                          "command-post-close",
                          "idem-post-close",
                          "cause-post-close",
                          "null",
                          "null",
                          SourceChannel.CLI.wireValue(),
                          "null"));
                  insertJournalLineRow(
                      database, "posting-post-close", 0, "1000", "DEBIT", "EUR", 1000);
                  insertJournalLineRow(
                      database, "posting-post-close", 1, "3000", "CREDIT", "EUR", 1000);

                  SqliteNativeException backfilledPosting =
                      assertThrows(
                          SqliteNativeException.class,
                          () ->
                              insertPostingFactRow(
                                  database,
                                  "posting-closed-period",
                                  "STANDARD",
                                  "2026-04-15",
                                  "2026-05-01T10:15:31Z",
                                  new PostingFactSqlLiterals(
                                      "actor-closed-period",
                                      "AGENT",
                                      "command-closed-period",
                                      "idem-closed-period",
                                      "cause-closed-period",
                                      "null",
                                      "null",
                                      SourceChannel.CLI.wireValue(),
                                      "null")));
                  assertEquals(
                      SqliteNativeResultCode.code("CONSTRAINT_TRIGGER"),
                      backfilledPosting.resultCode());
                  assertEquals("SQLITE_CONSTRAINT_TRIGGER", backfilledPosting.resultName());

                  SqliteNativeException brokenCloseLink =
                      assertThrows(
                          SqliteNativeException.class,
                          () ->
                              database.executeStatement(
                                  """
                                  insert into interim_result_sweep_posting (
                                      interim_result_sweep_order,
                                      posting_id
                                  ) values (
                                      1,
                                      'posting-post-close'
                                  )
                                  """));
                  assertEquals(
                      SqliteNativeResultCode.code("CONSTRAINT_TRIGGER"),
                      brokenCloseLink.resultCode());
                  assertEquals("SQLITE_CONSTRAINT_TRIGGER", brokenCloseLink.resultName());
                }));
  }

  private static void insertPostingFactRow(
      SqliteNativeDatabase database,
      String postingId,
      String actorId,
      String commandId,
      String idempotencyKey,
      String causationId,
      String correlationIdSqlLiteral) {
    insertPostingFactRow(
        database,
        postingId,
        "STANDARD",
        "2026-04-07",
        "2026-04-07T10:15:30Z",
        new PostingFactSqlLiterals(
            actorId,
            "AGENT",
            commandId,
            idempotencyKey,
            causationId,
            correlationIdSqlLiteral,
            "null",
            SourceChannel.CLI.wireValue(),
            "null"));
  }

  private static void insertPostingFactRow(
      SqliteNativeDatabase database,
      String postingId,
      String postingKind,
      String effectiveDate,
      String recordedAt,
      PostingFactSqlLiterals sqlLiterals) {
    String postingOriginKind = defaultPostingOriginKind(postingKind);
    database.executeStatement(
        """
        insert into posting_fact (
            posting_id,
            posting_kind,
            posting_origin_kind,
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
            '%s',
            '%s',
            '%s',
            '%s',
            '%s',
            '%s',
            '%s',
            %s,
            %s,
            '%s',
            %s,
            %d,
            '%s'
        )
        """
            .formatted(
                postingId,
                postingKind,
                postingOriginKind,
                effectiveDate,
                recordedAt,
                sqlLiterals.commandId(),
                sqlLiterals.idempotencyKey(),
                sqlLiterals.causationId(),
                sqlLiterals.correlationIdSqlLiteral(),
                sqlLiterals.reasonSqlLiteral(),
                sqlLiterals.sourceChannel(),
                sqlLiterals.priorPostingIdSqlLiteral(),
                RequestFingerprint.CURRENT_VERSION,
                "0".repeat(64)));
  }

  private static String defaultPostingOriginKind(String postingKind) {
    return switch (postingKind) {
      case "OPENING_BALANCE" ->
          dev.erst.fingrind.core.PostingOriginKind.OPENING_POSITION.wireValue();
      case "INTERIM_RESULT_SWEEP" ->
          dev.erst.fingrind.core.PostingOriginKind.INTERIM_RESULT_SWEEP.wireValue();
      case "FISCAL_YEAR_CLOSE" ->
          dev.erst.fingrind.core.PostingOriginKind.FISCAL_YEAR_CLOSE.wireValue();
      default -> dev.erst.fingrind.core.PostingOriginKind.REVERSAL.wireValue();
    };
  }

  private record PostingFactSqlLiterals(
      String actorId,
      String actorType,
      String commandId,
      String idempotencyKey,
      String causationId,
      String correlationIdSqlLiteral,
      String reasonSqlLiteral,
      String sourceChannel,
      String priorPostingIdSqlLiteral) {}
}
