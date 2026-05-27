package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliPlanJsonModels;
import dev.erst.fingrind.contract.workflow.LedgerFact;
import java.util.List;

/** Maps low-level ledger facts into generic JSON payload nodes. */
final class CliLedgerFactPayloadMapper {
  private CliLedgerFactPayloadMapper() {}

  static List<CliPlanJsonModels.LedgerFactPayload> factPayloads(List<LedgerFact> facts) {
    return facts.stream().map(CliLedgerFactPayloadMapper::ledgerFactPayload).toList();
  }

  private static CliPlanJsonModels.LedgerFactPayload ledgerFactPayload(LedgerFact fact) {
    return switch (fact) {
      case LedgerFact.Text text ->
          new CliPlanJsonModels.TextLedgerFactPayload("text", text.name(), text.value());
      case LedgerFact.Flag flag ->
          new CliPlanJsonModels.FlagLedgerFactPayload("flag", flag.name(), flag.value());
      case LedgerFact.Count count ->
          new CliPlanJsonModels.CountLedgerFactPayload("count", count.name(), count.value());
      case LedgerFact.Money money ->
          new CliPlanJsonModels.MoneyLedgerFactPayload("money", money.name(), money.value());
      case LedgerFact.Group group ->
          new CliPlanJsonModels.GroupLedgerFactPayload(
              "group", group.name(), factPayloads(group.facts()));
    };
  }
}
