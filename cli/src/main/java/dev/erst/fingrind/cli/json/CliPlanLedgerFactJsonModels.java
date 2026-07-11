package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.copyList;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireValue;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.workflow.LedgerFactKind;
import java.util.List;
import java.util.Objects;

/** Typed ledger-fact JSON payloads emitted by plan execution transport. */
public interface CliPlanLedgerFactJsonModels {

  /** JSON shape for one typed ledger fact observation. */
  sealed interface LedgerFactPayload
      permits TextLedgerFactPayload,
          FlagLedgerFactPayload,
          CountLedgerFactPayload,
          MoneyLedgerFactPayload,
          GroupLedgerFactPayload {}

  record TextLedgerFactPayload(LedgerFactKind kind, String name, String value)
      implements LedgerFactPayload {
    public TextLedgerFactPayload {
      kind = requireValue(kind, "kind");
      name = requireText(name, "name");
      value = requireText(value, "value");
    }
  }

  record FlagLedgerFactPayload(LedgerFactKind kind, String name, boolean value)
      implements LedgerFactPayload {
    public FlagLedgerFactPayload {
      kind = requireValue(kind, "kind");
      name = requireText(name, "name");
    }
  }

  record CountLedgerFactPayload(LedgerFactKind kind, String name, int value)
      implements LedgerFactPayload {
    public CountLedgerFactPayload {
      kind = requireValue(kind, "kind");
      name = requireText(name, "name");
    }
  }

  record MoneyLedgerFactPayload(LedgerFactKind kind, String name, MonetaryAmount value)
      implements LedgerFactPayload {
    public MoneyLedgerFactPayload {
      kind = requireValue(kind, "kind");
      name = requireText(name, "name");
      Objects.requireNonNull(value, "value");
    }
  }

  record GroupLedgerFactPayload(LedgerFactKind kind, String name, List<LedgerFactPayload> facts)
      implements LedgerFactPayload {
    public GroupLedgerFactPayload {
      kind = requireValue(kind, "kind");
      name = requireText(name, "name");
      facts = copyList(facts, "facts");
      if (facts.isEmpty()) {
        throw new IllegalArgumentException("facts must not be empty.");
      }
    }
  }
}
