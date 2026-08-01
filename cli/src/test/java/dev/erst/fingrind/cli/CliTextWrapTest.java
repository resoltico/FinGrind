package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Verifies that rendered operator instructions preserve opaque copy-paste tokens. */
class CliTextWrapTest {

  @Test
  void wrapLines_keepsAnAbsoluteLauncherPathAsOneToken() {
    String launcher =
        "/private/tmp/claude-501/-Users-erst-Tools-FinGrind/0d106da2-fa48-400b-bcfa-60b6ff3a75c6/bin/fingrind";

    List<String> wrapped = CliTextWrap.wrapLines("Operator guide " + launcher + " help", 72);

    assertEquals(List.of("Operator guide", launcher, "help"), wrapped);
    assertTrue(String.join("\n", wrapped).contains(launcher));
  }

  @Test
  void wrapLines_keepsRedactedPathHintsWithWhitespaceOnOneLine() {
    String pathHint = "<redacted>/Rīga büro/nested/-entity [bundle-compatibility-floor].sqlite";

    assertEquals(List.of(pathHint), CliTextWrap.wrapLines(pathHint, 63));
  }
}
