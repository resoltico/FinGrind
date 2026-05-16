package dev.erst.fingrind.executor.spi;

/** Reads one selected book's lifecycle state without mutating it. */
@FunctionalInterface
public interface BookLifecycleReader {
  /** Returns one local lifecycle snapshot for the selected book. */
  BookLifecycleInspection inspectBook();
}
