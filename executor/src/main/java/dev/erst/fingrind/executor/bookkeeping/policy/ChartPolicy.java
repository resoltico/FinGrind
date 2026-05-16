package dev.erst.fingrind.executor.bookkeeping.policy;

/** Operational seam for chart-taxonomy and statement-line policy. */
@FunctionalInterface
public interface ChartPolicy {
  /** Returns whether the current pack supports hierarchical chart taxonomy in the kernel. */
  boolean supportsHierarchicalChart();
}
