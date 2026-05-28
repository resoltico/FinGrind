package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OperationId;
import java.util.Arrays;
import java.util.List;

/** Selects the operator-facing primary example for one discovery help surface. */
final class CliDiscoveryExampleSelector {
  private CliDiscoveryExampleSelector() {}

  static String selectPrimaryCommandExample(OperationId operationId, List<String> commandExamples) {
    String directInvocation = CliInvocationText.commandExample(operationId);
    return exactOrPrefixedMatch(commandExamples, directInvocation)
        .orElseGet(
            () ->
                commandExamples.stream()
                    .filter(example -> containsOperationToken(example, operationId.wireName()))
                    .findFirst()
                    .orElseGet(commandExamples::getFirst));
  }

  private static java.util.Optional<String> exactOrPrefixedMatch(
      List<String> commandExamples, String directInvocation) {
    java.util.Optional<String> exactMatch =
        commandExamples.stream().filter(directInvocation::equals).findFirst();
    if (exactMatch.isPresent()) {
      return exactMatch;
    }
    return commandExamples.stream()
        .filter(example -> example.startsWith(directInvocation + " "))
        .findFirst();
  }

  private static boolean containsOperationToken(String example, String operationWireName) {
    return Arrays.stream(example.strip().split("\\s+")).anyMatch(operationWireName::equals);
  }
}
