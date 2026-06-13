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
            "3.53.2", "2.3.5", "managed-only", List.of("SECURE_DELETE", "TEMP_STORE=3"));
    assertEquals("3.53.2", exception.loadedSqliteVersion());
    assertEquals("2.3.5", exception.loadedSqlite3mcVersion());
    assertEquals("managed-only", exception.libraryMode());
    assertEquals(List.of("SECURE_DELETE", "TEMP_STORE=3"), exception.missingCompileOptions());
    assertTrue(NullTestSupport.messageOf(exception).contains("missing required compile options"));
  }

  @Test
  void constructor_rejectsInvalidInputs() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new UnsupportedSqliteCompileOptionsException(
                " ", "2.3.5", "managed-only", List.of("X")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new UnsupportedSqliteCompileOptionsException(
                "3.53.2", " ", "managed-only", List.of("X")));
    assertThrows(
        IllegalArgumentException.class,
        () -> new UnsupportedSqliteCompileOptionsException("3.53.2", "2.3.5", " ", List.of("X")));
    assertEquals(
        "missingCompileOptions",
        assertThrows(
                NullPointerException.class,
                () ->
                    new UnsupportedSqliteCompileOptionsException(
                        "3.53.2", "2.3.5", "managed-only", NullTestSupport.nullOf(List.class)))
            .getMessage());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new UnsupportedSqliteCompileOptionsException(
                "3.53.2", "2.3.5", "managed-only", List.of()));
  }
}
