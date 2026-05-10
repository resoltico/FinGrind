package dev.erst.fingrind.cli;

import static dev.erst.fingrind.cli.CliJsonFieldAccess.rejectUnexpectedFields;
import static dev.erst.fingrind.cli.CliJsonFieldAccess.requiredObject;
import static dev.erst.fingrind.cli.CliJsonFieldAccess.requiredText;

import dev.erst.fingrind.contract.MonetaryAmount;
import dev.erst.fingrind.contract.protocol.ProtocolMoneyFields;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PositiveMoney;
import java.util.Set;
import tools.jackson.databind.node.ObjectNode;

/** Parses exact money objects from CLI JSON request payloads. */
final class CliJsonMoneyParser {
  private static final Set<String> MONEY_FIELDS = Set.copyOf(ProtocolMoneyFields.fields());

  private CliJsonMoneyParser() {}

  static Money requiredMoney(ObjectNode parentNode, String fieldName) {
    ObjectNode moneyNode = requiredObject(parentNode, fieldName);
    rejectUnexpectedFields(moneyNode, fieldName, MONEY_FIELDS);
    return new MonetaryAmount(
            requiredText(moneyNode, ProtocolMoneyFields.CURRENCY_CODE),
            requiredText(moneyNode, ProtocolMoneyFields.MINOR_UNITS))
        .toMoney();
  }

  static PositiveMoney requiredPositiveMoney(ObjectNode parentNode, String fieldName) {
    return PositiveMoney.of(requiredMoney(parentNode, fieldName));
  }
}
