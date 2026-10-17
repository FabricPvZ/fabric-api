# fabric-api

The mod API, shaped like Fabric API: a **multi-module** Gradle build with its **own** Fabric-shaped
event bus (`Event<T>` / `EventFactory`, package `cn.enaium.fabricpvz.api.event`), plus our own modules
on top of it.

| Gradle module | artifact / mod id | contents |
|---|---|---|
| `:` | `fabric-api` | aggregator, no code: mods depend on this coordinate and get the five modules, which the aggregate jar carries **inside itself** (jar-in-jar, see below) |
| `api-base` | `api-base` | `PvzFabricApi`, `ApiState`, `ApiModInitializer`, `ModQuery`, and the event bus `api.event` (`Event`, `EventFactory`, the array-backed implementation, `ListenerException`) |
| `api-events` | `api-events` | `api.lawn.LawnEvents` (level start/end, waves, zombie spawn/death/hurt, plant placed/removed/hurt, sun collected, planting validation, every tick, before/after draw) and `api.lifecycle.GameLifecycleEvents`, plus the hook bridges |
| `api-plant` | `api-plant` | `api.plant`: `PlantType` (+builder), `PlantRegistry`, `PlantRules`, `CustomPlantEvents` - register a plant with its seed cost, packet art (a fixed image id/cel, or a lazy `Supplier<Image>` the game asks once per packet draw), reanim, flags (including `upgradeFrom`: which plants this seed may be planted on as their upgrade, injected into `Plant::IsUpgradableTo` - distinct from `upgrade`, which only picks the purple packet background), almanac text, behaviour hooks and its burst (2/4 shots per launch, injected into `Plant::FindTargetAndFire`/`UpdateShooter`/`UpdateShooting`); `PlantOverrides` - rebalance the plants that already exist, retail seed types included: the definition-row numbers (sun cost, recharge, launch rate - plain writes into `Plant.gPlantDefs`, effective from the mod entry point on) the spawn health (`Plant#mPlantHealth`/`mPlantMaxHealth`) and the spawn countdown (`Plant#mStateCountdown`), applied by the module's `PlantOverridesMixin` at `Plant::PlantInitialize`, and the numbers the game reads while the plant is alive - a chomper's third "bite instead of eat" type and its digesting pause, a spikerock's hit damage and its two crack levels, a potato mine's blast arguments and a marigold's coins - handed over by the same Mixin at those read points; `ProjectileOverrides` - the damage of an existing projectile type, a row of `Projectile.gProjectileDefinition` the game reads at impact time; `BoardOverrides` - the board-level knobs a rebalance needs: which zombie types a game mode allows in addition to the game's own list (applied right after `Challenge::InitZombieWaves` filled `Board.mZombieAllowed`, so the added types really take part in the level's waves), the two per-zombie spawn vetoes (`theLevel < mStartingLevel` and `mPickWeight == 0`, dropped through the `BoardWavesMixin` redirects so the allowed-levels table alone decides), single cells of `Challenge.gZombieAllowedLevels` (an immediate write, `reset()` puts the retail value back), the Beghouled winning score and its "almost there" threshold, the `Board::KillAllZombiesInRadius` blast damage, the raining-seeds interval, the grave-stone answer, the sun values and the sun-bank cap, the conveyor-belt seed list (`Challenge::UpdateConveyorBelt`), the mode whose sky drop is a usable seed packet and the coin an ordinary sky drop is (`Board::UpdateSunSpawning`), the death-loot sun branch and its three coins (`Board::DropLootPiece`), the sun a Last Stand level starts with (`Board::InitLevel`), a coin's usable seed type (`Coin::CoinInitialize`), the wave counts (`BoardWaves::GetNumWavesPerSurvivalStage` and `BoardWaves::PickZombieWaves`) and the seed picker's vetoes (`SeedChooserScreen::SeedNotAllowedToPick`, plus the Grave Buster advice bit inversion) |
| `api-resource` | `api-resource` | `api.resource`: `ModImageSlot` (a retail image id the game never draws, kept bound to a mod's image across resource loads - for art that *is* a resource file; art built at runtime goes through the packet-art supplier of `api-plant`), `ModImages` (load/paint/hue-re-map), `GameArt` (render the game's own packet art - at the seed's own packet placement - and cached plant frame), `ModReanims` (per-track image overrides, whole-reanimation recolouring, and the extra head layer), `ResourceOverrides` (loose files above the pak: a directory / single files / raw bytes shadow the pak member of the same name - the "95 版" style re-skin) |
| `api-zombie` | `api-zombie` | `api.zombie`: `ZombieType` (+builder), `ZombieRegistry`, `CustomZombieEvents` - register a zombie with its reanims, hp/armour, rects, wave participation and tick hook; `ZombieOverrides` - rebalance the zombies that already exist, retail types included: spawn-time body/helm/shield health, phase counter, type replacement and body animation, applied by the module's `ZombieOverridesMixin` at `Zombie::ZombieInitialize` |

## Rebalance seams

The overrides above cover the numbers a plant or a zombie is *defined and born* with. A rebalance that
has to rewrite a **decision** the game makes later needs a seam at that decision, and that is what this
batch adds - all of them "the game's own value until a mod sets one":

| module | seam | decided at |
|---|---|---|
| `api-zombie` | `ZombieOverrides.mindControlBoost(int bodyBonus, int helmBonus, float scale)` and the per-type `ZombieOverride.mindControlBodyHealth(int)` - what a zombie gains when it is mind-controlled (cleared chill, body/helm health, scale) | `Zombie::StartMindControlled` (`ZombieMixin`) |
| `api-zombie` | `ZombieOverrides.burnImmunity(int totalHealth, int... immunePhases)` - the ash/vault clamp's predicate: body + helmet + shield, plus the zombie phases that always take it | `Zombie::ApplyBurn` (`ZombieStatusMixin`) |
| `api-zombie` | `ZombieOverrides.coldRemovalChilledCounter(int)` - `Zombie::RemoveColdEffects` writes this instead of clearing the counter | `Zombie::RemoveColdEffects` (`ZombieStatusMixin`) |
| `api-zombie` | `ZombieOverrides.shieldAlwaysAbsorbs(boolean)` - both damage flags lose their power, so every hit eats into the shield first | `Zombie::TakeDamage`'s two `PvzpCommon.testBit` calls (`ZombieCombatMixin`) |
| `api-plant` | `PlantOverride.crushProof(boolean)` - a plant a gargantuar or a vehicle bounces off instead of squashing | `Zombie::UpdateZombieGargantuar` and `Zombie::SquishAllInSquare` (`ZombieSmashMixin` / `ZombieCrushMixin`) |
| `api-plant` | `PlantOverrides.notOnGroundState(int plantState)` - the state that makes `Plant::NotOnGround` answer "yes", replacing the game's "rising with a bungee" clause | `Plant::NotOnGround` (`PlantMixin`) |
| `api-plant` | `PlantOverrides.extraFireSeedType(int seedType)` - the seed type `Plant::UpdateShooter` fires its extra launch-counter-50 shot for | `Plant::UpdateShooter` (`PlantOverridesMixin`) |

`PlantOverrides.rules()` / `ZombieOverrides.rules()` are the diagnostics views of the whole-game rules,
the mirror of `registered()`. The two rules that are a rewrite of a *method* rather than an answer - the
95 Edition's bite (8 a chew, and the two transformations on it) and its seed-packet rewrite - are the
mod's own `LawnHooks` implementation rather than an API knob; see `docs/guide/events.md`.

Two names exist in both modules: `ZombieCombatMixin` (api-zombie's answers the shield flags, api-plant's
answers the square-squash comparison), and each module's `ZombieSmashMixin`/`ZombieCrushMixin` lives in
`api-plant` because what it asks is a question about a plant.

## Jar-in-jar: one jar, six mods

The aggregator ships the way Fabric API does: the five feature modules are nested in
`fabric-api-1.2.0.jar` under `META-INF/jars/`, listed in its packaged `fabric.mod.json`

```json
"jars": [ { "file": "META-INF/jars/api-base-1.2.0.jar" }, … ]
```

so Fabric Loader loads them as mods of their own, with the id, the version and the entry points of
their own metadata - an installation has **one** jar to drop into `mods/` (`fabric-api-1.2.0.jar`)
instead of six, and a mod declares `"fabric-api": ">=1.0.0"` and nothing else. The packaging is the
toolchain's mechanism (Fabric Loom's `include(...)`), not a hand-rolled step:
`loom/.../JarInJar.kt`, which the build here calls directly because the aggregator is not a mod
project of the toolchain (see `loom/README.md`, "Jar-in-jar" and "The API").

What that means for the dependencies, in both directions:

- **The aggregate keeps depending on the five** (`api(project(":api-base"))` … in the build and in the
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

**Where the content comes from.** The game itself has no idea what a mod is: it owns a definition table
per content kind (`Plant::gPlantDefs`, `Zombie::gZombieDefs`), each as wide as its id space
(`SeedType::MAX_SEED_TYPES` / `ZombieType::MAX_ZOMBIE_TYPES`) with the retail rows at the bottom and
`null` rows above them, and it reads the table through a single accessor that answers `null` for a row
nobody defined (`Plant::getPlantDefinition`, `Zombie::getZombieDefinition`). Registering content is
therefore a plain write into that table, which `api-plant`/`api-zombie` do on behalf of their mods
(`PlantRegistry` allocates an id and writes `Plant.gPlantDefs[id] = new PlantDefinition(...)`).

Everything that is *not* table data - the name and tooltip, the planting/collision rectangles, the
per-tick callback, the projectile of a shooting plant, the unlock gate the seed chooser reads, the seed
packet art, the zombie's stats and head attachment, and the re-dispatch of the placement/spawn
callbacks after a mid-level save is loaded - is dispatched by **Mixins** that this API ships and
the loader applies to the game's classes (`cn.enaium.fabricpvz.api.plant.mixin`, `...zombie.mixin`, each
documented with the game method it injects into; both modules also inject `Board::loadGame` so a
registered plant's/zombie's callback re-runs when a save is restored). Nothing of that lives in the game:
an installation without this API has exactly the retail behaviour, because every table row above the
retail ones is `null` and no mixin is applied.

The tools that keep this true: the game's own tests pin the table contract (retail rows complete and
index-aligned, higher rows empty, lookups answering `null`), and the loader end-to-end test in
`PvZ-Portable` (plus this API's own module tests) load a fixture mod through the real Fabric Loader with
the real Mixin service, so a silent injection failure cannot ship.

## Resource overrides: loose files above the pak

`api-resource` also carries `ResourceOverrides` (`cn.enaium.fabricpvz.api.resource`), the layer a
loose-package re-skin ("95 版") needs: a directory of `images/`, `reanim/`, `properties/LawnStrings.txt`
… that shadows the pak. The game resolves a resource as "pak member, then filesystem", so the pak
always wins; the module's `ResourceOverridesMixin` injects at the head of `PakInterface.open` (read
mode) and `PakInterface.exists` and resolves in this order:

