package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.sqlite.SqliteBookPassphrase;
import dev.erst.fingrind.sqlite.SqlitePassphraseIntent;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Focused plan-setup prompt selection coverage for {@link CliBookPassphraseResolver}. */
class CliBookPassphraseResolverPlanSetupTest {
  @TempDir Path tempDirectory;

  @BeforeEach
  void hardenTempDirectory() {
    CliTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(tempDirectory);
  }

  @Test
  void resolve_planSetupIntentUsesSinglePromptForExistingBooks() throws Exception {
    Path bookPath = tempDirectory.resolve("existing.sqlite");
    Files.writeString(bookPath, "existing");
    CliBookPassphraseResolver resolver =
        new CliBookPassphraseResolver(
            new ByteArrayInputStream(new byte[0]),
            prompt -> {
              assertTrue(prompt.startsWith("Passphrase for "));
              return ContractDecision.accepted("existing-secret".toCharArray());
            });

    try (SqliteBookPassphrase passphrase =
        resolver
            .resolve(
                bookPath,
                BookAccess.PassphraseSource.InteractivePrompt.INSTANCE,
                SqlitePassphraseIntent.PLAN_SETUP_SECRET)
            .requireAccepted()) {
      assertEquals("interactive prompt", passphrase.sourceDescription());
    }
  }

  @Test
  void resolve_planSetupIntentUsesConfirmedPromptForNewBooks() throws Exception {
    Path bookPath = tempDirectory.resolve("new.sqlite");
    CliBookPassphraseResolver resolver =
        new CliBookPassphraseResolver(
            new ByteArrayInputStream(new byte[0]),
            new CliBookPassphraseResolver.Terminal() {
              private int readCount;

              @Override
              public ContractDecision<char[]> readPassword(String prompt) {
                readCount++;
                if (readCount == 1) {
                  assertTrue(prompt.startsWith("New passphrase for "));
                  return ContractDecision.accepted("new-secret".toCharArray());
                }
                assertEquals("Confirm new passphrase: ", prompt);
                return ContractDecision.accepted("new-secret".toCharArray());
              }
            });

    try (SqliteBookPassphrase passphrase =
        resolver
            .resolve(
                bookPath,
                BookAccess.PassphraseSource.InteractivePrompt.INSTANCE,
                SqlitePassphraseIntent.PLAN_SETUP_SECRET)
            .requireAccepted()) {
      assertEquals("interactive prompt", passphrase.sourceDescription());
    }
  }

  @Test
  void promptStyle_planSetupPrimaryPromptMustBeResolvedBeforeUse() {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> CliBookPassphraseResolver.PromptStyle.PLAN_SETUP.primaryPrompt("book.sqlite"));

    assertEquals("PLAN_SETUP must be resolved first.", exception.getMessage());
  }
}
