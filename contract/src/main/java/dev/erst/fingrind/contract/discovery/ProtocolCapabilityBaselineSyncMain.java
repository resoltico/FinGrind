package dev.erst.fingrind.contract.discovery;

import java.io.IOException;
import java.nio.file.Path;

/** Command-line entrypoint that synchronizes one release-smoke capability baseline directory. */
public final class ProtocolCapabilityBaselineSyncMain {
  private ProtocolCapabilityBaselineSyncMain() {}

  /** Synchronizes the capability baseline at one explicit destination directory. */
  public static void main(String[] args) throws IOException {
    if (args.length != 1) {
      throw new IllegalArgumentException(
          "Expected exactly one argument: the capability baseline JSON destination directory.");
    }
    ProtocolCapabilityBaseline.sync(Path.of(args[0]));
  }
}
