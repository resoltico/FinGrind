package dev.erst.fingrind.contract;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** One deterministic CLI-visible failure carrying a contract-owned error code and repair hints. */
public final class ContractErrorException extends IllegalStateException {
  private static final long serialVersionUID = 1L;

  private final ContractErrors.Descriptor descriptor;
  private final @Nullable String hint;
  private final @Nullable String argument;

  /** Creates one deterministic failure with its canonical contract descriptor. */
  public ContractErrorException(
      ContractErrors.Descriptor descriptor,
      String message,
      @Nullable String hint,
      @Nullable String argument) {
    super(message);
    this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
    this.hint = hint;
    this.argument = argument;
  }

  /** Creates one deterministic failure with its canonical contract descriptor and root cause. */
  public ContractErrorException(
      ContractErrors.Descriptor descriptor,
      String message,
      @Nullable String hint,
      @Nullable String argument,
      Throwable cause) {
    super(message, cause);
    this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
    this.hint = hint;
    this.argument = argument;
  }

  /** Returns the stable contract descriptor for this deterministic failure. */
  public ContractErrors.Descriptor descriptor() {
    return descriptor;
  }

  /** Returns the stable machine error code for this deterministic failure. */
  public String code() {
    return descriptor.code();
  }

  /** Returns the optional operator hint for repairing the invocation. */
  public @Nullable String hint() {
    return hint;
  }

  /** Returns the optional argument associated with the deterministic failure. */
  public @Nullable String argument() {
    return argument;
  }
}
