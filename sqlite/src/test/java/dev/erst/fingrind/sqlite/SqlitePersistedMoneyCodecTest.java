package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.Money;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Unit tests for the canonical persisted SQLite money codec. */
class SqlitePersistedMoneyCodecTest extends SqlitePostingFactStoreTestSupport {
  @Test
  void readMoney_decodesExactAmountsAcrossSupportedCurrencyScaleBuckets() {
    Path bookPath = tempDirectory.resolve("supported-scale-buckets.sqlite");
    withStandaloneDatabase(
        bookAccess(bookPath),
        database -> {
          assertReadMoney(database, "JPY", 100L, Money.parse("JPY", "100"));
          assertReadMoney(database, "EUR", 1_250L, Money.parse("EUR", "12.50"));
          assertReadMoney(database, "BHD", 1_250L, Money.parse("BHD", "1.250"));
        });
  }

  @Test
  void readMoney_rejectsNegativeMinorUnits() {
    Path bookPath = tempDirectory.resolve("negative-minor-units.sqlite");
    withStandaloneDatabase(
        bookAccess(bookPath),
        database -> {
          try (SqliteNativeStatement statement =
              SqliteNativeStatements.prepare(database, "select 'EUR', -1")) {
            assertEquals(SqliteNativeResultCodes.ROW, statement.step());
            IllegalStateException exception =
                assertThrows(
                    IllegalStateException.class,
                    () -> SqlitePersistedMoneyCodec.readMoney(statement, 0, 1));
            assertEquals(
                "Persisted SQLite money minor units must not be negative.", exception.getMessage());
          }
        });
  }

  @Test
  void readCurrencyUnit_usesThePinnedRegistryAsTheOnlyScaleAuthority() {
    assertEquals(CurrencyUnit.of("EUR"), SqlitePersistedMoneyCodec.readCurrencyUnit("EUR"));
  }

  private static void assertReadMoney(
      SqliteNativeDatabase database, String currencyCode, long amountMinor, Money expected) {
    try (SqliteNativeStatement statement =
        SqliteNativeStatements.prepare(
            database, "select '%s', %d".formatted(currencyCode, Long.valueOf(amountMinor)))) {
      assertEquals(SqliteNativeResultCodes.ROW, statement.step());
      assertEquals(expected, SqlitePersistedMoneyCodec.readMoney(statement, 0, 1));
    }
  }
}
