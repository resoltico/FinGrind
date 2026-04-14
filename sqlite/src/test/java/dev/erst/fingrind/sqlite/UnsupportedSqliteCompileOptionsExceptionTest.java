package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests for {@link UnsupportedSqliteCompileOptionsException}. */
class UnsupportedSqliteCompileOptionsExceptionTest {
  @Test
  void constructor_exposesStableValueSemantics() {
    UnsupportedSqliteCompileOptionsException exception =
        new UnsupportedSqliteCompileOptionsException(
            "3.53.0", "2.3.3", "managed", List.of("SECURE_DELETE", "TEMP_STORE=3"));

    assertEquals("3.53.0", exception.loadedSqliteVersion());
    assertEquals("2.3.3", exception.loadedSqlite3mcVersion());
    assertEquals("managed", exception.librarySource());
    assertEquals(List.of("SECURE_DELETE", "TEMP_STORE=3"), exception.missingCompileOptions());
    assertTrue(exception.getMessage().contains("missing required compile options"));
  }

  @Test
  void constructor_rejectsInvalidInputs() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new UnsupportedSqliteCompileOptionsException(" ", "2.3.3", "managed", List.of("X")));
    assertThrows(
        IllegalArgumentException.class,
        () -> new UnsupportedSqliteCompileOptionsException("3.53.0", " ", "managed", List.of("X")));
    assertThrows(
        IllegalArgumentException.class,
        () -> new UnsupportedSqliteCompileOptionsException("3.53.0", "2.3.3", " ", List.of("X")));
    assertThrows(
        NullPointerException.class,
        () -> new UnsupportedSqliteCompileOptionsException("3.53.0", "2.3.3", "managed", null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new UnsupportedSqliteCompileOptionsException("3.53.0", "2.3.3", "managed", List.of()));
  }
}
