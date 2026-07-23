package dev.erst.fingrind.contract.bookkeeping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Covers each published outcome of an attestation registry mutation. */
class AttestationRegistryMutationResultTest {
  @Test
  void mutated_normalizesItsBookPathAndPreservesItsAuthenticatedHead() {
    AttestationRegistryMutationResult.Mutated result =
        new AttestationRegistryMutationResult.Mutated(
            Path.of("build", "book.fgdb"),
            "enroll-key",
            new AttestationCommit(BigInteger.ONE, "0".repeat(64)));

    assertEquals(Path.of("build", "book.fgdb").toAbsolutePath().normalize(), result.bookFilePath());
    assertEquals("enroll-key", result.operationKind());
    assertEquals(BigInteger.ONE, result.attestationCommit().operationOrder());
    assertEquals("0".repeat(64), result.attestationCommit().operationHeadHex());
    assertInstanceOf(AttestationRegistryMutationResult.class, result);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationRegistryMutationResult.Mutated(
                Path.of("build", "book.fgdb"),
                "enroll-key",
                new AttestationCommit(BigInteger.ONE, "not-a-head")));
  }

  @Test
  void rejected_preservesTheDeterministicMaintenanceRefusal() {
    BookMaintenanceRejection rejection =
        new BookMaintenanceRejection.BookDestinationOccupied(Path.of("build", "book.fgdb"));

    AttestationRegistryMutationResult.Rejected result =
        new AttestationRegistryMutationResult.Rejected(rejection);

    assertEquals(rejection, result.rejection());
    assertInstanceOf(AttestationRegistryMutationResult.class, result);
  }

  @Test
  void authorizationRejected_preservesTheHistoricalAuthorizationFailure() {
    AttestationRegistryMutationResult.AuthorizationRejected result =
        new AttestationRegistryMutationResult.AuthorizationRejected(
            AttestationVerificationFailure.KEY_NOT_ENROLLED);

    assertEquals(AttestationVerificationFailure.KEY_NOT_ENROLLED, result.failure());
    assertInstanceOf(AttestationRegistryMutationResult.class, result);
  }
}
