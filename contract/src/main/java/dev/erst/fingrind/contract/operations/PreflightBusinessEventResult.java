package dev.erst.fingrind.contract.operations;

import dev.erst.fingrind.core.BusinessEventId;
import dev.erst.fingrind.core.BusinessEventKind;
import java.time.LocalDate;
import java.util.Objects;

/** Closed result family for business-event preflight. */
public sealed interface PreflightBusinessEventResult
    permits PreflightBusinessEventResult.Accepted, PreflightBusinessEventResult.Rejected {
  /** Successful validation outcome. */
  record Accepted(
      BusinessEventId businessEventId, BusinessEventKind businessEventKind, LocalDate effectiveDate)
      implements PreflightBusinessEventResult {
    public Accepted {
      Objects.requireNonNull(businessEventId, "businessEventId");
      Objects.requireNonNull(businessEventKind, "businessEventKind");
      Objects.requireNonNull(effectiveDate, "effectiveDate");
    }
  }

  /** Deterministic refusal. */
  record Rejected(BusinessEventId businessEventId, BusinessEventRejection rejection)
      implements PreflightBusinessEventResult {
    public Rejected {
      Objects.requireNonNull(businessEventId, "businessEventId");
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
