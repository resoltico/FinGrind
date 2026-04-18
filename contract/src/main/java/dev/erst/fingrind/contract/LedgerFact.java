package dev.erst.fingrind.contract;

import java.util.List;
import java.util.Objects;

/** One compact name/value fact recorded for a ledger-plan step. */
public sealed interface LedgerFact
    permits LedgerFact.Text, LedgerFact.Flag, LedgerFact.Count, LedgerFact.Group {
  /** Stable fact name within a step journal entry. */
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

  /** Creates one grouped fact that nests a structured set of child facts. */
  static Group group(String name, List<LedgerFact> facts) {
    return new Group(name, facts);
  }

  private static void requireName(String name) {
    Objects.requireNonNull(name, "name");
    if (name.isBlank()) {
      throw new IllegalArgumentException("Ledger fact name must not be blank.");
    }
  }

  /** String-valued journal fact. */
  record Text(String name, String value) implements LedgerFact {
    /** Validates one string-valued journal fact. */
    public Text {
      requireName(name);
      Objects.requireNonNull(value, "value");
    }
  }

  /** Boolean-valued journal fact. */
  record Flag(String name, boolean value) implements LedgerFact {
    /** Validates one boolean-valued journal fact. */
    public Flag {
      requireName(name);
    }
  }

  /** Integer-valued journal fact. */
  record Count(String name, int value) implements LedgerFact {
    /** Validates one integer-valued journal fact. */
    public Count {
      requireName(name);
    }
  }

  /** Structured nested fact group for one repeatable machine-readable observation. */
  record Group(String name, List<LedgerFact> facts) implements LedgerFact {
    /** Validates one grouped journal fact. */
    public Group {
      requireName(name);
      facts = List.copyOf(Objects.requireNonNull(facts, "facts"));
      if (facts.isEmpty()) {
        throw new IllegalArgumentException("Grouped ledger facts must not be empty.");
      }
    }
  }
}
