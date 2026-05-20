package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.Money;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link CliTextFormat}. */
class CliTextFormatTest {
  @Test
  void renderers_escapeCsvAndNormalizeDisplayAmounts() {
    assertTrue(
        CliTextFormat.renderKeyValueBlock(List.of(List.of("State", "initialized")))
            .contains("State : initialized"));
    assertTrue(
        CliTextFormat.renderTable(List.of("Account", "Amount"), List.of(), 1).contains("(none)"));
    assertTrue(
        CliTextFormat.renderTable(
                List.of("Account", "Amount"), List.of(List.of("1000", "10.00")), 1)
            .contains("1000"));
    assertThrows(
        IllegalArgumentException.class,
        () -> CliTextFormat.renderTable(List.of("A", "B"), List.of(List.of("only-one-cell"))));
    assertEquals(
        "name,value\n\"Cash, reserve\",\"Line 1\nLine \"\"2\"\"\"",
        CliTextFormat.renderCsv(
            List.of("name", "value"), List.of(List.of("Cash, reserve", "Line 1\nLine \"2\""))));
    assertEquals(
        "name,value\nsimple,plain",
        CliTextFormat.renderCsv(List.of("name", "value"), List.of(List.of("simple", "plain"))));
    assertEquals(
        "name,value\nquote-only,\"Said \"\"hello\"\"\"",
        CliTextFormat.renderCsv(
            List.of("name", "value"), List.of(List.of("quote-only", "Said \"hello\""))));
    assertEquals(
        "name,value\nnewline-only,\"Line 1\nLine 2\"",
        CliTextFormat.renderCsv(
            List.of("name", "value"), List.of(List.of("newline-only", "Line 1\nLine 2"))));
    assertEquals("1.20", CliTextFormat.displayMoney(Money.parse("EUR", "1.20")));
    assertEquals("1", CliTextFormat.displayMoney(Money.parse("JPY", "1")));
    assertEquals("1.250", CliTextFormat.displayMoney(Money.parse("BHD", "1.25")));
    assertEquals("1.000", CliTextFormat.displayMoney(Money.parse("KWD", "1.000")));
    assertEquals("alpha, beta", CliTextFormat.joined(List.of("alpha", "", "  ", "beta")));
  }

  @Test
  void wrapAndAdaptiveRenderers_coverCompactFallbackAndEdgeCases() {
    assertTrue(
        CliTextFormat.renderAdaptiveTable(12, List.of("Account", "Amount"), List.of())
            .contains("(none)"));
    assertEquals("", CliTextFormat.wrapLineBlock(List.of(), 12));
    assertEquals("", CliTextFormat.renderBulletedBlock(List.of(), 12));
    assertEquals("- alpha", CliTextFormat.renderBulletedBlock(List.of("alpha"), Integer.MAX_VALUE));
    assertEquals("alph\na", CliTextFormat.wrap("alpha   ", 4));
    assertEquals("alpha/\nbeta", CliTextFormat.wrap("alpha/beta", 6));
    assertEquals("alpha\nbeta", CliTextFormat.wrap("alpha beta", 7));
    assertEquals("super\nlongt\noken", CliTextFormat.wrap("superlongtoken", 5));
    assertEquals("x", CliTextFormat.wrap("x\n      ", 4));
    assertEquals("- alpha/\n  beta", CliTextFormat.renderBulletedBlock(List.of("alpha/beta"), 8));

    String compactRows =
        CliTextFormat.renderAdaptiveTable(
            12,
            List.of("Date", "Name", "Detail"),
            List.of(List.of("", "", "detail value"), List.of("2026-04-07", "Cash", "10.00")));
    assertTrue(compactRows.contains("Row 1"));
    assertTrue(compactRows.contains("Detail : detail value"));
    assertTrue(compactRows.contains("2026-04-07 | Cash"));

    String singleColumnCompact =
        CliTextFormat.renderAdaptiveTable(3, List.of("Only"), List.of(List.of("")));
    assertTrue(singleColumnCompact.contains("Row 1"));
    assertTrue(singleColumnCompact.contains("Only :"));
  }
}
