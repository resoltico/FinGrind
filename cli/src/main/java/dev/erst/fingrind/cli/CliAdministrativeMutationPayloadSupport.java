package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliDeclareAccountPayload;
import dev.erst.fingrind.cli.json.CliEnvelopeJsonModels;
import dev.erst.fingrind.contract.protocol.ProtocolArtifactOutput;
import dev.erst.fingrind.contract.runtime.BookAccess;
import java.nio.file.Path;
import java.util.List;

/** Shared administrative mutation payload and artifact mapping helpers. */
final class CliAdministrativeMutationPayloadSupport {
  private CliAdministrativeMutationPayloadSupport() {}

  static CliDeclareAccountPayload declareAccountPayload(
      String outcome, dev.erst.fingrind.contract.bookkeeping.DeclaredAccount account) {
    return new CliDeclareAccountPayload(outcome, CliBookQueryPayloadMapper.accountPayload(account));
  }

  static List<String> tightenedParentDirectoryPayloads(List<Path> tightenedDirectories) {
    return tightenedDirectories.stream()
        .map(CliAdministrativeMutationPayloadSupport::absolutePath)
        .toList();
  }

  static List<CliEnvelopeJsonModels.SuccessArtifact> replacementPassphraseArtifacts(
      BookAccess.PassphraseSource replacementPassphraseSource) {
    if (replacementPassphraseSource instanceof BookAccess.PassphraseSource.KeyFile keyFile) {
      return CliEnvelopeMapper.successArtifacts(
          CliEnvelopeMapper.successArtifact(
              ProtocolArtifactOutput.bookKeyFileFormat(), keyFile.bookKeyFilePath()));
    }
    return List.of();
  }

  static String replacementPassphraseSourceKind(
      BookAccess.PassphraseSource replacementPassphraseSource) {
    return switch (replacementPassphraseSource) {
      case BookAccess.PassphraseSource.KeyFile _ -> "key-file";
      case BookAccess.PassphraseSource.StandardInput _ -> "standard-input";
      case BookAccess.PassphraseSource.InteractivePrompt _ -> "interactive-prompt";
    };
  }

  private static String absolutePath(Path path) {
    return CliPublicPaths.redactedValue(path);
  }
}
