package dev.erst.fingrind.sqlite;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Derives the canonical SQLite schema object manifest from the packaged schema resource. */
final class SqliteCanonicalSchemaManifest {
  private static final Pattern CREATE_OBJECT_PATTERN =
      Pattern.compile(
          "(?im)^create\\s+(?:unique\\s+)?(table|index|trigger)\\s+if\\s+not\\s+exists\\s+([A-Za-z0-9_]+)");

  private SqliteCanonicalSchemaManifest() {}

  static List<String> objectNames() {
    return loadManifest().objectNames();
  }

  static int objectCount() {
    return objectNames().size();
  }

  static String loadObjectsQuery() {
    return loadManifest().loadObjectsQuery();
  }

  static String loadNonCanonicalObjectsQuery() {
    return loadManifest().loadNonCanonicalObjectsQuery();
  }

  private static Manifest loadManifest() {
    return ManifestHolder.MANIFEST;
  }

  private static Manifest buildManifest() {
    String schemaSql =
        SqliteBookSchemaBootstrap.readSchema(SqliteBookSchemaBootstrap::openSchemaStreamForTests);
    List<String> objectNames = parseObjectNames(schemaSql);
    return new Manifest(
        objectNames,
        buildLoadObjectsQuery(objectNames),
        buildLoadNonCanonicalObjectsQuery(objectNames));
  }

  static List<String> parseObjectNames(String schemaSql) {
    Objects.requireNonNull(schemaSql, "schemaSql");
    Matcher matcher = CREATE_OBJECT_PATTERN.matcher(schemaSql);
    List<String> objectNames = new ArrayList<>();
    while (matcher.find()) {
      objectNames.add(matcher.group(2));
    }
    if (objectNames.isEmpty()) {
      throw new IllegalStateException("SQLite canonical schema manifest found no schema objects.");
    }
    return List.copyOf(objectNames);
  }

  private static String buildLoadObjectsQuery(List<String> objectNames) {
    return """
        select type, name, ifnull(sql, '')
        from sqlite_schema
        where type in ('table', 'index', 'trigger')
          and name in (
              %s
          )
        order by type, name
        """
        .formatted("'" + String.join("',\n              '", objectNames) + "'");
  }

  private static String buildLoadNonCanonicalObjectsQuery(List<String> objectNames) {
    return """
        select type, name, ifnull(sql, '')
        from sqlite_schema
        where type in ('table', 'index', 'trigger', 'view')
          and name not like 'sqlite_%%'
          and name not in (
              %s
          )
        order by type, name
        """
        .formatted("'" + String.join("',\n              '", objectNames) + "'");
  }

  private record Manifest(
      List<String> objectNames, String loadObjectsQuery, String loadNonCanonicalObjectsQuery) {
    private Manifest {
      objectNames = List.copyOf(Objects.requireNonNull(objectNames, "objectNames"));
      Objects.requireNonNull(loadObjectsQuery, "loadObjectsQuery");
      Objects.requireNonNull(loadNonCanonicalObjectsQuery, "loadNonCanonicalObjectsQuery");
    }
  }

  /** Lazy holder for the canonical schema manifest singleton. */
  private static final class ManifestHolder {
    private static final Manifest MANIFEST = buildManifest();

    private ManifestHolder() {}
  }
}
