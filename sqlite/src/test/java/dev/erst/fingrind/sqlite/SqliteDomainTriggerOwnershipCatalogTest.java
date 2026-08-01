package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Verifies that durable SQLite business-rule triggers retain one explicit executor owner. */
class SqliteDomainTriggerOwnershipCatalogTest {
  private static final Pattern CREATE_TRIGGER_PATTERN =
      Pattern.compile("(?im)^create\\s+trigger\\s+if\\s+not\\s+exists\\s+([A-Za-z0-9_]+)");

  @Test
  void everyBusinessRuleTriggerHasAnExplicitExecutorOwner() {
    Set<String> businessRuleTriggers = new LinkedHashSet<>();
    Set<String> durabilityOnlyTriggers = new LinkedHashSet<>();

    Matcher matcher = CREATE_TRIGGER_PATTERN.matcher(canonicalSchemaSql());
    while (matcher.find()) {
      String triggerName = matcher.group(1);
      if (SqliteDomainTriggerOwnershipCatalog.isDurabilityOnlyTrigger(triggerName)) {
        durabilityOnlyTriggers.add(triggerName);
      } else {
        businessRuleTriggers.add(triggerName);
      }
    }

    assertEquals(SqliteDomainTriggerOwnershipCatalog.domainTriggerNames(), businessRuleTriggers);
    assertTrue(
        durabilityOnlyTriggers.stream()
            .allMatch(SqliteDomainTriggerOwnershipCatalog::isDurabilityOnlyTrigger));
  }

  private static String canonicalSchemaSql() {
    return SqliteBookSchemaBootstrap.readSchema(
        SqliteBookSchemaBootstrap::openSchemaStreamForTests);
  }
}
