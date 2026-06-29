package dev.erst.fingrind.cli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.core.AccountCode;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Covers deterministic posting-workflow fuzz entry behavior. */
class PostingWorkflowFuzzAssertionsTest {
  @Test
  void helper_accepts_committable_requests() {
    assertDoesNotThrow(
        () ->
            PostingWorkflowFuzzAssertions.exerciseParsedPostingWorkflow(
                SqliteRoundTripWorkflowTestSupport.basicValidCommand(),
                CliFuzzRequestSeedSupport.basicValidRequestBytes()));
    assertDoesNotThrow(
        () ->
            PostingWorkflowFuzzAssertions.exercisePostingWorkflow(
                CliFuzzRequestSeedSupport.validJpyRequestBytes()));
    assertDoesNotThrow(
        () ->
            PostingWorkflowFuzzAssertions.exercisePostingWorkflow(
                CliFuzzRequestSeedSupport.validBhdRequestBytes()));
  }

  @Test
  void helper_accepts_deterministically_rejected_reversal_requests() {
    assertDoesNotThrow(
        () ->
            PostingWorkflowFuzzAssertions.exerciseParsedPostingWorkflow(
                CliFuzzFixtures.readPostEntryCommand(
                    CliFuzzRequestSeedSupport.reversalTargetMissingRequest().getBytes(UTF_8)),
                CliFuzzRequestSeedSupport.reversalTargetMissingRequestBytes()));
  }

  @Test
  void helper_ignores_invalid_request_shapes() {
    assertDoesNotThrow(
        () ->
            PostingWorkflowFuzzAssertions.exercisePostingWorkflow(
                CliFuzzRequestSeedSupport.invalidExponentAmountRequestBytes()));
    assertDoesNotThrow(
        () ->
            PostingWorkflowFuzzAssertions.exercisePostingWorkflow(
                CliFuzzRequestSeedSupport.missingReversalReasonRequestBytes()));
  }

  @Test
  void helper_accepts_deterministically_rejected_sameAccount_requests() {
    assertDoesNotThrow(
        () ->
            PostingWorkflowFuzzAssertions.exercisePostingWorkflow(
                CliFuzzRequestSeedSupport.sameAccountCashRevenueRequestBytes()));
  }

  @Test
  void unknownAccountPreDeclarationClassifier_acceptsOnlyPureUnknownAccountViolations() {
    PostingRejection.AccountStateViolations unknownOnlyViolation =
        new PostingRejection.AccountStateViolations(
            List.of(new PostingRejection.UnknownAccount(new AccountCode("1000"))));
    PostingRejection.AccountStateViolations mixedAccountStateViolation =
        new PostingRejection.AccountStateViolations(
            List.of(
                new PostingRejection.UnknownAccount(new AccountCode("1000")),
                new PostingRejection.InactiveAccount(new AccountCode("2000"))));
    PostingRejection.EntrySemanticsViolations entrySemanticsViolation =
        new PostingRejection.EntrySemanticsViolations(
            List.of(
                new PostingRejection.EntrySemanticsViolation(
                    "distinct-role-accounts-required",
                    "cashAccountCode",
                    "role accounts must differ")));

    assertTrue(
        PostingWorkflowFuzzAssertions.isUnknownAccountPreDeclarationState(unknownOnlyViolation));
    assertFalse(
        PostingWorkflowFuzzAssertions.isUnknownAccountPreDeclarationState(
            mixedAccountStateViolation));
    assertFalse(
        PostingWorkflowFuzzAssertions.isUnknownAccountPreDeclarationState(entrySemanticsViolation));
  }

  @Test
  void fuzz_entrypoint_consumes_provider_for_valid_rejected_and_invalid_requests() {
    PostingWorkflowFuzzTest harness = new PostingWorkflowFuzzTest();
    assertDoesNotThrow(
        () ->
            harness.exercisePostingWorkflow(
                CliFuzzHarnessInvocationSupport.fuzzedBytes(
                    CliFuzzRequestSeedSupport.basicValidRequestBytes())));
    assertDoesNotThrow(
        () ->
            harness.exercisePostingWorkflow(
                CliFuzzHarnessInvocationSupport.fuzzedBytes(
                    CliFuzzRequestSeedSupport.validJpyRequestBytes())));
    assertDoesNotThrow(
        () ->
            harness.exercisePostingWorkflow(
                CliFuzzHarnessInvocationSupport.fuzzedBytes(
                    CliFuzzRequestSeedSupport.validBhdRequestBytes())));
    assertDoesNotThrow(
        () ->
            harness.exercisePostingWorkflow(
                CliFuzzHarnessInvocationSupport.fuzzedBytes(
                    CliFuzzRequestSeedSupport.reversalTargetMissingRequestBytes())));
    assertDoesNotThrow(
        () ->
            harness.exercisePostingWorkflow(
                CliFuzzHarnessInvocationSupport.fuzzedBytes(
                    CliFuzzRequestSeedSupport.invalidBlankActorRequestBytes())));
  }
}
