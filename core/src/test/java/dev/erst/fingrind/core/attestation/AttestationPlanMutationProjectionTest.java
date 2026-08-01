package dev.erst.fingrind.core.attestation;

import static dev.erst.fingrind.core.NullTestSupport.nullOf;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.assertFailure;
import static dev.erst.fingrind.core.attestation.AttestationMutationProjectionFixtures.account;
import static dev.erst.fingrind.core.attestation.AttestationMutationProjectionFixtures.decode;
import static dev.erst.fingrind.core.attestation.AttestationMutationProjectionFixtures.operationRequest;
import static dev.erst.fingrind.core.attestation.AttestationMutationProjectionFixtures.planPostingEffect;
import static dev.erst.fingrind.core.attestation.AttestationMutationProjectionFixtures.planPostingRequest;
import static dev.erst.fingrind.core.attestation.AttestationMutationProjectionFixtures.postingEffect;
import static dev.erst.fingrind.core.attestation.AttestationMutationProjectionFixtures.postingRequest;
import static dev.erst.fingrind.core.attestation.AttestationMutationProjectionFixtures.qualifiedSourceSteps;
import static dev.erst.fingrind.core.attestation.AttestationMutationProjectionFixtures.replaceField;
import static dev.erst.fingrind.core.attestation.AttestationMutationProjectionFixtures.richAccount;
import static dev.erst.fingrind.core.attestation.AttestationMutationProjectionFixtures.tags;
import static dev.erst.fingrind.core.attestation.AttestationMutationProjectionFixtures.taxRegistration;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Verifies execute-plan aggregation and the direct child-mutation profile it admits. */
class AttestationPlanMutationProjectionTest {
  @Test
  void planProjection_qualifiesCompleteChildFactsAndRejectsMalformedChildCollections() {
    AttestationOperationPreimages posting =
        AttestationPostingMutationProjection.project(planPostingRequest(), planPostingEffect());
    AttestationOperationPreimages account =
        AttestationAccountMutationProjection.project(
            AttestationAccountMutationIntent.DECLARATION,
            "declare-account",
            account("1000"),
            account("1000"),
            AttestationEffectMutation.CREATE);
    AttestationOperationPreimages projected =
        AttestationPlanMutationProjection.project(
            "plan-1",
            List.of(
                new AttestationPlanChildMutation(3, "post-entry", posting),
                new AttestationPlanChildMutation(9, "declare-account", account)));

    AttestationPreimage request = decode(projected.request());
    AttestationPreimage effect = decode(projected.effect());
    assertDoesNotThrow(
        () ->
            AttestationPlanChildMutationProfile.requireValid(
                decode(posting.request()), decode(posting.effect())));
    assertDoesNotThrow(
        () ->
            AttestationPlanChildMutationProfile.requireValid(
                decode(account.request()), decode(account.effect())));
    assertEquals(1, tags(request).stream().filter(tag -> tag == 0x0100).count());
    assertTrue(
        request.records().stream()
            .filter(fact -> fact.recordTypeTag() != 0x0100)
            .allMatch(
                fact ->
                    fact.recordTypeTag() == AttestationPlanQualifiedFact.REQUEST_RECORD_TYPE_TAG));
    assertTrue(
        effect.records().stream()
            .allMatch(
                fact ->
                    fact.recordTypeTag() == AttestationPlanQualifiedFact.EFFECT_RECORD_TYPE_TAG));
    assertDoesNotThrow(() -> AttestationPlanQualifiedFact.requireValid(request, effect));
    assertThrows(
        IllegalArgumentException.class,
        () -> AttestationPlanMutationProjection.project("plan-1", List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AttestationPlanMutationProjection.project(
                "plan-1",
                List.of(
                    new AttestationPlanChildMutation(2, "post-entry", posting),
                    new AttestationPlanChildMutation(2, "declare-account", account))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AttestationPlanMutationProjection.project(
                "plan-1",
                List.of(new AttestationPlanChildMutation(1, "declare-account", posting))));
    assertThrows(
        IllegalArgumentException.class,
        () -> AttestationPlanMutationProjection.project(" ", List.of()));
    assertThrows(
        NullPointerException.class,
        () -> AttestationPlanMutationProjection.project(nullOf(), List.of()));
    assertThrows(
        NullPointerException.class,
        () -> AttestationPlanMutationProjection.project("plan-1", nullOf()));
    assertThrows(
        NullPointerException.class,
        () ->
            AttestationPlanMutationProjection.project(
                "plan-1", java.util.Arrays.asList(nullOf(AttestationPlanChildMutation.class))));
  }

  @Test
  void planQualifiedFactsRejectCombinedChildRecordAndSortKeyBudgetOverflow() {
    int overflowPayloadLength =
        AttestationPlanQualifiedFact.maximumEmbeddedValueByteCount() / 3 + 1;
    byte[] embeddedPayload = new byte[overflowPayloadLength];
    AttestationPreimage.Fact oversizedNestedChild =
        new AttestationPreimage.Fact(
            AttestationPlanQualifiedFact.REQUEST_RECORD_TYPE_TAG,
            List.of(
                AttestationPreimageProjectionFields.unsigned32(0),
                AttestationPreimageProjectionFields.present(
                    AttestationNumericFieldValue.unsigned16(0x0100)),
                AttestationPreimageProjectionFields.present(
                    AttestationBinaryFieldValue.embedded(embeddedPayload)),
                AttestationPreimageProjectionFields.present(
                    AttestationBinaryFieldValue.embedded(embeddedPayload))));

    assertEquals(
        "A plan-qualified child record and its sort key exceed the aggregate preimage budget.",
        assertThrows(
                IllegalArgumentException.class,
                () -> AttestationPlanQualifiedFact.requestFact(1, oversizedNestedChild))
            .getMessage());
  }

  @Test
  void planProjection_preservesRepeatedAccountAndTaxIdentitiesAtTheirSourceSteps() {
    AttestationAccountSnapshot reactivatedAccount = account("1000");
    AttestationAccountSnapshot renamedAccount =
        new AttestationAccountSnapshot(
            new AccountCode("1000"),
            new AccountName("Cash Reserve"),
            AccountType.ASSET,
            AccountTaxonomy.empty(),
            null,
            true);
    AttestationOperationPreimages reactivated =
        AttestationAccountMutationProjection.project(
            AttestationAccountMutationIntent.DECLARATION,
            "declare-account",
            reactivatedAccount,
            reactivatedAccount,
            AttestationEffectMutation.REACTIVATE);
    AttestationOperationPreimages renamed =
        AttestationAccountMutationProjection.project(
            AttestationAccountMutationIntent.DECLARATION,
            "declare-account",
            renamedAccount,
            renamedAccount,
            AttestationEffectMutation.AMEND);
    AttestationTaxRegistrationSnapshot initialRegistration =
        taxRegistration("LV-VAT", "LV-000000001");
    AttestationTaxRegistrationSnapshot amendedRegistration =
        taxRegistration("LV-VAT", "LV-000000002");
    AttestationOperationPreimages declaredRegistration =
        AttestationTaxRegistrationMutationProjection.project(
            "declare-tax-registration",
            initialRegistration,
            initialRegistration,
            AttestationEffectMutation.CREATE);
    AttestationOperationPreimages amendedRegistrationPreimages =
        AttestationTaxRegistrationMutationProjection.project(
            "declare-tax-registration",
            amendedRegistration,
            amendedRegistration,
            AttestationEffectMutation.AMEND);

    AttestationOperationPreimages projected =
        AttestationPlanMutationProjection.project(
            "monthly-close",
            List.of(
                new AttestationPlanChildMutation(2, "declare-account", reactivated),
                new AttestationPlanChildMutation(3, "declare-account", renamed),
                new AttestationPlanChildMutation(
                    4, "declare-tax-registration", declaredRegistration),
                new AttestationPlanChildMutation(
                    5, "declare-tax-registration", amendedRegistrationPreimages)));
    AttestationPreimage request = decode(projected.request());
    AttestationPreimage effect = decode(projected.effect());
    assertDoesNotThrow(
        () ->
            AttestationPlanChildMutationProfile.requireValid(
                decode(declaredRegistration.request()), decode(declaredRegistration.effect())));
    assertDoesNotThrow(
        () ->
            AttestationPlanChildMutationProfile.requireValid(
                decode(amendedRegistrationPreimages.request()),
                decode(amendedRegistrationPreimages.effect())));

    assertEquals(
        List.of(2, 2, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5),
        qualifiedSourceSteps(request, AttestationPlanQualifiedFact.REQUEST_RECORD_TYPE_TAG));
    assertEquals(
        List.of(2, 3, 4, 4, 4, 5, 5, 5),
        qualifiedSourceSteps(effect, AttestationPlanQualifiedFact.EFFECT_RECORD_TYPE_TAG));
    assertDoesNotThrow(() -> AttestationPlanQualifiedFact.requireValid(request, effect));
  }

  @Test
  void planQualifiedChildrenRejectUnscopedStepsAndInternallyContradictoryEffects() {
    AttestationOperationPreimages posting =
        AttestationPostingMutationProjection.project(planPostingRequest(), planPostingEffect());
    AttestationPreimage postingRequest = decode(posting.request());
    AttestationPreimage postingEffect = decode(posting.effect());

    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationPlanChildMutationProfile.requireValid(
                replaceField(
                    postingRequest, 0x0120, 0, AttestationPreimageProjectionFields.unsigned32(1)),
                postingEffect));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationPlanChildMutationProfile.requireValid(
                replaceField(
                    postingRequest, 0x0100, 3, AttestationPreimageProjectionFields.token("system")),
                postingEffect));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationPlanChildMutationProfile.requireValid(
                replaceField(
                    postingRequest,
                    0x0100,
                    0,
                    AttestationPreimageProjectionFields.token("record-reversal")),
                postingEffect));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationPlanChildMutationProfile.requireValid(
                AttestationPreimage.of(List.of()), postingEffect));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationPlanChildMutationProfile.requireValid(
                replaceField(
                    postingRequest,
                    0x0120,
                    1,
                    AttestationPreimageProjectionFields.token("declare-account")),
                postingEffect));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationPlanChildMutationProfile.requireValid(
                postingRequest,
                replaceField(
                    postingEffect,
                    0x0020,
                    0,
                    AttestationPreimageProjectionFields.present(
                        AttestationNumericFieldValue.mutation(
                            AttestationEffectMutation.REVERSE.wireValue())))));

    AttestationAccountSnapshot classifiedAccount = richAccount(true);
    AttestationOperationPreimages account =
        AttestationAccountMutationProjection.project(
            AttestationAccountMutationIntent.DECLARATION,
            "declare-account",
            classifiedAccount,
            classifiedAccount,
            AttestationEffectMutation.CREATE);
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationPlanChildMutationProfile.requireValid(
                decode(account.request()),
                replaceField(
                    decode(account.effect()),
                    0x0011,
                    0,
                    AttestationPreimageProjectionFields.present(
                        AttestationNumericFieldValue.mutation(
                            AttestationEffectMutation.RETIRE.wireValue())))));

    AttestationTaxRegistrationSnapshot registration = taxRegistration("LV-VAT", "LV-000000001");
    AttestationOperationPreimages taxRegistration =
        AttestationTaxRegistrationMutationProjection.project(
            "declare-tax-registration",
            registration,
            registration,
            AttestationEffectMutation.CREATE);
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationPlanChildMutationProfile.requireValid(
                decode(taxRegistration.request()),
                replaceField(
                    decode(taxRegistration.effect()),
                    0x0014,
                    0,
                    AttestationPreimageProjectionFields.present(
                        AttestationNumericFieldValue.mutation(
                            AttestationEffectMutation.RETIRE.wireValue())))));
  }

  @Test
  void planProjection_rejectsAChildWhoseCommandMatchesButProfileDoesNot() {
    AttestationOperationPreimages posting =
        AttestationPostingMutationProjection.project(planPostingRequest(), planPostingEffect());
    AttestationPreimage malformedEffect =
        replaceField(
            decode(posting.effect()),
            0x0020,
            0,
            AttestationPreimageProjectionFields.present(
                AttestationNumericFieldValue.mutation(
                    AttestationEffectMutation.REVERSE.wireValue())));
    AttestationOperationPreimages malformed =
        new AttestationOperationPreimages(posting.request(), malformedEffect.encoded());

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                AttestationPlanMutationProjection.project(
                    "plan-1",
                    List.of(new AttestationPlanChildMutation(1, "post-entry", malformed))));

    assertEquals(
        "A ledger-plan child mutation must retain a valid direct operation profile.",
        exception.getMessage());
    assertTrue(exception.getCause() instanceof AttestationAuthorizationException);
  }

  @Test
  void planChildProfiles_acceptAccountAmendmentsAndReactivationsButRejectInactiveEffects() {
    AttestationAccountSnapshot activeAccount = richAccount(true);
    AttestationOperationPreimages amended =
        AttestationAccountMutationProjection.project(
            AttestationAccountMutationIntent.DECLARATION,
            "declare-account",
            activeAccount,
            activeAccount,
            AttestationEffectMutation.AMEND);
    AttestationOperationPreimages reactivated =
        AttestationAccountMutationProjection.project(
            AttestationAccountMutationIntent.DECLARATION,
            "declare-account",
            activeAccount,
            activeAccount,
            AttestationEffectMutation.REACTIVATE);
    AttestationOperationPreimages inactive =
        AttestationAccountMutationProjection.project(
            AttestationAccountMutationIntent.DECLARATION,
            "declare-account",
            activeAccount,
            richAccount(false),
            AttestationEffectMutation.CREATE);
    AttestationOperationPreimages retired =
        AttestationAccountMutationProjection.project(
            AttestationAccountMutationIntent.RETIREMENT,
            "declare-account",
            activeAccount,
            activeAccount,
            AttestationEffectMutation.RETIRE);

    assertDoesNotThrow(
        () ->
            AttestationPlanChildMutationProfile.requireValid(
                decode(amended.request()), decode(amended.effect())));
    assertDoesNotThrow(
        () ->
            AttestationPlanChildMutationProfile.requireValid(
                decode(reactivated.request()), decode(reactivated.effect())));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationPlanChildMutationProfile.requireValid(
                decode(inactive.request()), decode(inactive.effect())));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationPlanChildMutationProfile.requireValid(
                decode(retired.request()), decode(retired.effect())));
  }

  @Test
  void planChildProfiles_acceptTaxAmendmentsAndRejectUnsupportedRegistrationMutations() {
    AttestationTaxRegistrationSnapshot registration = taxRegistration("LV-VAT", "LV-000000001");
    AttestationOperationPreimages amended =
        AttestationTaxRegistrationMutationProjection.project(
            "declare-tax-registration",
            registration,
            registration,
            AttestationEffectMutation.AMEND);

    assertDoesNotThrow(
        () ->
            AttestationPlanChildMutationProfile.requireValid(
                decode(amended.request()), decode(amended.effect())));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationPlanChildMutationProfile.requireValid(
                decode(amended.request()),
                replaceField(
                    decode(amended.effect()),
                    0x0013,
                    0,
                    AttestationPreimageProjectionFields.present(
                        AttestationNumericFieldValue.mutation(
                            AttestationEffectMutation.RETIRE.wireValue())))));
  }

  @Test
  void planChildProfiles_rejectDuplicateAccountRequests() {
    AttestationOperationPreimages account =
        AttestationAccountMutationProjection.project(
            AttestationAccountMutationIntent.DECLARATION,
            "declare-account",
            account("1000"),
            account("1000"),
            AttestationEffectMutation.CREATE);
    AttestationOperationPreimages differentAccount =
        AttestationAccountMutationProjection.project(
            AttestationAccountMutationIntent.DECLARATION,
            "declare-account",
            account("1001"),
            account("1001"),
            AttestationEffectMutation.CREATE);
    AttestationPreimage duplicateRequest =
        appendFact(
            decode(account.request()), factWithTag(decode(differentAccount.request()), 0x0110));

    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationPlanChildMutationProfile.requireValid(
                duplicateRequest, decode(account.effect())));
  }

  @Test
  void planOperationAuthorizer_signsOnlyTheFinalAggregateOperation() {
    AttestationEvidence expected =
        new AttestationEvidence(new byte[] {1}, new byte[] {2}, new byte[] {3});
    AtomicInteger authorizationCalls = new AtomicInteger();
    AttestationPlanOperationAuthorizer authorizer =
        new AttestationPlanOperationAuthorizer(
            request -> {
              authorizationCalls.incrementAndGet();
              return expected;
            });
    AttestationOperationPreimages child =
        AttestationPostingMutationProjection.project(
            postingRequest(), postingEffect("record-reversal"));

    assertFalse(
        AttestationOperationAuthorizer.class.isAssignableFrom(
            AttestationPlanOperationAuthorizer.class));
    assertThrows(
        IllegalArgumentException.class,
        () -> authorizer.authorizePlan(operationRequest("record-reversal", child)));
    assertEquals(0, authorizationCalls.get());
    assertSame(expected, authorizer.authorizePlan(operationRequest("execute-plan", child)));
    assertEquals(1, authorizationCalls.get());
    assertThrows(
        IllegalArgumentException.class,
        () -> new AttestationPlanChildMutation(-1, "record-reversal", child));
    assertThrows(
        IllegalArgumentException.class, () -> new AttestationPlanChildMutation(1, " ", child));
    assertThrows(
        NullPointerException.class,
        () -> new AttestationPlanChildMutation(1, "record-reversal", nullOf()));
    assertThrows(
        NullPointerException.class, () -> new AttestationPlanOperationAuthorizer(nullOf()));
  }

  @Test
  void planOperationAuthorizer_remainsIdentityBoundWhenWrappersShareOneDelegate() {
    AttestationOperationAuthorizer delegate =
        request -> new AttestationEvidence(new byte[0], new byte[0], new byte[0]);
    AttestationPlanOperationAuthorizer first = new AttestationPlanOperationAuthorizer(delegate);
    AttestationPlanOperationAuthorizer second = new AttestationPlanOperationAuthorizer(delegate);

    assertEquals(first, first);
    assertNotEquals(first, second);
    assertEquals(System.identityHashCode(first), first.hashCode());
  }

  private static AttestationPreimage appendFact(
      AttestationPreimage preimage, AttestationPreimage.Fact fact) {
    List<AttestationPreimage.Fact> facts = new ArrayList<>(preimage.records());
    facts.add(fact);
    return AttestationPreimage.of(facts);
  }

  private static AttestationPreimage.Fact factWithTag(
      AttestationPreimage preimage, int recordTypeTag) {
    return preimage.records().stream()
        .filter(fact -> fact.recordTypeTag() == recordTypeTag)
        .findFirst()
        .orElseThrow();
  }
}
