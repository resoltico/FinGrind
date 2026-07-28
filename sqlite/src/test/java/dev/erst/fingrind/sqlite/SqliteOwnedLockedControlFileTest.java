package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Verifies untransferred coordination locks release their opaque resource exactly once. */
class SqliteOwnedLockedControlFileTest {

  @Test
  void releaseClosesAnOwnedControlExactlyOnceAndAcceptsNoLock() throws Exception {
    AtomicInteger closes = new AtomicInteger();
    var control =
        SqliteCoordinationControlFiles.lockedControlFile(
            Path.of("owned-lock.control"), closes::incrementAndGet);
    SqliteOwnedLockedControlFile owned =
        Objects.requireNonNull(SqliteOwnedLockedControlFile.acquire(control), "owned control");

    owned.release();
    owned.release();

    assertEquals(1, closes.get());
    assertNull(SqliteOwnedLockedControlFile.acquire(null));
  }
}
