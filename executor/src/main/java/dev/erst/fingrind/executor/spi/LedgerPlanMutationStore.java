package dev.erst.fingrind.executor.spi;

/** Composite capability for the only mutation families valid inside an aggregate ledger plan. */
public interface LedgerPlanMutationStore
    extends PlanAccountDeclarationStore, PlanTaxRegistrationStore, PlanPostingCommitStore {}
