package dev.erst.fingrind.core.attestation;

import java.math.BigInteger;
import java.util.Objects;
import java.util.UUID;

/** One append-only activation decision for an autonomous CLOSE_PERIOD workflow. */
record AttestationSystemWorkflowPolicy(
    BigInteger acceptedOrder,
    UUID workflowId,
    AttestationSystemWorkflowKind workflowKind,
    boolean active) {
  AttestationSystemWorkflowPolicy {
    acceptedOrder =
        AttestationUnsignedEncoding.requireUnsigned(acceptedOrder, Long.BYTES, "acceptedOrder");
    Objects.requireNonNull(workflowId, "workflowId");
    Objects.requireNonNull(workflowKind, "workflowKind");
  }
}
