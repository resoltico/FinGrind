package dev.erst.fingrind.core.attestation;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.EdECPrivateKeySpec;
import java.security.spec.NamedParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Deterministically re-authors lifecycle corpus sources from B-02 and fixed non-production keys.
 */
final class AttestationLifecycleCorpusAuthoring {
  private static final UUID BACKUP_ID = UUID.fromString("ffeeddcc-bbaa-9988-7766-554433221100");
  private static final Instant GENESIS_RECORDED_AT = Instant.parse("2026-12-31T03:00:00.000Z");
  private static final Signer A =
      signer(
          "10213243-5465-7687-98a9-babcbddceeff",
          "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f",
          "302a300506032b657003210003a107bff3ce10be1d70dd18e74bc09967e4d6309ba50d5f1ddc8664125531b8");
  private static final Signer B =
      signer(
          "11223344-5566-7788-99aa-bbccddeeff00",
          "202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f",
          "302a300506032b657003210029acbae141bccaf0b22e1a94d34d0bc7361e526d0bfe12c89794bc9322966dd7");

  private AttestationLifecycleCorpusAuthoring() {}

  static Map<String, byte[]> sources() {
    List<AttestationBookOperation> source =
        AttestationStaticCorpusVectors.book("B-02").operations();
    AttestationBookOperation sourceOperation = source.getLast();
    AttestationBackupAcknowledgement acknowledgement =
        new AttestationBackupAcknowledgement(
            BACKUP_ID,
            AttestationHash.sha256(AttestationStaticCorpusVectors.source("B-05-artifact")).bytes(),
            sourceOperation.envelope().payload().operationOrder(),
            sourceOperation.envelope().head().bytes());
    return Map.of(
        "B-05-book", backup(source, acknowledgement),
        "B-06", restore(source, acknowledgement, "fixture-b-06-4"),
        "B-07", restore(source, acknowledgement, "fixture-b-07-4"),
        "B-10", rekey(source));
  }

  private static byte[] backup(
      List<AttestationBookOperation> source, AttestationBackupAcknowledgement acknowledgement) {
    AttestationOperationPreimages preimages =
        AttestationLifecycleMutationProjection.backupBook(
            AttestationOperationKind.BACKUP_CREATED.wireToken(), acknowledgement);
    return encoded(
        append(
            source,
            AttestationOperationKind.BACKUP_CREATED,
            request(
                AttestationOperationKind.BACKUP_CREATED,
                "fixture-b-05-4",
                preimages.request(),
                0x0150),
            effect(preimages.effect()),
            List.of(A)));
  }

  private static byte[] restore(
      List<AttestationBookOperation> source,
      AttestationBackupAcknowledgement acknowledgement,
      String idempotencyKey) {
    AttestationOperationPreimages preimages =
        AttestationLifecycleMutationProjection.restoreBook(
            AttestationOperationKind.RESTORE_BOOK.wireToken(), acknowledgement);
    return encoded(
        append(
            source,
            AttestationOperationKind.RESTORE_BOOK,
            request(
                AttestationOperationKind.RESTORE_BOOK, idempotencyKey, preimages.request(), 0x0160),
            effect(preimages.effect()),
            List.of(A, B)));
  }

  private static byte[] rekey(List<AttestationBookOperation> source) {
    Instant recordedAt = recordedAt(source.size());
    AttestationOperationPreimages preimages =
        AttestationLifecycleMutationProjection.rekeyBook(
            AttestationOperationKind.REKEY_BOOK.wireToken(),
            BigInteger.TWO,
            recordedAt,
            Optional.empty());
    return encoded(
        append(
            source,
            AttestationOperationKind.REKEY_BOOK,
            request(
                AttestationOperationKind.REKEY_BOOK, "fixture-b-10-4", preimages.request(), 0x0170),
            effect(preimages.effect()),
            List.of(A, B)));
  }

