package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.BookAccess;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteRoundTripWorkflowResourcesTest {
  @TempDir Path tempDirectory;

  @Test
  void cleanup_and_interactive_prompt_guards_are_proven() {
    assertDoesNotThrow(
        () ->
            SqliteRoundTripWorkflowResources.deleteRecursively(
                tempDirectory.resolve("missing-root")));

    BookAccess interactiveBook =
        new BookAccess(
            tempDirectory.resolve("interactive.sqlite"),
            BookAccess.PassphraseSource.InteractivePrompt.INSTANCE);
    IllegalStateException prompt =
        assertThrows(
            IllegalStateException.class,
            () -> SqliteRoundTripWorkflowResources.sqliteWorkflow().inspectBook(interactiveBook));
    SqliteRoundTripWorkflowTestSupport.assertMessageContains(
        prompt, "Interactive passphrase prompting must not occur");
  }
}
