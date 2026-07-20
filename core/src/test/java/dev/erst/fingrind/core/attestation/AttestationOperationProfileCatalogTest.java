package dev.erst.fingrind.core.attestation;

import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.assertFailure;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.credential;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/** Exercises the closed profile catalog independently of authorization and chain state. */
class AttestationOperationProfileCatalogTest {
  private static final Map<AttestationFieldType, Supplier<AttestationFieldValue>> VALUES =
      Map.ofEntries(
          Map.entry(
              AttestationFieldType.UNSIGNED_8, () -> AttestationNumericFieldValue.unsigned8(1)),
          Map.entry(
              AttestationFieldType.UNSIGNED_16, () -> AttestationNumericFieldValue.unsigned16(1)),
          Map.entry(
              AttestationFieldType.UNSIGNED_32,
              () -> AttestationNumericFieldValue.unsigned32(BigInteger.ONE)),
          Map.entry(
              AttestationFieldType.UNSIGNED_64,
              () -> AttestationNumericFieldValue.unsigned64(BigInteger.ONE)),
          Map.entry(
              AttestationFieldType.SIGNED_64,
              () -> AttestationNumericFieldValue.signed64(BigInteger.ONE)),
          Map.entry(
              AttestationFieldType.SIGNED_128,
              () -> AttestationNumericFieldValue.signed128(BigInteger.ONE)),
          Map.entry(
              AttestationFieldType.UUID,
              () -> AttestationBinaryFieldValue.uuid(AttestationAuthorizationTestSupport.BOOK_ID)),
          Map.entry(
              AttestationFieldType.HASH,
              () -> AttestationBinaryFieldValue.hash(AttestationHash.sha256(new byte[] {1}))),
          Map.entry(
              AttestationFieldType.SPKI,
              () -> AttestationBinaryFieldValue.spki(credential().pair().getPublic().getEncoded())),
          Map.entry(
              AttestationFieldType.BYTES, () -> AttestationBinaryFieldValue.bytes(new byte[] {1})),
          Map.entry(AttestationFieldType.TOKEN, () -> AttestationTextFieldValue.token("value")),
          Map.entry(AttestationFieldType.TEXT, () -> AttestationTextFieldValue.text("value")),
          Map.entry(AttestationFieldType.CURRENCY, () -> AttestationTextFieldValue.currency("EUR")),
          Map.entry(
              AttestationFieldType.DATE,
              () -> AttestationTextFieldValue.date(LocalDate.of(2026, 7, 20))),
          Map.entry(
              AttestationFieldType.INSTANT,
              () -> AttestationTextFieldValue.instant(Instant.parse("2026-07-20T00:00:00Z"))),
          Map.entry(
              AttestationFieldType.MONEY,
              () -> AttestationNumericFieldValue.money("EUR", false, BigInteger.ONE)),
          Map.entry(
              AttestationFieldType.SCALED,
              () -> AttestationNumericFieldValue.scaled(0, false, BigInteger.ONE)),
          Map.entry(
              AttestationFieldType.BOOLEAN, () -> AttestationNumericFieldValue.booleanValue(true)),
          Map.entry(AttestationFieldType.MUTATION, () -> AttestationNumericFieldValue.mutation(0)));

