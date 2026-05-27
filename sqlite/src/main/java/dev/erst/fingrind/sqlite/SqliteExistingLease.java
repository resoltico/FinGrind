package dev.erst.fingrind.sqlite;

import java.nio.file.Path;
import java.util.Objects;

/** Pre-existing lease file discovered while another workflow owns the artifact path. */
record SqliteExistingLease(Path leasePath) implements SqliteLeaseCreation {
  SqliteExistingLease {
    Objects.requireNonNull(leasePath, "leasePath");
  }
}