1. a single file or byte array registered for that exact pak path;
2. each registered root directory, in registration order;
3. the pak member (the engine's own lookup);
4. the filesystem relative to the resource folder (the engine's own fallback).

```java
ResourceOverrides.addRoot(Path.of("95-edition"));          // 95-edition/reanim/… shadows main.pak
ResourceOverrides.addFile("PROPERTIES/LAWNSTRINGS.TXT", Path.of("overrides/LawnStrings.txt"));
ResourceOverrides.addBytes("REANIM/FOO.PNG", Files.readAllBytes(Path.of("foo.png"))); // from the mod's jar
```

Keys are compared the way the engine does - case-insensitively, `'\\'` and `'/'` alike, `.` / `..`
resolved, upper-case internally - and root lookups match the real case of the file on disk, so the
same registration works on a case-sensitive filesystem. With nothing registered the Mixin is a single
volatile read away from the engine's original code path, so an installation that registers no override
behaves exactly as before.

Build and publish:

```bash
./gradlew build                    # unit tests
./gradlew publishToMavenLocal      # publishes cn.enaium.fabricpvz:fabric-api:1.2.0 locally
./gradlew publishToMavenCentral    # publishes all six artifacts to Maven Central
```

`publishToMavenCentral` uploads one Central Portal deployment holding the aggregator and the five
modules (with the sources and javadoc jars and the signature of every file Central requires) and
releases it as soon as the portal has validated it. The credentials it authenticates with - the
Central Portal user tokens `mavenCentralUsername` / `mavenCentralPassword` - and the GPG key it signs
with (`signing.keyId` / `signing.password` / `signing.secretKeyRingFile`, or the `signingInMemory*`
properties on CI) are read from `~/.gradle/gradle.properties`: none of them is in this build. A
checkout without them can still `build`; publishing to the portal is the one thing it cannot do.

The build reads `cn.enaium.fabricpvz:loom` from the Gradle Plugin Portal (`pluginManagement` in
`settings.gradle.kts`; the toolchain's `JarInJar`, see above), so nothing has to be published locally
before this project - or the toolchain - can be built.

Dependencies: Fabric Loader (compileOnly + tests, runtime-provided by the loader), the game
(compileOnly + tests, because per Fabric semantics "fabric-api depends on minecraft") and this build's
own five modules, which are both `api` dependencies (compilation) and the jars nested into the
aggregate (runtime, above).

## License

MIT (see `LICENSE`). The game port itself (`PvZ-Portable`) is LGPL-3.0-or-later with the PopCap framework licence.
