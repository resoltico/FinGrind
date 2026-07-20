package dev.erst.fingrind.core.attestation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Byte-for-byte conformance coverage for the normative Slice 0 envelope vectors. */
class AttestationEnvelopeConformanceTest {
  private static final UUID BOOK_ID = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff");

  @Test
  void normativeDocumentationVectors_areTheConformanceOracle() throws IOException {
    byte[] operationPayload =
        AttestationDocumentVectors.bytes(
            AttestationDocumentVectors.PROTOCOL_DOCUMENT, "V-OP-01", "payload");
    AttestationEnvelope<AttestationOperationPayload> operation =
        AttestationEnvelope.of(
            operationPayload(),
            List.of(
                new AttestationSignatureEntry(
                    UUID.fromString("10213243-5465-7687-98a9-babcbddceeff"),
                    AttestationHash.of(
                        AttestationDocumentVectors.bytes(
                            AttestationDocumentVectors.PROTOCOL_DOCUMENT, "V-OP-01", "keyId")),
                    AttestationDocumentVectors.bytes(
                        AttestationDocumentVectors.PROTOCOL_DOCUMENT, "V-OP-01", "signature"))));
    assertArrayEquals(operationPayload, operation.payload().encoded());
    assertEquals(
        AttestationDocumentVectors.value(
            AttestationDocumentVectors.PROTOCOL_DOCUMENT, "V-OP-01", "head"),
        operation.head().hex());

    byte[] twoPrincipalOperationEnvelope =
        AttestationDocumentVectors.bytes(
            AttestationDocumentVectors.PROTOCOL_DOCUMENT, "V-OP-02", "envelope");
    List<AttestationSignatureEntry> operationEntries =
        AttestationDocumentVectors.entries(twoPrincipalOperationEnvelope, 181);
    AttestationEnvelope<AttestationOperationPayload> twoPrincipalOperation =
        AttestationEnvelope.of(
            operationPayload(), List.of(operationEntries.get(1), operationEntries.get(0)));
    assertArrayEquals(twoPrincipalOperationEnvelope, twoPrincipalOperation.encoded());
    assertEquals(
        AttestationDocumentVectors.value(
            AttestationDocumentVectors.PROTOCOL_DOCUMENT, "V-OP-02", "head"),
        twoPrincipalOperation.head().hex());

    byte[] manifestEnvelope =
        AttestationDocumentVectors.bytes(
            AttestationDocumentVectors.ARTIFACT_DOCUMENT, "V-MANIFEST-02", "envelope");
    AttestationEnvelope<AttestationBackupManifestPayload> manifest =
        AttestationEnvelope.of(
            manifestPayload(), AttestationDocumentVectors.entries(manifestEnvelope, 121));
    assertArrayEquals(
        AttestationDocumentVectors.bytes(
            AttestationDocumentVectors.ARTIFACT_DOCUMENT, "V-MANIFEST-02", "payload"),
        manifest.payload().encoded());
    assertArrayEquals(manifestEnvelope, manifest.encoded());
    assertEquals(
        AttestationDocumentVectors.value(
            AttestationDocumentVectors.ARTIFACT_DOCUMENT, "V-MANIFEST-02", "head"),
        manifest.head().hex());

    byte[] receiptEnvelope =
        AttestationDocumentVectors.bytes(
            AttestationDocumentVectors.ARTIFACT_DOCUMENT, "V-RECEIPT-02", "envelope");
    AttestationEnvelope<AttestationReceiptPayload> receipt =
        AttestationEnvelope.of(
            receiptPayload(), AttestationDocumentVectors.entries(receiptEnvelope, 97));
    assertArrayEquals(
        AttestationDocumentVectors.bytes(
            AttestationDocumentVectors.ARTIFACT_DOCUMENT, "V-RECEIPT-02", "payload"),
        receipt.payload().encoded());
    assertArrayEquals(receiptEnvelope, receipt.encoded());
    assertEquals(
        AttestationDocumentVectors.value(
            AttestationDocumentVectors.ARTIFACT_DOCUMENT, "V-RECEIPT-02", "head"),
        receipt.head().hex());

    byte[] expectedContainer =
        AttestationDocumentVectors.bytes(
            AttestationDocumentVectors.ARTIFACT_DOCUMENT, "V-CONTAINER-01", "container");
    byte[] snapshot =
        AttestationDocumentVectors.bytes(
            AttestationDocumentVectors.ARTIFACT_DOCUMENT, "V-CONTAINER-01", "snapshot");
    byte[] trailer =
        AttestationDocumentVectors.bytes(
            AttestationDocumentVectors.ARTIFACT_DOCUMENT, "V-CONTAINER-01", "trailer");
    byte[] expectedContainerManifest =
        Arrays.copyOfRange(
            expectedContainer, snapshot.length, expectedContainer.length - trailer.length);
    AttestationEnvelope<AttestationBackupManifestPayload> containerManifest =
        AttestationEnvelope.of(
            new AttestationBackupManifestPayload(
                BOOK_ID,
                UUID.fromString("00112233-4455-6677-8899-aabbccddeeff"),
                BigInteger.valueOf(42),
                AttestationHash.of(
                    AttestationDocumentVectors.hex(
                        "d7e8fb5126e2d1a7ff28398faec6bfa0e061ca1c74ffd4d1947ea5f70a339213")),
                AttestationHash.of(
                    AttestationDocumentVectors.bytes(
                        AttestationDocumentVectors.ARTIFACT_DOCUMENT,
                        "V-CONTAINER-01",
                        "snapshotDigest"))),
            AttestationDocumentVectors.entries(expectedContainerManifest, 121));
    AttestationArtifactContainer container =
        new AttestationArtifactContainer(snapshot, containerManifest);
    assertArrayEquals(expectedContainer, container.encoded());
    assertArrayEquals(
        AttestationDocumentVectors.bytes(
            AttestationDocumentVectors.ARTIFACT_DOCUMENT, "V-CONTAINER-01", "trailer"),
        container.trailer());
    assertEquals(
        AttestationDocumentVectors.value(
            AttestationDocumentVectors.ARTIFACT_DOCUMENT, "V-CONTAINER-01", "containerDigest"),
        container.digest().hex());
  }

