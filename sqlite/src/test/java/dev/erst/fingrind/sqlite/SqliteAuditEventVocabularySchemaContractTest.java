package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.executor.bookkeeping.BookAuditEventKind;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Keeps the durable SQLite audit vocabulary exactly aligned with its executor-owned model. */
class SqliteAuditEventVocabularySchemaContractTest {
  private static final Pattern AUDIT_EVENT_TABLE_PATTERN =
      Pattern.compile("(?s)create table if not exists audit_event \\(.*?\\n\\) strict;");
  private static final Pattern EVENT_KIND_LIST_PATTERN =
      Pattern.compile("(?s)event_kind\\s+in\\s*\\((.*?)\\)");
  private static final Pattern QUOTED_AUDIT_KIND_PATTERN = Pattern.compile("'([A-Z_]+)'");

  @Test
  void auditEventSchemaVocabulary_matchesTheClosedExecutorVocabularyEverywhere() {
    List<List<String>> declaredKindLists = auditEventKindLists(auditEventTableDefinition());

    assertFalse(declaredKindLists.isEmpty(), "audit_event must declare an event-kind vocabulary.");
    assertEquals(BookAuditEventKind.wireValues(), declaredKindLists.getFirst());

    Set<String> allSchemaKinds = new LinkedHashSet<>();
    declaredKindLists.forEach(allSchemaKinds::addAll);
    assertEquals(Set.copyOf(BookAuditEventKind.wireValues()), allSchemaKinds);

    Set<String> payloadShapeKinds = new LinkedHashSet<>();
    declaredKindLists.subList(1, declaredKindLists.size()).forEach(payloadShapeKinds::addAll);
    assertEquals(Set.copyOf(BookAuditEventKind.wireValues()), payloadShapeKinds);
  }

  private static String auditEventTableDefinition() {
    Matcher matcher = AUDIT_EVENT_TABLE_PATTERN.matcher(canonicalSchemaSql());
    assertTrue(matcher.find(), "The canonical schema must declare audit_event.");
    return matcher.group();
  }

  private static List<List<String>> auditEventKindLists(String auditEventTableDefinition) {
    List<List<String>> declaredKindLists = new ArrayList<>();
    Matcher listMatcher = EVENT_KIND_LIST_PATTERN.matcher(auditEventTableDefinition);
    while (listMatcher.find()) {
      Matcher kindMatcher = QUOTED_AUDIT_KIND_PATTERN.matcher(listMatcher.group(1));
      declaredKindLists.add(kindMatcher.results().map(result -> result.group(1)).toList());
    }
    return List.copyOf(declaredKindLists);
  }

  private static String canonicalSchemaSql() {
    return SqliteBookSchemaBootstrap.readSchema(
        SqliteBookSchemaBootstrap::openSchemaStreamForTests);
  }
}
