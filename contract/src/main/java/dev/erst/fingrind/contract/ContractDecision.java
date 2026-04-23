package dev.erst.fingrind.contract;

import java.util.Objects;
import java.util.function.Function;

/** Generic accepted-or-rejected decision used at low-level deterministic contract seams. */
public sealed interface ContractDecision<T>
    permits ContractDecision.Accepted, ContractDecision.Rejected {
  /** Maps the accepted and rejected alternatives onto one common result shape. */
  default <R> R fold(
      Function<? super T, ? extends R> acceptedMapper,
      Function<? super ContractFailure, ? extends R> rejectedMapper) {
    Objects.requireNonNull(acceptedMapper, "acceptedMapper");
    Objects.requireNonNull(rejectedMapper, "rejectedMapper");
    return switch (this) {
      case Accepted<T>(T value) -> acceptedMapper.apply(value);
      case Rejected<T>(ContractFailure failure) -> rejectedMapper.apply(failure);
    };
  }

  /** Returns the accepted value or throws when this decision is rejected. */
  default T requireAccepted() {
    return switch (this) {
      case Accepted<T>(T value) -> value;
      case Rejected<T>(ContractFailure failure) -> throw new ContractFailureException(failure);
    };
  }

  /** Returns the rejection payload or throws when this decision was accepted. */
  default ContractFailure requireRejected() {
    return switch (this) {
      case Accepted<T> _ ->
          throw new IllegalStateException("Expected a rejected contract decision.");
      case Rejected<T>(ContractFailure failure) -> failure;
    };
  }

  /** Accepted alternative carrying the produced non-null value. */
  record Accepted<T>(T value) implements ContractDecision<T> {
    /** Validates one accepted decision value. */
    public Accepted {
      Objects.requireNonNull(value, "value");
    }
  }

  /** Rejected alternative carrying the deterministic contract failure. */
  record Rejected<T>(ContractFailure failure) implements ContractDecision<T> {
    /** Validates one rejected contract decision. */
    public Rejected {
      Objects.requireNonNull(failure, "failure");
    }
  }

  /** Creates one accepted contract decision. */
  static <T> ContractDecision<T> accepted(T value) {
    return new Accepted<>(value);
  }

  /** Creates one rejected contract decision. */
  static <T> ContractDecision<T> rejected(ContractFailure failure) {
    return new Rejected<>(failure);
  }
}
