package dev.erst.fingrind.core.attestation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Byte-for-byte conformance coverage for the normative Slice 0 envelope vectors. */
class AttestationEnvelopeConformanceTest {
  private static final String PROTOCOL_DOCUMENT = "docs/DOC_02_VerifiableOperationAttestation.md";
  private static final String ARTIFACT_DOCUMENT =
      "docs/DOC_02_VerifiableOperationAttestationArtifacts.md";
  private static final Pattern DOCUMENT_VALUE =
      Pattern.compile("^\\s*([A-Za-z][A-Za-z0-9]*)\\s*=\\s*([0-9a-f]+)\\s*$");
  private static final UUID BOOK_ID = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff");

  @Test
  void normativeDocumentationVectors_areTheConformanceOracle() throws IOException {
    byte[] operationPayload = documentedBytes(PROTOCOL_DOCUMENT, "V-OP-01", "payload");
    AttestationEnvelope<AttestationOperationPayload> operation =
        AttestationEnvelope.of(
            operationPayload(),
            List.of(
                new AttestationSignatureEntry(
                    uuid(documentedBytes(PROTOCOL_DOCUMENT, "V-OP-01", "principalId"), 0),
                    AttestationHash.of(documentedBytes(PROTOCOL_DOCUMENT, "V-OP-01", "keyId")),
                    documentedBytes(PROTOCOL_DOCUMENT, "V-OP-01", "signature"))));
    assertArrayEquals(operationPayload, operation.payload().encoded());
    assertEquals(documentedValue(PROTOCOL_DOCUMENT, "V-OP-01", "head"), operation.head().hex());

    byte[] twoPrincipalOperationEnvelope =
        documentedBytes(PROTOCOL_DOCUMENT, "V-OP-02", "envelope");
    List<AttestationSignatureEntry> operationEntries = entries(twoPrincipalOperationEnvelope, 181);
    AttestationEnvelope<AttestationOperationPayload> twoPrincipalOperation =
        AttestationEnvelope.of(
            operationPayload(), List.of(operationEntries.get(1), operationEntries.get(0)));
    assertArrayEquals(twoPrincipalOperationEnvelope, twoPrincipalOperation.encoded());
    assertEquals(
        documentedValue(PROTOCOL_DOCUMENT, "V-OP-02", "head"), twoPrincipalOperation.head().hex());

    byte[] manifestEnvelope = documentedBytes(ARTIFACT_DOCUMENT, "V-MANIFEST-02", "envelope");
    AttestationEnvelope<AttestationBackupManifestPayload> manifest =
        AttestationEnvelope.of(manifestPayload(), entries(manifestEnvelope, 121));
    assertArrayEquals(
        documentedBytes(ARTIFACT_DOCUMENT, "V-MANIFEST-02", "payload"),
        manifest.payload().encoded());
    assertArrayEquals(manifestEnvelope, manifest.encoded());
    assertEquals(
        documentedValue(ARTIFACT_DOCUMENT, "V-MANIFEST-02", "head"), manifest.head().hex());

    byte[] receiptEnvelope = documentedBytes(ARTIFACT_DOCUMENT, "V-RECEIPT-02", "envelope");
    AttestationEnvelope<AttestationReceiptPayload> receipt =
        AttestationEnvelope.of(receiptPayload(), entries(receiptEnvelope, 97));
    assertArrayEquals(
        documentedBytes(ARTIFACT_DOCUMENT, "V-RECEIPT-02", "payload"), receipt.payload().encoded());
    assertArrayEquals(receiptEnvelope, receipt.encoded());
    assertEquals(documentedValue(ARTIFACT_DOCUMENT, "V-RECEIPT-02", "head"), receipt.head().hex());

    byte[] expectedContainer = documentedBytes(ARTIFACT_DOCUMENT, "V-CONTAINER-01", "container");
    byte[] snapshot = documentedBytes(ARTIFACT_DOCUMENT, "V-CONTAINER-01", "snapshot");
    byte[] trailer = documentedBytes(ARTIFACT_DOCUMENT, "V-CONTAINER-01", "trailer");
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
                    hex("d7e8fb5126e2d1a7ff28398faec6bfa0e061ca1c74ffd4d1947ea5f70a339213")),
                AttestationHash.of(
                    documentedBytes(ARTIFACT_DOCUMENT, "V-CONTAINER-01", "snapshotDigest"))),
            entries(expectedContainerManifest, 121));
    AttestationArtifactContainer container =
        new AttestationArtifactContainer(snapshot, containerManifest);
    assertArrayEquals(expectedContainer, container.encoded());
    assertArrayEquals(
        documentedBytes(ARTIFACT_DOCUMENT, "V-CONTAINER-01", "trailer"), container.trailer());
    assertEquals(
        documentedValue(ARTIFACT_DOCUMENT, "V-CONTAINER-01", "containerDigest"),
        container.digest().hex());
  }

  private static AttestationOperationPayload operationPayload() {
    return new AttestationOperationPayload(
        BOOK_ID,
        BigInteger.valueOf(42),
        "record-sale-settled",
        AttestationHash.of(hex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f")),
        Instant.parse("2026-07-17T03:34:00.485Z"),
        AttestationHash.of(hex("202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f")),
        AttestationHash.of(
            hex("404142434445464748494a4b4c4d4e4f505152535455565758595a5b5c5d5e5f")));
  }

  private static AttestationBackupManifestPayload manifestPayload() {
    return new AttestationBackupManifestPayload(
        BOOK_ID,
        UUID.fromString("ffeeddcc-bbaa-9988-7766-554433221100"),
        BigInteger.valueOf(42),
        AttestationHash.of(hex("d7e8fb5126e2d1a7ff28398faec6bfa0e061ca1c74ffd4d1947ea5f70a339213")),
        AttestationHash.of(
            hex("606162636465666768696a6b6c6d6e6f707172737475767778797a7b7c7d7e7f")));
  }

  private static AttestationReceiptPayload receiptPayload() {
    return new AttestationReceiptPayload(
        BOOK_ID,
        BigInteger.valueOf(42),
        AttestationHash.of(hex("d7e8fb5126e2d1a7ff28398faec6bfa0e061ca1c74ffd4d1947ea5f70a339213")),
        Instant.parse("2026-07-17T04:00:00Z"));
  }

  private static List<AttestationSignatureEntry> entries(byte[] envelope, int payloadLength) {
    int count =
        Short.toUnsignedInt((short) ((envelope[payloadLength] << 8) | envelope[payloadLength + 1]));
    int offset = payloadLength + Short.BYTES;
    List<AttestationSignatureEntry> entries = new ArrayList<>(count);
    for (int index = 0; index < count; index++) {
      UUID principalId = uuid(envelope, offset);
      offset += 16;
      AttestationHash keyId =
          AttestationHash.of(java.util.Arrays.copyOfRange(envelope, offset, offset + 32));
      offset += 32;
      entries.add(
          new AttestationSignatureEntry(
              principalId, keyId, java.util.Arrays.copyOfRange(envelope, offset, offset + 64)));
      offset += 64;
    }
    assertEquals(envelope.length, offset);
    return List.copyOf(entries);
  }

  private static UUID uuid(byte[] bytes, int offset) {
    java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(bytes, offset, 16);
    return new UUID(buffer.getLong(), buffer.getLong());
  }

  private static byte[] hex(String value) {
    return HexFormat.of().parseHex(value);
  }

  private static byte[] documentedBytes(String document, String vector, String field)
      throws IOException {
    return hex(documentedValue(document, vector, field));
  }

  private static String documentedValue(String document, String vector, String field)
      throws IOException {
    for (String line : vectorSection(document, vector)) {
      Matcher matcher = DOCUMENT_VALUE.matcher(line);
      if (matcher.matches() && field.equals(matcher.group(1))) {
        return matcher.group(2);
      }
    }
    throw new IllegalStateException("Cannot locate " + field + " in " + vector + ".");
  }

  private static List<String> vectorSection(String document, String vector) throws IOException {
    List<String> section = new ArrayList<>();
    boolean inSection = false;
    for (String line : Files.readAllLines(protocolDocument(document))) {
      if (line.startsWith("### " + vector)) {
        inSection = true;
      } else if (inSection && line.startsWith("### ")) {
        break;
      } else if (inSection) {
        section.add(line);
      }
    }
    if (section.isEmpty()) {
      throw new IllegalStateException("Cannot locate " + vector + " in " + document + ".");
    }
    return List.copyOf(section);
  }

  private static Path protocolDocument(String document) {
    for (Path directory = Path.of("").toAbsolutePath();
        directory != null;
        directory = directory.getParent()) {
      Path candidate = directory.resolve(document);
      if (Files.isRegularFile(candidate)) {
        return candidate;
      }
    }
    throw new IllegalStateException(
        "Cannot locate " + document + " from the test working directory.");
  }
}
