package dev.erst.fingrind.contract.reportmodel;

/** One deterministic builder from a family-owned contract result to a shared report model. */
@FunctionalInterface
public interface ReportModelBuilder<T> {
  /** Builds one shared report model from one family-owned reported result. */
  ReportModel build(T value);
}
