package dev.erst.fingrind.contract.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Locks repository-root discovery to the explicit Gradle test binding. */
class ProtocolContractRepositorySupportTest {
  @Test
  void repositoryRoot_usesTheGradleConfiguredCheckoutRatherThanAmbientWorkingDirectory() {
    Path configuredRoot =
        Path.of(System.getProperty("fingrind.repository.root")).toAbsolutePath().normalize();
    Path resolvedRoot = new ProtocolContractRepositorySupport().repositoryRoot();

    assertEquals(configuredRoot, resolvedRoot);
    assertTrue(Files.isRegularFile(resolvedRoot.resolve("settings.gradle.kts")));
  }
}
