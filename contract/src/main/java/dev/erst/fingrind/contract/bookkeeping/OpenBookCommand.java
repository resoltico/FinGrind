package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.BookIdentity;
import java.util.List;
import java.util.Objects;

/** Explicit initialization command for one new book identity. */
public record OpenBookCommand(
    BookIdentity bookIdentity, List<AttestationFounderInput> attestationFounders) {
  /** Validates one open-book command. */
  public OpenBookCommand {
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    attestationFounders =
        List.copyOf(Objects.requireNonNull(attestationFounders, "attestationFounders"));
    if (attestationFounders.isEmpty() || attestationFounders.size() > 5) {
      throw new IllegalArgumentException(
          "Attested book creation requires between one and five founders.");
    }
    long distinctPrincipals =
        attestationFounders.stream().map(AttestationFounderInput::principalId).distinct().count();
    long distinctKeyPaths =
        attestationFounders.stream()
            .map(AttestationFounderInput::encryptedKeyFilePath)
            .distinct()
            .count();
    if (distinctPrincipals != attestationFounders.size()
        || distinctKeyPaths != attestationFounders.size()) {
      throw new IllegalArgumentException(
          "Attestation founders must have distinct principal identifiers and encrypted key files.");
    }
  }
}
