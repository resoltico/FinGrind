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
                CliFuzzHarnessTestSupport.basicValidRequestBytes()));
    assertDoesNotThrow(
        () ->
            PostingWorkflowFuzzAssertions.exercisePostingWorkflow(
                CliFuzzHarnessTestSupport.validJpyRequestBytes()));
    assertDoesNotThrow(
        () ->
            PostingWorkflowFuzzAssertions.exercisePostingWorkflow(
                CliFuzzHarnessTestSupport.validBhdRequestBytes()));
  }

  @Test
  void helper_accepts_deterministically_rejected_reversal_requests() {
    assertDoesNotThrow(
        () ->
            PostingWorkflowFuzzAssertions.exerciseParsedPostingWorkflow(
                CliFuzzFixtures.readPostEntryCommand(
                    CliFuzzHarnessTestSupport.reversalTargetMissingRequest().getBytes(UTF_8)),
                CliFuzzHarnessTestSupport.reversalTargetMissingRequestBytes()));
  }

  @Test
  void helper_ignores_invalid_request_shapes() {
    assertDoesNotThrow(
        () ->
            PostingWorkflowFuzzAssertions.exercisePostingWorkflow(
                CliFuzzHarnessTestSupport.invalidExponentAmountRequestBytes()));
    assertDoesNotThrow(
        () ->
            PostingWorkflowFuzzAssertions.exercisePostingWorkflow(
                CliFuzzHarnessTestSupport.missingReversalReasonRequestBytes()));
  }

  @Test
  void fuzz_entrypoint_consumes_provider_for_valid_rejected_and_invalid_requests() {
    PostingWorkflowFuzzTest harness = new PostingWorkflowFuzzTest();
    assertDoesNotThrow(
        () ->
            harness.exercisePostingWorkflow(
                CliFuzzHarnessTestSupport.fuzzedBytes(
                    CliFuzzHarnessTestSupport.basicValidRequestBytes())));
    assertDoesNotThrow(
        () ->
            harness.exercisePostingWorkflow(
                CliFuzzHarnessTestSupport.fuzzedBytes(
                    CliFuzzHarnessTestSupport.validJpyRequestBytes())));
    assertDoesNotThrow(
        () ->
            harness.exercisePostingWorkflow(
                CliFuzzHarnessTestSupport.fuzzedBytes(
                    CliFuzzHarnessTestSupport.validBhdRequestBytes())));
    assertDoesNotThrow(
        () ->
            harness.exercisePostingWorkflow(
                CliFuzzHarnessTestSupport.fuzzedBytes(
                    CliFuzzHarnessTestSupport.reversalTargetMissingRequestBytes())));
    assertDoesNotThrow(
        () ->
            harness.exercisePostingWorkflow(
                CliFuzzHarnessTestSupport.fuzzedBytes(
                    CliFuzzHarnessTestSupport.invalidBlankActorRequestBytes())));
  }
}
