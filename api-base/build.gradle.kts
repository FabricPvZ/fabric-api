plugins {
    id("java-library")
    // Maven Central (Central Portal) publication of this module: see the root build for the credentials
    alias(libs.plugins.maven.publish)
}

group = "cn.enaium.fabricpvz"
version = "1.2.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

// api-base is the module every other API module and every mod builds on: the mod-facing facade
// (PvzFabricApi), the mod query helpers and the entrypoint that marks the API as initialised.
dependencies {
    // Fabric Loader is runtime-provided: it is on the class path of every mod and of the game, so it
    // is never bundled and never published as a transitive dependency. Tests instantiate the entry
    // point, so they need it on the test class path too.
    compileOnly(libs.fabric.loader)
    testImplementation(libs.fabric.loader)

    // No event-bus dependency: our own Fabric-API-shaped Event/EventFactory live in this module, in
    // cn.enaium.fabricpvz.api.event. The upstream Fabric API base module is not usable outside a Loom
    // environment from its 0.4.0 release on, and only its oldest 0.3.0 jar is pure JDK, so the API
    // does not depend on it; see ../verification/VERIFICATION.md §27 (the audit) and §28 (the switch).

    // Fabric parity: fabric-api depends on the game it extends, and the game depends on this API at
    // runtime, so the game is a compile-only dependency of every API module (it is never published
    // in the POM, which keeps the game -> api -> game cycle out of the dependency graph).
    compileOnly("cn.enaium.fabricpvz:pvz-portable:1.2.1")
    testImplementation("cn.enaium.fabricpvz:pvz-portable:1.2.1")

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// The game's published POM depends on the API aggregator (Fabric parity), so resolving the game
// would drag the API back onto this module's class path (Gradle then refuses the implicit jar
// dependency). The modules are compiled and tested from their own sources, so exclude self.
configurations.configureEach {
    exclude(group = "cn.enaium.fabricpvz", module = "fabric-api")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 25
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// The publication to Maven Central (the Central Portal). The credentials and the GPG key come from
// ~/.gradle/gradle.properties (see the root build, where the same block is configured for the
// aggregator); the sources and javadoc jars Central requires, and the signature of every artifact,
// are added by the plugin.
mavenPublishing {
    publishToMavenCentral(true)
    signAllPublications()

    coordinates("cn.enaium.fabricpvz", "api-base", "1.2.0")

    pom {
        name.set("api-base")
        description.set("PvZ Fabric API: mod facade, mod queries and the Fabric-shaped event bus")
        url.set("https://github.com/FabricPvZ/fabric-api")
        licenses {
            license {
                name.set("MIT")
                url.set("https://opensource.org/licenses/MIT")
            }
        }
        developers {
            developer {
                id.set("enaium")
                name.set("Enaium")
                url.set("https://github.com/Enaium")
            }
        }
        scm {
            url.set("https://github.com/FabricPvZ/fabric-api")
            connection.set("scm:git:git://github.com/FabricPvZ/fabric-api.git")
            developerConnection.set("scm:git:ssh://git@github.com/FabricPvZ/fabric-api.git")
        }
    }
}
