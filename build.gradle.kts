import cn.enaium.fabricpvz.loom.JarInJar

plugins {
    id("java-library")
    // The Maven Central publication (Central Portal + GPG signing): the root project is one of the six
    // artifacts of this build (`cn.enaium.fabricpvz:fabric-api`, the aggregator), so it configures the
    // same publication the five modules do, and one `publishToMavenCentral` publishes all six.
    alias(libs.plugins.maven.publish)
    // The jar-in-jar mechanism of the toolchain (see `JarInJar` below) lives in the loom plugin. This
    // project is not a mod project of the toolchain - it has no game code, no `pvz` distribution, no
    // `runClient` and its own publication - so it applies nothing and asks the plugin only for its
    // classes, which is what this declaration puts on the build script class path.
    id("cn.enaium.fabricpvz.loom") version "1.0.5" apply false
}

group = "cn.enaium.fabricpvz"
version = "1.2.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

// The five feature modules, as one list: the aggregate names each of them twice - as a dependency to
// compile against, and as a jar to carry inside itself - and the two must not drift apart.
val apiModules = listOf(project(":api-base"), project(":api-events"), project(":api-plant"),
    project(":api-resource"), project(":api-zombie"))

// The jars the aggregate carries inside itself (Fabric Loom's `include(...)`): the toolchain's
// mechanism, so the aggregator does not hand-roll a zip: they are nested into
// build/libs/fabric-api-1.2.0.jar under `META-INF/jars/` and listed in its packaged
// `fabric.mod.json`, from where Fabric Loader loads them as mods of their own - with their own ids,
// versions and entry points, exactly as Fabric API ships its modules.
val included = JarInJar.configuration(project)

// The aggregator carries no code: it is the artifact mods and the game depend on
// (`cn.enaium.fabricpvz:fabric-api`), and it pulls the feature modules in transitively exactly
// like `net.fabricmc.fabric-api:fabric-api` pulls in the Fabric API modules. It ships its own
// `fabric.mod.json` (id `fabric-api`, no entrypoints) so the loader sees the API as one mod that
// depends on its modules.
dependencies {
    // The five stay `api` dependencies and stay in the published POM although the same five jars
    // are nested in this one: what a build needs them *compiled* against is the jars themselves (javac
    // reads jar files, not jars inside a jar), which is exactly why Fabric API's aggregate POM lists
    // its modules too. What the nesting removes is the *runtime* need for them: Fabric Loader loads
    // the nested jars, so a mod that depends on the aggregate needs nothing else on its runtime class
    // path (see loom's `configureApi`, which puts this one jar there and not the five).
    for (module in apiModules) {
        api(module)
    }

    // The same five jars, to be nested into the aggregate jar.
    for (module in apiModules) {
        add(included.name, module)
    }

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// Nest them: the aggregate jar gets the modules under `META-INF/jars/` and the `jars` array in its
// `fabric.mod.json` naming them (a jar with nothing to nest is not touched at all - see JarInJar).
JarInJar.include(tasks.jar.get(), included)

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
    // AggregatorJarInJarTest asserts on the artifact itself, so the jar has to exist (and to have been
    // through the nesting above) before the tests run.
    dependsOn(tasks.jar)
    systemProperty("fabric.api.jar", tasks.jar.get().archiveFile.get().asFile.absolutePath)
}

// The publication to Maven Central (the Central Portal), the only repository this artifact is
// published to: the credentials (`mavenCentralUsername`/`mavenCentralPassword`, Central Portal user
// tokens) and the GPG key the signatures are made with (`signing.*`) are read from
// ~/.gradle/gradle.properties, so nothing of them is in this build. The plugin adds the sources and
// javadoc jars Central requires and signs every artifact; `publishToMavenCentral(true)` releases the
// deployment on the portal as soon as the portal has validated it.
mavenPublishing {
    publishToMavenCentral(true)
    signAllPublications()

    coordinates("cn.enaium.fabricpvz", "fabric-api", "1.2.0")

    pom {
        name.set("fabric-api")
        description.set("Aggregator of the Fabric-style modding API modules for the PvZ-Portable Java port")
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
