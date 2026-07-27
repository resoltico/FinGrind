package dev.erst.fingrind.core.attestation;

import java.util.Objects;

/** Applies universal immutable-preimage checks before chain position or authority is considered. */
final class AttestationOperationProfile {
  private AttestationOperationProfile() {}

  static AttestationVerifiedOperationProvenance requireValid(
      AttestationOperationPayload payload,
      AttestationOperationKind operationKind,
      AttestationPreimage requestPreimage,
      AttestationPreimage effectPreimage) {
    AttestationOperationKind checkedKind = Objects.requireNonNull(operationKind, "operationKind");
    AttestationPreimage checkedRequest = Objects.requireNonNull(requestPreimage, "requestPreimage");
    AttestationPreimage checkedEffect = Objects.requireNonNull(effectPreimage, "effectPreimage");
    AttestationVerifiedOperationProvenance provenance =
        AttestationVerifiedOperationProvenance.verify(
            Objects.requireNonNull(payload, "payload"), checkedRequest);
    if (provenance.sourceChannel() == AttestationSourceChannel.SYSTEM
        && checkedKind != AttestationOperationKind.INTERIM_RESULT_SWEEP
        && checkedKind != AttestationOperationKind.FISCAL_YEAR_CLOSE) {
      throw failure();
    }
    AttestationOperationProfileCatalog.profile(checkedKind)
        .requireTags(checkedRequest, checkedEffect);
    if (checkedKind == AttestationOperationKind.EXECUTE_PLAN) {
      AttestationPlanQualifiedFact.requireValid(checkedRequest, checkedEffect);
    } else if (checkedKind == AttestationOperationKind.INTERIM_RESULT_SWEEP) {
      AttestationInterimResultSweepEffectProfile.requireValid(checkedRequest, checkedEffect);
    } else if (checkedKind == AttestationOperationKind.FISCAL_YEAR_CLOSE) {
      AttestationFiscalYearCloseEffectProfile.requireValid(checkedRequest, checkedEffect);
    } else if (isLifecycleOperation(checkedKind)) {
      AttestationLifecycleEffectProfile.requireValid(checkedKind, checkedRequest, checkedEffect);
      if (checkedKind == AttestationOperationKind.RESTORE_BOOK) {
        AttestationLifecycleEffectProfile.requireRestorePredecessor(
            Objects.requireNonNull(payload, "payload"), checkedRequest);
      } else if (checkedKind == AttestationOperationKind.REKEY_BOOK) {
        AttestationLifecycleEffectProfile.requireRekeyRecordedAt(
            Objects.requireNonNull(payload, "payload"), checkedEffect);
      }
    }
    return provenance;
  }

  static void requireDirectProfile(
      AttestationOperationKind operationKind,
      AttestationPreimage requestPreimage,
      AttestationPreimage effectPreimage) {
    AttestationOperationKind checkedOperationKind =
        Objects.requireNonNull(operationKind, "operationKind");
    if (checkedOperationKind == AttestationOperationKind.EXECUTE_PLAN) {
      throw failure();
    }
    AttestationPreimage checkedRequest = Objects.requireNonNull(requestPreimage, "requestPreimage");
    AttestationPreimage checkedEffect = Objects.requireNonNull(effectPreimage, "effectPreimage");
    AttestationOperationProfileCatalog.profile(checkedOperationKind)
        .requireTags(checkedRequest, checkedEffect);
    if (checkedOperationKind == AttestationOperationKind.INTERIM_RESULT_SWEEP) {
      AttestationInterimResultSweepEffectProfile.requireValid(checkedRequest, checkedEffect);
    } else if (checkedOperationKind == AttestationOperationKind.FISCAL_YEAR_CLOSE) {
      AttestationFiscalYearCloseEffectProfile.requireValid(checkedRequest, checkedEffect);
    } else if (isLifecycleOperation(checkedOperationKind)) {
      AttestationLifecycleEffectProfile.requireValid(
          checkedOperationKind, checkedRequest, checkedEffect);
    }
  }

  private static boolean isLifecycleOperation(AttestationOperationKind operationKind) {
    return operationKind == AttestationOperationKind.BACKUP_CREATED
        || operationKind == AttestationOperationKind.RESTORE_BOOK
        || operationKind == AttestationOperationKind.REKEY_BOOK;
  }

  static AttestationAuthorizationException failure() {
    return new AttestationAuthorizationException(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID);
  }
}
