package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Tests for {@link UnsupportedSqliteSourceIdException}. */
class UnsupportedSqliteSourceIdExceptionTest {
  @Test
  void constructor_exposesStableValueSemantics() {
    UnsupportedSqliteSourceIdException exception =
        new UnsupportedSqliteSourceIdException(
            "loaded-source-id", "required-source-id", "managed-only", "3.53.3", "2.3.6");
    assertEquals("loaded-source-id", exception.loadedSourceId());
    assertEquals("required-source-id", exception.requiredSourceId());
    assertEquals("managed-only", exception.libraryMode());
    assertEquals("3.53.3", exception.loadedSqliteVersion());
    assertEquals("2.3.6", exception.loadedSqlite3mcVersion());
    assertTrue(NullTestSupport.messageOf(exception).contains("requires SQLite source id"));
  }
}
