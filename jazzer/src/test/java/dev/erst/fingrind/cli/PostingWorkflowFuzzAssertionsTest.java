package dev.erst.fingrind.cli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

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
