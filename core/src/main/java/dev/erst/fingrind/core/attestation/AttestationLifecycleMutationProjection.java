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
                            AttestationPreimageProjectionFields.instant(checkedRekeyedAt)))))
            .encoded());
  }

  /** Projects one exact public credential-enrollment operation. */
  static AttestationOperationPreimages enrollKey(AttestationRegistryMutation.EnrollKey mutation) {
    AttestationRegistryMutation.EnrollKey checked = Objects.requireNonNull(mutation, "mutation");
    return binding(
        checked.operationKind().wireToken(),
        checked.principalId(),
        checked.credential(),
        "enroll",
        checked.purpose().token(),
        Optional.empty(),
        Optional.empty());
  }

  /** Projects one exact public credential-rollover operation. */
  static AttestationOperationPreimages rolloverKey(
      AttestationRegistryMutation.RolloverKey mutation) {
    AttestationRegistryMutation.RolloverKey checked = Objects.requireNonNull(mutation, "mutation");
    AttestationHash predecessorKeyId = AttestationHash.of(checked.predecessorCredential().keyId());
    return binding(
        checked.operationKind().wireToken(),
        checked.principalId(),
        checked.credential(),
        "rollover",
        checked.purpose().token(),
        Optional.of(predecessorKeyId),
        Optional.of(AttestationCredentialRetirementState.SUPERSEDED));
  }

  /** Projects one exact public revoked credential-retirement operation. */
  static AttestationOperationPreimages revokeKey(AttestationRegistryMutation.RevokeKey mutation) {
    AttestationRegistryMutation.RevokeKey checked = Objects.requireNonNull(mutation, "mutation");
    AttestationHash keyId = AttestationHash.of(checked.credential().keyId());
    return new AttestationOperationPreimages(
        AttestationPreimage.of(
                List.of(
                    command(checked.operationKind().wireToken()),
                    new AttestationPreimage.Fact(
                        0x0185,
                        List.of(
                            present(AttestationBinaryFieldValue.hash(keyId)),
                            present(AttestationBinaryFieldValue.uuid(checked.principalId())),
                            present(
                                AttestationTextFieldValue.token(
                                    AttestationCredentialRetirementState.REVOKED.token())),
                            checked
                                .reason()
                                .<AttestationField>map(AttestationLifecycleMutationProjection::text)
                                .orElseGet(AttestationField::absent)))))
            .encoded(),
        AttestationPreimage.of(
                List.of(
                    new AttestationPreimage.Fact(
                        0x0009,
                        List.of(
                            present(AttestationNumericFieldValue.mutation(0)),
                            present(AttestationBinaryFieldValue.hash(keyId)),
                            present(AttestationBinaryFieldValue.uuid(checked.principalId())),
                            present(
                                AttestationTextFieldValue.token(
                                    AttestationCredentialRetirementState.REVOKED.token())),
                            checked
                                .reason()
                                .<AttestationField>map(AttestationLifecycleMutationProjection::text)
                                .orElseGet(AttestationField::absent)))))
            .encoded());
  }

  /** Projects one exact public quorum, principal-grant, and system-workflow policy mutation. */
  static AttestationOperationPreimages alterPolicy(
      AttestationRegistryMutation.AlterPolicy mutation) {
    AttestationRegistryMutation.AlterPolicy checked = Objects.requireNonNull(mutation, "mutation");
    List<AttestationPreimage.Fact> requestRecords = new java.util.ArrayList<>();
    requestRecords.add(command(checked.operationKind().wireToken()));
    List<AttestationPreimage.Fact> effectRecords = new java.util.ArrayList<>();
    for (AttestationRegistryMutation.PolicyRule rule : checked.policyRules()) {
      requestRecords.add(
          new AttestationPreimage.Fact(
              0x0182,
              List.of(
                  present(AttestationTextFieldValue.token(rule.capability().token())),
                  present(AttestationNumericFieldValue.unsigned16(rule.quorum())))));
      effectRecords.add(
          new AttestationPreimage.Fact(
              0x0005,
              List.of(
                  present(AttestationNumericFieldValue.mutation(0)),
                  present(AttestationTextFieldValue.token(rule.capability().token())),
                  present(AttestationNumericFieldValue.unsigned16(rule.quorum())))));
    }
    for (AttestationRegistryMutation.CapabilityGrant grant : checked.capabilityGrants()) {
      requestRecords.add(
          new AttestationPreimage.Fact(
              0x0183,
              List.of(
                  present(AttestationBinaryFieldValue.uuid(grant.principalId())),
                  present(AttestationTextFieldValue.token(grant.capability().token())),
                  present(AttestationTextFieldValue.token(grant.state().token())))));
      effectRecords.add(
          new AttestationPreimage.Fact(
              0x0003,
              List.of(
                  present(AttestationNumericFieldValue.mutation(0)),
                  present(AttestationBinaryFieldValue.uuid(grant.principalId())),
                  present(AttestationTextFieldValue.token(grant.capability().token())),
                  present(AttestationTextFieldValue.token(grant.state().token())))));
    }
    for (AttestationRegistryMutation.SystemWorkflowPolicy policy :
        checked.systemWorkflowPolicies()) {
      requestRecords.add(
          new AttestationPreimage.Fact(
              0x0184,
              List.of(
                  present(AttestationBinaryFieldValue.uuid(policy.workflowId())),
                  present(AttestationTextFieldValue.token(policy.workflowKind().wireToken())),
                  text(policy.resultHoldingAccountCode()),
                  optionalText(policy.capitalAccountCode()),
                  optionalText(policy.retainedResultAccountCode()),
                  present(AttestationNumericFieldValue.booleanValue(policy.active())))));
      effectRecords.add(
          new AttestationPreimage.Fact(
              0x0008,
              List.of(
                  present(AttestationNumericFieldValue.mutation(0)),
                  present(AttestationBinaryFieldValue.uuid(policy.workflowId())),
                  present(AttestationTextFieldValue.token(policy.workflowKind().wireToken())),
                  text(policy.resultHoldingAccountCode()),
                  optionalText(policy.capitalAccountCode()),
                  optionalText(policy.retainedResultAccountCode()),
                  present(AttestationNumericFieldValue.booleanValue(policy.active())))));
    }
    return new AttestationOperationPreimages(
        AttestationPreimage.of(requestRecords).encoded(),
        AttestationPreimage.of(effectRecords).encoded());
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

  static AttestationOperationPreimages binding(
      String operationKind,
      java.util.UUID principalId,
      AttestationPublicCredential credential,
      String action,
      String purpose,
      Optional<AttestationHash> predecessorKeyId,
      Optional<AttestationCredentialRetirementState> predecessorRetirementState) {
    AttestationPublicCredential checkedCredential =
        Objects.requireNonNull(credential, "credential");
    AttestationHash keyId = AttestationHash.of(checkedCredential.keyId());
    AttestationField predecessor =
        Objects.requireNonNull(predecessorKeyId, "predecessorKeyId")
            .<AttestationField>map(value -> present(AttestationBinaryFieldValue.hash(value)))
            .orElseGet(AttestationField::absent);
    if (predecessorKeyId.isPresent() != predecessorRetirementState.isPresent()) {
      throw new IllegalArgumentException(
          "A credential rollover must project its predecessor retirement in the same operation.");
    }
    List<AttestationPreimage.Fact> requestFacts = new java.util.ArrayList<>();
    requestFacts.add(command(operationKind));
    requestFacts.add(
        new AttestationPreimage.Fact(
            0x0180,
            List.of(
                present(AttestationBinaryFieldValue.uuid(principalId)),
                present(AttestationBinaryFieldValue.hash(keyId)),
                present(AttestationTextFieldValue.token(action)),
                present(AttestationBinaryFieldValue.spki(checkedCredential.spki())),
                present(AttestationTextFieldValue.token(purpose)),
                predecessor)));
    List<AttestationPreimage.Fact> effectFacts = new java.util.ArrayList<>();
    effectFacts.add(
        new AttestationPreimage.Fact(
            0x0002,
            List.of(
                present(AttestationNumericFieldValue.mutation(0)),
                present(AttestationBinaryFieldValue.uuid(principalId)),
                present(AttestationBinaryFieldValue.hash(keyId)),
                present(AttestationTextFieldValue.token(action)),
                present(AttestationBinaryFieldValue.spki(checkedCredential.spki())),
                present(AttestationTextFieldValue.token(purpose)),
                predecessor)));
    if (predecessorKeyId.isPresent()) {
      AttestationHash checkedPredecessor = predecessorKeyId.orElseThrow();
      AttestationCredentialRetirementState checkedState = predecessorRetirementState.orElseThrow();
      requestFacts.add(
          new AttestationPreimage.Fact(
              0x0185,
              List.of(
                  present(AttestationBinaryFieldValue.hash(checkedPredecessor)),
                  present(AttestationBinaryFieldValue.uuid(principalId)),
                  present(AttestationTextFieldValue.token(checkedState.token())),
                  AttestationField.absent())));
      effectFacts.add(
          new AttestationPreimage.Fact(
              0x0009,
              List.of(
                  present(AttestationNumericFieldValue.mutation(0)),
                  present(AttestationBinaryFieldValue.hash(checkedPredecessor)),
                  present(AttestationBinaryFieldValue.uuid(principalId)),
                  present(AttestationTextFieldValue.token(checkedState.token())),
                  AttestationField.absent())));
    }
    return new AttestationOperationPreimages(
        AttestationPreimage.of(requestFacts).encoded(),
        AttestationPreimage.of(effectFacts).encoded());
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

  private static AttestationField optionalText(@org.jspecify.annotations.Nullable String value) {
    return value == null ? AttestationField.absent() : text(value);
  }
}
