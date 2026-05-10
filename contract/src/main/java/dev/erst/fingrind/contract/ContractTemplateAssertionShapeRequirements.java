package dev.erst.fingrind.contract;

/** Presence policy for one ledger-assertion template shape. */
record ContractTemplateAssertionShapeRequirements(
    ContractTemplateFieldPresence accountCode,
    ContractTemplateFieldPresence effectiveDateFrom,
    ContractTemplateFieldPresence effectiveDateTo,
    ContractTemplateFieldPresence netAmount,
    ContractTemplateFieldPresence balanceSide,
    ContractTemplateFieldPresence postingId) {}
