package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Focused unit coverage for CLI output-mode and exit-code policy decisions. */
class CliExecutionPolicyTest {
  @Test
  void interactivePromptOutputFailure_onlyRejectsMachineOutputsWithPromptSources() {
    assertEquals(
        Optional.empty(),
        CliExecutionPolicy.interactivePromptOutputFailure(
            OutputMode.TEXT, BookAccess.PassphraseSource.InteractivePrompt.INSTANCE));
    assertEquals(
        Optional.empty(),
        CliExecutionPolicy.interactivePromptOutputFailure(
            OutputMode.JSON, BookAccess.PassphraseSource.StandardInput.INSTANCE));
    assertTrue(
        CliExecutionPolicy.interactivePromptOutputFailure(
                OutputMode.JSON, BookAccess.PassphraseSource.InteractivePrompt.INSTANCE)
            .isPresent());
    assertTrue(
        CliExecutionPolicy.interactivePromptOutputFailure(
                OutputMode.JSON,
                BookAccess.PassphraseSource.StandardInput.INSTANCE,
                BookAccess.PassphraseSource.InteractivePrompt.INSTANCE)
            .isPresent());
  }

  @Test
  void failureExitCode_usesThePublishedErrorDescriptor() {
    assertEquals(
        5,
        CliExecutionPolicy.failureExitCode(
            new CliFailure(
                ContractErrors.Descriptor.INTERACTIVE_PROMPT_UNAVAILABLE.code(),
                "Prompt unavailable.",
                null,
                null)));
    assertEquals(
        70,
        CliExecutionPolicy.failureExitCode(
            new CliFailure("internal-error", "Internal error.", null, null)));
    assertEquals(
        4,
        CliExecutionPolicy.failureExitCode(
            new CliFailure(
                ContractErrors.Descriptor.PROTECTED_BOOK_PAIR_PUBLICATION_UNCERTAIN.code(),
                "Protected-book pair publication remains uncertain.",
                null,
                null)));
    assertEquals(1, CliExecutionPolicy.invalidInvocationExitCode());
    assertFalse(
        CliExecutionPolicy.interactivePromptOutputFailure(
                OutputMode.TEXT, BookAccess.PassphraseSource.StandardInput.INSTANCE)
            .isPresent());
  }
}
