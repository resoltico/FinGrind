package dev.erst.fingrind.contract.protocol;

import java.io.IOException;
import java.nio.file.Path;

/** Command-line entrypoint that synchronizes the generated USER_CLI command table in place. */
public final class ProtocolUserCliDocumentSyncMain {
  private ProtocolUserCliDocumentSyncMain() {}

  /** Synchronizes docs/USER_CLI.md or one explicit target document path. */
  public static void main(String[] args) throws IOException {
    if (args.length != 1) {
      throw new IllegalArgumentException(
          "Expected exactly one argument: the path to docs/USER_CLI.md.");
    }
    ProtocolUserCliDocumentSync.sync(Path.of(args[0]));
  }
}
