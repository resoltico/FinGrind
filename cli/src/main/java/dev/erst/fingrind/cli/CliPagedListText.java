package dev.erst.fingrind.cli;

import java.util.List;

/** Immutable input for the shared paged-list text renderer. */
record CliPagedListText(
    String title,
    String returnedSubjectPlural,
    String emptySubjectPlural,
    int returnedCount,
    int limit,
    String nextCursor,
    String renderedRows,
    boolean withContext,
    List<List<String>> contextRows) {
  CliPagedListText {
    java.util.Objects.requireNonNull(title, "title");
    java.util.Objects.requireNonNull(returnedSubjectPlural, "returnedSubjectPlural");
    java.util.Objects.requireNonNull(emptySubjectPlural, "emptySubjectPlural");
    java.util.Objects.requireNonNull(nextCursor, "nextCursor");
    java.util.Objects.requireNonNull(renderedRows, "renderedRows");
    contextRows = List.copyOf(java.util.Objects.requireNonNull(contextRows, "contextRows"));
  }
}
