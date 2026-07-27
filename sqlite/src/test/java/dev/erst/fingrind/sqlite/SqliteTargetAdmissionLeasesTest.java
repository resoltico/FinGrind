package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Proves exact target-admission references transfer once or close in reverse acquisition order. */
class SqliteTargetAdmissionLeasesTest {
  @TempDir Path tempDirectory;

  @Test
  void transferMovesBothReferencesToPreparationResourcesWithoutPrematureRelease() {
    AtomicInteger bookCloses = new AtomicInteger();
    AtomicInteger secretCloses = new AtomicInteger();
    SqliteTargetAdmissionLeases leases = leases(bookCloses, secretCloses);

    try (SqlitePairPublicationPreparationResources resources =
        new SqlitePairPublicationPreparationResources()) {
      leases.transferTo(resources);
      leases.close();
      assertEquals(0, bookCloses.get());
      assertEquals(0, secretCloses.get());
    }

    assertEquals(1, bookCloses.get());
    assertEquals(1, secretCloses.get());
  }

  @Test
  void closedTargetAdmissionLeasesCannotTransferAgain() {
    AtomicInteger bookCloses = new AtomicInteger();
    AtomicInteger secretCloses = new AtomicInteger();
    SqliteTargetAdmissionLeases leases = leases(bookCloses, secretCloses);
    leases.close();

    try (SqlitePairPublicationPreparationResources resources =
        new SqlitePairPublicationPreparationResources()) {
      assertThrows(IllegalStateException.class, () -> leases.transferTo(resources));
    }

    assertEquals(1, bookCloses.get());
    assertEquals(1, secretCloses.get());
  }

  private SqliteTargetAdmissionLeases leases(AtomicInteger bookCloses, AtomicInteger secretCloses) {
    return new SqliteTargetAdmissionLeases(
        new SqliteHeldLease(tempDirectory.resolve("book.sqlite"), bookCloses::incrementAndGet),
        new SqliteHeldLease(tempDirectory.resolve("book.key"), secretCloses::incrementAndGet));
  }
}
