package dev.erst.fingrind.executor.workflow;

import dev.erst.fingrind.contract.LedgerFact;
import java.util.List;
import java.util.Objects;

/** Internal workflow failure payload before public journal materialization. */
public record BookWorkflowFailure(String code, String message, List<LedgerFact> facts) {
  public BookWorkflowFailure {
    Objects.requireNonNull(code, "code");
    Objects.requireNonNull(message, "message");
    facts = facts == null ? List.of() : List.copyOf(facts);
  }
}
