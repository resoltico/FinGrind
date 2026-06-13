package dev.erst.fingrind.contract.protocol;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Command-line entrypoint that synchronizes the generated USER_INSTALL and USER_QUICK_START blocks.
 */
public final class ProtocolUserInstallDocumentSyncMain {
  private ProtocolUserInstallDocumentSyncMain() {}

  /** Synchronizes docs/USER_INSTALL.md and docs/USER_QUICK_START.md for one repository root. */
  public static void main(String[] args) throws IOException {
    if (args.length != 1) {
      throw new IllegalArgumentException(
          "Expected exactly one argument: the repository root that contains docs/USER_INSTALL.md and docs/USER_QUICK_START.md.");
    }
    Path repositoryRoot = Path.of(args[0]);
    ProtocolUserInstallDocumentSync.syncUserInstall(
        repositoryRoot, repositoryRoot.resolve("docs/USER_INSTALL.md"));
    ProtocolUserInstallDocumentSync.syncUserQuickStart(
        repositoryRoot, repositoryRoot.resolve("docs/USER_QUICK_START.md"));
  }
}
