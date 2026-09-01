package dev.erst.fingrind.buildlogic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Functional contract for the shared mutation-testing convention plugin. */
class FinGrindMutationConventionsPluginTest {
  @TempDir Path projectDirectory;

  @Test
  void wiresPinnedStrictPitPolicyAggregateTaskAndReportCleanup() throws IOException {
    writeBuildFixture();

    BuildResult policy = runner("printMutationPolicy").build();
    assertTrue(policy.getOutput().contains("pitestVersion=1.30.0"));
    assertTrue(policy.getOutput().contains("junit5PluginVersion=1.2.3"));
    assertTrue(policy.getOutput().contains("targetClasses=[example.Target]"));
    assertTrue(policy.getOutput().contains("targetTests=[example.TargetTest]"));
    assertTrue(policy.getOutput().contains("expectedMutationCounts=[example.Target:1]"));
    assertTrue(policy.getOutput().contains("mutators=[DEFAULTS, EXPERIMENTAL_SWITCH]"));
    assertTrue(policy.getOutput().contains("mutationThreshold=100"));
    assertTrue(policy.getOutput().contains("coverageThreshold=95"));
    assertTrue(policy.getOutput().contains("testStrengthThreshold=100"));
    assertTrue(policy.getOutput().contains("maxSurviving=0"));
    assertTrue(policy.getOutput().contains("threads="));
    assertTrue(policy.getOutput().contains("outputFormats=[XML, HTML]"));
    assertTrue(policy.getOutput().contains("timestampedReports=false"));
    assertTrue(policy.getOutput().contains("failWhenNoMutations=true"));
    assertTrue(policy.getOutput().contains("jvmPath=java"));

    BuildResult dryRun = runner("mutationCheck", "--dry-run").build();
    assertTrue(dryRun.getOutput().contains(":cleanPitestReport SKIPPED"));
    assertTrue(dryRun.getOutput().contains(":pitest SKIPPED"));
    assertTrue(dryRun.getOutput().contains(":mutationCheck SKIPPED"));

    Path staleReport = projectDirectory.resolve("build/reports/pitest/stale.html");
    Files.createDirectories(staleReport.getParent());
    Files.writeString(staleReport, "stale");
    runner("cleanPitestReport").build();
    assertFalse(Files.exists(staleReport));
  }

  private void writeBuildFixture() throws IOException {
    Files.createDirectories(projectDirectory.resolve("gradle"));
    Files.writeString(projectDirectory.resolve("settings.gradle"), "rootProject.name = 'fixture'\n");
    Files.writeString(
        projectDirectory.resolve("gradle/fingrind-build.properties"),
        """
        fingrindJavaVersion=26
        fingrindPythonVersion=3.12
        fingrindKotlinVersion=2.4.10
        fingrindUvVersion=0.12.7
        implementationVendor=Ervins Strauhmanis
        implementationLicense=MIT
        foojayResolverConventionVersion=1.0.0
        normalizedArtifactEpochSeconds=1781455388
        """);
    Files.writeString(
        projectDirectory.resolve("gradle/libs.versions.toml"),
        """
        [versions]
        pitest = "1.30.0"
        pitest-junit5-plugin = "1.2.3"
        """);
    Files.writeString(
        projectDirectory.resolve("build.gradle"),
        """
        plugins {
          id 'java'
          id 'dev.erst.fingrind.mutation-conventions'
        }

        repositories {
          mavenCentral()
        }

        dependencies {
          testImplementation platform('org.junit:junit-bom:6.1.3')
          testImplementation 'org.junit.jupiter:junit-jupiter'
        }

        tasks.withType(Test).configureEach {
          useJUnitPlatform()
        }

        fingrindMutation {
          targetClasses.set(['example.Target'] as Set)
          targetTests.set(['example.TargetTest'] as Set)
          expectedMutationCounts.put('example.Target', 1)
        }

        tasks.register('printMutationPolicy') {
          doLast {
            def policy = project.extensions.getByName('pitest')
            println "pitestVersion=${policy.pitestVersion.get()}"
            println "junit5PluginVersion=${policy.junit5PluginVersion.get()}"
            println "targetClasses=${policy.targetClasses.get()}"
            println "targetTests=${policy.targetTests.get()}"
            println "expectedMutationCounts=${fingrindMutation.expectedMutationCounts.get()}"
            println "mutators=${policy.mutators.get()}"
            println "mutationThreshold=${policy.mutationThreshold.get()}"
            println "coverageThreshold=${policy.coverageThreshold.get()}"
            println "testStrengthThreshold=${policy.testStrengthThreshold.get()}"
            println "maxSurviving=${policy.maxSurviving.get()}"
            println "threads=${policy.threads.get()}"
            println "outputFormats=${policy.outputFormats.get()}"
            println "timestampedReports=${policy.timestampedReports.get()}"
            println "failWhenNoMutations=${policy.failWhenNoMutations.get()}"
            println "jvmPath=${policy.jvmPath.get().asFile.name}"
          }
        }
        """);
  }

