package cn.enaium.fabricpvz.api.plant.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import cn.enaium.fabricpvz.api.plant.ProjectileOverrides;
import cn.enaium.fabricpvz.portable.lawn.Plant;
import cn.enaium.fabricpvz.portable.lawn.Projectile;
import cn.enaium.fabricpvz.portable.lawn.ProjectileType;

/**
 * Applies {@link ProjectileOverrides#torchwoodConversion(int, int)} at the two conversion calls
 * {@code Plant::UpdateTorchwood} makes.
 *
 * <p>The game's own torchwood loop is left exactly as it is - which projectile it looks at, the attack
 * rect it tests and the "this torchwood already converted me" column it remembers - and only the two
 * conversions that loop makes (a pea into a fireball, a snow pea into a plain pea) go through the
 * registered table, with the game's own answer as the fallback. Everything a conversion does besides
 * the type - the flame reanimation of a fireball, the dying attachment and the foley of the plain
 * shape, the torchwood column - is the game's own method, so a registered row changes the projectile
 * type and nothing else.
 */
@Mixin(Plant.class)
public abstract class TorchwoodConversionMixin {

    /** A pea reaching the flame: the game converts it to a fireball unless a row says otherwise. */
    @Redirect(method = "updateTorchwood()V",
            at = @At(value = "INVOKE",
                    target = "Lcn/enaium/fabricpvz/portable/lawn/Projectile;convertToFireball(I)V"))
    private void fabricpvz$torchwoodPea(Projectile theProjectile, int theGridX) {
        ProjectileOverrides.applyTorchwoodConversion(theProjectile, theGridX, ProjectileType.PROJECTILE_FIREBALL);
    }

    /** A snow pea reaching the flame: the game converts it to a plain pea unless a row says otherwise. */
    @Redirect(method = "updateTorchwood()V",
            at = @At(value = "INVOKE",
                    target = "Lcn/enaium/fabricpvz/portable/lawn/Projectile;convertToPea(I)V"))
    private void fabricpvz$torchwoodSnowPea(Projectile theProjectile, int theGridX) {
        ProjectileOverrides.applyTorchwoodConversion(theProjectile, theGridX, ProjectileType.PROJECTILE_PEA);
    }
}
