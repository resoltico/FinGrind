package dev.erst.fingrind.contract.protocol;

import java.io.IOException;
import java.nio.file.Path;

/** Command-line entrypoint that synchronizes user PDF capability inventories. */
public final class ProtocolUserPdfCapabilityDocumentSyncMain {
  private ProtocolUserPdfCapabilityDocumentSyncMain() {}

  /** Synchronizes all descriptor-owned PDF report inventories for one repository root. */
  public static void main(String[] args) throws IOException {
    if (args.length != 1) {
      throw new IllegalArgumentException(
          "Expected exactly one argument: the repository root that contains the user PDF guides.");
    }
    ProtocolUserPdfCapabilityDocumentSync.sync(Path.of(args[0]));
  }
}
