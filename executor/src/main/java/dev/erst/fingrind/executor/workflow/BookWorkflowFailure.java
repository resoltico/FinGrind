package dev.erst.fingrind.executor.workflow;

import java.util.List;
import java.util.Objects;

/** Internal workflow failure payload before public journal materialization. */
public record BookWorkflowFailure(String code, String message, List<BookWorkflowFact> facts) {
  public BookWorkflowFailure {
    Objects.requireNonNull(code, "code");
    Objects.requireNonNull(message, "message");
    facts = List.copyOf(Objects.requireNonNull(facts, "facts"));
  }
}
