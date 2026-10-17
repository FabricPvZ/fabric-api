package cn.enaium.fabricpvz.api.plant;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import cn.enaium.fabricpvz.portable.lawn.Projectile;
import cn.enaium.fabricpvz.portable.lawn.ProjectileDefinition;
import cn.enaium.fabricpvz.portable.lawn.ProjectileType;

/**
 * Overrides the numbers a <b>projectile</b> type is defined with - the damage of a star, a spike, a
 * pea. Retail types only: projectile types are the game's own table, and a mod's projectiles reach
 * the game through the plant that fires them ({@link PlantType.Builder}).
 *
 * <pre>{@code
 * ProjectileOverrides.of(ProjectileType.PROJECTILE_STAR).damage(40);
 * ProjectileOverrides.of(ProjectileType.PROJECTILE_SPIKE).damage(1);
 * }</pre>
 *
 * <p>Every type has at most one override, handed out by {@link #of(int)}, which is idempotent: asking
 * twice gives the same handle, and the last value written to a field is the one applied. It is the
 * projectile-side twin of {@link PlantOverrides}: the numbers live in
 * {@code Projectile.gProjectileDefinition}, so a field is written into that row immediately and the
 * game reads it at impact time - no Mixin, only the ordering contract a mod entry point satisfies
 * (see {@link ProjectileOverride}).
 *
 * <p>The one projectile <em>behaviour</em> this module carries is the torchwood's conversion
 * ({@link #torchwoodConversion(int, int)}), which is a table in the game too - the two calls
 * {@code Plant::UpdateTorchwood} makes are its rows - and so is registered here rather than by a new
 * plant hook.
 */
public final class ProjectileOverrides {

    private static final Object LOCK = new Object();
    private static final Map<Integer, ProjectileOverride> BY_PROJECTILE_TYPE = new LinkedHashMap<>();

    /** projectile type the torchwood's flame reaches -> the type it leaves behind. */
    private static final Map<Integer, Integer> TORCHWOOD_CONVERSIONS = new LinkedHashMap<>();

    private ProjectileOverrides() {
    }

    /**
     * Returns the override of a projectile type, creating it on first use.
     *
     * @param theProjectileType a {@code ProjectileType} value
     * @return the type's override, to be filled in with the {@link ProjectileOverride} methods
     * @throws IllegalArgumentException if the value is not a projectile type
     */
    public static ProjectileOverride of(int theProjectileType) {
        definitionRowOrNull(theProjectileType);
        synchronized (LOCK) {
            return BY_PROJECTILE_TYPE.computeIfAbsent(theProjectileType, ProjectileOverride::new);
        }
    }

    /**
     * @return every override a mod registered, keyed by projectile type, in the order the types were
     *     first asked for; for diagnostics, the map is a snapshot and cannot be written through
     */
    public static Map<Integer, ProjectileOverride> registered() {
        synchronized (LOCK) {
            return Collections.unmodifiableMap(new LinkedHashMap<>(BY_PROJECTILE_TYPE));
        }
    }

    // -------------------------------------------------------------------------------------------
    // the torchwood's conversion
    // -------------------------------------------------------------------------------------------

    /**
     * What a projectile of {@code theFromProjectileType} becomes when a torchwood's flame reaches it -
     * the game's own conversion table, as a table a mod can rewrite.
     *
     * <pre>{@code
     * ProjectileOverrides.torchwoodConversion(ProjectileType.PROJECTILE_PEA, ProjectileType.PROJECTILE_SNOWPEA);
     * ProjectileOverrides.torchwoodConversion(ProjectileType.PROJECTILE_SNOWPEA, ProjectileType.PROJECTILE_FIREBALL);
     * }</pre>
     *
     * <p>Without a registration the game's own answer stands: a pea becomes a fireball and a snow pea
     * becomes a plain pea ({@code Plant::UpdateTorchwood}, {@code Projectile::ConvertToFireball} /
     * {@code ConvertToPea}). A registered row replaces that answer for the projectile type it names, and
     * the same type may be registered twice - the last write wins, exactly like a
     * {@link ProjectileOverride} field.
     *
     * <p>Only a conversion the game really offers is asked for: the torchwood's own loop only ever
     * looks at a pea and a snow pea, so a row for another type (a fireball, a star) is never consulted
     * - a projectile the game does not convert stays what it is.
     *
     * <p>The art follows the type, not the row: a projectile draws itself by its own type
     * ({@code Projectile::Draw}'s per-type image, the snow pea's {@code PROJECTILE_SNOWPEA} image), and
     * a conversion to a fireball additionally gets the flame reanimation the game's own
     * {@code ConvertToFireball} attaches - which is why the shape of a registered conversion is picked
     * by its target: a fireball keeps the game's flame art, anything else the game's plain
     * {@code ConvertToPea} shape (its attachment dies, the throw foley plays) with the registered type
     * written on top.
     *
     * @param theFromProjectileType the {@code ProjectileType} the flame reaches
     * @param theToProjectileType   the {@code ProjectileType} it leaves behind
     * @throws IllegalArgumentException if either value is not a projectile type
     */
    public static void torchwoodConversion(int theFromProjectileType, int theToProjectileType) {
        definitionRowOrNull(theFromProjectileType);
        definitionRowOrNull(theToProjectileType);
        synchronized (LOCK) {
            TORCHWOOD_CONVERSIONS.put(theFromProjectileType, theToProjectileType);
        }
    }

