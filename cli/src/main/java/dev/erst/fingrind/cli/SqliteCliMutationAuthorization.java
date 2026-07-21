package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.core.attestation.AttestationOperationAuthorizer;
import dev.erst.fingrind.executor.AttestationCredentialException;
import dev.erst.fingrind.executor.AttestationMutationAuthorization;
import dev.erst.fingrind.executor.spi.BookLifecycleReader;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Resolves attestation authorization only after a mutation-capable book session establishes state.
 */
final class SqliteCliMutationAuthorization {
  private SqliteCliMutationAuthorization() {}

  static <T> ContractDecision<T> withAttestationAuthorization(
      BookAccess bookAccess, Function<AttestationOperationAuthorizer, ContractDecision<T>> action) {
    Objects.requireNonNull(bookAccess, "bookAccess");
    Objects.requireNonNull(action, "action");
    if (bookAccess.attestationCredentialSources().isEmpty()) {
      return ContractDecision.rejected(
          ContractErrors.Descriptor.INVALID_ATTESTATION_CREDENTIAL.failureAt(
              bookAccess.bookFilePath(),
              "Protected-book mutation requires at least one explicit attestation credential.",
              "Provide aligned "
                  + ProtocolOptions.Attestation.PRINCIPAL_ID
                  + ", "
                  + ProtocolOptions.Attestation.KEY_FILE
                  + ", and "
                  + ProtocolOptions.Attestation.PASSPHRASE_FILE
                  + " arguments.",
              ProtocolOptions.Attestation.PRINCIPAL_ID));
    }
    try {
      return AttestationMutationAuthorization.withAuthorizer(
          bookAccess.requireAttestationCredentialSources(), action);
    } catch (AttestationCredentialException exception) {
      return ContractDecision.rejected(
          ContractErrors.Descriptor.INVALID_ATTESTATION_CREDENTIAL.failureAt(
              exception.credentialPath(),
              "FinGrind could not open the selected attestation authorization credential.",
              "Confirm the credential key and passphrase files are readable, distinct, and match.",
              ProtocolOptions.Attestation.KEY_FILE));
    }
  }

  static <T> ContractDecision<T> withInitializedBook(
      BookLifecycleReader lifecycleReader,
      Supplier<ContractDecision<T>> initializedBookAction,
      Supplier<T> missingBookResult) {
    Objects.requireNonNull(lifecycleReader, "lifecycleReader");
    Objects.requireNonNull(initializedBookAction, "initializedBookAction");
    Objects.requireNonNull(missingBookResult, "missingBookResult");
    return lifecycleReader.allowsInitializedWorkflow()
        ? initializedBookAction.get()
        : ContractDecision.accepted(missingBookResult.get());
  }
}
