# fabric-api

The mod API, shaped like Fabric API: a **multi-module** Gradle build with its **own** Fabric-shaped
event bus (`Event<T>` / `EventFactory`, package `cn.enaium.fabricpvz.api.event`), plus our own modules
on top of it.

| Gradle module | artifact / mod id | contents |
|---|---|---|
| `:` | `fabric-api` | aggregator, no code: mods depend on this coordinate and get the four modules, which the aggregate jar carries **inside itself** (jar-in-jar, see below) |
| `api-base` | `api-base` | `PvzFabricApi`, `ApiState`, `ApiModInitializer`, `ModQuery`, and the event bus `api.event` (`Event`, `EventFactory`, the array-backed implementation, `ListenerException`) |
| `api-events` | `api-events` | `api.lawn.LawnEvents` (level start/end, waves, zombie spawn/death/hurt, plant placed/removed/hurt, sun collected, planting validation, every tick, before/after draw) and `api.lifecycle.GameLifecycleEvents`, plus the hook bridges |
| `api-plant` | `api-plant` | `api.plant`: `PlantType` (+builder), `PlantRegistry`, `PlantRules`, `CustomPlantEvents` - register a plant with its seed cost, packet art, reanim, flags, almanac text and behaviour hooks |
| `api-zombie` | `api-zombie` | `api.zombie`: `ZombieType` (+builder), `ZombieRegistry`, `CustomZombieEvents` - register a zombie with its reanims, hp/armour, rects, wave participation and tick hook |

## Jar-in-jar: one jar, five mods

The aggregator ships the way Fabric API does: the four feature modules are nested in
`fabric-api-1.0.0.jar` under `META-INF/jars/`, listed in its packaged `fabric.mod.json`

```json
"jars": [ { "file": "META-INF/jars/api-base-1.0.0.jar" }, … ]
```

so Fabric Loader loads them as mods of their own, with the id, the version and the entry points of
their own metadata - an installation has **one** jar to drop into `mods/` (`fabric-api-1.0.0.jar`)
instead of five, and a mod declares `"fabric-api": ">=1.0.0"` and nothing else. The packaging is the
toolchain's mechanism (Fabric Loom's `include(...)`), not a hand-rolled step:
`loom/.../JarInJar.kt`, which the build here calls directly because the aggregator is not a mod
project of the toolchain (see `loom/README.md`, "Jar-in-jar" and "The API").

What that means for the dependencies, in both directions:

- **The aggregate keeps depending on the four** (`api(project(":api-base"))` … in the build and in the
  published POM, as Fabric API's aggregate POM does too), because **compilation needs the module jars
  themselves**: javac reads jar files, not jars nested inside a jar, so a mod's `import
  cn.enaium.fabricpvz.api.lawn.LawnEvents` only resolves when the module jar is on the compile class
  path.
- **Nothing needs them on a runtime class path.** The nested jars are what the loader reads, so
  `loom` puts the aggregate *alone* (`isTransitive = false`) on the class paths a run and a mod's
  end-to-end test launch with, and a mod that depends on this aggregate needs nothing else either. A
  class path carrying the modules separately as well would resolve every module twice (the loader
  deduplicates that silently - Fabric's own development environments have both shapes - but the
  installed shape is the one worth testing).


Event bus: we ship **our own** Fabric-shaped bus (`cn.enaium.fabricpvz.api.event`) instead of
depending on the upstream Fabric API base module. That module stopped being usable outside a Loom
environment at **0.4.0**: every release from there on (including the newest, `2.0.7+fcdff87fba`) has
event classes compiled against Minecraft/Guava — `Event` / `EventFactory` / `ArrayBackedEvent` /
`EventPhaseData` reference `net/minecraft/resources/Identifier` (in 0.4.0 the *intermediary*
`net/minecraft/class_2960`, i.e. bytecode only Loom can remap), `EventFactoryImpl` uses Guava
`MapMaker`, and `TriState` / `EventResult` use `net/minecraft/util/StringRepresentable`. `0.3.0` was
the last pure-JDK release, and pinning an old version was not wanted either, so the API has no Fabric
API event-bus dependency at all (and the loader-resolved mod list no longer contains it). See
`docs/VERIFICATION.md` §27 for the per-version scan and §28 for our bus and its tests.

The game-side extension points live in the game's own `cn.enaium.fabricpvz.portable.lawn.content`
package (`PlantTypeRegistry`, `ZombieTypeRegistry`, `CustomPlantType`, `CustomZombieType`,
`PlantBehaviour`, `ZombieBehaviour`), so the game itself never depends on the loader or this API.

Build and publish:

```bash
./gradlew build                 # 52 tests
./gradlew publishToMavenLocal   # publishes cn.enaium.fabricpvz:fabric-api:1.0.0
```

The build reads `cn.enaium.fabricpvz:loom` from the Gradle Plugin Portal (`pluginManagement` in
`settings.gradle.kts`; the toolchain's `JarInJar`, see above), so nothing has to be published locally
before this project - or the toolchain - can be built.

Dependencies: Fabric Loader (compileOnly + tests, runtime-provided by the loader), the game
(compileOnly + tests, because per Fabric semantics "fabric-api depends on minecraft") and this build's
own four modules, which are both `api` dependencies (compilation) and the jars nested into the
aggregate (runtime, above).

## License

MIT (see `LICENSE`). The game port itself (`PvZ-Portable`) is LGPL-3.0-or-later with the PopCap framework licence.
