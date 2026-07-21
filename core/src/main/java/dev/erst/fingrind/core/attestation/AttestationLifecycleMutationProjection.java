package dev.erst.fingrind.core.attestation;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Projects the catalog-complete immutable facts for backup, restore, and rekey operations. */
public final class AttestationLifecycleMutationProjection {
  private static final String CLI = "cli";

  private AttestationLifecycleMutationProjection() {}

  /** Projects the one immutable acknowledgement that indexes a published backup artifact. */
  public static AttestationOperationPreimages backupBook(
      String operationKind, AttestationBackupAcknowledgement acknowledgement) {
    String checkedOperationKind = Objects.requireNonNull(operationKind, "operationKind");
    AttestationBackupAcknowledgement checked =
        Objects.requireNonNull(acknowledgement, "acknowledgement");
    return new AttestationOperationPreimages(
        AttestationPreimage.of(
                List.of(command(checkedOperationKind), backupRequest(0x0150, checked)))
            .encoded(),
        AttestationPreimage.of(List.of(backupEffect(checked))).encoded());
  }

  /** Projects the restoration-derived continuation that preserves the original book identity. */
  public static AttestationOperationPreimages restoreBook(
      String operationKind, AttestationBackupAcknowledgement acknowledgement) {
    String checkedOperationKind = Objects.requireNonNull(operationKind, "operationKind");
    AttestationBackupAcknowledgement checked =
        Objects.requireNonNull(acknowledgement, "acknowledgement");
    return new AttestationOperationPreimages(
        AttestationPreimage.of(
                List.of(command(checkedOperationKind), backupRequest(0x0160, checked)))
            .encoded(),
        AttestationPreimage.of(
                List.of(
                    new AttestationPreimage.Fact(
                        0x00A0,
                        List.of(
                            present(AttestationNumericFieldValue.mutation(0)),
                            present(AttestationBinaryFieldValue.uuid(checked.backupId())),
                            present(
                                AttestationBinaryFieldValue.hash(
                                    AttestationHash.of(checked.backupArtifactDigest()))),
                            present(AttestationNumericFieldValue.unsigned64(checked.sourceOrder())),
                            present(AttestationNumericFieldValue.booleanValue(true))))))
            .encoded());
  }

  /** Projects one attested secret-material rotation without including secret material itself. */
  public static AttestationOperationPreimages rekeyBook(
      String operationKind, BigInteger keyEpoch, Instant rekeyedAt, Optional<String> reason) {
    String checkedOperationKind = Objects.requireNonNull(operationKind, "operationKind");
    BigInteger checkedKeyEpoch = Objects.requireNonNull(keyEpoch, "keyEpoch");
    Instant checkedRekeyedAt = Objects.requireNonNull(rekeyedAt, "rekeyedAt");
    Optional<String> checkedReason = Objects.requireNonNull(reason, "reason");
    return new AttestationOperationPreimages(
        AttestationPreimage.of(
                List.of(
                    command(checkedOperationKind),
                    new AttestationPreimage.Fact(
                        0x0170,
                        List.of(
                            present(AttestationNumericFieldValue.unsigned64(checkedKeyEpoch)),
                            checkedReason
                                .<AttestationField>map(AttestationLifecycleMutationProjection::text)
                                .orElseGet(AttestationField::absent)))))
            .encoded(),
        AttestationPreimage.of(
                List.of(
                    new AttestationPreimage.Fact(
                        0x0007,
                        List.of(
                            present(AttestationNumericFieldValue.mutation(1)),
                            present(AttestationNumericFieldValue.unsigned64(checkedKeyEpoch)),
                            present(AttestationTextFieldValue.instant(checkedRekeyedAt))))))
            .encoded());
  }

  private static AttestationPreimage.Fact command(String operationKind) {
    return new AttestationPreimage.Fact(
        0x0100,
        List.of(
            present(AttestationTextFieldValue.token(operationKind)),
            AttestationField.absent(),
            AttestationField.absent(),
            present(AttestationTextFieldValue.token(CLI))));
  }

  private static AttestationPreimage.Fact backupRequest(
      int recordTypeTag, AttestationBackupAcknowledgement acknowledgement) {
    return new AttestationPreimage.Fact(
        recordTypeTag,
        List.of(
            present(AttestationBinaryFieldValue.uuid(acknowledgement.backupId())),
            present(
                AttestationBinaryFieldValue.hash(
                    AttestationHash.of(acknowledgement.backupArtifactDigest()))),
            present(AttestationNumericFieldValue.unsigned64(acknowledgement.sourceOrder())),
            present(
                AttestationBinaryFieldValue.hash(
                    AttestationHash.of(acknowledgement.sourceOperationHead())))));
  }

  private static AttestationPreimage.Fact backupEffect(
      AttestationBackupAcknowledgement acknowledgement) {
    return new AttestationPreimage.Fact(
        0x0006,
        List.of(
            present(AttestationNumericFieldValue.mutation(0)),
            present(AttestationBinaryFieldValue.uuid(acknowledgement.backupId())),
            present(
                AttestationBinaryFieldValue.hash(
                    AttestationHash.of(acknowledgement.backupArtifactDigest()))),
            present(AttestationNumericFieldValue.unsigned64(acknowledgement.sourceOrder())),
            present(
                AttestationBinaryFieldValue.hash(
                    AttestationHash.of(acknowledgement.sourceOperationHead())))));
  }

  private static AttestationField present(AttestationFieldValue value) {
    return AttestationField.present(value);
  }

  private static AttestationField text(String value) {
    return present(AttestationTextFieldValue.text(value));
  }
}
