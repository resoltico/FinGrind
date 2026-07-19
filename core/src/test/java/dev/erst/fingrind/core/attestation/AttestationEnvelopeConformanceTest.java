package dev.erst.fingrind.core.attestation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Byte-for-byte conformance coverage for the normative Slice 0 envelope vectors. */
class AttestationEnvelopeConformanceTest {
  private static final UUID BOOK_ID = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff");
  private static final String OPERATION_PAYLOAD_HEX =
      "46474154544f50310100112233445566778899aabbccddeeff000000000000002a"
          + "137265636f72642d73616c652d736574746c656407656432353531390001020304"
          + "05060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f323032362d30"
          + "372d31375430333a33343a30302e3438355a202122232425262728292a2b2c2d2e"
          + "2f303132333435363738393a3b3c3d3e3f404142434445464748494a4b4c4d4e4f"
          + "505152535455565758595a5b5c5d5e5f";
  private static final String OPERATION_ENVELOPE_HEX =
      OPERATION_PAYLOAD_HEX
          + "0001102132435465768798a9babcbddceeffa050837d85070582ccf7394b098884"
          + "7cc312cb88259b894899f6f239cf1791a58f09b867c26f97cf7887d76fe87035b1e"
          + "cf96ba078f816463e439d2d035e882288a6b4ec50951ba6e2bc7f28b954c1579e1f"
          + "c37328a405b869644ff15f877d0e";
  private static final String OPERATION_TWO_PRINCIPAL_ENVELOPE_HEX =
      OPERATION_PAYLOAD_HEX
          + "0002112233445566778899aabbccddeeff00824c89aa8efb95ef93629b451959912"
          + "9cace4adac9a6180daba31ceed41ecee6aa1ed4763cf3b2712c1826c25d43ff3d4c"
          + "ef5fb11ebd840ae97e57036f7003b2408a59ec16c9d3754ab1467d27488a96b455b"
          + "b178182a4d56fd2d96d2b4be601102132435465768798a9babcbddceeffa050837d"
          + "85070582ccf7394b0988847cc312cb88259b894899f6f239cf1791a58f09b867c26"
          + "f97cf7887d76fe87035b1ecf96ba078f816463e439d2d035e882288a6b4ec50951b"
          + "a6e2bc7f28b954c1579e1fc37328a405b869644ff15f877d0e";
  private static final String MANIFEST_ENVELOPE_HEX =
      "4647415454424d310100112233445566778899aabbccddeeffffeeddccbbaa998877"
          + "66554433221100000000000000002ad7e8fb5126e2d1a7ff28398faec6bfa0e061ca1"
          + "c74ffd4d1947ea5f70a339213606162636465666768696a6b6c6d6e6f7071727374"
          + "75767778797a7b7c7d7e7f07656432353531390002112233445566778899aabbccd"
          + "deeff00824c89aa8efb95ef93629b4519599129cace4adac9a6180daba31ceed41ec"
          + "ee6fe5c371ee312e047907cb70c3f7f93d0f187412869138f58287a8ff8662eb6902"
          + "1f9163e470f3230e89109128204088abe5c5520460b514547ed002c12efa0041021"
          + "32435465768798a9babcbddceeffa050837d85070582ccf7394b0988847cc312cb88"
          + "259b894899f6f239cf1791a59a8259fa79252defc53e7bd64215f5b15e63ec4d16e"
          + "f5cb3377762c2134371d4194ab61e929e87068475a9ad5e2b19829f16c32eb8f2f2"
          + "be0721c219e6372804";
  private static final String RECEIPT_ENVELOPE_HEX =
      "46474154545243310100112233445566778899aabbccddeeff000000000000002ad7"
          + "e8fb5126e2d1a7ff28398faec6bfa0e061ca1c74ffd4d1947ea5f70a339213323032"
          + "362d30372d31375430343a30303a30302e3030305a07656432353531390002112233"
          + "445566778899aabbccddeeff00824c89aa8efb95ef93629b4519599129cace4adac9"
          + "a6180daba31ceed41ecee68f69835573aa8fe7afb8456eca706eb32700b4a19faf7f"
          + "b544e8f9e55e49393bafa0316be4dd0a01362c2650df94e37ca857a994aac46a869"
          + "f33c5d8a788320b102132435465768798a9babcbddceeffa050837d85070582ccf7"
          + "394b0988847cc312cb88259b894899f6f239cf1791a556cf223436c6e05b65040e2"
          + "6eb5674686e575846c4f4b78ff7645a7bfb2d5dddfeb0c7b76e67d4b2557a45c549"
          + "9a1c890192d4daa2840b6b682da7be5cdff20e";
  private static final String CONTAINER_MANIFEST_ENVELOPE_HEX =
      "4647415454424d310100112233445566778899aabbccddeeff001122334455667788"
          + "99aabbccddeeff000000000000002ad7e8fb5126e2d1a7ff28398faec6bfa0e061ca"
          + "1c74ffd4d1947ea5f70a339213be45cb2605bf36bebde684841a28f0fd43c69850a"
          + "3dce5fedba69928ee3a899107656432353531390002112233445566778899aabbccd"
          + "deeff00824c89aa8efb95ef93629b4519599129cace4adac9a6180daba31ceed41ec"
          + "ee67653ae182cf8e3eb9cfbfb479a11ac87effa34ea3b7deafbec65ca7a29fd4993a"
          + "93f66ef8cd42fac7d2f3cef70f54cbe3f8a359c89ee3ebaa5e5397efce8840610213"
          + "2435465768798a9babcbddceeffa050837d85070582ccf7394b0988847cc312cb88"
          + "259b894899f6f239cf1791a5555760252105dfdd5f3a45358581f7ede854f5c8ed7"
          + "e156ee80a488a67c0da8c28a5c85a16d12d8d415448f8cfe6ee4558566a157ec51f"
          + "97af4f22b4d5d45c0d";

