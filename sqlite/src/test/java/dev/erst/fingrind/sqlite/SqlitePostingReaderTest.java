package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.core.CurrencyUnit;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link SqlitePostingBalanceReader}. */
class SqlitePostingReaderTest {
  @Test
  void orderedCurrencyCodes_sortsCurrencyBucketsByWireCode() {
    assertEquals(
        List.of(CurrencyUnit.of("EUR"), CurrencyUnit.of("JPY"), CurrencyUnit.of("USD")),
        SqlitePostingBalanceReader.orderedCurrencyCodes(
            List.of(CurrencyUnit.of("USD"), CurrencyUnit.of("EUR"), CurrencyUnit.of("JPY"))));
  }
}
