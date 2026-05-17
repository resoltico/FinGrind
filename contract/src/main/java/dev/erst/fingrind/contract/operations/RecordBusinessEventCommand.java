package dev.erst.fingrind.contract.operations;

import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import java.util.Objects;

/** Application command for preflighting or committing one typed business event. */
public record RecordBusinessEventCommand(
    BusinessEventRequest businessEventRequest,
    RequestProvenance requestProvenance,
    SourceChannel sourceChannel) {
  /** Validates one typed business-event command. */
  public RecordBusinessEventCommand {
    Objects.requireNonNull(businessEventRequest, "businessEventRequest");
    Objects.requireNonNull(requestProvenance, "requestProvenance");
    Objects.requireNonNull(sourceChannel, "sourceChannel");
  }
}
