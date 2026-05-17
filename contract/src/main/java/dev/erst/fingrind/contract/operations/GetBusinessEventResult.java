package dev.erst.fingrind.contract.operations;

import java.util.Objects;

/** Closed result family for one business-event lookup. */
public sealed interface GetBusinessEventResult
    permits GetBusinessEventResult.Found, GetBusinessEventResult.Rejected {
  /** Successful lookup. */
  record Found(BusinessEventRecord businessEventRecord) implements GetBusinessEventResult {
    public Found {
      Objects.requireNonNull(businessEventRecord, "businessEventRecord");
    }
  }

  /** Deterministic refusal. */
  record Rejected(BusinessEventRejection rejection) implements GetBusinessEventResult {
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
