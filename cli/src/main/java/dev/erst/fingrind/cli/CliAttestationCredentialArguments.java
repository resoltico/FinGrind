package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.core.attestation.AttestationAuthorizationLimits;
import dev.erst.fingrind.core.attestation.AttestationCredentialSource;
import dev.erst.fingrind.core.attestation.AttestationCustodian;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Parses ordered attestation credential selections under one explicit custody selection. */
final class CliAttestationCredentialArguments {
  private final List<UUID> principalIds = new ArrayList<>();
  private final List<Path> keyFiles = new ArrayList<>();
  private final List<Path> passphraseFiles = new ArrayList<>();
  private @Nullable AttestationCustodian custodian;

  boolean apply(String argument, ListIterator<String> argumentIterator) {
    switch (argument) {
      case ProtocolOptions.Attestation.CUSTODIAN -> {
        if (custodian != null) {
          throw CliArgumentValueParser.invalid(argument, "Duplicate argument: " + argument);
        }
        custodian = CliAttestationCustodianArgument.require(argumentIterator);
      }
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
    if (count == 0 && keyFiles.isEmpty() && passphraseFiles.isEmpty() && custodian == null) {
      return List.of();
    }
    requireAlignedCredentialCount(count);
    AttestationCustodian selectedCustodian = requireCustodian();
    List<AttestationCredentialSource> sources = new ArrayList<>(count);
    for (int index = 0; index < count; index++) {
      sources.add(
          new AttestationCredentialSource(
              selectedCustodian,
              principalIds.get(index),
              keyFiles.get(index),
              passphraseFiles.get(index)));
    }
    return List.copyOf(sources);
  }

  static void requirePresent(BookAccess bookAccess) {
    Objects.requireNonNull(bookAccess, "bookAccess");
    if (bookAccess.attestationCredentialSources().isEmpty()) {
      throw invalidCredentialCount();
    }
  }

  private void requireAlignedCredentialCount(int count) {
    if (count == 0
        || count > AttestationAuthorizationLimits.MAXIMUM_QUORUM
        || keyFiles.size() != count
        || passphraseFiles.size() != count) {
      throw invalidCredentialCount();
    }
  }

  private AttestationCustodian requireCustodian() {
    if (custodian == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.Attestation.CUSTODIAN,
          "A "
              + ProtocolOptions.Attestation.CUSTODIAN
              + " argument is required for every attestation credential selection.");
    }
    return custodian;
  }

  private static IllegalArgumentException invalidCredentialCount() {
    return CliArgumentValueParser.invalid(
        ProtocolOptions.Attestation.PRINCIPAL_ID,
        "Provide one through "
            + AttestationAuthorizationLimits.MAXIMUM_QUORUM
            + " aligned attestation credential triplets after selecting "
            + ProtocolOptions.Attestation.CUSTODIAN
            + ": "
            + ProtocolOptions.Attestation.PRINCIPAL_ID
            + ", "
            + ProtocolOptions.Attestation.KEY_FILE
            + ", and "
            + ProtocolOptions.Attestation.PASSPHRASE_FILE
            + ".");
  }
}
