package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.foreign.MemorySegment;
import org.junit.jupiter.api.Test;

/** Focused behavioral proofs for {@link SqliteStoreCloseSequence}. */
class SqliteStoreCloseSequenceTest {
  @Test
  void close_suppressesSessionSecretFailureOntoDatabaseFailure() {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                new SqliteStoreCloseSequence(
                        () -> {
                          throw new IllegalStateException("secret boom");
                        },
                        new SqliteNativeDatabase(MemorySegment.NULL) {
                          @Override
                          public void close() {
                            throw new IllegalStateException("database boom");
                          }
                        })
                    .close());

    assertEquals("database boom", exception.getMessage());
    assertEquals(1, exception.getSuppressed().length);
    assertEquals("secret boom", exception.getSuppressed()[0].getMessage());
  }

  @Test
  void close_propagatesSessionSecretFailureWhenDatabaseSucceeds() {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                new SqliteStoreCloseSequence(
                        () -> {
                          throw new IllegalStateException("secret boom");
                        },
                        null)
                    .close());

    assertEquals("secret boom", exception.getMessage());
  }

  @Test
  void close_rethrowsSessionSecretErrorFailures() {
    AssertionError error =
        assertThrows(
            AssertionError.class,
            () ->
                new SqliteStoreCloseSequence(
                        () -> {
                          throw new AssertionError("secret error");
                        },
                        null)
                    .close());

    assertEquals("secret error", error.getMessage());
  }
}
