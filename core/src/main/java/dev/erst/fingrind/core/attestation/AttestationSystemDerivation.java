package dev.erst.fingrind.core.attestation;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

/** Recomputes non-discretionary system-close date fields after position-resolved authorization. */
final class AttestationSystemDerivation {
  private static final int REQUEST_POSTING = 0x0120;
  private static final int REQUEST_PERIOD_CLOSE = 0x0140;
  private static final int EFFECT_POSTING = 0x0020;
  private static final String PERIOD_CLOSE = "period-close";
  private static final String SYSTEM = "system";

  private AttestationSystemDerivation() {}

  static AttestationPeriodCloseHistory requireValid(
      AttestationPeriodCloseHistory closeHistory,
      AttestationRegistry registry,
      AttestationOperationPayload payload,
      AttestationOperationKind operationKind,
      AttestationVerifiedOperationProvenance provenance,
      AttestationPreimage requestPreimage,
      AttestationPreimage effectPreimage) {
    AttestationPeriodCloseHistory checkedCloseHistory =
        Objects.requireNonNull(closeHistory, "closeHistory");
    AttestationRegistry checkedRegistry = Objects.requireNonNull(registry, "registry");
    Objects.requireNonNull(payload, "payload");
    Objects.requireNonNull(operationKind, "operationKind");
    AttestationVerifiedOperationProvenance checkedProvenance =
        Objects.requireNonNull(provenance, "provenance");
    if (checkedProvenance.sourceChannel() != AttestationSourceChannel.SYSTEM) {
      return checkedCloseHistory;
    }
    AttestationSystemWorkflowPolicy workflowPolicy =
        requireWorkflow(checkedRegistry, payload, operationKind, checkedProvenance);
    requireDerivedShape(payload, operationKind, workflowPolicy, requestPreimage, effectPreimage);
    return checkedCloseHistory.acceptSystem(operationKind, payload, effectPreimage);
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
    LocalDate recordedOn = payload.recordedAt().atZone(ZoneOffset.UTC).toLocalDate();
    AttestationPreimage.Fact periodClose = exactlyOne(requestPreimage, REQUEST_PERIOD_CLOSE);
    AttestationPreimage.Fact posting = exactlyOne(requestPreimage, REQUEST_POSTING);
    LocalDate effectiveTo = date(periodClose, 2);
    boolean recordedAtMatchesEffectiveTo =
        workflowPolicy.workflowKind() != AttestationSystemWorkflowKind.INTERIM_RESULT_SWEEP
            || recordedOn.minusDays(1).equals(effectiveTo);
    List<AttestationPreimage.Fact> effectPostings =
        effectPostings(effectPreimage, expectedCloseKind);
    List<Boolean> derivedFields =
        List.of(
            expectedCloseKind.equals(token(periodClose, 0)),
            recordedAtMatchesEffectiveTo,
            matchesWorkflowAccounts(periodClose, workflowPolicy),
            expectedCloseKind.equals(token(posting, 1)),
            effectiveTo.equals(date(posting, 2)),
            PERIOD_CLOSE.equals(token(posting, 3)));
    if (derivedFields.contains(false)) {
      throw failure();
    }
    for (AttestationPreimage.Fact effectPosting : effectPostings) {
      List<Boolean> postingFields =
          List.of(
              expectedCloseKind.equals(token(effectPosting, 3)),
              PERIOD_CLOSE.equals(token(effectPosting, 4)),
              expectedCloseKind.equals(token(effectPosting, 5)),
              effectiveTo.equals(date(effectPosting, 6)),
              SYSTEM.equals(token(effectPosting, 12)));
      if (postingFields.contains(false)) {
        throw failure();
      }
    }
  }

  private static List<AttestationPreimage.Fact> effectPostings(
      AttestationPreimage effectPreimage, String expectedCloseKind) {
    List<AttestationPreimage.Fact> postings =
        AttestationPreimageFields.records(effectPreimage, EFFECT_POSTING).stream()
            .filter(posting -> expectedCloseKind.equals(token(posting, 3)))
            .toList();
    if (postings.isEmpty()) {
      throw failure();
    }
    return postings;
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