  @Test
  void acceptsOnlyTheRequiredAndDeclaredOptionalPostingGroups() {
    AttestationOperationProfileCatalog.TagProfile profile =
        AttestationOperationProfileCatalog.profile(AttestationOperationKind.POST_ENTRY);
    AttestationPreimage request = preimage(0x0100, 0x0120, 0x0124, 0x012A);
    AttestationPreimage effect = preimage(0x0020, 0x0021, 0x0025);

    assertDoesNotThrow(() -> profile.requireTags(request, effect));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () -> profile.requireTags(AttestationPreimage.of(List.of()), effect));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () -> profile.requireTags(append(request, 0x0123), effect));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () -> profile.requireTags(request, append(effect, 0x0030)));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () -> profile.requireTags(request, preimage(0x0020, 0x0021)));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () -> profile.requireTags(append(request, 0x0127), effect));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () -> profile.requireTags(request, append(effect, 0x0024)));
    assertDoesNotThrow(() -> profile.requireTags(append(request, 0x0127), append(effect, 0x0024)));

    AttestationOperationProfileCatalog.TagProfile policy =
        AttestationOperationProfileCatalog.profile(AttestationOperationKind.ALTER_POLICY);
    AttestationPreimage policyRequest = preimage(0x0100);
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () -> policy.requireTags(policyRequest, AttestationPreimage.of(List.of())));
    assertDoesNotThrow(() -> policy.requireTags(policyRequest, preimage(0x0003)));
  }

  @Test
  void rejectsSystemPostingAndOrphanedLifecycleEffectsAfterItsTagProfilePasses() {
    AttestationPreimage systemPost =
        AttestationAuthorizationTestSupport.requestPreimage(
            AttestationOperationKind.POST_ENTRY,
            AttestationSourceChannel.SYSTEM,
            AttestationAuthorizationTestSupport.SYSTEM_WORKFLOW_ID);
    AttestationOperationPayload systemPayload =
        AttestationAuthorizationTestSupport.operationPayload(
            BigInteger.ONE, AttestationOperationKind.POST_ENTRY, systemPost);
    AttestationPreimage request =
        AttestationPreimage.of(
            List.of(
                AttestationAuthorizationTestSupport.requestPreimage(
                        AttestationOperationKind.EXECUTE_PLAN, AttestationSourceChannel.CLI, null)
                    .records()
                    .getFirst(),
                fact(0x0120),
                fact(0x0124)));
    AttestationPreimage effect = preimage(0x0020, 0x0021, 0x0025);
    AttestationOperationPayload payload =
        AttestationAuthorizationTestSupport.operationPayload(
            BigInteger.ONE, AttestationOperationKind.EXECUTE_PLAN, request);

    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationOperationProfile.requireValid(
                systemPayload,
                AttestationOperationKind.POST_ENTRY,
                systemPost,
                AttestationPreimage.of(List.of())));
    AttestationPreimage systemInterim =
        AttestationAuthorizationTestSupport.requestPreimage(
            AttestationOperationKind.INTERIM_RESULT_SWEEP,
            AttestationSourceChannel.SYSTEM,
            AttestationAuthorizationTestSupport.SYSTEM_WORKFLOW_ID);
    AttestationPreimage systemFiscal =
        AttestationAuthorizationTestSupport.requestPreimage(
            AttestationOperationKind.FISCAL_YEAR_CLOSE,
            AttestationSourceChannel.SYSTEM,
            AttestationAuthorizationTestSupport.SYSTEM_WORKFLOW_ID);
    AttestationOperationPayload systemInterimPayload =
        AttestationAuthorizationTestSupport.operationPayload(
            BigInteger.ONE, AttestationOperationKind.INTERIM_RESULT_SWEEP, systemInterim);
    AttestationOperationPayload systemFiscalPayload =
        AttestationAuthorizationTestSupport.operationPayload(
            BigInteger.ONE, AttestationOperationKind.FISCAL_YEAR_CLOSE, systemFiscal);
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationOperationProfile.requireValid(
                systemInterimPayload,
                AttestationOperationKind.INTERIM_RESULT_SWEEP,
                systemInterim,
                AttestationPreimage.of(List.of())));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationOperationProfile.requireValid(
                systemFiscalPayload,
                AttestationOperationKind.FISCAL_YEAR_CLOSE,
                systemFiscal,
                AttestationPreimage.of(List.of())));
    assertDoesNotThrow(
        () ->
            AttestationOperationProfile.requireValid(
                payload, AttestationOperationKind.EXECUTE_PLAN, request, effect));
    AttestationPreimage matchedLifecycleRequest = append(request, 0x0128);
    AttestationOperationPayload matchedLifecyclePayload =
        AttestationAuthorizationTestSupport.operationPayload(
            BigInteger.ONE, AttestationOperationKind.EXECUTE_PLAN, matchedLifecycleRequest);
    assertDoesNotThrow(
        () ->
            AttestationOperationProfile.requireValid(
                matchedLifecyclePayload,
                AttestationOperationKind.EXECUTE_PLAN,
                matchedLifecycleRequest,
                append(effect, 0x0030)));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationOperationProfile.requireValid(
                payload, AttestationOperationKind.EXECUTE_PLAN, request, append(effect, 0x0030)));
  }

  private static AttestationPreimage append(AttestationPreimage preimage, int tag) {
    List<AttestationPreimage.Fact> records = new ArrayList<>(preimage.records());
    records.add(fact(tag));
    return AttestationPreimage.of(records);
  }

  private static AttestationPreimage preimage(int... tags) {
    List<AttestationPreimage.Fact> records = new ArrayList<>(tags.length);
    for (int tag : tags) {
      records.add(fact(tag));
    }
    return AttestationPreimage.of(records);
  }

  private static AttestationPreimage.Fact fact(int tag) {
    AttestationRecordSchema schema = AttestationPreimageCatalog.require(tag);
    List<AttestationField> fields = new ArrayList<>(schema.fieldCount());
    for (int index = 0; index < schema.fieldCount(); index++) {
      fields.add(AttestationField.present(value(schema.fieldSchema(index).type())));
    }
    return new AttestationPreimage.Fact(tag, fields);
  }

  private static AttestationFieldValue value(AttestationFieldType type) {
    return Objects.requireNonNull(VALUES.get(type), "value factory").get();
  }
}