  private static List<AttestationBookOperation> append(
      List<AttestationBookOperation> source,
      AttestationOperationKind kind,
      AttestationPreimage request,
      AttestationPreimage effect,
      List<Signer> signers) {
    List<AttestationBookOperation> operations = new ArrayList<>(source);
    AttestationBookOperation previous = operations.getLast();
    AttestationOperationPayload payload =
        new AttestationOperationPayload(
            operations.getFirst().envelope().payload().bookId(),
            BigInteger.valueOf(operations.size()),
            kind.wireToken(),
            previous.envelope().head(),
            recordedAt(operations.size()),
            AttestationHash.sha256(request.encoded()),
            AttestationHash.sha256(effect.encoded()));
    AttestationEnvelope<AttestationOperationPayload> envelope =
        AttestationEnvelope.of(
            payload,
            signers.stream()
                .map(
                    signer ->
                        new AttestationSignatureEntry(
                            signer.principalId(),
                            signer.keyId(),
                            AttestationEd25519.sign(
                                signer.keyPair().getPrivate(), payload.encoded())))
                .toList());
    operations.add(
        AttestationBookOperation.decode(envelope.encoded(), request.encoded(), effect.encoded()));
    return List.copyOf(operations);
  }

  private static AttestationPreimage request(
      AttestationOperationKind kind,
      String idempotencyKey,
      byte[] projectedRequest,
      int lifecycleRequestTag) {
    return AttestationPreimage.of(
        List.of(command(kind, idempotencyKey), record(projectedRequest, lifecycleRequestTag)));
  }

  private static AttestationPreimage effect(byte[] encoded) {
    return AttestationPreimage.decode(encoded, AttestationAuthorizationFailure.PREIMAGE_INVALID);
  }

  private static AttestationPreimage.Fact command(
      AttestationOperationKind kind, String idempotencyKey) {
    return new AttestationPreimage.Fact(
        0x0100,
        List.of(
            AttestationField.present(AttestationTextFieldValue.token(kind.wireToken())),
            AttestationField.present(AttestationTextFieldValue.text(idempotencyKey)),
            AttestationField.absent(),
            AttestationField.present(AttestationTextFieldValue.token("cli"))));
  }

  private static AttestationPreimage.Fact record(byte[] encoded, int tag) {
    List<AttestationPreimage.Fact> matches =
        effect(encoded).records().stream().filter(record -> record.recordTypeTag() == tag).toList();
    if (matches.size() != 1) {
      throw new IllegalStateException(
          "Lifecycle projection did not produce one expected request record.");
    }
    return matches.getFirst();
  }

  private static Instant recordedAt(int operationOrder) {
    return GENESIS_RECORDED_AT.plusMillis(operationOrder);
  }

  private static byte[] encoded(List<AttestationBookOperation> operations) {
    try {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try (DataOutputStream output = new DataOutputStream(bytes)) {
        output.write(new byte[] {'F', 'G', 'B', 'K', '1'});
        output.writeInt(operations.size());
        for (AttestationBookOperation operation : operations) {
          write(output, operation.envelope().encoded());
          write(output, operation.requestPreimage().encoded());
          write(output, operation.effectPreimage().encoded());
        }
      }
      return bytes.toByteArray();
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Cannot encode deterministic lifecycle corpus source.", exception);
    }
  }

  private static void write(DataOutputStream output, byte[] value) throws IOException {
    output.writeInt(value.length);
    output.write(value);
  }

  private static Signer signer(String principalId, String seed, String spki) {
    try {
      KeyFactory factory = KeyFactory.getInstance("Ed25519");
      PrivateKey privateKey =
          factory.generatePrivate(
              new EdECPrivateKeySpec(NamedParameterSpec.ED25519, HexFormat.of().parseHex(seed)));
      PublicKey publicKey =
          factory.generatePublic(new X509EncodedKeySpec(HexFormat.of().parseHex(spki)));
      return new Signer(
          UUID.fromString(principalId),
          new KeyPair(publicKey, privateKey),
          AttestationEd25519.keyId(publicKey));
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException(
          "The deterministic lifecycle corpus key is unavailable.", exception);
    }
  }

  private record Signer(UUID principalId, KeyPair keyPair, AttestationHash keyId) {}
}
