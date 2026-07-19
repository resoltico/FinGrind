package dev.erst.fingrind.core.attestation;

import static dev.erst.fingrind.core.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Boundary coverage for the Slice 1 canonical codec. */
class AttestationCodecValidationTest {
  private static final String ED25519_SPKI_HEX =
      "302a300506032b657003210003a107bff3ce10be1d70dd18e74bc09967e4d6309ba50d5f1ddc8664125531b8";
  private static final UUID PRINCIPAL_A = UUID.fromString("10213243-5465-7687-98a9-babcbddceeff");
  private static final UUID PRINCIPAL_B = UUID.fromString("11223344-5566-7788-99aa-bbccddeeff00");
  private static final AttestationHash HASH_A = hash(1);
  private static final AttestationHash HASH_B = hash(2);

  @Test
  void hash_isImmutableComparableAndReportsUnavailableAlgorithms() {
    byte[] source = new byte[AttestationHash.BYTE_LENGTH];
    source[0] = 1;
    AttestationHash value = AttestationHash.of(source);
    source[0] = 9;

    assertEquals(1, Byte.toUnsignedInt(value.bytes()[0]));
    byte[] copiedBytes = value.bytes();
    copiedBytes[0] = 7;
    assertEquals(1, Byte.toUnsignedInt(value.bytes()[0]));
    assertEquals(value.hex(), value.toString());
    assertEquals(value, AttestationHash.of(value.bytes()));
    assertEquals(value.hashCode(), AttestationHash.of(value.bytes()).hashCode());
    assertNotEquals(value, HASH_A);
    assertNotEquals(value, "not-a-hash");
    assertTrue(HASH_A.compareTo(HASH_B) < 0);
    assertTrue(HASH_B.compareTo(HASH_A) > 0);
    assertEquals(0, HASH_A.compareTo(AttestationHash.of(HASH_A.bytes())));
    assertThrows(NullPointerException.class, () -> HASH_A.compareTo(nullOf()));
    assertEquals(
        "Attestation hash must contain exactly 32 bytes.",
        assertThrows(IllegalArgumentException.class, () -> AttestationHash.of(new byte[31]))
            .getMessage());
    assertEquals(
        "missing-digest is unavailable in this Java runtime.",
        assertThrows(
                IllegalStateException.class,
                () -> AttestationHash.digest(new byte[0], "missing-digest"))
            .getMessage());
    assertEquals(
        "Attestation hash must contain exactly 32 bytes.",
        assertThrows(
                IllegalArgumentException.class, () -> AttestationHash.digest(new byte[0], "SHA-1"))
            .getMessage());
    assertEquals(
        "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
        AttestationHash.sha256(new byte[0]).hex());
  }

  @Test
  void envelope_and_signature_entry_protectCanonicalStructure() {
    byte[] signature = new byte[AttestationSignatureEntry.SIGNATURE_BYTE_LENGTH];
    signature[0] = 4;
    AttestationSignatureEntry entry = new AttestationSignatureEntry(PRINCIPAL_A, HASH_A, signature);
    signature[0] = 5;
    assertEquals(PRINCIPAL_A, entry.principalId());
    assertSame(HASH_A, entry.keyId());
    assertEquals(4, Byte.toUnsignedInt(entry.signature()[0]));
    byte[] copiedSignature = entry.signature();
    copiedSignature[0] = 6;
    assertEquals(4, Byte.toUnsignedInt(entry.signature()[0]));
    assertEquals(
        "Attestation signature must contain exactly 64 bytes.",
        assertThrows(
                IllegalArgumentException.class,
                () -> new AttestationSignatureEntry(PRINCIPAL_A, HASH_A, new byte[63]))
            .getMessage());

    AttestationOperationPayload payload = operationPayload();
    AttestationEnvelope<AttestationOperationPayload> envelope =
        AttestationEnvelope.of(payload, List.of(entry));
    assertSame(payload, envelope.payload());
    assertEquals(List.of(entry), envelope.entries());
    assertEquals(295, envelope.encoded().length);
    assertArrayEquals(envelope.encoded(), envelope.encoded());

    AttestationSignatureEntry secondKey =
        new AttestationSignatureEntry(PRINCIPAL_A, HASH_B, new byte[64]);
    assertEquals(
        "Attestation envelope principal IDs must be distinct.",
        assertThrows(
                IllegalArgumentException.class,
                () -> AttestationEnvelope.of(payload, List.of(entry, secondKey)))
            .getMessage());
    AttestationSignatureEntry secondPrincipal =
        new AttestationSignatureEntry(PRINCIPAL_B, HASH_A, new byte[64]);
    assertEquals(
        "Attestation envelope key IDs must be distinct.",
        assertThrows(
                IllegalArgumentException.class,
                () -> AttestationEnvelope.of(payload, List.of(entry, secondPrincipal)))
            .getMessage());
    assertEquals(
        "Attestation envelope may contain at most 65535 signatures.",
        assertThrows(
                IllegalArgumentException.class,
                () -> AttestationEnvelope.of(payload, java.util.Collections.nCopies(65_536, entry)))
            .getMessage());
  }

