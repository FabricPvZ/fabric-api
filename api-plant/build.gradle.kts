plugins {
    id("java-library")
    `maven-publish`
}

group = "cn.enaium.fabricpvz"
version = "1.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

dependencies {
    api(project(":api-base"))


    compileOnly("cn.enaium.fabricpvz:pvz-portable:1.0.0")
    testImplementation("cn.enaium.fabricpvz:pvz-portable:1.0.0")

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

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            groupId = "cn.enaium.fabricpvz"
            artifactId = "api-plant"
            version = "1.0.0"
            pom {
                name.set("api-plant")
                description.set("PvZ Fabric API: register custom plants")
                licenses {
                    license {
                        name.set("MIT")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
            }
        }
    }
}
