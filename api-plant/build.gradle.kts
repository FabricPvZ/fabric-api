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

dependencies {
    api(project(":api-base"))


    compileOnly("cn.enaium.fabricpvz:pvz-portable:1.1.0")
    testImplementation("cn.enaium.fabricpvz:pvz-portable:1.1.0")

    // Compile-time only: the Mixin annotations the dispatch Mixins are written against. The real Mixin
    // engine, with the same API, is supplied by Fabric Loader at runtime (through fabric-provider), so
    // this artifact must never be shipped in the module jar.
    compileOnly("net.fabricmc:sponge-mixin:0.17.4+mixin.0.8.7")

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

// The publication to Maven Central (the Central Portal); the credentials and the signing key are the
// ones the root build documents.
mavenPublishing {
    publishToMavenCentral(true)
    signAllPublications()

    coordinates("cn.enaium.fabricpvz", "api-plant", "1.2.0")

    pom {
        name.set("api-plant")
        description.set("PvZ Fabric API: register custom plants")
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
