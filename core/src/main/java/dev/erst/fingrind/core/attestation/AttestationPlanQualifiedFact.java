package dev.erst.fingrind.core.attestation;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Encodes aggregate execute-plan facts and reconstructs their source-step child bundles. */
final class AttestationPlanQualifiedFact {
  static final int REQUEST_RECORD_TYPE_TAG = 0x0186;
  static final int EFFECT_RECORD_TYPE_TAG = 0x00A1;
  private static final int EMBEDDED_VALUE_FIXED_OVERHEAD =
      Integer.BYTES
          + 2 * Short.BYTES
          + (Byte.BYTES + Integer.BYTES)
          + (Byte.BYTES + Short.BYTES)
          + 2 * (Byte.BYTES + Integer.BYTES);

  private AttestationPlanQualifiedFact() {}

  /** Returns the largest one embedded field can carry after fixed wrapper framing. */
  static int maximumEmbeddedValueByteCount() {
    return AttestationPreimage.maximumEncodedByteCount() - EMBEDDED_VALUE_FIXED_OVERHEAD;
  }

  static AttestationPreimage.Fact requestFact(
      int sourceStepOrder, AttestationPreimage.Fact childFact) {
    return wrap(REQUEST_RECORD_TYPE_TAG, sourceStepOrder, childFact);
  }

  static AttestationPreimage.Fact effectFact(
      int sourceStepOrder, AttestationPreimage.Fact childFact) {
    return wrap(EFFECT_RECORD_TYPE_TAG, sourceStepOrder, childFact);
  }

  /** Verifies every complete child bundle represented by the aggregate execute-plan preimages. */
  static void requireValid(
      AttestationPreimage requestPreimage, AttestationPreimage effectPreimage) {
    Map<Integer, List<AttestationPreimage.Fact>> requestFacts =
        factsBySourceStep(requestPreimage, QualifiedFactCategory.REQUEST);
    Map<Integer, List<AttestationPreimage.Fact>> effectFacts =
        factsBySourceStep(effectPreimage, QualifiedFactCategory.EFFECT);
    if (!requestFacts.keySet().equals(effectFacts.keySet())) {
      throw failure();
    }
    for (int sourceStepOrder : orderedSourceStepOrders(requestFacts)) {
      AttestationPreimage childRequest =
          preimage(Objects.requireNonNull(requestFacts.get(sourceStepOrder)));
      AttestationPreimage childEffect =
          preimage(Objects.requireNonNull(effectFacts.get(sourceStepOrder)));
      AttestationPlanChildMutationProfile.requireValid(childRequest, childEffect);
    }
  }

  /** Returns original posting-effect facts after their enclosing aggregate evidence is verified. */
  static List<AttestationPreimage.Fact> effectFacts(AttestationPreimage effectPreimage) {
    Map<Integer, List<AttestationPreimage.Fact>> factsBySourceStep =
        factsBySourceStep(effectPreimage, QualifiedFactCategory.EFFECT);
    return orderedSourceStepOrders(factsBySourceStep).stream()
        .flatMap(
            sourceStepOrder ->
                Objects.requireNonNull(factsBySourceStep.get(sourceStepOrder)).stream())
        .toList();
  }

  private static AttestationPreimage.Fact wrap(
      int wrapperRecordTypeTag, int sourceStepOrder, AttestationPreimage.Fact childFact) {
    if (sourceStepOrder < 0) {
      throw new IllegalArgumentException("sourceStepOrder must not be negative.");
    }
    AttestationPreimage.Fact checkedChildFact = Objects.requireNonNull(childFact, "childFact");
    byte[] childRecordSortKey = checkedChildFact.encodedSortKey();
    byte[] childRecord = checkedChildFact.encoded();
    if ((long) childRecordSortKey.length + childRecord.length > maximumEmbeddedValueByteCount()) {
      throw new IllegalArgumentException(
          "A plan-qualified child record and its sort key exceed the aggregate preimage budget.");
    }
    return new AttestationPreimage.Fact(
        wrapperRecordTypeTag,
        List.of(
            AttestationPreimageProjectionFields.unsigned32(sourceStepOrder),
            AttestationField.present(
                AttestationNumericFieldValue.unsigned16(checkedChildFact.recordTypeTag())),
            AttestationField.present(AttestationBinaryFieldValue.embedded(childRecordSortKey)),
            AttestationField.present(AttestationBinaryFieldValue.embedded(childRecord))));
  }

