package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.workflow.LedgerFact;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Reads typed ledger facts from workflow journal entries. */
final class CliLedgerFactAccess {
  private CliLedgerFactAccess() {}

  static String requiredTextFact(List<LedgerFact> facts, String name) {
    @Nullable String value = optionalTextFact(facts, name);
    if (value == null) {
      throw missingFact(name);
    }
    return value;
  }

  static @Nullable String optionalTextFact(List<LedgerFact> facts, String name) {
    for (LedgerFact fact : facts) {
      if (fact instanceof LedgerFact.Text text && text.name().equals(name)) {
        return text.value();
      }
    }
    return null;
  }

  static boolean requiredFlagFact(List<LedgerFact> facts, String name) {
    for (LedgerFact fact : facts) {
      if (fact instanceof LedgerFact.Flag flag && flag.name().equals(name)) {
        return flag.value();
      }
    }
    throw missingFact(name);
  }

  static int requiredCountFact(List<LedgerFact> facts, String name) {
    for (LedgerFact fact : facts) {
      if (fact instanceof LedgerFact.Count count && count.name().equals(name)) {
        return count.value();
      }
    }
    throw missingFact(name);
  }

  static MonetaryAmount requiredMoneyFact(List<LedgerFact> facts, String name) {
    for (LedgerFact fact : facts) {
      if (fact instanceof LedgerFact.Money money && money.name().equals(name)) {
        return money.value();
      }
    }
    throw missingFact(name);
  }

  static List<List<LedgerFact>> groupedFacts(List<LedgerFact> facts, String name) {
    return facts.stream()
        .filter(fact -> fact instanceof LedgerFact.Group group && group.name().equals(name))
        .map(fact -> ((LedgerFact.Group) fact).facts())
        .toList();
  }

  static List<LedgerFact> requiredGroupFacts(List<LedgerFact> facts, String name) {
    @Nullable List<LedgerFact> groupFacts = optionalGroupFacts(facts, name);
    if (groupFacts == null) {
      throw missingFact(name);
    }
    return groupFacts;
  }

  static @Nullable List<LedgerFact> optionalGroupFacts(List<LedgerFact> facts, String name) {
    return groupedFacts(facts, name).stream().findFirst().orElse(null);
  }

  static List<String> textFacts(List<LedgerFact> facts, String name) {
    return facts.stream()
        .filter(fact -> fact instanceof LedgerFact.Text text && text.name().equals(name))
        .map(fact -> ((LedgerFact.Text) fact).value())
        .toList();
  }

  private static IllegalArgumentException missingFact(String name) {
    return new IllegalArgumentException("Missing ledger fact: " + name);
  }
}
