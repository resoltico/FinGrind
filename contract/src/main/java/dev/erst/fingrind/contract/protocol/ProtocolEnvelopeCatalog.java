package dev.erst.fingrind.contract.protocol;

import java.util.List;

/** Public envelope-status catalog for FinGrind protocol responses. */
public final class ProtocolEnvelopeCatalog {
  static final ProtocolEnvelopeCatalog INSTANCE = new ProtocolEnvelopeCatalog();

  private ProtocolEnvelopeCatalog() {}

  /** Returns the canonical top-level JSON-envelope statuses. */
  public List<ProtocolEnvelopeStatus> statuses() {
    return ProtocolCatalogFacts.envelopeStatuses();
  }

  /** Returns the canonical success envelope status. */
  public ProtocolEnvelopeStatus successStatus() {
    return ProtocolCatalogFacts.successStatus();
  }

  /** Returns the canonical deterministic rejection envelope status. */
  public ProtocolEnvelopeStatus rejectionStatus() {
    return ProtocolCatalogFacts.rejectionStatus();
  }

  /** Returns the canonical runtime or invalid-request error envelope status. */
  public ProtocolEnvelopeStatus errorStatus() {
    return ProtocolCatalogFacts.errorStatus();
  }
}
