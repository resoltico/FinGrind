package dev.erst.fingrind.contract.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Contract-lint tests for operation identifiers referenced from code and docs. */
class ProtocolOperationReferenceLintTest extends ProtocolContractOperationSupport {
  @Test
  void productionJavaDoesNotEmbedHyphenatedOperationIdsInStringLiteralsOutsideContractProtocol()
      throws IOException {
    Set<String> registeredHyphenatedIds = registeredHyphenatedOperationIds();
    Set<String> violations = new HashSet<>();
    for (Path sourceFile : productionJavaFiles()) {
      String source = Files.readString(sourceFile);
      stringLiterals(source)
          .forEach(
              literal ->
                  registeredHyphenatedIds.stream()
                      .filter(operationId -> containsToken(literal, operationId))
                      .map(
                          operationId ->
                              relative(sourceFile)
                                  + " embeds `"
                                  + operationId
                                  + "` inside a string literal")
                      .forEach(violations::add));
    }

    assertTrue(
        violations.isEmpty(),
        () -> "Operation id string-literal authorship drift:\n" + sorted(violations));
  }

  @Test
  void stringLiteralScanner_handlesLargeEscapedSourceWithoutRegexBacktracking() {
    String literal = "\"escaped \\\"help\\\" token and post-entry\"";
    String source = ("final String sample = " + literal + ";\n").repeat(20_000);

    long matchingLiteralCount =
        stringLiterals(source).filter(extracted -> containsToken(extracted, "post-entry")).count();

    assertEquals(20_000L, matchingLiteralCount);
  }

  @Test
  void documentationFingrindInvocationsReferenceRegisteredOperations() throws IOException {
    Set<String> registeredIds = registeredOperationIds();
    Set<String> violations = new HashSet<>();
    for (Path document : currentDocumentationFiles()) {
      Files.readAllLines(document).stream()
          .filter(this::looksLikeCommandInvocation)
          .forEach(
              line ->
                  FINGRIND_COMMAND_PATTERN
                      .matcher(line)
                      .results()
                      .map(match -> match.group(1))
                      .filter(command -> !registeredIds.contains(command))
                      .map(
                          command ->
                              relative(document)
                                  + " invokes unregistered operation `"
                                  + command
                                  + "`")
                      .forEach(violations::add));
    }

    assertTrue(
        violations.isEmpty(),
        () -> "Unregistered fingrind command references:\n" + sorted(violations));
  }

  @Test
  void documentationBacktickedHyphenIdsAreRegisteredOperationsOrKnownNonOperationIds()
      throws IOException {
    Set<String> registeredIds = registeredOperationIds();
    Set<String> violations = new HashSet<>();
    for (Path document : currentDocumentationFiles()) {
      String text = Files.readString(document);
      BACKTICKED_HYPHEN_ID_PATTERN
          .matcher(text)
          .results()
          .map(match -> match.group(1))
          .filter(token -> !registeredIds.contains(token))
          .filter(token -> !NON_OPERATION_BACKTICK_IDS.contains(token))
          .map(token -> relative(document) + " mentions unregistered hyphen id `" + token + "`")
          .forEach(violations::add);
    }

    assertTrue(
        violations.isEmpty(), () -> "Unregistered user-facing hyphen ids:\n" + sorted(violations));
  }

  @Test
  void catalogUsageAndExamplesReferenceOnlyRegisteredOperations() {
    Set<String> registeredIds = registeredOperationIds();
    Set<String> violations = new HashSet<>();
    ProtocolCatalog.operations()
        .forEach(
            operation ->
                Stream.concat(
                        Stream.of(operation.usage()),
                        operation.exampleSteps().stream().map(ProtocolExampleStep::text))
                    .forEach(
                        text ->
                            FINGRIND_COMMAND_PATTERN
                                .matcher(text)
                                .results()
                                .map(match -> match.group(1))
                                .filter(command -> !registeredIds.contains(command))
                                .map(
                                    command ->
                                        operation.id().wireName()
                                            + " references unregistered operation `"
                                            + command
                                            + "`")
                                .forEach(violations::add)));

    assertTrue(
        violations.isEmpty(), () -> "Catalog operation reference drift:\n" + sorted(violations));
  }

  @Test
  void catalogOperationIdsAndTokensAreUnique() {
    Set<OperationId> ids = EnumSet.noneOf(OperationId.class);
    Set<String> duplicateIds = new HashSet<>();
    Set<String> tokens = new HashSet<>();
    Set<String> duplicateTokens = new HashSet<>();

    ProtocolCatalog.operations()
        .forEach(
            operation -> {
              if (!ids.add(operation.id())) {
                duplicateIds.add(operation.id().wireName());
              }
              if (!tokens.add(operation.id().wireName())) {
                duplicateTokens.add(operation.id().wireName());
              }
              operation.aliases().stream()
                  .filter(alias -> !tokens.add(alias))
                  .forEach(duplicateTokens::add);
            });

    assertTrue(
        duplicateIds.isEmpty(), () -> "Duplicate catalog operation ids:\n" + sorted(duplicateIds));
    assertTrue(
        duplicateTokens.isEmpty(),
        () -> "Duplicate catalog operation tokens:\n" + sorted(duplicateTokens));
  }
}
