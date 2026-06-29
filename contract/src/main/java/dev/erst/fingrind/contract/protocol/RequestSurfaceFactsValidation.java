package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Shared uniqueness validation for the published request-surface fact catalog. */
final class RequestSurfaceFactsValidation {
  private RequestSurfaceFactsValidation() {}

  static void requireUniqueEntryKinds(
      List<RequestSurfaceFacts.BookkeepingEntryKindFacts> factsByEntryKind) {
    Set<BookkeepingEntryKind> seen = EnumSet.noneOf(BookkeepingEntryKind.class);
    factsByEntryKind.forEach(
        facts -> {
          if (!seen.add(facts.entryKind())) {
            throw new IllegalArgumentException(
                "Duplicate request-surface facts for entryKind " + facts.entryKind() + ".");
          }
          requireDistinctTopLevelFields(facts);
        });
  }

  private static void requireDistinctTopLevelFields(
      RequestSurfaceFacts.BookkeepingEntryKindFacts facts) {
    Set<String> acceptedFields = new java.util.LinkedHashSet<>(facts.requiredTopLevelFields());
    facts
        .optionalTopLevelFields()
        .forEach(
            field -> {
              if (!acceptedFields.add(field)) {
                throw new IllegalArgumentException(
                    "Entry kind "
                        + facts.entryKind()
                        + " repeats accepted top-level field "
                        + field
                        + ".");
              }
            });
  }

  static void requireUniqueReachabilityCells(
      List<RequestSurfaceFacts.ReachabilityCellFacts> cells) {
    Set<String> seen = new java.util.LinkedHashSet<>();
    cells.stream()
        .map(
            cell ->
                cell.classificationFamily()
                    + "|"
                    + cell.accountType().wireValue()
                    + "|"
                    + cell.classification())
        .filter(cellKey -> !seen.add(cellKey))
        .findFirst()
        .ifPresent(
            cellKey -> {
              throw new IllegalArgumentException(
                  "Duplicate reachability matrix facts for cell " + cellKey + ".");
            });
  }

  static void requireUniqueTemporalArchetypes(
      List<RequestSurfaceFacts.TemporalScopeFacts> factsByArchetype) {
    Set<TemporalScopeArchetype> seen = EnumSet.noneOf(TemporalScopeArchetype.class);
    factsByArchetype.stream()
        .map(RequestSurfaceFacts.TemporalScopeFacts::archetype)
        .filter(archetype -> !seen.add(archetype))
        .findFirst()
        .ifPresent(
            archetype -> {
              throw new IllegalArgumentException(
                  "Duplicate temporal-scope facts for archetype " + archetype + ".");
            });
  }

  static void requireUniqueTemporalScopeCommands(
      List<RequestSurfaceFacts.CommandTemporalScopeFacts> commandMappings) {
    Set<OperationId> seen = EnumSet.noneOf(OperationId.class);
    commandMappings.stream()
        .map(RequestSurfaceFacts.CommandTemporalScopeFacts::operationId)
        .filter(operationId -> !seen.add(operationId))
        .findFirst()
        .ifPresent(
            operationId -> {
              throw new IllegalArgumentException(
                  "Duplicate temporal-scope facts for command " + operationId + ".");
            });
  }
}
