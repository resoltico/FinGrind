package dev.erst.fingrind.sqlite;

import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.UserPrincipal;

/** User principal identified solely by name. */
record AclFixturePrincipal(String name) implements UserPrincipal {
  @Override
  public String getName() {
    return name;
  }
}

/** Group principal identified solely by name. */
record AclFixtureGroup(String name) implements GroupPrincipal {
  @Override
  public String getName() {
    return name;
  }
}
