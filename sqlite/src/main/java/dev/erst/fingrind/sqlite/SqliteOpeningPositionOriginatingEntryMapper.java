package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.QuantityText;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.Quantity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Rebuilds opening positions and their persisted inventory quantities. */
final class SqliteOpeningPositionOriginatingEntryMapper {
  private SqliteOpeningPositionOriginatingEntryMapper() {}

  static BookkeepingEntry.OpeningPosition originatingEntry(
      SqliteNativeDatabase activeDatabase,
      SqliteNativeStatement postingRow,
      JournalEntry journalEntry) {
    Map<AccountCode, QuantityText> quantities =
        openingInventoryQuantities(activeDatabase, postingRow);
    return new BookkeepingEntry.OpeningPosition(
        journalEntry.effectiveDate(),
        journalEntry.lines().stream()
            .map(
                line ->
                    new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                        line.accountCode(),
                        line.side(),
                        MonetaryAmount.of(line.amount().money()),
                        quantities.get(line.accountCode())))
            .toList());
  }

  private static Map<AccountCode, QuantityText> openingInventoryQuantities(
      SqliteNativeDatabase activeDatabase, SqliteNativeStatement postingRow) {
    String postingId =
        SqlitePostingMapper.requiredText(postingRow, SqlitePostingColumnIndexes.COL_POSTING_ID);
    List<OpeningInventoryQuantity> quantities = new ArrayList<>();
    try (SqliteNativeStatement statement =
        activeDatabase.prepare(
            SqliteInventoryCostingSql.LOAD_OPENING_INVENTORY_QUANTITIES_BY_POSTING_ID)) {
      statement.bindText(1, postingId);
      while (statement.step() == SqliteNativeResultCode.code("ROW")) {
        quantities.add(readOpeningInventoryQuantity(statement));
      }
    } catch (SqliteNativeException exception) {
      throw SqliteStoreOperations.sqliteFailure(
          "Failed to read persisted opening inventory quantities.", exception);
    }
    return quantities.stream()
        .collect(
            Collectors.toUnmodifiableMap(
                OpeningInventoryQuantity::accountCode, OpeningInventoryQuantity::quantity));
  }

  private static OpeningInventoryQuantity readOpeningInventoryQuantity(
      SqliteNativeStatement statement) {
    AccountCode accountCode = new AccountCode(SqlitePostingMapper.requiredText(statement, 0));
    long scaledUnits = statement.columnLong(1);
    int quantityScale = statement.columnInt(2);
    return new OpeningInventoryQuantity(
        accountCode,
        new QuantityText(Quantity.ofScaledUnits(quantityScale, scaledUnits).canonicalDecimal()));
  }

  private record OpeningInventoryQuantity(AccountCode accountCode, QuantityText quantity) {}
}
