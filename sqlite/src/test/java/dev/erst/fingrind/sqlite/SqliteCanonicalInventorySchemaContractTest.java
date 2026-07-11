package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Strict schema constraints for inventory-owned account and trading doctrine state. */
class SqliteCanonicalInventorySchemaContractTest extends SqlitePostingFactStoreTestSupport {
  @Test
  void canonicalStrictSchema_acceptsInventoryAssetAccounts() {
    Path bookPath = tempDirectory.resolve("inventory-account-contract.sqlite");
    assertDoesNotThrow(
        () ->
            withStandaloneDatabase(
                bookAccess(bookPath),
                database -> {
                  SqliteBookSchemaBootstrap.initializeBook(database);
                  insertCanonicalInitializedBookMetadata(database);
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
                          '1400',
                          'Inventory',
                          'ASSET',
                          'POSTABLE',
                          null,
                          'INVENTORY',
                          'NON_CASH',
                          null,
                          'unit',
                          0,
                          1,
                          '2026-04-07T10:15:30Z'
                      )
                      """);
                  assertEquals(1, queryInt(database, "select count(*) from account"));
                }));
  }

  @Test
  void canonicalStrictSchema_rejectsInventoryAccountsWithoutUnitOwnership() {
    Path bookPath = tempDirectory.resolve("inventory-account-unit-contract.sqlite");
    assertDoesNotThrow(
        () ->
            withStandaloneDatabase(
                bookAccess(bookPath),
                database -> {
                  SqliteBookSchemaBootstrap.initializeBook(database);
                  insertCanonicalInitializedBookMetadata(database);

                  SqliteNativeException missingUnit =
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
                                      '1400',
                                      'Inventory',
                                      'ASSET',
                                      'POSTABLE',
                                      null,
                                      'INVENTORY',
                                      'NON_CASH',
                                      null,
                                      null,
                                      null,
                                      1,
                                      '2026-04-07T10:15:30Z'
                                  )
                                  """));
                  assertEquals(
                      SqliteNativeResultCode.code("CONSTRAINT_CHECK"), missingUnit.resultCode());
                  assertEquals("SQLITE_CONSTRAINT_CHECK", missingUnit.resultName());

                  SqliteNativeException nonInventoryUnit =
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
                                      '1001',
                                      'Cash With Units',
                                      'ASSET',
                                      'POSTABLE',
                                      null,
                                      'CURRENT_ASSET',
                                      'CASH_AND_CASH_EQUIVALENT',
                                      null,
                                      'unit',
                                      0,
                                      1,
                                      '2026-04-07T10:15:30Z'
                                  )
                                  """));
                  assertEquals(
                      SqliteNativeResultCode.code("CONSTRAINT_CHECK"),
                      nonInventoryUnit.resultCode());
                  assertEquals("SQLITE_CONSTRAINT_CHECK", nonInventoryUnit.resultName());
                  assertEquals(0, queryInt(database, "select count(*) from account"));
                }));
  }

  @Test
  void canonicalStrictSchema_enforcesTradingCostingDoctrineOwnership() {
    Path bookPath = tempDirectory.resolve("book-identity-costing-doctrine.sqlite");
    assertDoesNotThrow(
        () ->
            withStandaloneDatabase(
                bookAccess(bookPath),
                database -> {
                  SqliteBookSchemaBootstrap.initializeBook(database);

                  SqliteNativeException tradingWithoutDoctrine =
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
                                      fiscal_year_start_day
                                  ) values (
                                      1,
                                      'Acme Trading',
                                      'internal-management-bookkeeping-kernel',
                                      'ACCRUAL',
                                      'NON_STATUTORY_INTERNAL_MANAGEMENT',
                                      'OWNER_MANAGED_SINGLE_ENTITY',
                                      'OWNER_MANAGED_TRADING',
                                      null,
                                      'EUR',
                                      1,
                                      1
                                  )
                                  """));
                  assertEquals(
                      SqliteNativeResultCode.code("CONSTRAINT_CHECK"),
                      tradingWithoutDoctrine.resultCode());
                  assertEquals("SQLITE_CONSTRAINT_CHECK", tradingWithoutDoctrine.resultName());

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
                          fiscal_year_start_day
                      ) values (
                          1,
                          'Acme Trading',
                          'internal-management-bookkeeping-kernel',
                          'ACCRUAL',
                          'NON_STATUTORY_INTERNAL_MANAGEMENT',
                          'OWNER_MANAGED_SINGLE_ENTITY',
                          'OWNER_MANAGED_TRADING',
                          'WEIGHTED_AVERAGE',
                          'EUR',
                          1,
                          1
                      )
                      """);
                  assertEquals(1, queryInt(database, "select count(*) from book_identity"));
                }));
  }
}
