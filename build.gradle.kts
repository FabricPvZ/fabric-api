import cn.enaium.fabricpvz.loom.JarInJar

plugins {
    id("java-library")
    `maven-publish`
    // The jar-in-jar mechanism of the toolchain (see `JarInJar` below) lives in the loom plugin. This
    // project is not a mod project of the toolchain - it has no game code, no `pvz` distribution, no
    // `runClient` and its own publication - so it applies nothing and asks the plugin only for its
    // classes, which is what this declaration puts on the build script class path.
    id("cn.enaium.fabricpvz.loom") version "1.0.0" apply false
}

group = "cn.enaium.fabricpvz"
version = "1.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

// The four feature modules, as one list: the aggregate names each of them twice - as a dependency to
// compile against, and as a jar to carry inside itself - and the two must not drift apart.
val apiModules = listOf(project(":api-base"), project(":api-events"), project(":api-plant"), project(":api-zombie"))

// The jars the aggregate carries inside itself (Fabric Loom's `include(...)`): the toolchain's
// mechanism, so the aggregator does not hand-roll a zip: they are nested into
// build/libs/fabric-api-1.0.0.jar under `META-INF/jars/` and listed in its packaged
// `fabric.mod.json`, from where Fabric Loader loads them as mods of their own - with their own ids,
// versions and entry points, exactly as Fabric API ships its modules.
val included = JarInJar.configuration(project)

// The aggregator carries no code: it is the artifact mods and the game depend on
// (`cn.enaium.fabricpvz:fabric-api`), and it pulls the feature modules in transitively exactly
// like `net.fabricmc.fabric-api:fabric-api` pulls in the Fabric API modules. It ships its own
// `fabric.mod.json` (id `fabric-api`, no entrypoints) so the loader sees the API as one mod that
// depends on its modules.
dependencies {
    // The four stay `api` dependencies and stay in the published POM although the same four jars
    // are nested in this one: what a build needs them *compiled* against is the jars themselves (javac
    // reads jar files, not jars inside a jar), which is exactly why Fabric API's aggregate POM lists
    // its modules too. What the nesting removes is the *runtime* need for them: Fabric Loader loads
    // the nested jars, so a mod that depends on the aggregate needs nothing else on its runtime class
    // path (see loom's `configureApi`, which puts this one jar there and not the four).
    for (module in apiModules) {
        api(module)
    }

    // The same four jars, to be nested into the aggregate jar.
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

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            groupId = "cn.enaium.fabricpvz"
            artifactId = "fabric-api"
            version = "1.0.0"
            pom {
                name.set("fabric-api")
                description.set("Aggregator of the Fabric-style modding API modules for the PvZ-Portable Java port")
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
