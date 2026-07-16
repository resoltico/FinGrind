package dev.erst.fingrind.executor.maintenance;

import java.util.Objects;

/** Internal control-flow exception that carries one deterministic maintenance refusal. */
public final class ProtectedBookMaintenanceRejectionException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  private final ProtectedBookMaintenanceRejection rejection;

  /** Creates the control-flow exception for one already-classified maintenance rejection. */
  public ProtectedBookMaintenanceRejectionException(ProtectedBookMaintenanceRejection rejection) {
    super(Objects.requireNonNull(rejection, "rejection").toString());
    this.rejection = rejection;
  }

  /** Creates the control-flow exception while retaining the storage failure that triggered it. */
  public ProtectedBookMaintenanceRejectionException(
      ProtectedBookMaintenanceRejection rejection, Throwable cause) {
    super(Objects.requireNonNull(rejection, "rejection").toString(), cause);
    this.rejection = rejection;
  }

  /** Returns the deterministic maintenance rejection carried by this control-flow exception. */
  public ProtectedBookMaintenanceRejection rejection() {
    return rejection;
  }
}
