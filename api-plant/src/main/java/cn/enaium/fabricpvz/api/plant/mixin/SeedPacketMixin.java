package cn.enaium.fabricpvz.api.plant.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import cn.enaium.fabricpvz.api.plant.PlantHooks;
import cn.enaium.fabricpvz.portable.lawn.SeedPacket;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;

/**
 * Draws the packet art a mod pinned to a fixed image/cel.
 *
 * <p>{@code SeedPacket::SeedPacketDrawSeed} used to check {@code SeedType.CUSTOM_SEED_FIRST} and draw
 * the pinned image instead of the reanimation-derived one; the check moved into {@link PlantHooks},
 * which returns {@code false} (and the caller keeps the upstream drawing) unless a registered plant
 * actually pinned art.
 */
@Mixin(SeedPacket.class)
public abstract class SeedPacketMixin {

    @Inject(method = "seedPacketDrawSeed(Lcn/enaium/fabricpvz/portable/sexy/graphics/Graphics;FFIIFFF)V",
            at = @At("HEAD"), cancellable = true)
    private static void fabricpvz$drawPacketArt(Graphics g, float x, float y, int theSeedType, int theImitaterType,
            float offX, float offY, float scale, CallbackInfo ci) {
        if (PlantHooks.drawPacketArt(g, x, y, theSeedType, theImitaterType, offX, offY, scale)) {
            ci.cancel();
        }
    }
}