  @Test
  void verifiesFreshCompleteMutationEvidenceAndRejectsEveryRedPath() throws IOException {
    writeBuildFixture();
    writeMutationReport("KILLED", "example.Target", 95, 100);

    BuildResult verified = runner("verifyMutationEvidence", "-x", "pitest").build();
    assertTrue(verified.getOutput().contains("BUILD SUCCESSFUL"));

    writeMutationReport("SURVIVED", "example.Target", 95, 100);
    BuildResult survivor = runner("verifyMutationEvidence", "-x", "pitest").buildAndFail();
    assertTrue(survivor.getOutput().contains("non-killed mutations"));

    writeMutationReport("KILLED", "example.OtherTarget", 95, 100);
    BuildResult missingClass = runner("verifyMutationEvidence", "-x", "pitest").buildAndFail();
    assertTrue(missingClass.getOutput().contains("mutation inventory drifted"));

    writeEmptyMutationReport();
    BuildResult noMutations = runner("verifyMutationEvidence", "-x", "pitest").buildAndFail();
    assertTrue(noMutations.getOutput().contains("mutation inventory drifted"));
  }

  @Test
  void rejectsNewDeterministicRuleSourcesWithoutAMutationDisposition() throws IOException {
    writeBuildFixture();
    writeJavaMutationFixture(
        """
        package example;

        class TargetTest {}
        """);
    Path unclassifiedPolicy =
        projectDirectory.resolve("src/main/java/example/UnclassifiedPolicy.java");
    Files.createDirectories(unclassifiedPolicy.getParent());
    Files.writeString(
        unclassifiedPolicy,
        """
        package example;

        final class UnclassifiedPolicy {}
        """);

    BuildResult missingDisposition = runner("verifyMutationScope").buildAndFail();

    assertTrue(missingDisposition.getOutput().contains("PIT scope admission is missing"));
    assertTrue(missingDisposition.getOutput().contains("example.UnclassifiedPolicy"));
  }

  @Test
  void executesPitAndRejectsThenKillsOneRealMutant() throws IOException {
    writeBuildFixture();
    writeJavaMutationFixture(
        """
        package example;

        import org.junit.jupiter.api.Test;

        class TargetTest {
          @Test
          void value_is_callable() {
            new Target().value();
          }
        }
        """);

    BuildResult survivor = runner("mutationCheck").buildAndFail();
    assertTrue(survivor.getOutput().contains("SURVIVED"));

    writeJavaMutationFixture(
        """
        package example;

        import static org.junit.jupiter.api.Assertions.assertEquals;

        import org.junit.jupiter.api.Test;

        class TargetTest {
          @Test
          void value_hasItsExactMeaning() {
            assertEquals(1, new Target().value());
          }
        }
        """);

    BuildResult killed = runner("mutationCheck").build();
    assertTrue(killed.getOutput().contains("Generated 1 mutations Killed 1 (100%)"));
    assertTrue(killed.getOutput().contains(":verifyMutationEvidence"));
  }

  private void writeMutationReport(
      String status, String mutatedClass, int coveredLines, int totalLines) throws IOException {
    Path reportDirectory = projectDirectory.resolve("build/reports/pitest");
    Files.createDirectories(reportDirectory);
    Files.writeString(
        reportDirectory.resolve("mutations.xml"),
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <mutations>
          <mutation status='%s' numberOfTestsRun='1'>
            <mutatedClass>%s</mutatedClass>
          </mutation>
        </mutations>
        """.formatted(status, mutatedClass));
    Files.writeString(
        reportDirectory.resolve("index.html"),
        """
        <table><tbody><tr><td>1</td><td>95%%
        <div class="coverage_legend">%d/%d</div></td></tr></tbody></table>
        """.formatted(coveredLines, totalLines));
  }

  private void writeJavaMutationFixture(String testSource) throws IOException {
    Path mainSource = projectDirectory.resolve("src/main/java/example/Target.java");
    Path testSourcePath = projectDirectory.resolve("src/test/java/example/TargetTest.java");
    Files.createDirectories(mainSource.getParent());
    Files.createDirectories(testSourcePath.getParent());
    Files.writeString(
        mainSource,
        """
        package example;

        public final class Target {
          public int value() {
            return 1;
          }
        }
        """);
    Files.writeString(testSourcePath, testSource);
  }

  private void writeEmptyMutationReport() throws IOException {
    Path reportDirectory = projectDirectory.resolve("build/reports/pitest");
    Files.createDirectories(reportDirectory);
    Files.writeString(
        reportDirectory.resolve("mutations.xml"),
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <mutations/>
        """);
    Files.writeString(
        reportDirectory.resolve("index.html"),
        """
        <table><tbody><tr><td>1</td><td>95%%
        <div class="coverage_legend">95/100</div></td></tr></tbody></table>
        """);
  }

  private GradleRunner runner(String... arguments) {
    return GradleRunner.create()
        .withProjectDir(projectDirectory.toFile())
        .withPluginClasspath()
        .withArguments(arguments)
        .forwardOutput();
  }
}
