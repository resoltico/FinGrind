package dev.erst.fingrind.core.attestation;

import static dev.erst.fingrind.core.NullTestSupport.nullOf;
import static dev.erst.fingrind.core.attestation.AttestationMutationProjectionFixtures.EFFECTIVE_DATE;
import static dev.erst.fingrind.core.attestation.AttestationMutationProjectionFixtures.RECORDED_AT;
import static dev.erst.fingrind.core.attestation.AttestationMutationProjectionFixtures.decode;
import static dev.erst.fingrind.core.attestation.AttestationMutationProjectionFixtures.document;
import static dev.erst.fingrind.core.attestation.AttestationMutationProjectionFixtures.line;
import static dev.erst.fingrind.core.attestation.AttestationMutationProjectionFixtures.postingEffect;
import static dev.erst.fingrind.core.attestation.AttestationMutationProjectionFixtures.postingRequest;
import static dev.erst.fingrind.core.attestation.AttestationMutationProjectionFixtures.tags;
import static dev.erst.fingrind.core.attestation.AttestationMutationProjectionFixtures.token;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Verifies direct posting snapshots and their immutable preimage projection. */
class AttestationPostingMutationProjectionTest {
  @Test
  void postingProjection_commitsEveryRequestAndEffectFact() {
    AttestationPostingRequestSnapshot request = postingRequest();
    AttestationPostingEffectSnapshot effect = postingEffect("record-reversal");

    AttestationOperationPreimages projected =
        AttestationPostingMutationProjection.project(request, effect);
    AttestationPreimage requestPreimage = decode(projected.request());
    AttestationPreimage effectPreimage = decode(projected.effect());

    assertEquals(List.of(0x0100, 0x0120, 0x0124, 0x0124, 0x012A, 0x012A), tags(requestPreimage));
    assertEquals(List.of(0x0020, 0x0021, 0x0021, 0x0025, 0x0025), tags(effectPreimage));
    assertEquals("record-reversal", token(requestPreimage, 0x0100, 0));
    assertEquals("record-reversal", token(effectPreimage, 0x0020, 3));
    assertEquals("CLI", request.sourceChannel());
    assertNotEquals(List.of(), requestPreimage.records());

    assertThrows(
        IllegalArgumentException.class,
        () -> AttestationPostingMutationProjection.project(request, postingEffect("post-entry")));
  }

  @Test
  void postingSnapshotValues_rejectIncompleteOrInvalidCallerFacts() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationPostingRequestSnapshot(
                "post-entry",
                "idempotency",
                "cause",
                "cli",
                EFFECTIVE_DATE,
                "post-entry",
                null,
                "requires-prior",
                List.of(document()),
                List.of(line("1000", "DEBIT", 100))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationPostingRequestSnapshot(
                "post-entry",
                "idempotency",
                "cause",
                "cli",
                EFFECTIVE_DATE,
                "post-entry",
                "b2431ea7-bb0d-4677-bd1e-04cb7fcfd12f",
                null,
                List.of(document()),
                List.of(line("1000", "DEBIT", 100))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationPostingRequestSnapshot(
                "post-entry",
                "idempotency",
                "cause",
                "cli",
                EFFECTIVE_DATE,
                "post-entry",
                null,
                null,
                List.of(),
                List.of(line("1000", "DEBIT", 100))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationPostingRequestSnapshot(
                "post-entry",
                "idempotency",
                "cause",
                "cli",
                EFFECTIVE_DATE,
                "post-entry",
                null,
                null,
                List.of(document()),
                List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new AttestationPostingLine("1000", "DEBIT", "EUR", 0));
    assertThrows(
        NullPointerException.class,
        () -> new AttestationPostingEvidenceDocument("document", "invoice", nullOf()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationPostingEffectSnapshot(
                UUID.randomUUID(),
                " ",
                "post-entry",
                "post-entry",
                RECORDED_AT,
                null,
                UUID.randomUUID()));
  }

  @Test
  void timeBearingSnapshots_canonicalizeLiveClockPrecisionBeforePreimageEncoding() {
    Instant liveClockInstant = Instant.parse("2026-07-20T12:30:45.123456789Z");
    AttestationPostingEffectSnapshot posting =
        new AttestationPostingEffectSnapshot(
            UUID.fromString("42617efc-7425-4b42-b990-4b4eca2843ce"),
            "record-reversal",
            "RECORD_REVERSAL",
            "RECORD_REVERSAL",
            liveClockInstant,
            UUID.fromString("d3d93f87-d85b-457c-b3e3-75b0f7bb6b9f"),
            UUID.fromString("927bf5f6-d6a0-4084-9365-7a7e375fc72b"));
    AttestationClosePostingSnapshot close =
        new AttestationClosePostingSnapshot(
            UUID.fromString("0d566aee-13cc-429c-af01-b11dfd4687fe"),
            UUID.fromString("d324c4b5-503a-4e10-a9ef-699ffddc147e"),
            "close-idempotency",
            "close-causation",
            "interim-result-sweep",
            "interim-result-sweep",
            EFFECTIVE_DATE,
            liveClockInstant,
            "SYSTEM",
            List.of(line("4000", "DEBIT", 100), line("3200", "CREDIT", 100)));

    assertEquals(Instant.parse("2026-07-20T12:30:45.123Z"), posting.recordedAt());
    assertEquals(Instant.parse("2026-07-20T12:30:45.123Z"), close.recordedAt());
    assertDoesNotThrow(
        () -> AttestationPostingMutationProjection.project(postingRequest(), posting));
    assertDoesNotThrow(
        () ->
            AttestationLifecycleMutationProjection.rekeyBook(
                "rekey-book", BigInteger.TWO, liveClockInstant, Optional.empty()));
  }
}
