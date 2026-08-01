package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests immutable publication-capability witness acquisition plans. */
class SqlitePublicationCapabilityWitnessPlanTest {
  @Test
  void entryRejectsAnEmptyRequirementSet() {
    SqlitePublicationCapabilityWitnessKey key =
        new SqlitePublicationCapabilityWitnessKey(
            Path.of("witness-parent"),
            "test-parent-fingerprint",
            SqlitePublicationCapabilityWitness.PrimitiveKind.NO_REPLACE_LINK);

    IllegalArgumentException failure =
        assertThrows(
            IllegalArgumentException.class,
            () -> new SqlitePublicationCapabilityWitnessPlan.Entry(key, List.of()));

    assertEquals("A publication-capability witness plan entry is empty.", failure.getMessage());
  }
}
