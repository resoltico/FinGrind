package dev.erst.fingrind.core.attestation;

import static dev.erst.fingrind.core.NullTestSupport.nullOf;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.credential;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Exercises the receipt-specific size and signer-count boundary before artifact verification. */
class AttestationReceiptTest {
  private static final UUID BOOK_ID = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff");
  private static final Instant RECEIPT_TIMESTAMP = Instant.parse("2026-07-20T12:30:45.123Z");

  @Test
  void createsAnUnsignedEnvelopeAtTheSigningSeamAndRejectsExcessSignersBeforeUse() {
    byte[] receipt =
        AttestationReceipt.create(
            BOOK_ID,
            BigInteger.ONE,
            new byte[AttestationHash.BYTE_LENGTH],
            RECEIPT_TIMESTAMP,
            List.of());
    AttestationReceiptPayload payload = AttestationDecodedEnvelope.receipt(receipt).payload();

    assertEquals(BOOK_ID, payload.bookId());
    assertEquals(BigInteger.ONE, payload.operationOrder());
    assertEquals(RECEIPT_TIMESTAMP, payload.receiptTimestamp());

    TestCredential testCredential = credential();
    AttestationPublicCredential publicCredential =
        new AttestationPublicCredential(testCredential.pair().getPublic().getEncoded());
    try (AttestationSigningCredential signer =
        new AttestationSigningCredential(
            testCredential.principalId(),
            publicCredential,
            Path.of("unused-attestation-key.fgatk"),
            new char[] {'p'})) {
      assertThrows(
          IllegalArgumentException.class,
          () ->
              AttestationReceipt.create(
                  BOOK_ID,
                  BigInteger.ONE,
                  new byte[AttestationHash.BYTE_LENGTH],
                  RECEIPT_TIMESTAMP,
                  Collections.nCopies(AttestationAuthorizationLimits.MAXIMUM_QUORUM + 1, signer)));
    }
    assertThrows(
        NullPointerException.class,
        () -> AttestationReceipt.requireMaximumEncodedByteCount(nullOf()));
  }
}
