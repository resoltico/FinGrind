package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Regression coverage for providers whose {@link Path#equals(Object)} case-folds names. */
class SqliteProtectedBookPathIdentityTest {
  @Test
  void submittedBindingIdentityNeverAdoptsCaseFoldedPathEquality() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("acl"))) {
      Path first = new CaseFoldingFixturePath(fileSystem, "\\targets\\Book.sqlite");
      Path second = new CaseFoldingFixturePath(fileSystem, "\\targets\\book.sqlite");

      assertTrue(first.equals(second), "Fixture must model a case-folding Path provider.");
      assertFalse(SqliteProtectedBookPathIdentity.sameNormalizedSpelling(first, second));
      assertFalse(
          SqliteProtectedBookPathIdentity.containsNormalizedSpelling(List.of(first), second));
    }
  }

  @Test
  void sameParentSpellingThatTheProviderCannotProveAsOneObjectFailsClosed() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("acl"))) {
      Path firstParent = new DistinctIdentityFixturePath(fileSystem, "\\targets");
      Path secondParent = new DistinctIdentityFixturePath(fileSystem, "\\targets");
      Path firstTarget =
          new ParentOverrideFixturePath(fileSystem, "\\targets\\book.sqlite", firstParent);
      Path secondTarget =
          new ParentOverrideFixturePath(fileSystem, "\\targets\\book.key", secondParent);

      SqliteCallerPathContractException exception =
          assertThrows(
              SqliteCallerPathContractException.class,
              () ->
                  SqliteProtectedBookPathIdentity.distinctPhysicalParents(
                      firstTarget, secondTarget));

      assertEquals(SqliteCallerPathFailure.TARGET_IDENTITY_UNESTABLISHED, exception.pathFailure());
    }
  }

  /** Test-only path whose equality deliberately folds case to model hostile provider semantics. */
  private static final class CaseFoldingFixturePath extends AclFixtureAbstractPath {
    private CaseFoldingFixturePath(AclFixtureFileSystem fileSystem, String value) {
      super(fileSystem, value);
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof AclFixtureAbstractPath path
          && fileSystem == path.fileSystem
          && value.equalsIgnoreCase(path.value);
    }

    @Override
    public int hashCode() {
      return value.toLowerCase(java.util.Locale.ROOT).hashCode();
    }
  }

  /** Test-only parent path whose spelling matches another path without claiming its identity. */
  private static final class DistinctIdentityFixturePath extends AclFixturePath {
    private DistinctIdentityFixturePath(AclFixtureFileSystem fileSystem, String value) {
      super(fileSystem, value);
    }

    @Override
    public boolean equals(Object other) {
      return this == other;
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(this);
    }
  }

  /** Test-only target that supplies one explicit parent identity without provider path caching. */
  private static final class ParentOverrideFixturePath extends AclFixtureAbstractPath {
    private final Path parent;

    private ParentOverrideFixturePath(AclFixtureFileSystem fileSystem, String value, Path parent) {
      super(fileSystem, value);
      this.parent = parent;
    }

    @Override
    public Path getParent() {
      return parent;
    }
  }
}
