package dev.erst.fingrind.executor.maintenance;

import java.util.Objects;
import java.util.function.Function;

/** Local accepted-or-failed decision used inside protected-book maintenance orchestration. */
public sealed interface MaintenanceDecision<T>
    permits MaintenanceDecision.Accepted, MaintenanceDecision.Failed {
  /** Folds this local decision into one caller-owned result. */
  default <R> R fold(
      Function<? super T, ? extends R> acceptedMapper,
      Function<? super MaintenanceFailure, ? extends R> failedMapper) {
    Objects.requireNonNull(acceptedMapper, "acceptedMapper");
    Objects.requireNonNull(failedMapper, "failedMapper");
    return switch (this) {
      case Accepted<T>(T value) -> acceptedMapper.apply(value);
      case Failed<T>(MaintenanceFailure failure) -> failedMapper.apply(failure);
    };
  }

  /** Returns one accepted local decision carrying a non-null value. */
  static <T> MaintenanceDecision<T> accepted(T value) {
    return new Accepted<>(value);
  }

  /** Returns one failed local decision carrying one local failure. */
  static <T> MaintenanceDecision<T> failed(MaintenanceFailure failure) {
    return new Failed<>(failure);
  }

  /** Accepted alternative carrying one non-null local value. */
  record Accepted<T>(T value) implements MaintenanceDecision<T> {
    public Accepted {
      Objects.requireNonNull(value, "value");
    }
  }

  /** Failed alternative carrying one local failure. */
  record Failed<T>(MaintenanceFailure failure) implements MaintenanceDecision<T> {
    public Failed {
      Objects.requireNonNull(failure, "failure");
    }
  }
}
