package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailure;
import java.util.Objects;

/** Builds caller-facing failures for attestation credential selection before custody is opened. */
final class CliAttestationCredentialFailures {
  private CliAttestationCredentialFailures() {}

  /** Returns the canonical refusal for a protected-book mutation with no credential selection. */
  static ContractFailure missingMutationCredentials(BookAccess bookAccess) {
    BookAccess selectedBookAccess = Objects.requireNonNull(bookAccess, "bookAccess");
    return ContractErrors.Descriptor.INVALID_ATTESTATION_CREDENTIAL.failureAt(
        selectedBookAccess.bookFilePath(),
        "Protected-book mutation requires at least one explicit attestation credential.",
        "Provide aligned "
            + ProtocolOptions.Attestation.PRINCIPAL_ID
            + ", "
            + ProtocolOptions.Attestation.KEY_FILE
            + ", and "
            + ProtocolOptions.Attestation.PASSPHRASE_FILE
            + " arguments.",
        ProtocolOptions.Attestation.PRINCIPAL_ID);
  }
}
