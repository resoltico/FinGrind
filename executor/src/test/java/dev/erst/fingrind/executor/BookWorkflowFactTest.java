package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.executor.workflow.BookWorkflowFact;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Direct coverage for the workflow-owned local fact model. */
class BookWorkflowFactTest {
  @Test
  void workflowFacts_createTypedFactsAndCopyGroupedFacts() {
    List<BookWorkflowFact> groupedFacts =
        new ArrayList<>(List.of(BookWorkflowFact.text("postingId", "posting-1")));

    BookWorkflowFact.Group group = BookWorkflowFact.group("posting", groupedFacts);

    groupedFacts.clear();

    assertEquals(new BookWorkflowFact.Text("postingId", "posting-1"), group.facts().getFirst());
    assertEquals(
        new BookWorkflowFact.Text("code", "unknown-account"),
        BookWorkflowFact.text("code", "unknown-account"));
    assertEquals(
        new BookWorkflowFact.Flag("initialized", true), BookWorkflowFact.flag("initialized", true));
    assertEquals(
        new BookWorkflowFact.Count("violationCount", 2),
        BookWorkflowFact.count("violationCount", 2));
  }

  @Test
  void workflowFacts_rejectBlankNamesBlankValuesAndEmptyGroups() {
    assertEquals(
        "name",
        assertThrows(NullPointerException.class, () -> BookWorkflowFact.text(nullOf(), "value"))
            .getMessage());
    assertEquals(
        "Workflow fact name must not be blank.",
        assertThrows(IllegalArgumentException.class, () -> BookWorkflowFact.flag(" ", true))
            .getMessage());
    assertEquals(
        "Workflow fact value must not be blank.",
        assertThrows(IllegalArgumentException.class, () -> BookWorkflowFact.text("code", " "))
            .getMessage());
    assertEquals(
        "facts",
        assertThrows(NullPointerException.class, () -> BookWorkflowFact.group("group", nullOf()))
            .getMessage());
    assertEquals(
        "Grouped workflow facts must not be empty.",
        assertThrows(
                IllegalArgumentException.class, () -> BookWorkflowFact.group("group", List.of()))
            .getMessage());
  }
}
