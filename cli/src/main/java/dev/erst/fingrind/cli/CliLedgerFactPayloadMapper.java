package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliPlanJsonModels;
import dev.erst.fingrind.contract.workflow.LedgerFact;
import dev.erst.fingrind.contract.workflow.LedgerFactKind;
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
          new CliPlanJsonModels.TextLedgerFactPayload(
              LedgerFactKind.TEXT, text.name(), text.value());
      case LedgerFact.Flag flag ->
          new CliPlanJsonModels.FlagLedgerFactPayload(
              LedgerFactKind.FLAG, flag.name(), flag.value());
      case LedgerFact.Count count ->
          new CliPlanJsonModels.CountLedgerFactPayload(
              LedgerFactKind.COUNT, count.name(), count.value());
      case LedgerFact.Money money ->
          new CliPlanJsonModels.MoneyLedgerFactPayload(
              LedgerFactKind.MONEY, money.name(), money.value());
      case LedgerFact.Group group ->
          new CliPlanJsonModels.GroupLedgerFactPayload(
              LedgerFactKind.GROUP, group.name(), factPayloads(group.facts()));
    };
  }
}
