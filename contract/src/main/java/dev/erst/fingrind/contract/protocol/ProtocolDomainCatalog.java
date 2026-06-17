package dev.erst.fingrind.contract.protocol;

/** Public domain-facts catalog for the executable bookkeeping contract. */
public final class ProtocolDomainCatalog {
  static final ProtocolDomainCatalog INSTANCE = new ProtocolDomainCatalog();

  private ProtocolDomainCatalog() {}

  /** Returns the structured hard book-model facts. */
  public BookModelFacts bookModel() {
    return ProtocolCatalogFacts.BOOK_MODEL;
  }

  /** Returns the structured currency-model facts. */
  public CurrencyFacts currency() {
    return ProtocolCatalogFacts.CURRENCY;
  }

  /** Returns the structured executable bookkeeping-kernel facts. */
  public BookkeepingKernelFacts bookkeepingKernel() {
    return ProtocolCatalogFacts.BOOKKEEPING_KERNEL;
  }

  /** Returns the structured preflight semantics. */
  public PreflightFacts preflight() {
    return ProtocolCatalogFacts.PREFLIGHT;
  }

  /** Returns the structured ledger-plan execution semantics. */
  public PlanExecutionFacts planExecution() {
    return ProtocolCatalogFacts.PLAN_EXECUTION;
  }

  /** Returns the structured request-surface facts shared by discovery and validation. */
  public RequestSurfaceFacts requestSurface() {
    return ProtocolCatalogFacts.REQUEST_SURFACE;
  }
}