  @Test
  void field_values_enforceTheirSemanticBoundsAndProtectCallerArrays() {
    assertArrayEquals(
        new byte[] {0, 0, 0, 3, 1, 2, 3},
        AttestationBinaryFieldValue.bytes(new byte[] {1, 2, 3}).encoded());
    assertEquals(
        "002c" + ED25519_SPKI_HEX,
        HexFormat.of().formatHex(AttestationBinaryFieldValue.spki(ed25519Spki()).encoded()));
    assertArrayEquals(
        new byte[] {
          0x10,
          0x21,
          0x32,
          0x43,
          0x54,
          0x65,
          0x76,
          (byte) 0x87,
          (byte) 0x98,
          (byte) 0xa9,
          (byte) 0xba,
          (byte) 0xbc,
          (byte) 0xbd,
          (byte) 0xdc,
          (byte) 0xee,
          (byte) 0xff
        },
        AttestationBinaryFieldValue.uuid(PRINCIPAL_A).encoded());
    assertArrayEquals(HASH_A.bytes(), AttestationBinaryFieldValue.hash(HASH_A).encoded());
    assertArrayEquals(
        new byte[] {'E', 'U', 'R'}, AttestationTextFieldValue.currency("EUR").encoded());
    assertArrayEquals(new byte[] {1}, AttestationNumericFieldValue.booleanValue(true).encoded());
    assertArrayEquals(new byte[] {0}, AttestationNumericFieldValue.booleanValue(false).encoded());
    assertArrayEquals(new byte[] {6}, AttestationNumericFieldValue.mutation(6).encoded());
    assertEquals(
        "money zero must use the plus sign.",
        assertThrows(
                IllegalArgumentException.class,
                () -> AttestationNumericFieldValue.money("EUR", true, BigInteger.ZERO))
            .getMessage());
    assertArrayEquals(
        new byte[] {69, 85, 82, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1},
        AttestationNumericFieldValue.money("EUR", true, BigInteger.ONE).encoded());
    assertEquals(
        "unsigned16 must fit an unsigned 2-byte integer.",
        assertThrows(
                IllegalArgumentException.class, () -> AttestationNumericFieldValue.unsigned16(-1))
            .getMessage());
    assertEquals(
        "unsigned32 must fit an unsigned 4-byte integer.",
        assertThrows(
                IllegalArgumentException.class,
                () -> AttestationNumericFieldValue.unsigned32(BigInteger.ONE.shiftLeft(32)))
            .getMessage());
    assertEquals(
        "signed64 must fit a signed 8-byte integer.",
        assertThrows(
                IllegalArgumentException.class,
                () -> AttestationNumericFieldValue.signed64(BigInteger.ONE.shiftLeft(63)))
            .getMessage());
    assertArrayEquals(
        new byte[16], AttestationNumericFieldValue.signed128(BigInteger.ZERO).encoded());
    assertEquals(
        "scaled scale must be between 0 and 18.",
        assertThrows(
                IllegalArgumentException.class,
                () -> AttestationNumericFieldValue.scaled(19, false, BigInteger.ZERO))
            .getMessage());
    assertEquals(
        "scaled scale must be between 0 and 18.",
        assertThrows(
                IllegalArgumentException.class,
                () -> AttestationNumericFieldValue.scaled(-1, false, BigInteger.ZERO))
            .getMessage());
    assertEquals(
        "scaled zero must use the plus sign.",
        assertThrows(
                IllegalArgumentException.class,
                () -> AttestationNumericFieldValue.scaled(0, true, BigInteger.ZERO))
            .getMessage());
    assertArrayEquals(
        new byte[18], AttestationNumericFieldValue.scaled(0, false, BigInteger.ZERO).encoded());
    assertArrayEquals(
        new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1},
        AttestationNumericFieldValue.scaled(0, false, BigInteger.ONE).encoded());
    assertEquals(
        "mutation must be between 0 and 6.",
        assertThrows(IllegalArgumentException.class, () -> AttestationNumericFieldValue.mutation(7))
            .getMessage());
    assertEquals(
        "mutation must be between 0 and 6.",
        assertThrows(
                IllegalArgumentException.class, () -> AttestationNumericFieldValue.mutation(-1))
            .getMessage());
    assertEquals(
        "currency must be exactly three uppercase ASCII letters.",
        assertThrows(
                IllegalArgumentException.class,
                () -> AttestationNumericFieldValue.money("eur", false, BigInteger.ZERO))
            .getMessage());
    assertEquals(
        "Unsupported currency unit code: ZZZ.",
        assertThrows(
                IllegalArgumentException.class,
                () -> AttestationNumericFieldValue.money("ZZZ", false, BigInteger.ZERO))
            .getMessage());
    assertEquals(
        "token must be a lowercase ASCII kebab token of at most 64 bytes.",
        assertThrows(
                IllegalArgumentException.class,
                () -> AttestationTextFieldValue.token("Not a token"))
            .getMessage());
    assertEquals(
        "token must be a lowercase ASCII kebab token of at most 64 bytes.",
        assertThrows(
                IllegalArgumentException.class,
                () -> AttestationTextFieldValue.token("a".repeat(65)))
            .getMessage());
    assertEquals(
        "text must be NFC-normalized.",
        assertThrows(
                IllegalArgumentException.class, () -> AttestationTextFieldValue.text("e\u0301"))
            .getMessage());
    assertEquals(
        "text must not contain NUL.",
        assertThrows(
                IllegalArgumentException.class,
                () -> AttestationTextFieldValue.text("before\u0000after"))
            .getMessage());
    assertEquals(
        "text must be at most 1048576 bytes.",
        assertThrows(
                IllegalArgumentException.class,
                () -> AttestationTextFieldValue.text("a".repeat(1_048_577)))
            .getMessage());
    assertEquals(
        "spki must contain between 1 and 4096 bytes.",
        assertThrows(
                IllegalArgumentException.class, () -> AttestationBinaryFieldValue.spki(new byte[0]))
            .getMessage());
    assertEquals(
        "spki must contain between 1 and 4096 bytes.",
        assertThrows(
                IllegalArgumentException.class,
                () -> AttestationBinaryFieldValue.spki(new byte[4_097]))
            .getMessage());
    assertEquals(
        "spki must be a DER-encoded Ed25519 SubjectPublicKeyInfo.",
        assertThrows(
                IllegalArgumentException.class,
                () -> AttestationBinaryFieldValue.spki(new byte[] {1}))
            .getMessage());
    assertEquals(
        "spki must be a DER-encoded Ed25519 SubjectPublicKeyInfo.",
        assertThrows(
                IllegalArgumentException.class,
                () -> AttestationBinaryFieldValue.spki(new byte[44]))
            .getMessage());
    assertEquals(
        "bytes must be at most 1048576 bytes.",
        assertThrows(
                IllegalArgumentException.class,
                () -> AttestationBinaryFieldValue.bytes(new byte[1_048_577]))
            .getMessage());
    assertEquals(
        "instant must be precise to milliseconds.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    AttestationTextFieldValue.instant(
                        Instant.parse("2026-07-19T12:34:56.789000001Z")))
            .getMessage());
    assertEquals(
        "instant must fit the four-digit UTC wire timestamp range.",
        assertThrows(
                IllegalArgumentException.class,
                () -> AttestationTextFieldValue.instant(Instant.parse("+10000-01-01T00:00:00Z")))
            .getMessage());
    assertEquals(
        "date must fit the four-digit Gregorian date range.",
        assertThrows(
                IllegalArgumentException.class,
                () -> AttestationTextFieldValue.date(LocalDate.of(10_000, 1, 1)))
            .getMessage());
    assertEquals(
        "unsigned8 must be an unsigned byte.",
        assertThrows(
                IllegalArgumentException.class, () -> AttestationNumericFieldValue.unsigned8(-1))
            .getMessage());
    assertEquals(
        "unsigned8 must be an unsigned byte.",
        assertThrows(
                IllegalArgumentException.class, () -> AttestationNumericFieldValue.unsigned8(256))
            .getMessage());
    assertEquals(
        "signed64 must fit a signed 8-byte integer.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    AttestationNumericFieldValue.signed64(
                        BigInteger.ONE.shiftLeft(63).negate().subtract(BigInteger.ONE)))
            .getMessage());
    assertThrows(
        NullPointerException.class,
        () -> AttestationUnsignedEncoding.appendByte(nullOf(), 0, "unsigned8"));
  }

  @Test
  void artifact_container_defensivelyOwnsItsSnapshotAndRetainsItsManifest() {
    AttestationEnvelope<AttestationBackupManifestPayload> manifest =
        AttestationEnvelope.of(
            new AttestationBackupManifestPayload(
                PRINCIPAL_A, PRINCIPAL_B, BigInteger.ZERO, HASH_A, HASH_B),
            List.of());
    byte[] snapshot = new byte[] {1, 2};
    AttestationArtifactContainer container = new AttestationArtifactContainer(snapshot, manifest);
    snapshot[0] = 9;

    assertArrayEquals(new byte[] {1, 2}, container.snapshot());
    byte[] snapshotCopy = container.snapshot();
    snapshotCopy[0] = 8;
    assertArrayEquals(new byte[] {1, 2}, container.snapshot());
    assertSame(manifest, container.manifest());
  }

  @Test
  void preimage_and_fields_rejectInvalidGrammarAndPreserveImmutableFacts() {
    AttestationField absent = AttestationField.absent();
    AttestationField present = AttestationField.present(AttestationTextFieldValue.token("fact"));
    assertFalse(absent.isPresent());
    assertTrue(present.isPresent());
    assertArrayEquals(new byte[] {0}, absent.encoded());
    assertArrayEquals(new byte[] {1, 4, 'f', 'a', 'c', 't'}, present.encoded());

    AttestationPreimage.Fact fact =
        new AttestationPreimage.Fact(
            0x0103,
            List.of(present, AttestationField.present(AttestationNumericFieldValue.unsigned16(1))));
    AttestationPreimage preimage = AttestationPreimage.of(List.of(fact));
    assertEquals(List.of(fact), preimage.records());
    assertEquals(0x0103, fact.recordTypeTag());
    assertArrayEquals(new byte[] {1, 0, 1}, fact.fields().get(1).encoded());
    byte[] sortKey = fact.encodedSortKey();
    sortKey[0] = 0;
    assertEquals(1, Byte.toUnsignedInt(fact.encodedSortKey()[0]));

    assertEquals(
        "recordTypeTag must fit an unsigned 2-byte integer.",
        assertThrows(
                IllegalArgumentException.class,
                () -> new AttestationPreimage.Fact(65_536, List.of(present)))
            .getMessage());
    assertEquals(
        "Attestation preimage record type is unknown.",
        assertThrows(
                IllegalArgumentException.class,
                () -> new AttestationPreimage.Fact(0x7fff, List.of()))
            .getMessage());
    assertEquals(
        "Attestation record request.policy-rule must contain its catalog-defined field count.",
        assertThrows(
                IllegalArgumentException.class,
                () -> new AttestationPreimage.Fact(0x0103, List.of(present)))
            .getMessage());
    assertEquals(
        "Attestation record request.policy-rule field capability violates its catalog type or presence rule.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    new AttestationPreimage.Fact(
                        0x0103,
                        List.of(
                            AttestationField.present(AttestationNumericFieldValue.unsigned16(1)),
                            AttestationField.present(AttestationNumericFieldValue.unsigned16(1)))))
            .getMessage());
    assertEquals(
        "Attestation record request.policy-rule field capability violates its catalog type or presence rule.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    new AttestationPreimage.Fact(
                        0x0103,
                        List.of(
                            AttestationField.absent(),
                            AttestationField.present(AttestationNumericFieldValue.unsigned16(1)))))
            .getMessage());
    assertEquals(
        "recordTypeTag must fit an unsigned 2-byte integer.",
        assertThrows(
                IllegalArgumentException.class,
                () -> new AttestationPreimage.Fact(-1, List.of(present)))
            .getMessage());
    assertEquals(77, AttestationPreimageCatalog.recordCount());
    assertEquals(
        "Attestation preimage may contain at most 1000000 records.",
        assertThrows(
                IllegalArgumentException.class,
                () -> AttestationPreimage.of(oversizedList(1_000_001)))
            .getMessage());
    assertEquals(
        "Attestation preimage must be at most 16777216 bytes.",
        assertThrows(IllegalArgumentException.class, () -> AttestationPreimage.of(oversizedFacts()))
            .getMessage());
  }

  private static AttestationOperationPayload operationPayload() {
    return new AttestationOperationPayload(
        UUID.fromString("00112233-4455-6677-8899-aabbccddeeff"),
        BigInteger.valueOf(42),
        "record-sale-settled",
        hash(3),
        Instant.parse("2026-07-19T12:34:56.789Z"),
        hash(4),
        hash(5));
  }

  private static AttestationHash hash(int finalByte) {
    byte[] bytes = new byte[AttestationHash.BYTE_LENGTH];
    bytes[AttestationHash.BYTE_LENGTH - 1] = (byte) finalByte;
    return AttestationHash.of(bytes);
  }

  private static byte[] ed25519Spki() {
    return HexFormat.of().parseHex(ED25519_SPKI_HEX);
  }

  private static <T> List<T> oversizedList(int size) {
    return new AbstractList<>() {
      @Override
      public T get(int index) {
        throw new AssertionError("The size guard must run before list traversal.");
      }

      @Override
      public int size() {
        return size;
      }
    };
  }

  private static List<AttestationPreimage.Fact> oversizedFacts() {
    String largeText = "x".repeat(1_048_576);
    List<AttestationPreimage.Fact> facts = new ArrayList<>();
    for (int stepOrder = 0; stepOrder < 17; stepOrder++) {
      facts.add(
          new AttestationPreimage.Fact(
              0x0124,
              List.of(
                  AttestationField.present(
                      AttestationNumericFieldValue.unsigned32(BigInteger.valueOf(stepOrder))),
                  AttestationField.present(AttestationTextFieldValue.text(largeText)),
                  AttestationField.present(AttestationTextFieldValue.text("source")),
                  AttestationField.present(
                      AttestationTextFieldValue.date(LocalDate.of(2026, 7, 19))))));
    }
    return List.copyOf(facts);
  }
}
