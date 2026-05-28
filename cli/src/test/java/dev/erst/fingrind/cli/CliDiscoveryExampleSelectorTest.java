package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.contract.protocol.OperationId;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link CliDiscoveryExampleSelector}. */
class CliDiscoveryExampleSelectorTest {
  @Test
  void selectPrimaryCommandExample_prefersDirectPrefixTokenAndFirstFallbacksInOrder() {
    String openBookCommand = CliInvocationText.commandExample(OperationId.OPEN_BOOK);
    String helpCommand = CliInvocationText.commandExample(OperationId.HELP);
    String trialBalanceCommand = CliInvocationText.commandExample(OperationId.TRIAL_BALANCE);
    String capabilitiesCommand = CliInvocationText.commandExample(OperationId.CAPABILITIES);

    assertEquals(
        openBookCommand + " --book-file ./books/acme.sqlite",
        CliDiscoveryExampleSelector.selectPrimaryCommandExample(
            OperationId.OPEN_BOOK,
            List.of(
                openBookCommand + " --book-file ./books/acme.sqlite",
                "cat ./secrets/acme.book-key | " + openBookCommand + " --book-passphrase-stdin")));

    assertEquals(
        helpCommand + " post-entry",
        CliDiscoveryExampleSelector.selectPrimaryCommandExample(
            OperationId.HELP,
            List.of(helpCommand + " post-entry", helpCommand + " post-entry --output json")));

    assertEquals(
        helpCommand,
        CliDiscoveryExampleSelector.selectPrimaryCommandExample(
            OperationId.HELP, List.of(helpCommand, helpCommand + " post-entry")));

    assertEquals(
        trialBalanceCommand + " --book-file ./books/acme.sqlite --output text",
        CliDiscoveryExampleSelector.selectPrimaryCommandExample(
            OperationId.TRIAL_BALANCE,
            List.of(
                trialBalanceCommand + " --book-file ./books/acme.sqlite --output text",
                "cat ./request.json | jq .")));

    assertEquals(
        "cat ./secrets/acme.book-key | " + openBookCommand + " --book-passphrase-stdin",
        CliDiscoveryExampleSelector.selectPrimaryCommandExample(
            OperationId.OPEN_BOOK,
            List.of(
                "cat ./secrets/acme.book-key | " + openBookCommand + " --book-passphrase-stdin")));

    assertEquals(
        capabilitiesCommand + " --output json",
        CliDiscoveryExampleSelector.selectPrimaryCommandExample(
            OperationId.CAPABILITIES,
            List.of(
                capabilitiesCommand + " --output json",
                CliInvocationText.commandExample(OperationId.ENVIRONMENT) + " --output json")));

    assertEquals(
        "printf '{}' | jq .",
        CliDiscoveryExampleSelector.selectPrimaryCommandExample(
            OperationId.HELP, List.of("printf '{}' | jq .", "printf 'done'")));
  }
}
