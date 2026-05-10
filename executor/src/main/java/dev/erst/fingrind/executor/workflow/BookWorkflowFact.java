package dev.erst.fingrind.executor.workflow;

import dev.erst.fingrind.contract.MonetaryAmount;
import java.util.List;
import java.util.Objects;

/** Local machine-readable fact recorded while executing one workflow step. */
public sealed interface BookWorkflowFact
    permits BookWorkflowFact.Text,
        BookWorkflowFact.Flag,
        BookWorkflowFact.Count,
        BookWorkflowFact.Money,
        BookWorkflowFact.Group {
  /** Stable fact name within one workflow journal entry. */
  String name();

  /** Creates one string-valued fact. */
  static Text text(String name, String value) {
    return new Text(name, value);
  }

  /** Creates one boolean-valued fact. */
  static Flag flag(String name, boolean value) {
    return new Flag(name, value);
  }

  /** Creates one integer-valued fact. */
  static Count count(String name, int value) {
    return new Count(name, value);
  }

  /** Creates one exact-money-valued fact. */
  static Money money(String name, MonetaryAmount value) {
    return new Money(name, value);
  }

  /** Creates one structured fact group. */
  static Group group(String name, List<BookWorkflowFact> facts) {
    return new Group(name, facts);
  }

  private static void requireName(String name) {
    Objects.requireNonNull(name, "name");
    if (name.isBlank()) {
      throw new IllegalArgumentException("Workflow fact name must not be blank.");
    }
  }

  /** String-valued workflow fact. */
  record Text(String name, String value) implements BookWorkflowFact {
    /** Creates one validated string-valued workflow fact. */
    public Text(String name, String value) {
      requireName(name);
      Objects.requireNonNull(value, "value");
      if (value.isBlank()) {
        throw new IllegalArgumentException("Workflow fact value must not be blank.");
      }
      this.name = name;
      this.value = value;
    }
  }

  /** Boolean-valued workflow fact. */
  record Flag(String name, boolean value) implements BookWorkflowFact {
    /** Creates one validated boolean-valued workflow fact. */
    public Flag(String name, boolean value) {
      requireName(name);
      this.name = name;
      this.value = value;
    }
  }

  /** Integer-valued workflow fact. */
  record Count(String name, int value) implements BookWorkflowFact {
    /** Creates one validated integer-valued workflow fact. */
    public Count(String name, int value) {
      requireName(name);
      this.name = name;
      this.value = value;
    }
  }

  /** Exact-money-valued workflow fact. */
  record Money(String name, MonetaryAmount value) implements BookWorkflowFact {
    /** Creates one validated exact-money-valued workflow fact. */
    public Money(String name, MonetaryAmount value) {
      requireName(name);
      this.name = name;
      this.value = Objects.requireNonNull(value, "value");
    }
  }

  /** Structured nested workflow fact group. */
  record Group(String name, List<BookWorkflowFact> facts) implements BookWorkflowFact {
    /** Creates one validated grouped workflow fact. */
    public Group(String name, List<BookWorkflowFact> facts) {
      requireName(name);
      List<BookWorkflowFact> copiedFacts = List.copyOf(Objects.requireNonNull(facts, "facts"));
      if (copiedFacts.isEmpty()) {
        throw new IllegalArgumentException("Grouped workflow facts must not be empty.");
      }
      this.name = name;
      this.facts = copiedFacts;
    }
  }
}