    /**
     * What the torchwood conversion of a projectile type leaves behind.
     *
     * <p><b>Internal.</b> Called by this module's torchwood Mixin in place of the two conversion calls
     * {@code Plant::UpdateTorchwood} makes, with the game's own answer for that call as the fallback, so
     * a projectile type nobody registered is converted exactly as the game converts it. The name
     * carries the {@code Target} suffix rather than the registration's name, which is a different
     * question with the same arity.
     *
     * @param theFromProjectileType the {@code ProjectileType} that reached the flame
     * @param theGameTarget         the type the game's own branch converts it to
     * @return the registered target, or {@code theGameTarget}
     */
    public static int torchwoodTarget(int theFromProjectileType, int theGameTarget) {
        synchronized (LOCK) {
            Integer aTarget = TORCHWOOD_CONVERSIONS.get(theFromProjectileType);
            return aTarget == null ? theGameTarget : aTarget;
        }
    }

    /**
     * Performs the conversion a torchwood asked for: the registered one when the projectile's type has
     * a row, the game's own otherwise.
     *
     * <p><b>Internal.</b> The body of this module's torchwood Mixin. The conversion itself is the
     * game's own method - {@code ConvertToFireball} or {@code ConvertToPea} - so the attachment, the
     * torchwood grid column the projectile remembers and the foley are the game's; a registered target
     * that is not a fireball is written over the type {@code ConvertToPea} left, which is the whole
     * difference between "a pea" and "a snow pea".
     *
     * @param theProjectile the projectile the flame reached
     * @param theGridX      the torchwood's column, as the game passes it
     * @param theGameTarget the type the game's own branch would convert it to
     */
    public static void applyTorchwoodConversion(Projectile theProjectile, int theGridX, int theGameTarget) {
        int aTarget = torchwoodTarget(theProjectile.mProjectileType, theGameTarget);
        if (aTarget == ProjectileType.PROJECTILE_FIREBALL) {
            theProjectile.convertToFireball(theGridX);
        } else {
            theProjectile.convertToPea(theGridX);
            theProjectile.mProjectileType = aTarget;
        }
    }

    /**
     * @return every registered torchwood conversion, the type the flame reaches -> the type it leaves
     *     behind, in the order the rows were registered; for diagnostics, a read-only snapshot
     */
    public static Map<Integer, Integer> torchwoodConversions() {
        synchronized (LOCK) {
            return Collections.unmodifiableMap(new LinkedHashMap<>(TORCHWOOD_CONVERSIONS));
        }
    }

    /**
     * Clears every override and puts the damage back to what the game had; used by tests and by a
     * loader that reloads mods in-process.
     */
    public static void reset() {
        synchronized (LOCK) {
            for (ProjectileOverride aOverride : BY_PROJECTILE_TYPE.values()) {
                aOverride.restore();
            }
            BY_PROJECTILE_TYPE.clear();
            TORCHWOOD_CONVERSIONS.clear();
        }
    }

    /**
     * The definition row of a projectile type, or {@code null} when the type does not exist.
     *
     * <p>Unlike {@code Plant#getPlantDefinition}, the game has no accessor for this table: it indexes
     * it directly ({@code Projectile::GetProjectileDef}), and the table is index-aligned with
     * {@code ProjectileType}. The alignment is checked here, so a type outside it is refused instead
     * of patching a row of another projectile.
     *
     * @param theProjectileType a {@code ProjectileType} value
     * @return the type's definition row, or {@code null}
     * @throws IllegalArgumentException if the value is not a projectile type, or the table is not
     *     index-aligned with it
     */
    static ProjectileDefinition definitionRowOrNull(int theProjectileType) {
        if (theProjectileType < 0 || theProjectileType >= ProjectileType.NUM_PROJECTILES) {
            throw new IllegalArgumentException("Not a projectile type: " + theProjectileType);
        }

        ProjectileDefinition aDefinition = Projectile.gProjectileDefinition[theProjectileType];
        if (aDefinition == null) {
            return null;
        }
        if (aDefinition.mProjectileType != theProjectileType) {
            throw new IllegalArgumentException("gProjectileDefinition is not index-aligned at "
                    + theProjectileType + ": row " + aDefinition.mProjectileType);
        }
        return aDefinition;
    }
}
