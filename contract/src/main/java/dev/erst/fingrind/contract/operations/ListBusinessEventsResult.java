package dev.erst.fingrind.contract.operations;

import java.util.Objects;

/** Closed result family for paged business-event listing. */
public sealed interface ListBusinessEventsResult
    permits ListBusinessEventsResult.Listed, ListBusinessEventsResult.Rejected {
  /** Successful page result. */
  record Listed(BusinessEventPage businessEventPage) implements ListBusinessEventsResult {
    public Listed {
      Objects.requireNonNull(businessEventPage, "businessEventPage");
    }
  }

  /** Deterministic refusal. */
  record Rejected(BusinessEventRejection rejection) implements ListBusinessEventsResult {
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
