package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailure;
import dev.erst.fingrind.executor.AttestationFounderKeyTargetOccupiedException;
import java.util.Objects;

/** Maps an admitted generated founder-key collision to its deterministic no-clobber failure. */
final class CliFounderKeyCollisionFailure {
  private CliFounderKeyCollisionFailure() {}

  static ContractFailure from(AttestationFounderKeyTargetOccupiedException exception) {
    AttestationFounderKeyTargetOccupiedException checkedException =
        Objects.requireNonNull(exception, "exception");
    return ContractErrors.Descriptor.SECRET_TARGET_OCCUPIED.failureAt(
        checkedException.keyFilePath(),
        "Generated attestation founder key target already exists and will not be overwritten.",
        "Choose an absent "
            + ProtocolOptions.Attestation.FOUNDER_KEY_FILE
            + " path before rerunning "
            + OperationId.OPEN_BOOK.wireName()
            + ".",
        ProtocolOptions.Attestation.FOUNDER_KEY_FILE);
  }
}
