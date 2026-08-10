package dev.erst.fingrind.contract.runtime;

import java.util.Map;

/** Owns descriptor metadata for no-clobber artifact publication and custody failures. */
final class ContractArtifactErrorDescriptors {
  static final ContractErrorDescriptorDefinition ARTIFACT_PUBLICATION_OUTCOME_UNCERTAIN =
      ContractErrorDescriptorDefinitions.precondition(
          "artifact-publication-outcome-uncertain",
          "Artifact publication did not establish whether its candidate final no-clobber name exists after a failed link attempt.",
          4);
  static final ContractErrorDescriptorDefinition ARTIFACT_PUBLICATION_DURABILITY_UNCERTAIN =
      ContractErrorDescriptorDefinitions.precondition(
          "artifact-publication-durability-uncertain",
          "Artifact publication returned a final no-clobber path, but its parent-directory durability is unconfirmed.",
          4);
  static final ContractErrorDescriptorDefinition PUBLICATION_TRANSACTION_INCOMPLETE =
      ContractErrorDescriptorDefinitions.precondition(
          "publication-transaction-incomplete",
          "Publication transaction did not complete; its transaction identifier is the only recovery handle.",
          4);
  static final ContractErrorDescriptorDefinition SECRET_TARGET_OCCUPIED =
      ContractErrorDescriptorDefinitions.precondition(
          "secret-target-occupied",
          "Generated-secret publication refused because the selected target already exists and FinGrind will not overwrite it.",
          7);
  static final ContractErrorDescriptorDefinition ARTIFACT_OUTPUT_ALREADY_EXISTS =
      ContractErrorDescriptorDefinitions.precondition(
          "artifact-output-already-exists",
          "Artifact publication refused because the selected output destination already exists and FinGrind will not overwrite it.",
          7);
  static final ContractErrorDescriptorDefinition INVALID_ARTIFACT_OUTPUT_DIRECTORY =
      ContractErrorDescriptorDefinitions.precondition(
          "invalid-artifact-output-directory",
          "Artifact publication refused because the selected output parent is not an existing real private directory whose resolved ancestry resists non-owner substitution.",
          6);

  private ContractArtifactErrorDescriptors() {}

  static void addTo(Map<ContractErrors.Descriptor, ContractErrorDescriptorDefinition> definitions) {
    definitions.put(
        ContractErrors.Descriptor.ARTIFACT_PUBLICATION_OUTCOME_UNCERTAIN,
        ARTIFACT_PUBLICATION_OUTCOME_UNCERTAIN);
    definitions.put(
        ContractErrors.Descriptor.ARTIFACT_PUBLICATION_DURABILITY_UNCERTAIN,
        ARTIFACT_PUBLICATION_DURABILITY_UNCERTAIN);
    definitions.put(
        ContractErrors.Descriptor.PUBLICATION_TRANSACTION_INCOMPLETE,
        PUBLICATION_TRANSACTION_INCOMPLETE);
    definitions.put(ContractErrors.Descriptor.SECRET_TARGET_OCCUPIED, SECRET_TARGET_OCCUPIED);
    definitions.put(
        ContractErrors.Descriptor.ARTIFACT_OUTPUT_ALREADY_EXISTS, ARTIFACT_OUTPUT_ALREADY_EXISTS);
    definitions.put(
        ContractErrors.Descriptor.INVALID_ARTIFACT_OUTPUT_DIRECTORY,
        INVALID_ARTIFACT_OUTPUT_DIRECTORY);
  }
}
