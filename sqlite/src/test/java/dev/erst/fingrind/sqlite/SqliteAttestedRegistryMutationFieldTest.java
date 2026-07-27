package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.core.attestation.AttestationAdmissionRejectedException;
import dev.erst.fingrind.core.attestation.AttestationAuthorizationFailure;
import dev.erst.fingrind.core.attestation.AttestationCapability;
import dev.erst.fingrind.core.attestation.AttestationCredentialPurpose;
import dev.erst.fingrind.core.attestation.AttestationKeyFiles;
import dev.erst.fingrind.core.attestation.AttestationPublicCredential;
import dev.erst.fingrind.core.attestation.AttestationRegistryMutation;
import dev.erst.fingrind.core.attestation.AttestationVerification;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import java.math.BigInteger;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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

  @Test
  void classifiesProspectiveRegistryTargetRefusalAsDirectLiveAdmissionRejection() throws Exception {
    Path bookPath = tempDirectory.resolve("attested-registry-refusal.sqlite");
    BookAccess bookAccess = bookAccess(bookPath);
    initializeBook(bookAccess);
    Path enrolledKeyPath = tempDirectory.resolve("duplicate-principal.fgatk");
    char[] passphrase = "registry duplicate principal".toCharArray();
    AttestationPublicCredential credential;
    try {
      credential = AttestationKeyFiles.create(enrolledKeyPath, passphrase).credential();
    } finally {
      java.util.Arrays.fill(passphrase, '\0');
    }
    AttestationRegistryMutation mutation =
        new AttestationRegistryMutation.EnrollKey(
            UUID.fromString("10213243-5465-7687-98a9-babcbddceeff"),
            credential,
            AttestationCredentialPurpose.SYSTEM);
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();

    try (ProtectedBookMaintenanceStore.VerifiedBook verifiedBook =
        verifiedBook(store, bookAccess)) {
      AttestationAdmissionRejectedException rejected =
          assertThrows(
              AttestationAdmissionRejectedException.class,
              () ->
                  store.appendAttestedRegistryMutation(
                      verifiedBook,
                      mutation,
                      Instant.parse("2026-07-21T12:00:00Z"),
                      SqliteAttestationTestSupport.authorizer()));

      assertEquals(AttestationAuthorizationFailure.DUPLICATE_PRINCIPAL, rejected.failure());
    }
  }
}
