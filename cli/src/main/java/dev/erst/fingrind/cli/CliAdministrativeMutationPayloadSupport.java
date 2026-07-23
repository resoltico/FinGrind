package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliDeclareAccountPayload;
import dev.erst.fingrind.contract.bookkeeping.AttestationCommit;
import java.nio.file.Path;
import java.util.List;

/** Shared administrative mutation payload and artifact mapping helpers. */
final class CliAdministrativeMutationPayloadSupport {
  private CliAdministrativeMutationPayloadSupport() {}

  static CliDeclareAccountPayload declareAccountPayload(
      String outcome,
      dev.erst.fingrind.contract.bookkeeping.DeclaredAccount account,
      @org.jspecify.annotations.Nullable AttestationCommit attestationCommit) {
    return new CliDeclareAccountPayload(
        outcome,
        CliBookQueryPayloadMapper.accountPayload(account),
        CliAttestationCommitPresentation.payload(attestationCommit));
  }

  static List<String> tightenedParentDirectoryPayloads(List<Path> tightenedDirectories) {
    return tightenedDirectories.stream()
        .map(CliAdministrativeMutationPayloadSupport::absolutePath)
        .toList();
  }

  private static String absolutePath(Path path) {
    return CliPublicPaths.absoluteValue(path);
  }
}
