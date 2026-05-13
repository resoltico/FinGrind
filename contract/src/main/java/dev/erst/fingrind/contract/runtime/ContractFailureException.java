package dev.erst.fingrind.contract.runtime;

import java.util.Objects;

/** Runtime exception that preserves one deterministic contract failure across imperative seams. */
public final class ContractFailureException extends IllegalStateException {
  private static final long serialVersionUID = 1L;

  private final transient ContractFailure failure;

  /** Creates one runtime wrapper around the supplied deterministic contract failure. */
  public ContractFailureException(ContractFailure failure) {
    super(Objects.requireNonNull(failure, "failure").message());
    this.failure = failure;
  }

  /** Returns the preserved deterministic contract failure. */
  public ContractFailure failure() {
    return failure;
  }
}
