package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.attribute.UserPrincipalNotFoundException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Proves Windows privilege exceptions are resolved as stable principal identities. */
class WindowsTrustedAclPrincipalResolverTest {
  private static final UserPrincipal LOCAL_SYSTEM = () -> "localized-system";
  private static final UserPrincipal ADMINISTRATORS = () -> "localized-administrators";
  private static final UserPrincipal COLLABORATOR = () -> "collaborator";

  @Test
  void recognizesOnlyTheTwoTrustedPrincipalsThroughNativeSidMatching() throws IOException {
    WindowsTrustedAclPrincipalResolver.TrustedAclPrincipalMatcherSource matcherSource =
        trustedMatcher(LOCAL_SYSTEM, ADMINISTRATORS);

    assertTrue(
        WindowsTrustedAclPrincipalResolver.isTrustedForOperatingSystem(
            LOCAL_SYSTEM, "Windows 11", matcherSource));
    assertTrue(
        WindowsTrustedAclPrincipalResolver.isTrustedForOperatingSystem(
            ADMINISTRATORS, "Windows 11", matcherSource));
    assertFalse(
        WindowsTrustedAclPrincipalResolver.isTrustedForOperatingSystem(
            COLLABORATOR, "Windows 11", matcherSource));
  }

  @Test
  void appliesTheTrustedPrincipalPolicyOnlyOnWindows() throws IOException {
    WindowsTrustedAclPrincipalResolver.TrustedAclPrincipalMatcherSource matcherSource =
        trustedMatcher(LOCAL_SYSTEM, ADMINISTRATORS);

    assertTrue(
        WindowsTrustedAclPrincipalResolver.isTrustedForOperatingSystem(
            LOCAL_SYSTEM, "Windows 11", matcherSource));
    assertFalse(
        WindowsTrustedAclPrincipalResolver.isTrustedForOperatingSystem(
            LOCAL_SYSTEM,
            "Linux",
            () -> {
              throw new AssertionError("non-Windows trust checks must not acquire native state");
            }));
    assertFalse(
        WindowsTrustedAclPrincipalResolver.isTrustedForOperatingSystem(
            COLLABORATOR, "Windows 11", matcherSource));
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
  void releasesTheNativeTrustedMatcherAfterFailureAndIdentifiesWindowsWithoutLocaleDependence() {
    int[] releaseCount = {0};
    assertThrows(
        IOException.class,
        () ->
            WindowsTrustedAclPrincipalResolver.isTrustedForOperatingSystem(
                LOCAL_SYSTEM,
                "Windows 11",
                () ->
                    new WindowsTrustedAclPrincipalResolver.TrustedAclPrincipalMatcher() {
                      @Override
                      public boolean matchesTrusted(UserPrincipal principal) throws IOException {
                        throw new IOException("native trusted-principal lookup failed");
                      }

                      @Override
                      public void release() {
                        releaseCount[0]++;
                      }
                    }));
    assertEquals(1, releaseCount[0]);
    assertTrue(WindowsTrustedAclPrincipalResolver.isWindows("Windows Server 2025"));
    assertFalse(WindowsTrustedAclPrincipalResolver.isWindows("Mac OS X"));
  }

  @Test
  void rejectsMissingOrNullNativeTrustedMatchers() {
    assertThrows(
        IOException.class,
        () ->
            WindowsTrustedAclPrincipalResolver.isTrustedForOperatingSystem(
                COLLABORATOR,
                "Windows 11",
                () -> {
                  throw new IOException("native trusted-principal acquisition failed");
                }));
    assertThrows(
        NullPointerException.class,
        () ->
            WindowsTrustedAclPrincipalResolver.isTrustedForOperatingSystem(
                COLLABORATOR, "Windows 11", NullTestSupport::nullOf));
  }

  @Test
  void admitsOnlyAnObservedAclPrincipalMatchedByTheCurrentTokenSid() throws IOException {
    UserPrincipal currentTokenUser = () -> "localized-current-user";
    PrivateOutputDirectory.AclState aclState =
        new PrivateOutputDirectory.AclState(COLLABORATOR, List.of(allowEntry(currentTokenUser)));

    assertEquals(
        List.of(COLLABORATOR, currentTokenUser),
        WindowsTrustedAclPrincipalResolver.permittedAclMutationPrincipalsForCreation(
            "Windows 11",
            aclState,
            () -> candidate -> candidate.getName().equals(currentTokenUser.getName())));
    assertEquals(
        List.of(COLLABORATOR),
        WindowsTrustedAclPrincipalResolver.permittedAclMutationPrincipalsForCreation(
            "Windows 11",
            new PrivateOutputDirectory.AclState(COLLABORATOR, List.of()),
            () -> candidate -> candidate.getName().equals(currentTokenUser.getName())));
    assertEquals(
        List.of(COLLABORATOR),
        WindowsTrustedAclPrincipalResolver.permittedAclMutationPrincipalsForCreation(
            "Windows 11",
            new PrivateOutputDirectory.AclState(COLLABORATOR, List.of()),
            () -> candidate -> candidate.getName().equals(COLLABORATOR.getName())));
    assertEquals(
        List.of(COLLABORATOR),
        WindowsTrustedAclPrincipalResolver.permittedAclMutationPrincipalsForCreation(
            "Linux",
            aclState,
            () ->
                candidate -> {
                  throw new AssertionError("non-Windows creation must not resolve a token user");
                }));
    assertThrows(
        IOException.class,
        () ->
            WindowsTrustedAclPrincipalResolver.permittedAclMutationPrincipalsForCreation(
                "Windows 11",
                aclState,
                () ->
                    candidate -> {
                      throw new IOException("native current-token lookup failed");
                    }));
    assertThrows(
        IOException.class,
        () ->
            WindowsTrustedAclPrincipalResolver.permittedAclMutationPrincipalsForCreation(
                "Windows 11",
                aclState,
                () ->
                    candidate -> {
                      throw new IOException("native ACL-principal lookup failed");
                    }));
  }

  @Test
  void recognizesOnlyTheCurrentTokenPrincipalOnWindows() throws IOException {
    assertTrue(
        WindowsTrustedAclPrincipalResolver.isCurrentTokenForOperatingSystem(
            COLLABORATOR, "Windows 11", () -> candidate -> candidate.equals(COLLABORATOR)));
    assertFalse(
        WindowsTrustedAclPrincipalResolver.isCurrentTokenForOperatingSystem(
            COLLABORATOR,
            "Linux",
            () -> {
              throw new AssertionError("non-Windows lookup must not acquire a token matcher");
            }));
    assertThrows(
        IOException.class,
        () ->
            WindowsTrustedAclPrincipalResolver.isCurrentTokenForOperatingSystem(
                COLLABORATOR,
                "Windows 11",
                () -> {
                  throw new IOException("native current-token lookup failed");
                }));
  }

  @Test
  void classifiesUntrustedPrincipalsOnlyWhenWindowsPrincipalEvidenceExists() throws IOException {
    Map<String, UserPrincipal> principals = Map.of("S-1-1-0", LOCAL_SYSTEM);

    assertEquals(
        PrivateOutputDirectory.AclMutationPrincipalKind.EVERYONE,
        WindowsTrustedAclPrincipalResolver.classifyUntrustedForOperatingSystem(
            "Windows 11", LOCAL_SYSTEM, identifiers(principals)));
    assertEquals(
        PrivateOutputDirectory.AclMutationPrincipalKind.OTHER,
        WindowsTrustedAclPrincipalResolver.classifyUntrustedForOperatingSystem(
            "Linux", LOCAL_SYSTEM, identifiers(principals)));
  }

  private static AclEntry allowEntry(UserPrincipal principal) {
    return AclEntry.newBuilder()
        .setType(AclEntryType.ALLOW)
        .setPrincipal(principal)
        .setPermissions(AclEntryPermission.ADD_FILE)
        .build();
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

  private static WindowsTrustedAclPrincipalResolver.TrustedAclPrincipalMatcherSource trustedMatcher(
      UserPrincipal... trustedPrincipals) {
    List<UserPrincipal> trusted = List.of(trustedPrincipals);
    return () -> trusted::contains;
  }
}
