package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliPlanLedgerFactJsonModels;
import dev.erst.fingrind.contract.workflow.LedgerFact;
import dev.erst.fingrind.contract.workflow.LedgerFactKind;
import java.util.List;

/** Maps low-level ledger facts into generic JSON payload nodes. */
final class CliLedgerFactPayloadMapper {
  private CliLedgerFactPayloadMapper() {}

  static List<CliPlanLedgerFactJsonModels.LedgerFactPayload> factPayloads(List<LedgerFact> facts) {
    return facts.stream().map(CliLedgerFactPayloadMapper::ledgerFactPayload).toList();
  }

  private static CliPlanLedgerFactJsonModels.LedgerFactPayload ledgerFactPayload(LedgerFact fact) {
    return switch (fact) {
      case LedgerFact.Text text ->
          new CliPlanLedgerFactJsonModels.TextLedgerFactPayload(
              LedgerFactKind.TEXT, text.name(), text.value());
      case LedgerFact.Flag flag ->
          new CliPlanLedgerFactJsonModels.FlagLedgerFactPayload(
              LedgerFactKind.FLAG, flag.name(), flag.value());
      case LedgerFact.Count count ->
          new CliPlanLedgerFactJsonModels.CountLedgerFactPayload(
              LedgerFactKind.COUNT, count.name(), count.value());
      case LedgerFact.Money money ->
          new CliPlanLedgerFactJsonModels.MoneyLedgerFactPayload(
              LedgerFactKind.MONEY, money.name(), money.value());
      case LedgerFact.Group group ->
          new CliPlanLedgerFactJsonModels.GroupLedgerFactPayload(
              LedgerFactKind.GROUP, group.name(), factPayloads(group.facts()));
    };
  }
}
