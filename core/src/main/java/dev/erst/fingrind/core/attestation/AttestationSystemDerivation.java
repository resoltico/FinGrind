package dev.erst.fingrind.core.attestation;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

/** Recomputes the non-discretionary system-close date fields after historical authorization. */
final class AttestationSystemDerivation {
  private static final int REQUEST_POSTING = 0x0120;
  private static final int REQUEST_PERIOD_CLOSE = 0x0140;
  private static final int EFFECT_POSTING = 0x0020;

  private AttestationSystemDerivation() {}

  static void requireValid(
      AttestationRegistry registry,
      AttestationOperationPayload payload,
      AttestationOperationKind operationKind,
      AttestationVerifiedOperationProvenance provenance,
      AttestationPreimage requestPreimage,
      AttestationPreimage effectPreimage) {
    AttestationRegistry checkedRegistry = Objects.requireNonNull(registry, "registry");
    Objects.requireNonNull(payload, "payload");
    Objects.requireNonNull(operationKind, "operationKind");
    AttestationVerifiedOperationProvenance checkedProvenance =
        Objects.requireNonNull(provenance, "provenance");
    if (checkedProvenance.sourceChannel() != AttestationSourceChannel.SYSTEM) {
      return;
    }
    AttestationSystemWorkflowPolicy workflowPolicy =
        requireWorkflow(checkedRegistry, payload, operationKind, checkedProvenance);
    requireDerivedShape(payload, operationKind, workflowPolicy, requestPreimage, effectPreimage);
  }

  private static AttestationSystemWorkflowPolicy requireWorkflow(
      AttestationRegistry registry,
      AttestationOperationPayload payload,
      AttestationOperationKind operationKind,
      AttestationVerifiedOperationProvenance provenance) {
    return registry
        .activeSystemWorkflow(
            Objects.requireNonNull(provenance.systemWorkflowId(), "systemWorkflowId"),
            workflowKind(operationKind),
            payload.operationOrder().subtract(java.math.BigInteger.ONE))
        .orElseThrow(AttestationSystemDerivation::failure);
  }

  private static void requireDerivedShape(
      AttestationOperationPayload payload,
      AttestationOperationKind operationKind,
      AttestationSystemWorkflowPolicy workflowPolicy,
      AttestationPreimage requestPreimage,
      AttestationPreimage effectPreimage) {
    String expectedCloseKind = workflowKind(operationKind).wireToken();
    LocalDate expectedEffectiveTo =
        payload.recordedAt().atZone(ZoneOffset.UTC).toLocalDate().minusDays(1);
    AttestationPreimage.Fact periodClose = exactlyOne(requestPreimage, REQUEST_PERIOD_CLOSE);
    AttestationPreimage.Fact posting = exactlyOne(requestPreimage, REQUEST_POSTING);
    AttestationPreimage.Fact effectPosting = exactlyOne(effectPreimage, EFFECT_POSTING);
    List<Boolean> derivedFields =
        List.of(
            expectedCloseKind.equals(token(periodClose, 0)),
            expectedEffectiveTo.equals(date(periodClose, 2)),
            matchesWorkflowAccounts(periodClose, workflowPolicy),
            expectedCloseKind.equals(token(posting, 1)),
            expectedEffectiveTo.equals(date(posting, 2)),
            expectedCloseKind.equals(token(effectPosting, 3)),
            expectedCloseKind.equals(token(effectPosting, 5)),
            expectedEffectiveTo.equals(date(effectPosting, 6)));
    if (derivedFields.contains(false)) {
      throw failure();
    }
  }

  private static AttestationSystemWorkflowKind workflowKind(
      AttestationOperationKind operationKind) {
    return switch (operationKind) {
      case INTERIM_RESULT_SWEEP -> AttestationSystemWorkflowKind.INTERIM_RESULT_SWEEP;
      case FISCAL_YEAR_CLOSE -> AttestationSystemWorkflowKind.FISCAL_YEAR_CLOSE;
      default -> throw failure();
    };
  }

  private static boolean matchesWorkflowAccounts(
      AttestationPreimage.Fact periodClose, AttestationSystemWorkflowPolicy workflowPolicy) {
    if (!workflowPolicy.resultHoldingAccountCode().equals(text(periodClose, 4))) {
      return false;
    }
    if (workflowPolicy.workflowKind() == AttestationSystemWorkflowKind.INTERIM_RESULT_SWEEP) {
      return absent(periodClose, 3) && absent(periodClose, 5) && absent(periodClose, 6);
    }
    return Objects.requireNonNull(workflowPolicy.capitalAccountCode(), "capitalAccountCode")
            .equals(text(periodClose, 5))
        && Objects.requireNonNull(
                workflowPolicy.retainedResultAccountCode(), "retainedResultAccountCode")
            .equals(text(periodClose, 6));
  }

  private static AttestationPreimage.Fact exactlyOne(AttestationPreimage preimage, int tag) {
    List<AttestationPreimage.Fact> records = AttestationPreimageFields.records(preimage, tag);
    if (records.size() != 1) {
      throw failure();
    }
    return records.getFirst();
  }

  private static String token(AttestationPreimage.Fact fact, int fieldIndex) {
    return AttestationPreimageValueReader.token(
        fact, fieldIndex, AttestationAuthorizationFailure.SYSTEM_DERIVATION_INVALID);
  }

  private static LocalDate date(AttestationPreimage.Fact fact, int fieldIndex) {
    return AttestationPreimageValueReader.date(
        fact, fieldIndex, AttestationAuthorizationFailure.SYSTEM_DERIVATION_INVALID);
  }

  private static String text(AttestationPreimage.Fact fact, int fieldIndex) {
    return AttestationPreimageValueReader.text(
        fact, fieldIndex, AttestationAuthorizationFailure.SYSTEM_DERIVATION_INVALID);
  }

  private static boolean absent(AttestationPreimage.Fact fact, int fieldIndex) {
    return !AttestationPreimageFields.requireField(fact, fieldIndex).isPresent();
  }

  private static AttestationAuthorizationException failure() {
    return new AttestationAuthorizationException(
        AttestationAuthorizationFailure.SYSTEM_DERIVATION_INVALID);
  }
}