  @Test
  void operationEnvelope_reproducesSingleAndTwoPrincipalVectors() {
    byte[] singleExpected = hex(OPERATION_ENVELOPE_HEX);
    AttestationEnvelope<AttestationOperationPayload> single =
        AttestationEnvelope.of(operationPayload(), entries(singleExpected, 181));

    assertArrayEquals(hex(OPERATION_PAYLOAD_HEX), single.payload().encoded());
    assertArrayEquals(singleExpected, single.encoded());
    assertEquals(
        "d7e8fb5126e2d1a7ff28398faec6bfa0e061ca1c74ffd4d1947ea5f70a339213", single.head().hex());

    byte[] twoPrincipalExpected = hex(OPERATION_TWO_PRINCIPAL_ENVELOPE_HEX);
    List<AttestationSignatureEntry> entries = entries(twoPrincipalExpected, 181);
    AttestationEnvelope<AttestationOperationPayload> twoPrincipal =
        AttestationEnvelope.of(operationPayload(), List.of(entries.get(1), entries.get(0)));

    assertArrayEquals(twoPrincipalExpected, twoPrincipal.encoded());
    assertEquals(
        "1340639b39f477bde0427c9e347b9096e18ef19551ff288f88aa597f1347d45a",
        twoPrincipal.head().hex());
  }

  @Test
  void manifestAndReceiptEnvelopes_reproduceTwoPrincipalVectors() {
    byte[] manifestExpected = hex(MANIFEST_ENVELOPE_HEX);
    AttestationEnvelope<AttestationBackupManifestPayload> manifest =
        AttestationEnvelope.of(manifestPayload(), entries(manifestExpected, 121));
    assertArrayEquals(manifestExpected, manifest.encoded());
    assertEquals(
        "c3a03b2006e080726454b60ace100df0f9e4e78cdf2154b0454503794c830c69", manifest.head().hex());

    byte[] receiptExpected = hex(RECEIPT_ENVELOPE_HEX);
    AttestationEnvelope<AttestationReceiptPayload> receipt =
        AttestationEnvelope.of(receiptPayload(), entries(receiptExpected, 97));
    assertArrayEquals(receiptExpected, receipt.encoded());
    assertEquals(
        "42549e39bdb60205d16082d6e557c4c9d12e000a87b40f0974b2d82f62f3d0dc", receipt.head().hex());
  }

  @Test
  void artifactContainer_reproducesParserAndDigestVector() {
    byte[] expectedManifest = hex(CONTAINER_MANIFEST_ENVELOPE_HEX);
    AttestationEnvelope<AttestationBackupManifestPayload> manifest =
        AttestationEnvelope.of(
            new AttestationBackupManifestPayload(
                BOOK_ID,
                UUID.fromString("00112233-4455-6677-8899-aabbccddeeff"),
                BigInteger.valueOf(42),
                AttestationHash.of(
                    hex("d7e8fb5126e2d1a7ff28398faec6bfa0e061ca1c74ffd4d1947ea5f70a339213")),
                AttestationHash.of(
                    hex("be45cb2605bf36bebde684841a28f0fd43c69850a3dce5fedba69928ee3a8991"))),
            entries(expectedManifest, 121));
    AttestationArtifactContainer container =
        new AttestationArtifactContainer(hex("000102030405060708090a0b0c0d0e0f"), manifest);

    assertArrayEquals(expectedManifest, manifest.encoded());
    assertArrayEquals(hex("46474154424d46310100000000000000100000015b"), container.trailer());
    assertEquals(
        "3b0fc99b3916dadebfdfa6babcff83afdac8d23b861a4a4e5c43d9e386d9d6ff",
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
}
