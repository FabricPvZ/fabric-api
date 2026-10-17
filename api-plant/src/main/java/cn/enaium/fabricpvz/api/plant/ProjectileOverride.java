package cn.enaium.fabricpvz.api.plant;

import cn.enaium.fabricpvz.portable.lawn.Projectile;
import cn.enaium.fabricpvz.portable.lawn.ProjectileDefinition;

/**
 * One projectile type's overrides, handed out by {@link ProjectileOverrides#of(int)}.
 *
 * <p>Upstream keeps a projectile's numbers in {@code Projectile.gProjectileDefinition}, a table
 * indexed by {@code ProjectileType} whose {@code mDamage} is what every hit applies - the game reads
 * it at impact time ({@code Projectile::DoImpact}, {@code CheckForCollision}'s plant branch, the
 * splash damage of a melon), never copies it into the projectile. An override is therefore an
 * immediate write into that row, effective for every projectile of the type from the next read on
 * (projectiles already in flight included - there is no per-projectile copy to go stale):
 *
 * <pre>{@code
 * ProjectileOverrides.of(ProjectileType.PROJECTILE_STAR).damage(40);
 * }</pre>
 *
 * <p>Because it is a table write and the game reads the table when it needs the number, a mod has to
 * write it before the game uses it - i.e. from the mod entry point the loader runs ahead of the
 * game's own main class, the same ordering contract {@link PlantRegistry#register} writes new rows
 * under. No Mixin is involved.
 */
public final class ProjectileOverride {

    private final int mProjectileType;
    private Integer mDamage;
    private Integer mDamageBefore;

    /** An override handle; only {@link ProjectileOverrides#of(int)} creates one. */
    ProjectileOverride(int theProjectileType) {
        mProjectileType = theProjectileType;
    }

    /**
     * Damage one hit of the projectile applies, i.e. {@code mDamage} of the type's
     * {@link ProjectileDefinition} row.
     *
     * @param theDamage the damage, {@code 0} or more ({@code 0} is a value, not "unset")
     */
    public ProjectileOverride damage(int theDamage) {
        if (theDamage < 0) {
            throw new IllegalArgumentException("A projectile's damage cannot be negative: " + theDamage);
        }
        ProjectileDefinition aDefinition = definitionRow();
        if (mDamage == null) {
            mDamageBefore = aDefinition.mDamage;
        }
        mDamage = theDamage;
        aDefinition.mDamage = theDamage;
        return this;
    }

    /** @return the projectile type this override belongs to. */
    public int projectileType() {
        return mProjectileType;
    }

    /** @return the overridden damage, or {@code null} when nobody set one. */
    public Integer damage() {
        return mDamage;
    }

    @Override
    public String toString() {
        StringBuilder aText = new StringBuilder("ProjectileOverride[projectileType=").append(mProjectileType);
        if (mDamage != null) {
            aText.append(", damage=").append(mDamage);
        }
        return aText.append(']').toString();
    }

    /** Puts the damage this handle wrote back to what the game had, and forgets everything. */
    void restore() {
        ProjectileDefinition aDefinition = ProjectileOverrides.definitionRowOrNull(mProjectileType);
        if (aDefinition != null && mDamageBefore != null) {
            aDefinition.mDamage = mDamageBefore;
        }
        mDamage = null;
        mDamageBefore = null;
    }

    private ProjectileDefinition definitionRow() {
        ProjectileDefinition aDefinition = ProjectileOverrides.definitionRowOrNull(mProjectileType);
        if (aDefinition == null) {
            throw new IllegalStateException("No projectile definition row for projectile type "
                    + mProjectileType + ": the table is as wide as ProjectileType and index-aligned with it");
        }
        return aDefinition;
    }
}
