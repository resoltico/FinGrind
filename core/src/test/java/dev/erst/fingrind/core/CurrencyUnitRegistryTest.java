package dev.erst.fingrind.core;

import static dev.erst.fingrind.core.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link CurrencyUnitRegistry}. */
class CurrencyUnitRegistryTest {
  @Test
  void loadScaleByCode_reloadsThePinnedRegistryResource() {
    assertEquals(CurrencyUnitRegistry.snapshot(), CurrencyUnitRegistry.loadScaleByCode());
  }

  @Test
  void loadScaleByCode_rejectsMissingRegistryResources() {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> CurrencyUnitRegistry.loadScaleByCode(nullOf(InputStream.class), "missing.csv"));

    assertEquals(
        "Missing FinGrind currency-unit registry resource: missing.csv.", exception.getMessage());
  }

  @Test
  void loadScaleByCode_wrapsInputFailures() {
    UncheckedIOException exception =
        assertThrows(
            UncheckedIOException.class,
            () -> CurrencyUnitRegistry.loadScaleByCode(failingStream(), "broken.csv"));

    assertEquals(
        "Failed to load FinGrind currency-unit registry resource.", exception.getMessage());
    IOException cause = assertInstanceOf(IOException.class, exception.getCause());
    assertEquals("boom", cause.getMessage());
  }

  @Test
  void parseScaleByCode_rejectsEmptyBlankMalformedDuplicateAndOutOfRangeRegistryRecords()
      throws IOException {
    assertEquals(
        "reader",
        assertThrows(
                NullPointerException.class,
                () -> CurrencyUnitRegistry.parseScaleByCode(nullOf(BufferedReader.class)))
            .getMessage());
    assertEquals(
        "Currency-unit registry must not be empty.",
        assertThrows(
                IllegalStateException.class,
                () -> CurrencyUnitRegistry.parseScaleByCode(bufferedReader("")))
            .getMessage());
    assertEquals(
        "Currency-unit registry line 1 must not be blank.",
        assertThrows(
                IllegalStateException.class,
                () -> CurrencyUnitRegistry.parseScaleByCode(bufferedReader("\nEUR,2\n")))
            .getMessage());
    assertEquals(
        "Currency-unit registry line 1 must use one CODE,SCALE record format.",
        assertThrows(
                IllegalStateException.class,
                () -> CurrencyUnitRegistry.parseScaleByCode(bufferedReader("EUR:2\n")))
            .getMessage());
    assertEquals(
        "Currency-unit registry line 1 must use one CODE,SCALE record format.",
        assertThrows(
                IllegalStateException.class,
                () -> CurrencyUnitRegistry.parseScaleByCode(bufferedReader("EUR,2,0\n")))
            .getMessage());
    assertEquals(
        "Currency-unit registry line 1 has unsupported code text: Eur.",
        assertThrows(
                IllegalStateException.class,
                () -> CurrencyUnitRegistry.parseScaleByCode(bufferedReader("Eur,2\n")))
            .getMessage());
    assertEquals(
        "Currency-unit registry must remain strictly sorted without duplicates: EUR.",
        assertThrows(
                IllegalStateException.class,
                () -> CurrencyUnitRegistry.parseScaleByCode(bufferedReader("EUR,2\nEUR,2\n")))
            .getMessage());
    IllegalStateException invalidScaleException =
        assertThrows(
            IllegalStateException.class,
            () -> CurrencyUnitRegistry.parseScaleByCode(bufferedReader("EUR,two\n")));
    String invalidScaleMessage = assertInstanceOf(String.class, invalidScaleException.getMessage());
    assertTrue(
        invalidScaleMessage.contains(
            "Currency-unit registry line 1 has an invalid scale for EUR."));
    assertEquals(
        "Currency-unit registry line 1 publishes unsupported scale 10 for EUR.",
        assertThrows(
                IllegalStateException.class,
                () -> CurrencyUnitRegistry.parseScaleByCode(bufferedReader("EUR,10\n")))
            .getMessage());
    assertEquals(
        "Currency-unit registry line 1 publishes unsupported scale -1 for EUR.",
        assertThrows(
                IllegalStateException.class,
                () -> CurrencyUnitRegistry.parseScaleByCode(bufferedReader("EUR,-1\n")))
            .getMessage());
  }

  @Test
  void parseScaleByCode_returnsOneStableSortedSnapshot() throws IOException {
    Map<String, Integer> parsed =
        CurrencyUnitRegistry.parseScaleByCode(bufferedReader("EUR,2\nJPY,0\nUSD,2\n"));

    assertEquals(Map.of("EUR", 2, "JPY", 0, "USD", 2), parsed);
  }

  private static BufferedReader bufferedReader(String text) {
    return new BufferedReader(new StringReader(text));
  }

  private static InputStream failingStream() {
    return new InputStream() {
      @Override
      public int read() throws IOException {
        throw new IOException("boom");
      }

      @Override
      public int read(byte[] buffer, int offset, int length) throws IOException {
        throw new IOException("boom");
      }
    };
  }
}
