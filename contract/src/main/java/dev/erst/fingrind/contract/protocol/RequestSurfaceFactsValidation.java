package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.contract.bookkeeping.JournalRecipeKind;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Shared uniqueness validation for the published request-surface fact catalog. */
final class RequestSurfaceFactsValidation {
  private RequestSurfaceFactsValidation() {}

  static void requireUniqueEntryKinds(
      List<RequestSurfaceFacts.PostEntryKindFacts> factsByEntryKind) {
    Set<BookkeepingEntryKind> seen = EnumSet.noneOf(BookkeepingEntryKind.class);
    factsByEntryKind.stream()
        .map(RequestSurfaceFacts.PostEntryKindFacts::entryKind)
        .filter(entryKind -> !seen.add(entryKind))
        .findFirst()
        .ifPresent(
            entryKind -> {
              throw new IllegalArgumentException(
                  "Duplicate request-surface facts for entryKind " + entryKind + ".");
            });
  }

  static void requireUniqueRecipeKinds(
      List<RequestSurfaceFacts.JournalRecipeFacts> factsByRecipeKind) {
    Set<JournalRecipeKind> seen = EnumSet.noneOf(JournalRecipeKind.class);
    factsByRecipeKind.stream()
        .map(RequestSurfaceFacts.JournalRecipeFacts::recipeKind)
        .filter(recipeKind -> !seen.add(recipeKind))
        .findFirst()
        .ifPresent(
            recipeKind -> {
              throw new IllegalArgumentException(
                  "Duplicate journal recipe facts for recipeKind " + recipeKind + ".");
            });
  }

  static void requireUniqueEvidenceProfiles(
      List<RequestSurfaceFacts.EvidenceProfileFacts> profiles) {
    Set<String> seen = new java.util.LinkedHashSet<>();
    profiles.stream()
        .map(RequestSurfaceFacts.EvidenceProfileFacts::profileId)
        .filter(profileId -> !seen.add(profileId))
        .findFirst()
        .ifPresent(
            profileId -> {
              throw new IllegalArgumentException(
                  "Duplicate evidence profile facts for profileId " + profileId + ".");
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
