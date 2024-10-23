import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import org.gradle.kotlin.dsl.*


plugins {

    scala
    id("ai.acyclic.java-conventions")
}

val vs = versions()

// TODO: remove after https://github.com/ben-manes/gradle-versions-plugin/issues/816 resolved
tasks.named<DependencyUpdatesTask>("dependencyUpdates").configure {
    filterConfigurations = Spec<Configuration> {
        !it.name.startsWith("incrementalScalaAnalysis")
    }
}

allprojects {

    if (!plugins.hasPlugin("bloop")) {
        apply(plugin = "bloop")
    }

    // DO NOT enable! In VSCode it will cause the conflict:
    // Cannot add extension with name 'bloop', as there is an extension already registered with that name

    apply(plugin = "scala")

    dependencies {

        testRuntimeOnly("co.helmethair:scalatest-junit-runner:0.2.0")
    }

    fun SourceSet.compileJavaWithScalaC() {
        val moved = java.srcDirs

        scala {
            setSrcDirs(srcDirs + moved)
        }
        java {
            setSrcDirs(emptyList<String>())
        }
    }

    // scalaC will compiler both scala & java sources
    sourceSets {
        main { compileJavaWithScalaC() }
        testFixtures { compileJavaWithScalaC() }
        test { compileJavaWithScalaC() }
    }

    tasks {

        withType<ScalaCompile> {

            val jvmTarget = vs.jvmTarget.toString()

            sourceCompatibility = jvmTarget
            targetCompatibility = jvmTarget

            scalaCompileOptions.apply {

                loggingLevel = "verbose"

                forkOptions.apply {

                    memoryInitialSize = "1g"
                    memoryMaximumSize = "4g"

                    jvmArgs = listOf(
                        // this may be over the top but the test code in macro & core frequently run implicit search on church encoded Nat type
                        "-Xss256m",
                        // circumventing Java 17 + Apache Spark test runner
                        //
                    )
                }
            }
        }

        test {

            minHeapSize = "1024m"
            maxHeapSize = "4096m"

            useJUnitPlatform {
                includeEngines("scalatest")

                var addOpens =
                    """
                        java.base/java.lang
                        java.base/java.lang.invoke
                        java.base/java.lang.reflect
                        java.base/java.io
                        java.base/java.net
                        java.base/java.nio
                        java.base/java.util
                        java.base/java.util.concurrent
                        java.base/java.util.concurrent.atomic
                        java.base/jdk.internal.ref
                        java.base/sun.nio.ch
                        java.base/sun.nio.cs
                        java.base/sun.security.action
                        java.base/sun.util.calendar
                    """.trimIndent().split("\n")
                // from apache spark test runner

                var allArgs = listOf(
                    "-Xmx2048m",  // Maximum heap size
                    "-XX:MaxMetaspaceSize=512m",  // Maximum metaspace size
                    "-XX:+HeapDumpOnOutOfMemoryError",  // Dump heap on OOM
                    "-Dfile.encoding=UTF-8",  // Set file encoding
                )
                for (pkg in addOpens) {
                    allArgs += "--add-opens=$pkg=ALL-UNNAMED"
                }

                jvmArgs(allArgs)
            }

            testLogging {

                events("passed", "skipped", "failed")
//                events = setOf(org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED, org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED, org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED, org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_OUT)
//                exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
                showExceptions = true
                showCauses = true
                showStackTraces = true

                // stdout is used for occasional manual verification
                showStandardStreams = true
            }
        }

    }

    idea {

        module {

            excludeDirs = excludeDirs + listOf(
                file(".bloop"),
                file(".bsp"),
                file(".metals"),
                file(".ammonite"),
            )
        }
    }
}