  private static Map<Integer, List<AttestationPreimage.Fact>> factsBySourceStep(
      AttestationPreimage preimage, QualifiedFactCategory category) {
    Map<Integer, List<AttestationPreimage.Fact>> factsBySourceStep = mutableFactsBySourceStep();
    for (AttestationPreimage.Fact wrapper :
        AttestationPreimageFields.records(preimage, category.wrapperRecordTypeTag())) {
      appendQualifiedFact(factsBySourceStep, decode(wrapper, category));
    }
    Map<Integer, List<AttestationPreimage.Fact>> canonicalFactsBySourceStep =
        mutableFactsBySourceStep();
    factsBySourceStep.forEach(
        (sourceStepOrder, facts) ->
            canonicalFactsBySourceStep.put(sourceStepOrder, List.copyOf(facts)));
    return Map.copyOf(canonicalFactsBySourceStep);
  }

  private static void appendQualifiedFact(
      Map<Integer, List<AttestationPreimage.Fact>> factsBySourceStep, QualifiedFact qualified) {
    factsBySourceStep
        .computeIfAbsent(qualified.sourceStepOrder(), ignored -> new ArrayList<>())
        .add(qualified.childFact());
  }

  private static List<Integer> orderedSourceStepOrders(
      Map<Integer, List<AttestationPreimage.Fact>> factsBySourceStep) {
    return factsBySourceStep.keySet().stream().sorted().toList();
  }

  private static Map<Integer, List<AttestationPreimage.Fact>> mutableFactsBySourceStep() {
    return new HashMap<>();
  }

  private static QualifiedFact decode(
      AttestationPreimage.Fact wrapper, QualifiedFactCategory category) {
    AttestationAuthorizationFailure failure =
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID;
    BigInteger sourceStepOrder = AttestationPreimageValueReader.unsigned32(wrapper, 0, failure);
    int childRecordType = AttestationPreimageValueReader.unsigned16(wrapper, 1, failure);
    byte[] childRecordSortKey = AttestationPreimageValueReader.embedded(wrapper, 2, failure);
    AttestationPreimage.Fact childFact =
        AttestationPreimage.decodeFact(
            AttestationPreimageValueReader.embedded(wrapper, 3, failure), failure);
    if (sourceStepOrder.bitLength() > Integer.SIZE - 1
        || childFact.recordTypeTag() != childRecordType
        || !Arrays.equals(childFact.encodedSortKey(), childRecordSortKey)
        || !category.accepts(childFact)) {
      throw failure();
    }
    return new QualifiedFact(sourceStepOrder.intValueExact(), childFact);
  }

  private static AttestationPreimage preimage(List<AttestationPreimage.Fact> facts) {
    return AttestationPreimage.of(facts);
  }

  private static AttestationAuthorizationException failure() {
    return AttestationOperationProfile.failure();
  }

  /** Distinguishes the aggregate wrapper grammar for request facts from that for effect facts. */
  private enum QualifiedFactCategory {
    /** A plan-qualified child request fact. */
    REQUEST(REQUEST_RECORD_TYPE_TAG),
    /** A plan-qualified child effect fact. */
    EFFECT(EFFECT_RECORD_TYPE_TAG);

    private final int wrapperRecordTypeTag;

    QualifiedFactCategory(int wrapperRecordTypeTag) {
      this.wrapperRecordTypeTag = wrapperRecordTypeTag;
    }

    private int wrapperRecordTypeTag() {
      return wrapperRecordTypeTag;
    }

    private boolean accepts(AttestationPreimage.Fact childFact) {
      return switch (this) {
        case REQUEST ->
            childFact.recordTypeTag() >= 0x0100
                && childFact.recordTypeTag() != REQUEST_RECORD_TYPE_TAG;
        case EFFECT ->
            childFact.recordTypeTag() < 0x0100
                && childFact.recordTypeTag() != EFFECT_RECORD_TYPE_TAG;
      };
    }
  }

  private record QualifiedFact(int sourceStepOrder, AttestationPreimage.Fact childFact) {}
}