  private static AttestationOperationPayload operationPayload() {
    return new AttestationOperationPayload(
        BOOK_ID,
        BigInteger.valueOf(42),
        "record-sale-settled",
        AttestationHash.of(
            AttestationDocumentVectors.hex(
                "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f")),
        Instant.parse("2026-07-17T03:34:00.485Z"),
        AttestationHash.of(
            AttestationDocumentVectors.hex(
                "202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f")),
        AttestationHash.of(
            AttestationDocumentVectors.hex(
                "404142434445464748494a4b4c4d4e4f505152535455565758595a5b5c5d5e5f")));
  }

  private static AttestationBackupManifestPayload manifestPayload() {
    return new AttestationBackupManifestPayload(
        BOOK_ID,
        UUID.fromString("ffeeddcc-bbaa-9988-7766-554433221100"),
        BigInteger.valueOf(42),
        AttestationHash.of(
            AttestationDocumentVectors.hex(
                "d7e8fb5126e2d1a7ff28398faec6bfa0e061ca1c74ffd4d1947ea5f70a339213")),
        AttestationHash.of(
            AttestationDocumentVectors.hex(
                "606162636465666768696a6b6c6d6e6f707172737475767778797a7b7c7d7e7f")));
  }

  private static AttestationReceiptPayload receiptPayload() {
    return new AttestationReceiptPayload(
        BOOK_ID,
        BigInteger.valueOf(42),
        AttestationHash.of(
            AttestationDocumentVectors.hex(
                "d7e8fb5126e2d1a7ff28398faec6bfa0e061ca1c74ffd4d1947ea5f70a339213")),
        Instant.parse("2026-07-17T04:00:00Z"));
  }
}
