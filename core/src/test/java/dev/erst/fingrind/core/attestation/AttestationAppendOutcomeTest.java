package dev.erst.fingrind.core.attestation;

import static dev.erst.fingrind.core.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Verifies the exhaustive direct-append outcome boundary. */
class AttestationAppendOutcomeTest {
  @Test
  void distinguishesANewVerifiedAppendFromAnIdempotentReplay() {
    AttestationVerification verification =
        new AttestationVerification(
            UUID.fromString("00112233-4455-6677-8899-aabbccddeeff"),
            BigInteger.ONE,
            new byte[AttestationHash.BYTE_LENGTH],
            new byte[AttestationHash.BYTE_LENGTH],
            List.of());
    AttestationAppendOutcome.Appended appended =
        new AttestationAppendOutcome.Appended(verification);

    assertSame(appended, appended.requireAppended());
    assertSame(verification, appended.requireVerifiedAppend());
    assertThrows(NullPointerException.class, () -> new AttestationAppendOutcome.Appended(nullOf()));
    assertEquals(
        "An already-present attestation operation has no newly appended verification.",
        assertThrows(
                IllegalStateException.class,
                AttestationAppendOutcome.AlreadyPresent.INSTANCE::requireAppended)
            .getMessage());
  }
}
