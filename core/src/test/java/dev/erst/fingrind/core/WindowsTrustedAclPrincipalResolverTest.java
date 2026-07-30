package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.attribute.UserPrincipalNotFoundException;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Proves Windows privilege exceptions are resolved as stable principal identities. */
class WindowsTrustedAclPrincipalResolverTest {
  private static final UserPrincipal LOCAL_SYSTEM = () -> "localized-system";
  private static final UserPrincipal ADMINISTRATORS = () -> "localized-administrators";
  private static final UserPrincipal COLLABORATOR = () -> "collaborator";

  @Test
  void recognizesOnlyTheTwoTrustedPrincipalsThroughSidBackedLookup() throws IOException {
    Map<String, UserPrincipal> principals =
        Map.of("S-1-5-18", LOCAL_SYSTEM, "S-1-5-32-544", ADMINISTRATORS);

    assertTrue(WindowsTrustedAclPrincipalResolver.isTrusted(LOCAL_SYSTEM, identifiers(principals)));
    assertTrue(
        WindowsTrustedAclPrincipalResolver.isTrusted(ADMINISTRATORS, identifiers(principals)));
    assertFalse(
        WindowsTrustedAclPrincipalResolver.isTrusted(COLLABORATOR, identifiers(principals)));
  }

  @Test
  void fallsBackToTheCanonicalWellKnownAccountNameWhenSidLookupIsUnavailable() throws IOException {
    Map<String, UserPrincipal> principals =
        Map.of(
            "NT AUTHORITY\\SYSTEM", LOCAL_SYSTEM,
            "BUILTIN\\Administrators", ADMINISTRATORS);

    assertTrue(WindowsTrustedAclPrincipalResolver.isTrusted(LOCAL_SYSTEM, identifiers(principals)));
    assertTrue(
        WindowsTrustedAclPrincipalResolver.isTrusted(ADMINISTRATORS, identifiers(principals)));
  }

  @Test
  void appliesTheTrustedPrincipalPolicyOnlyOnWindows() throws IOException {
    Map<String, UserPrincipal> principals =
        Map.of("S-1-5-18", LOCAL_SYSTEM, "S-1-5-32-544", ADMINISTRATORS);

    assertTrue(
        WindowsTrustedAclPrincipalResolver.isTrustedForOperatingSystem(
            LOCAL_SYSTEM, "Windows 11", identifiers(principals)));
    assertFalse(
        WindowsTrustedAclPrincipalResolver.isTrustedForOperatingSystem(
            LOCAL_SYSTEM, "Linux", identifiers(principals)));
    assertFalse(
        WindowsTrustedAclPrincipalResolver.isTrustedForOperatingSystem(
            COLLABORATOR, "Windows 11", identifiers(principals)));
  }

  @Test
  void classifiesOnlyWellKnownUntrustedPrincipalsWithoutRetainingTheirDisplayIdentity()
      throws IOException {
    Map<String, UserPrincipal> principals =
        Map.of(
            "S-1-3-0", LOCAL_SYSTEM,
            "S-1-5-11", ADMINISTRATORS,
            "S-1-5-32-545", COLLABORATOR,
            "S-1-1-0", () -> "everyone");

    assertEquals(
        PrivateOutputDirectory.AclMutationPrincipalKind.CREATOR_OWNER,
        WindowsTrustedAclPrincipalResolver.classifyUntrusted(
            LOCAL_SYSTEM, identifiers(principals)));
    assertEquals(
        PrivateOutputDirectory.AclMutationPrincipalKind.AUTHENTICATED_USERS,
        WindowsTrustedAclPrincipalResolver.classifyUntrusted(
            ADMINISTRATORS, identifiers(principals)));
    assertEquals(
        PrivateOutputDirectory.AclMutationPrincipalKind.BUILTIN_USERS,
        WindowsTrustedAclPrincipalResolver.classifyUntrusted(
            COLLABORATOR, identifiers(principals)));
    assertEquals(
        PrivateOutputDirectory.AclMutationPrincipalKind.OTHER,
        WindowsTrustedAclPrincipalResolver.classifyUntrusted(
            () -> "ordinary-user", identifiers(principals)));
  }

  @Test
  void rejectsAnUnresolvableTrustedPrincipalAndIdentifiesWindowsWithoutLocaleDependence() {
    IOException failure =
        assertThrows(
            IOException.class,
            () ->
                WindowsTrustedAclPrincipalResolver.isTrusted(
                    LOCAL_SYSTEM,
                    identifier -> {
                      throw new UserPrincipalNotFoundException(identifier);
                    }));

    assertTrue(
        Objects.requireNonNull(failure.getMessage(), "failure message")
            .contains("trusted operating-system ACL principal"));
    assertTrue(WindowsTrustedAclPrincipalResolver.isWindows("Windows Server 2025"));
    assertFalse(WindowsTrustedAclPrincipalResolver.isWindows("Mac OS X"));
  }

  @Test
  void currentPlatformResolutionNeverTrustsAnOrdinaryPrincipalWithoutWindowsEvidence()
      throws IOException {
    assertFalse(
        WindowsTrustedAclPrincipalResolver.isTrustedForCurrentPlatform(Path.of("."), COLLABORATOR));
  }

  private static WindowsTrustedAclPrincipalResolver.PrincipalLookup identifiers(
      Map<String, UserPrincipal> principals) {
    return identifier -> {
      UserPrincipal principal = principals.get(identifier);
      if (principal == null) {
        throw new UserPrincipalNotFoundException(identifier);
      }
      return principal;
    };
  }
}
