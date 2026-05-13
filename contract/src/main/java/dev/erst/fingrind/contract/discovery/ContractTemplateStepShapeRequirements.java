package dev.erst.fingrind.contract.discovery;

/** Presence policy for one ledger-plan step template shape. */
record ContractTemplateStepShapeRequirements(
    ContractTemplateFieldPresence posting,
    ContractTemplateFieldPresence declareAccount,
    ContractTemplateFieldPresence query,
    ContractTemplateFieldPresence assertion,
    ContractTemplateFieldPresence postingId,
    boolean queryAccountCodeRequired) {}
