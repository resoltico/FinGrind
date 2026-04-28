package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.Test;

/** Tests for {@link UnsupportedSqliteSourceIdException}. */
@NullUnmarked
class UnsupportedSqliteSourceIdExceptionTest {
  @Test
  void constructor_exposesStableValueSemantics() {
    UnsupportedSqliteSourceIdException exception =
        new UnsupportedSqliteSourceIdException(
            "loaded-source-id", "required-source-id", "managed-only", "3.53.0", "2.3.3");

    assertEquals("loaded-source-id", exception.loadedSourceId());
    assertEquals("required-source-id", exception.requiredSourceId());
    assertEquals("managed-only", exception.libraryMode());
    assertEquals("3.53.0", exception.loadedSqliteVersion());
    assertEquals("2.3.3", exception.loadedSqlite3mcVersion());
    assertTrue(exception.getMessage().contains("requires SQLite source id"));
  }
}
