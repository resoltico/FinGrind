package dev.erst.fingrind.core.attestation;

import java.util.Map;
import java.util.Objects;

/** Applies universal immutable-preimage checks before chain position or authority is considered. */
final class AttestationOperationProfile {
  private static final Map<Integer, Integer> REQUIRED_REQUEST_BY_EFFECT =
      Map.ofEntries(
          Map.entry(0x0030, 0x0128),
          Map.entry(0x0050, 0x0130),
          Map.entry(0x0051, 0x0130),
          Map.entry(0x0060, 0x0131),
          Map.entry(0x0061, 0x0131),
          Map.entry(0x0062, 0x0131),
          Map.entry(0x0070, 0x0132),
          Map.entry(0x0071, 0x0132),
          Map.entry(0x0072, 0x0132),
          Map.entry(0x0080, 0x0133),
          Map.entry(0x0081, 0x0133),
          Map.entry(0x0082, 0x0133),
          Map.entry(0x0090, 0x0134),
          Map.entry(0x0092, 0x0134));

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
    requireNoOrphanLifecycleEffect(checkedRequest, checkedEffect);
    return provenance;
  }

  private static void requireNoOrphanLifecycleEffect(
      AttestationPreimage requestPreimage, AttestationPreimage effectPreimage) {
    for (Map.Entry<Integer, Integer> relation : REQUIRED_REQUEST_BY_EFFECT.entrySet()) {
      if (!AttestationPreimageFields.records(effectPreimage, relation.getKey()).isEmpty()
          && AttestationPreimageFields.records(requestPreimage, relation.getValue()).isEmpty()) {
        throw failure();
      }
    }
  }

  static AttestationAuthorizationException failure() {
    return new AttestationAuthorizationException(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID);
  }
}
