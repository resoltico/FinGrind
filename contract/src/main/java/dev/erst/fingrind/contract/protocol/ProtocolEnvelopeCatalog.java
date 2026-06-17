package dev.erst.fingrind.contract.protocol;

import java.util.List;

/** Public envelope-status catalog for FinGrind protocol responses. */
public final class ProtocolEnvelopeCatalog {
  static final ProtocolEnvelopeCatalog INSTANCE = new ProtocolEnvelopeCatalog();

  private ProtocolEnvelopeCatalog() {}

  /** Returns the canonical top-level JSON-envelope statuses. */
  public List<ProtocolEnvelopeStatus> statuses() {
    return ProtocolCatalogFacts.ENVELOPE_STATUSES;
  }

  /** Returns the canonical success envelope status. */
  public ProtocolEnvelopeStatus successStatus() {
    return ProtocolCatalogFacts.SUCCESS_STATUS;
  }

  /** Returns the canonical deterministic rejection envelope status. */
  public ProtocolEnvelopeStatus rejectionStatus() {
    return ProtocolCatalogFacts.REJECTION_STATUS;
  }

  /** Returns the canonical runtime or invalid-request error envelope status. */
  public ProtocolEnvelopeStatus errorStatus() {
    return ProtocolCatalogFacts.ERROR_STATUS;
  }
}
