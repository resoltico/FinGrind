package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliDeclareAccountPayload;
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

  private static String absolutePath(Path path) {
    return CliPublicPaths.absoluteValue(path);
  }
}
