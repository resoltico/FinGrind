package dev.erst.fingrind.contract.runtime;

/** Internal metadata used to materialize one public deterministic error descriptor. */
record ContractErrorDescriptorDefinition(
    String code, String description, int exitCode, FailureCategory category) {}
