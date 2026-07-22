package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.existingPosting;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.core.attestation.AttestationVerification;
import dev.erst.fingrind.executor.spi.PostingCommitResult;
import java.math.BigInteger;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Covers the attestation result invariant on ordinary durable posting commits. */
class PostingCommitResultTest {
  @Test
  void rejectsAnIdempotentReplayThatClaimsANewAttestationOperation() {
    AttestationVerification verification =
        new AttestationVerification(UUID.randomUUID(), BigInteger.ONE, new byte[32], List.of());

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PostingCommitResult.Committed(
                existingPosting("posting-1", "idem-1"), true, verification));
  }
}
