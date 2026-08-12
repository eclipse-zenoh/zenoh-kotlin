//
// An external consumer of the published zenoh-kotlin artifact — deliberately not
// part of the main Gradle build, with no path, project or composite dependency
// on it. What it proves is what this repository cannot prove from inside its own
// build: that the published coordinate resolves, that the POM's transitive
// zenoh-flat-jni dependency exists and is resolvable too, and that the native
// library inside it loads.
//
// Run against a candidate:
//   gradle run -PcandidateVersion=<version> [-PcandidateRepository=<url>]
//
plugins {
    kotlin("jvm") version "2.1.0"
    application
}

val candidateVersion: String by project

// Defaults to the Maven Central snapshot repository, which is where this
// repository's snapshot publication puts both coordinates.
val candidateRepository: String =
    project.findProperty("candidateRepository")?.toString()
        ?: "https://central.sonatype.com/repository/maven-snapshots"

repositories {
    // The content filters make the resolution source unambiguous: anything
    // org.eclipse.zenoh can only come from the candidate repository, never from
    // a released copy of the same coordinates on Central.
    maven {
        name = "candidate"
        url = uri(candidateRepository)
        content { includeGroup("org.eclipse.zenoh") }
    }
    mavenCentral {
        content { excludeGroup("org.eclipse.zenoh") }
    }
}

dependencies {
    implementation("org.eclipse.zenoh:zenoh-kotlin:$candidateVersion")
}

kotlin {
    jvmToolchain(11)
}

application {
    mainClass.set("smoke.SmokeTestKt")
}
