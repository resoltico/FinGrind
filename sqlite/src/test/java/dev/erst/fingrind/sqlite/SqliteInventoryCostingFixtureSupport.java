package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.InventoryMovementKind;
import dev.erst.fingrind.core.PostingOriginKind;
import java.time.LocalDate;

/** Shared initialized-book fixtures for durable inventory movement storage tests. */
class SqliteInventoryCostingFixtureSupport extends SqlitePostingFactStoreTestSupport {
  static void insertInventoryAccount(
      SqliteNativeDatabase database, String accountCode, String accountName) {
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
            '%s',
            '%s',
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
        """
            .formatted(accountCode, accountName));
  }

  static void insertPostingFactRow(
      SqliteNativeDatabase database, String postingId, String idempotencyKey) {
    insertPostingFactRow(
        database,
        postingId,
        idempotencyKey,
        "2026-04-07",
        "2026-04-07T10:15:30Z",
        "STANDARD",
        PostingOriginKind.PURCHASE_SETTLED);
  }

  static void insertPostingFactRow(
      SqliteNativeDatabase database,
      String postingId,
      String idempotencyKey,
      String effectiveDate,
      String recordedAt) {
    insertPostingFactRow(
        database,
        postingId,
        idempotencyKey,
        effectiveDate,
        recordedAt,
        "STANDARD",
        PostingOriginKind.PURCHASE_SETTLED);
  }

  static void insertPostingFactRow(
      SqliteNativeDatabase database,
      String postingId,
      String idempotencyKey,
      String effectiveDate,
      String recordedAt,
      String postingKind,
      PostingOriginKind postingOriginKind) {
    String canonicalPostingId = TestPostingIds.valueForLabel(postingId);
    PostingFactEntryFields entryFields = entryFields(postingOriginKind);
    String accountCode =
        entryFields.accountPair()
            ? "(select account_code from account order by account_code limit 1)"
            : "null";
    String amountCurrencyCode = entryFields.monetaryAmount() ? "'EUR'" : "null";
    String amountMinor = entryFields.monetaryAmount() ? "100" : "null";
    String quantity = entryFields.quantity() ? "'1'" : "null";
    String unitCostCurrencyCode = entryFields.unitCost() ? "'EUR'" : "null";
    String unitCostMinor = entryFields.unitCost() ? "100" : "null";
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
            '%s',
            '%s',
            %s,
            %s,
            null,
            %s,
            %s,
            null,
            %s,
            %s,
            %s,
            '%s',
            '%s',
            '019e26ff-0000-7002-8000-000000000001',
            '%s',
            'cause-1',
            null,
            null,
            'CLI',
            null,
            1,
            '%s'
        )
        """
            .formatted(
                canonicalPostingId,
                postingKind,
                postingOriginKind.wireValue(),
                accountCode,
                accountCode,
                amountCurrencyCode,
                amountMinor,
                quantity,
                unitCostCurrencyCode,
                unitCostMinor,
                effectiveDate,
                recordedAt,
                idempotencyKey,
                "0".repeat(64)));
  }

  static int insertTypedInventoryMovement(
      SqliteNativeDatabase database,
      String movementId,
      String accountCode,
      LocalDate effectiveDate,
      InventoryMovementKind movementKind,
      long quantityDelta,
      long costDeltaMinor,
      String postingId) {
    return SqliteInventoryCostingWriter.insertInventoryMovement(
        database,
        movementId,
        new AccountCode(accountCode),
        effectiveDate,
        movementKind,
        quantityDelta,
        costDeltaMinor,
        TestPostingIds.fromLabel(postingId));
  }

  private static PostingFactEntryFields entryFields(PostingOriginKind postingOriginKind) {
    return switch (postingOriginKind) {
      case SALE_SETTLED,
          SALE_ON_CREDIT,
          INVENTORY_CAPITALIZATION_SETTLED,
          INVENTORY_CAPITALIZATION_ON_CREDIT,
          INVENTORY_WRITE_DOWN ->
          new PostingFactEntryFields(true, true, false, false);
      case PURCHASE_SETTLED, PURCHASE_ON_CREDIT, INVENTORY_COUNT_INCREASE ->
          new PostingFactEntryFields(true, false, true, true);
      case INVENTORY_SHRINKAGE -> new PostingFactEntryFields(true, false, true, false);
      default -> new PostingFactEntryFields(false, false, false, false);
    };
  }

  private record PostingFactEntryFields(
      boolean accountPair, boolean monetaryAmount, boolean quantity, boolean unitCost) {}
}
