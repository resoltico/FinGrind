package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailure;
import dev.erst.fingrind.core.ArtifactPublicationOutcomeUncertainException;
import dev.erst.fingrind.executor.AttestationCredentialException;
import dev.erst.fingrind.executor.AttestationFounderKeyRetentionException;
import dev.erst.fingrind.executor.AttestationFounderKeyTargetOccupiedException;
import java.util.Objects;

/** Maps founder-key preparation failures onto the open-book public failure contract. */
final class CliOpenBookGenesisFailureMapper {
  private CliOpenBookGenesisFailureMapper() {}

  /** Translates the known preparation failures and preserves unknown runtime failures unchanged. */
  static ContractFailure failureFor(RuntimeException exception) {
    RuntimeException checkedException = Objects.requireNonNull(exception, "exception");
    if (checkedException instanceof AttestationFounderKeyRetentionException retention) {
      return ContractErrors.openBookPreparationArtifactsRetainedFailure(
          retention.retainedFounderKeyArtifacts());
    }
    if (checkedException instanceof ArtifactPublicationOutcomeUncertainException outcome) {
      return ContractErrors.artifactPublicationOutcomeUncertainFailure(
          outcome.candidateArtifactPath(),
          outcome.retainedStage(),
          ProtocolOptions.Attestation.FOUNDER_KEY_FILE);
    }
    if (checkedException instanceof AttestationFounderKeyTargetOccupiedException occupied) {
      return CliFounderKeyCollisionFailure.from(occupied);
    }
    if (checkedException instanceof AttestationCredentialException credential) {
      return ContractErrors.Descriptor.INVALID_ATTESTATION_CREDENTIAL.failureAt(
          credential.credentialPath(),
          "FinGrind could not open the selected attestation founder credential.",
          "Confirm the founder key and passphrase files are readable, distinct, and match, then"
              + " rerun "
              + OperationId.OPEN_BOOK.wireName()
              + ".",
          ProtocolOptions.Attestation.FOUNDER_KEY_FILE);
    }
    throw checkedException;
  }
}
