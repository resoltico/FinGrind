package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.core.attestation.AttestationCredentialSource;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.UUID;

/**
 * Parses and resolves the ordered attestation credential triples shared by protected-book commands.
 */
final class CliAttestationCredentialArguments {
  private final List<UUID> principalIds = new ArrayList<>();
  private final List<Path> keyFiles = new ArrayList<>();
  private final List<Path> passphraseFiles = new ArrayList<>();

  boolean apply(String argument, ListIterator<String> argumentIterator) {
    switch (argument) {
      case ProtocolOptions.Attestation.PRINCIPAL_ID ->
          principalIds.add(
              CliArgumentValueParser.requireValidArgument(
                  ProtocolOptions.Attestation.PRINCIPAL_ID,
                  () ->
                      UUID.fromString(
                          CliOptionValues.requireValue(
                              argumentIterator, ProtocolOptions.Attestation.PRINCIPAL_ID))));
      case ProtocolOptions.Attestation.KEY_FILE ->
          keyFiles.add(
              CliOptionValues.requirePathOptionValue(
                  argumentIterator, ProtocolOptions.Attestation.KEY_FILE));
      case ProtocolOptions.Attestation.PASSPHRASE_FILE ->
          passphraseFiles.add(
              CliOptionValues.requirePathOptionValue(
                  argumentIterator, ProtocolOptions.Attestation.PASSPHRASE_FILE));
      default -> {
        return false;
      }
    }
    return true;
  }

  List<AttestationCredentialSource> resolveOptional() {
    int count = principalIds.size();
    if (count == 0 && keyFiles.isEmpty() && passphraseFiles.isEmpty()) {
      return List.of();
    }
    requireAlignedTripleCount(count);
    List<AttestationCredentialSource> sources = new ArrayList<>(count);
    for (int index = 0; index < count; index++) {
      sources.add(
          new AttestationCredentialSource(
              principalIds.get(index), keyFiles.get(index), passphraseFiles.get(index)));
    }
    return List.copyOf(sources);
  }

  static void requirePresent(BookAccess bookAccess) {
    Objects.requireNonNull(bookAccess, "bookAccess");
    if (bookAccess.attestationCredentialSources().isEmpty()) {
      throw invalidTripleCount();
    }
  }

  private void requireAlignedTripleCount(int count) {
    if (count == 0 || count > 5 || keyFiles.size() != count || passphraseFiles.size() != count) {
      throw invalidTripleCount();
    }
  }

  private static IllegalArgumentException invalidTripleCount() {
    return CliArgumentValueParser.invalid(
        ProtocolOptions.Attestation.PRINCIPAL_ID,
        "Provide one through five aligned attestation credential triples: "
            + ProtocolOptions.Attestation.PRINCIPAL_ID
            + ", "
            + ProtocolOptions.Attestation.KEY_FILE
            + ", and "
            + ProtocolOptions.Attestation.PASSPHRASE_FILE
            + ".");
  }
}
