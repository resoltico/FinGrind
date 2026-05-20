package dev.erst.fingrind.contract.runtime;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;

/** Redacted evidence that identifies the loaded managed-SQLite provenance sidecars. */
public record SqliteRuntimeArtifactEvidence(
    String toolchainFingerprintPath,
    String toolchainFingerprintSha256,
    String buildContractPath,
    String buildContractSha256) {
  public SqliteRuntimeArtifactEvidence {
    toolchainFingerprintPath =
        ContractDescriptorValidation.requireText(
            toolchainFingerprintPath, "toolchainFingerprintPath");
    toolchainFingerprintSha256 =
        ContractDescriptorValidation.requireText(
            toolchainFingerprintSha256, "toolchainFingerprintSha256");
    buildContractPath =
        ContractDescriptorValidation.requireText(buildContractPath, "buildContractPath");
    buildContractSha256 =
        ContractDescriptorValidation.requireText(buildContractSha256, "buildContractSha256");
  }
}
