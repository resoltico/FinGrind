package dev.erst.fingrind.contract.operations;

import dev.erst.fingrind.core.BusinessEventId;
import java.util.Objects;

/** Closed result family for committed business-event commands. */
public sealed interface CommitBusinessEventResult
    permits CommitBusinessEventResult.Committed, CommitBusinessEventResult.Rejected {
  /** Successful durable business-event commit. */
  record Committed(BusinessEventRecord businessEventRecord) implements CommitBusinessEventResult {
    public Committed {
      Objects.requireNonNull(businessEventRecord, "businessEventRecord");
    }
  }

  /** Deterministic refusal. */
  record Rejected(BusinessEventId businessEventId, BusinessEventRejection rejection)
      implements CommitBusinessEventResult {
    public Rejected {
      Objects.requireNonNull(businessEventId, "businessEventId");
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
