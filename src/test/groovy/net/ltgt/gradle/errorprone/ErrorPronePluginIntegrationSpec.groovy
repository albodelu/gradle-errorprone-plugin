package net.ltgt.gradle.errorprone

import org.gradle.internal.jvm.Jvm
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification
import spock.lang.Unroll

import static org.junit.Assume.assumeFalse

class ErrorPronePluginIntegrationSpec extends Specification {
  @Rule final TemporaryFolder testProjectDir = new TemporaryFolder()
  File buildFile

  def setup() {
    buildFile = testProjectDir.newFile('build.gradle')
    buildFile << """\
      buildscript {
        dependencies {
          classpath files(\$/${System.getProperty('plugin')}/\$)
        }
      }
      apply plugin: 'net.ltgt.errorprone'
      apply plugin: 'java'

      repositories {
        mavenCentral()
      }
      dependencies {
        // 2.0.5 is compatible with JDK 7 (2.0.6 is not)
        errorprone 'com.google.errorprone:error_prone_core:2.0.5'
      }
""".stripIndent()
  }

  @Unroll
  def "compilation succeeds with Gradle #gradleVersion"() {
    given:
    def f = new File(testProjectDir.newFolder('src', 'main', 'java', 'test'), 'Success.java')
    f.createNewFile()
    getClass().getResource("/test/Success.java").withInputStream { f << it }

    when:
    def result = GradleRunner.create()
        .withGradleVersion(gradleVersion)
        .withProjectDir(testProjectDir.root)
        .withArguments('--info', 'compileJava')
        .build()

    then:
    result.output.contains("Compiling with error-prone compiler")
    result.task(':compileJava').outcome == TaskOutcome.SUCCESS

    where:
    gradleVersion << IntegrationTestHelper.GRADLE_VERSIONS
  }

  @Unroll
  def "compilation fails with Gradle #gradleVersion"() {
    given:
    def f = new File(testProjectDir.newFolder('src', 'main', 'java', 'test'), 'Failure.java')
    f.createNewFile()
    getClass().getResource("/test/Failure.java").withInputStream { f << it }

    when:
    def result = GradleRunner.create()
        .withGradleVersion(gradleVersion)
        .withProjectDir(testProjectDir.root)
        .withArguments('--info', 'compileJava')
        .buildAndFail()

    then:
    result.output.contains("Compiling with error-prone compiler")
    result.task(':compileJava').outcome == TaskOutcome.FAILED
    result.output.contains("Failure.java:6: error: [ArrayEquals]")

    where:
    gradleVersion << IntegrationTestHelper.GRADLE_VERSIONS
  }

  def "compatible with errorprone 1.x"() {
    assumeFalse("errorprone 1.x as deployed to Central only supports Java 7",
        Jvm.current().getJavaVersion().isJava8Compatible());

    given:
    buildFile << """\
      configurations.errorprone.resolutionStrategy {
        force 'com.google.errorprone:error_prone_core:1.1.2'
      }
    """.stripIndent()

    def f = new File(testProjectDir.newFolder('src', 'main', 'java', 'test'), 'Success.java')
    f.createNewFile()
    getClass().getResource("/test/Success.java").withInputStream { f << it }

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.root)
        .withArguments('--info', 'compileJava')
        .build()

    then:
    result.output.contains("Compiling with error-prone compiler")
    result.task(':compileJava').outcome == TaskOutcome.SUCCESS
  }
}
