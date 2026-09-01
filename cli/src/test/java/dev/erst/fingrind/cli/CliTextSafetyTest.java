package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Guards terminal-safe output escaping and display-cell alignment. */
class CliTextSafetyTest {
  @Test
  void tableEscapesTerminalControlsAndAlignsWideUnicodeCells() {
    String rendered =
        CliTextFormat.renderTable(
            List.of("Account", "Name"),
            List.of(List.of("cash", "Cash"), List.of("wide", "東京支店売上")));

    assertTrue(rendered.contains("東京支店売上"));
    assertEquals(
        rendered.lines().findFirst().orElseThrow().indexOf(" | "),
        rendered.lines().skip(3).findFirst().orElseThrow().indexOf(" | "));

    String controls =
        CliTextFormat.renderKeyValueBlock(List.of(List.of("Message", "bad\n\u001b[2J")));
    assertTrue(controls.contains("\\u000A\\u001B[2J"));
    assertFalse(controls.contains("\u001b"));
  }

  @Test
  void terminalWidthCoversCombiningAndEveryWideUnicodeRange() {
    assertEquals(1, CliTerminalWidth.cells("e\u0301"));
    assertEquals(0, CliTerminalWidth.cells("\u093E"));
    assertEquals(0, CliTerminalWidth.cells("\u20DD"));
    assertEquals(2, CliTerminalWidth.cells("ᄀ"));
    assertEquals(2, CliTerminalWidth.cells("東"));
    assertEquals(2, CliTerminalWidth.cells("가"));
    assertEquals(2, CliTerminalWidth.cells("豈"));
    assertEquals(2, CliTerminalWidth.cells("︐"));
    assertEquals(2, CliTerminalWidth.cells("Ａ"));
    assertEquals(2, CliTerminalWidth.cells("￠"));
    assertEquals(2, CliTerminalWidth.cells(new String(Character.toChars(0x1F600))));
    assertEquals(2, CliTerminalWidth.cells(new String(Character.toChars(0x20000))));
    assertEquals(
        1, CliTerminalWidth.cells(new String(Character.toChars(Character.MAX_CODE_POINT))));
    assertEquals(1, CliTerminalWidth.cells("A"));
  }

  @Test
  void visibleEscapesEveryTerminalControlAndDirectionalRange() {
    assertEquals("\\u007F", CliTextSafety.visible("\u007F"));
    assertEquals("\\u202A", CliTextSafety.visible("\u202A"));
    assertEquals("\\u2066", CliTextSafety.visible("\u2066"));
    assertEquals("plain text", CliTextSafety.visible("plain text"));
  }
}
