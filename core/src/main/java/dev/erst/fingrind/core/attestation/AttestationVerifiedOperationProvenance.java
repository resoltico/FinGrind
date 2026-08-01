package dev.erst.fingrind.core.attestation;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Request provenance recomputed from the immutable preimage bound by one operation payload. */
final class AttestationVerifiedOperationProvenance {
  private static final int COMMAND_RECORD_TYPE = 0x0100;
  private static final int SYSTEM_WORKFLOW_RUN_RECORD_TYPE = 0x0141;

  private final byte[] payload;
  private final AttestationSourceChannel sourceChannel;
  private final @Nullable UUID systemWorkflowId;

  private AttestationVerifiedOperationProvenance(
      byte[] payload, AttestationSourceChannel sourceChannel, @Nullable UUID systemWorkflowId) {
    this.payload = payload.clone();
    this.sourceChannel = sourceChannel;
    this.systemWorkflowId = systemWorkflowId;
  }

  static AttestationVerifiedOperationProvenance verify(
      AttestationOperationPayload payload, AttestationPreimage requestPreimage) {
    return verifyWithFailure(
        payload, requestPreimage, AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID);
  }

  static AttestationVerifiedOperationProvenance verifyGenesis(
      AttestationOperationPayload payload, AttestationPreimage requestPreimage) {
    return verifyWithFailure(
        payload, requestPreimage, AttestationAuthorizationFailure.GENESIS_INVALID);
  }

  private static AttestationVerifiedOperationProvenance verifyWithFailure(
      AttestationOperationPayload payload,
      AttestationPreimage requestPreimage,
      AttestationAuthorizationFailure failure) {
    AttestationOperationPayload checkedPayload = Objects.requireNonNull(payload, "payload");
    AttestationPreimage checkedPreimage =
        Objects.requireNonNull(requestPreimage, "requestPreimage");
    if (!checkedPayload.requestDigest().equals(AttestationHash.sha256(checkedPreimage.encoded()))) {
      throw failure(failure);
    }

    List<AttestationPreimage.Fact> commandRecords =
        AttestationPreimageFields.records(checkedPreimage, COMMAND_RECORD_TYPE);
    if (commandRecords.size() != 1) {
      throw failure(failure);
    }
    AttestationPreimage.Fact command = commandRecords.getFirst();
    if (!checkedPayload
        .operationKind()
        .equals(AttestationPreimageValueReader.token(command, 0, failure))) {
      throw failure(failure);
    }
    AttestationSourceChannel sourceChannel =
        AttestationSourceChannel.forWireToken(
            AttestationPreimageValueReader.token(command, 3, failure), failure);
    List<AttestationPreimage.Fact> workflowRecords =
        AttestationPreimageFields.records(checkedPreimage, SYSTEM_WORKFLOW_RUN_RECORD_TYPE);
    if (sourceChannel == AttestationSourceChannel.CLI) {
      if (!workflowRecords.isEmpty()) {
        throw failure(failure);
      }
      return new AttestationVerifiedOperationProvenance(
          checkedPayload.encoded(), sourceChannel, null);
    }
    if (workflowRecords.size() != 1) {
      throw failure(failure);
    }
    return new AttestationVerifiedOperationProvenance(
        checkedPayload.encoded(),
        sourceChannel,
        AttestationPreimageValueReader.uuid(workflowRecords.getFirst(), 0, failure));
  }

  boolean matches(AttestationOperationPayload candidate) {
    return Arrays.equals(payload, Objects.requireNonNull(candidate, "candidate").encoded());
  }

  AttestationSourceChannel sourceChannel() {
    return sourceChannel;
  }

  @Nullable UUID systemWorkflowId() {
    return systemWorkflowId;
  }

  private static AttestationAuthorizationException failure(
      AttestationAuthorizationFailure failure) {
    return new AttestationAuthorizationException(failure);
  }
}
