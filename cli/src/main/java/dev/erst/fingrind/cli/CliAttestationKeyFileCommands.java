package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OutputMode;
import java.nio.file.Path;
import java.util.Objects;

/** Standalone CLI commands for off-book attestation credential custody. */
record GenerateAttestationKeyFile(
    Path attestationKeyFilePath, Path passphraseFilePath, OutputMode outputMode)
    implements CliCommand.OutputModeCommand {
  GenerateAttestationKeyFile {
    Objects.requireNonNull(attestationKeyFilePath, "attestationKeyFilePath");
    Objects.requireNonNull(passphraseFilePath, "passphraseFilePath");
    Objects.requireNonNull(outputMode, "outputMode");
  }

  @Override
  public int execute(CliExecutionContext executionContext) {
    return Objects.requireNonNull(executionContext, "executionContext")
        .administrative()
        .runGenerateAttestationKeyFileCommand(
            attestationKeyFilePath, passphraseFilePath, outputMode);
  }
}

/* Standalone CLI command that reveals only an encrypted credential's public identity. */
record InspectAttestationKeyFile(Path attestationKeyFilePath, OutputMode outputMode)
    implements CliCommand.OutputModeCommand {
  InspectAttestationKeyFile {
    Objects.requireNonNull(attestationKeyFilePath, "attestationKeyFilePath");
    Objects.requireNonNull(outputMode, "outputMode");
  }

  @Override
  public int execute(CliExecutionContext executionContext) {
    return Objects.requireNonNull(executionContext, "executionContext")
        .administrative()
        .runInspectAttestationKeyFileCommand(attestationKeyFilePath, outputMode);
  }
}
