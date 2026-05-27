package dev.erst.fingrind.contract.protocol;

/** Public domain-facts catalog for the executable bookkeeping contract. */
public final class ProtocolDomainCatalog {
  static final ProtocolDomainCatalog INSTANCE = new ProtocolDomainCatalog();

  private ProtocolDomainCatalog() {}

  /** Returns the structured hard book-model facts. */
  public BookModelFacts bookModel() {
    return ProtocolCatalogFacts.bookModel();
  }

  /** Returns the structured currency-model facts. */
  public CurrencyFacts currency() {
    return ProtocolCatalogFacts.currency();
  }

  /** Returns the structured executable bookkeeping-kernel facts. */
  public BookkeepingKernelFacts bookkeepingKernel() {
    return ProtocolCatalogFacts.bookkeepingKernel();
  }

  /** Returns the structured preflight semantics. */
  public PreflightFacts preflight() {
    return ProtocolCatalogFacts.preflight();
  }

  /** Returns the structured ledger-plan execution semantics. */
  public PlanExecutionFacts planExecution() {
    return ProtocolCatalogFacts.planExecution();
  }
}
