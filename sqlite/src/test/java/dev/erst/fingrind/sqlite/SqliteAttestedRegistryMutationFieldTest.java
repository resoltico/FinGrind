package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.core.attestation.AttestationCapability;
import dev.erst.fingrind.core.attestation.AttestationRegistryMutation;
import dev.erst.fingrind.core.attestation.AttestationVerification;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import java.math.BigInteger;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Exercises a registry mutation through the live SQLite maintenance-store boundary. */
class SqliteAttestedRegistryMutationFieldTest extends SqliteArtifactPublicationTestSupport {
  @Test
  void appendsAnAdmittedRegistryMutationInTheSameImmediateWriteTransaction() {
    Path bookPath = tempDirectory.resolve("attested-registry.sqlite");
    BookAccess bookAccess = bookAccess(bookPath);
    initializeBook(bookAccess);
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();
    AttestationRegistryMutation mutation =
        new AttestationRegistryMutation.AlterPolicy(
            List.of(new AttestationRegistryMutation.PolicyRule(AttestationCapability.POST, 1)),
            List.of(),
            List.of());

    try (ProtectedBookMaintenanceStore.VerifiedBook verifiedBook =
        verifiedBook(store, bookAccess)) {
      AttestationVerification verification =
          store.appendAttestedRegistryMutation(
              verifiedBook,
              mutation,
              Instant.parse("2026-07-21T12:00:00Z"),
              SqliteAttestationTestSupport.authorizer());

      assertEquals(BigInteger.ONE, verification.headOrder());
      assertEquals(2, store.loadAttestationEvidence(verifiedBook).size());
    }
  }
}
