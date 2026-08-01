package dev.erst.fingrind.core.attestation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Verifies structural field correspondence between request and effect preimage facts. */
final class AttestationPreimageFactCorrespondence {
  private AttestationPreimageFactCorrespondence() {}

  static void requireMappedFacts(
      AttestationPreimage requestPreimage,
      int requestRecordTypeTag,
      int requestStartField,
      AttestationPreimage effectPreimage,
      int effectRecordTypeTag,
      int effectStartField) {
    List<AttestationPreimage.Fact> unmatchedEffects =
        new ArrayList<>(AttestationPreimageFields.records(effectPreimage, effectRecordTypeTag));
    for (AttestationPreimage.Fact request :
        AttestationPreimageFields.records(requestPreimage, requestRecordTypeTag)) {
      int matchingEffect =
          matchingEffect(unmatchedEffects, request, requestStartField, effectStartField);
      if (matchingEffect < 0) {
        throw failure();
      }
      unmatchedEffects.remove(matchingEffect);
    }
    if (!unmatchedEffects.isEmpty()) {
      throw failure();
    }
  }

  static void requireFieldsMatch(
      AttestationPreimage.Fact left,
      int leftStartField,
      AttestationPreimage.Fact right,
      int rightStartField,
      int fieldCount) {
    if (!fieldsMatch(left, leftStartField, right, rightStartField, fieldCount)) {
      throw failure();
    }
  }

  static void requireSameField(
      AttestationPreimage.Fact left,
      int leftField,
      AttestationPreimage.Fact right,
      int rightField) {
    if (!Arrays.equals(
        left.fields().get(leftField).encoded(), right.fields().get(rightField).encoded())) {
      throw failure();
    }
  }

  private static int matchingEffect(
      List<AttestationPreimage.Fact> effects,
      AttestationPreimage.Fact request,
      int requestStartField,
      int effectStartField) {
    for (int index = 0; index < effects.size(); index++) {
      if (fieldsMatch(request, requestStartField, effects.get(index), effectStartField)) {
        return index;
      }
    }
    return -1;
  }

  private static boolean fieldsMatch(
      AttestationPreimage.Fact left,
      int leftStartField,
      AttestationPreimage.Fact right,
      int rightStartField) {
    int fieldCount = left.fields().size() - leftStartField;
    return fieldCount == right.fields().size() - rightStartField
        && fieldsMatch(left, leftStartField, right, rightStartField, fieldCount);
  }

  private static boolean fieldsMatch(
      AttestationPreimage.Fact left,
      int leftStartField,
      AttestationPreimage.Fact right,
      int rightStartField,
      int fieldCount) {
    if (fieldCount < 0
        || left.fields().size() - leftStartField < fieldCount
        || right.fields().size() - rightStartField < fieldCount) {
      return false;
    }
    for (int offset = 0; offset < fieldCount; offset++) {
      if (!Arrays.equals(
          left.fields().get(leftStartField + offset).encoded(),
          right.fields().get(rightStartField + offset).encoded())) {
        return false;
      }
    }
    return true;
  }

  private static AttestationAuthorizationException failure() {
    return AttestationOperationProfile.failure();
  }
}
