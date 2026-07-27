package dev.erst.fingrind.executor.maintenance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.core.attestation.AttestationAppendOutcome;
import dev.erst.fingrind.core.attestation.AttestationOperationKind;
import dev.erst.fingrind.core.attestation.AttestationVerification;
import java.math.BigInteger;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Covers the lifecycle boundary that refuses to misrepresent an idempotent replay as an append. */
class AttestedProtectedBookAppendOutcomesTest {
  @Test
  void requireNewAppend_preservesANewAppendAndRejectsAnAlreadyPresentOperation() {
    AttestationAppendOutcome.Appended appended =
        new AttestationAppendOutcome.Appended(
            new AttestationVerification(
                UUID.fromString("018f0000-0000-7000-8000-000000000001"),
                BigInteger.ONE,
                new byte[32],
                new byte[32],
                List.of()));

    assertSame(
        appended,
        AttestedProtectedBookAppendOutcomes.requireNewAppend(
            appended, AttestationOperationKind.BACKUP_CREATED));

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                AttestedProtectedBookAppendOutcomes.requireNewAppend(
                    AttestationAppendOutcome.AlreadyPresent.INSTANCE,
                    AttestationOperationKind.BACKUP_CREATED));

    assertEquals(
        "backup-created cannot reuse an existing attestation operation.", exception.getMessage());
  }
}
