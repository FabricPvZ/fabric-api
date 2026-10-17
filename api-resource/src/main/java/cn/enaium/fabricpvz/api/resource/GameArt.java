package cn.enaium.fabricpvz.api.resource;

import cn.enaium.fabricpvz.portable.lawn.DrawVariation;
import cn.enaium.fabricpvz.portable.lawn.GameConstants;
import cn.enaium.fabricpvz.portable.lawn.LawnApp;
import cn.enaium.fabricpvz.portable.lawn.SeedPacket;
import cn.enaium.fabricpvz.portable.lawn.SeedType;
import cn.enaium.fabricpvz.portable.sexy.SexyAppBase;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;
import cn.enaium.fabricpvz.portable.sexy.graphics.MemoryImage;

/**
 * Produces a mod's art with the <em>game's own</em> drawing code, and installs game-cached frames.
 *
 * <p>Nothing here is hand-drawn: a mod that wants its plant to look like a plant the game already
 * draws asks the game to draw that plant, and re-colours the result. A copy built this way cannot
 * drift from the art it depicts, which is what a hand-rendered icon would do.
 *
 * <h2>The seed packet icon</h2>
 *
 * <p>{@link #packetArt(int)} renders one seed type's packet icon exactly as
 * {@code SeedPacket::DrawSeedPacket} draws that seed's own art: the packet's 50x70 cel, the plant at
 * the scale and offset that seed's case uses ({@code SeedPacket.getPacketArtPlacement} - the
 * peashooter and the repeater are 0.5 / (5, 9), the gatling pea 0.5 / (2, 9), the threepeater
 * 0.5 / (5, 11); SeedPacket.cpp:286-604). The result is a transparent 50x70 canvas; it is
 * {@code null} while the game cannot draw it yet, i.e. before there is a running application with a
 * reanimation cache and the loaded art the icon is made of.
 *
 * <h2>The cached plant frame</h2>
 *
 * <p>The cursor preview, the zen garden and every other reader of the game's plant-frame cache draw
 * the frame at {@code ReanimatorCache::mPlantImages[seedType]}, which the game builds lazily from the
 * plant's reanimation ({@code ReanimatorCache::MakeCachedPlantFrame}). A plant whose recolouring
 * happens per placed instance is not reflected there, so a mod that recolours a plant has to build
 * its own frame with {@link #cachedPlantFrame(int)} and put it into the cache with
 * {@link #installCachedPlantFrame(int, Image)} - otherwise the plant is one colour on the lawn and
 * another under the mouse pointer.
 */
public final class GameArt {

    private GameArt() {
    }

    /**
     * Renders one seed type's seed-packet icon through the game's own packet drawing, at the
     * placement that seed's own packet case uses.
     *
     * @param theSeedType the seed type to draw, usually a retail one whose art the mod recolours
     * @return the 50x70 icon, or {@code null} while the game has nothing to draw yet
     */
    public static MemoryImage packetArt(int theSeedType) {
        if (!(SexyAppBase.current instanceof LawnApp) || ((LawnApp) SexyAppBase.current).mReanimatorCache == null) {
            return null;
        }

        float[] aPlacement = SeedPacket.getPacketArtPlacement(theSeedType, SeedType.SEED_NONE);
        MemoryImage aCanvas = ModImages.blank(GameConstants.SEED_PACKET_WIDTH, GameConstants.SEED_PACKET_HEIGHT);
        Graphics aGraphics = new Graphics(aCanvas);
        aGraphics.setLinearBlend(true);
        SeedPacket.seedPacketDrawSeed(aGraphics, 0, 0, theSeedType, SeedType.SEED_NONE,
                aPlacement[0], aPlacement[1], aPlacement[2]);
        return ModImages.hasVisiblePixels(aCanvas) ? aCanvas : null;
    }

    /**
     * Builds one seed type's cached plant frame through the game's own frame code - the picture the
     * cursor preview and the zen garden draw.
     *
     * @param theSeedType the seed type to draw, usually a retail one whose art the mod recolours
     * @return the frame, or {@code null} while the game has nothing to draw yet
     */
    public static MemoryImage cachedPlantFrame(int theSeedType) {
        if (!(SexyAppBase.current instanceof LawnApp)) {
            return null;
        }

        LawnApp aApp = (LawnApp) SexyAppBase.current;
        if (aApp.mReanimatorCache == null) {
            return null;
        }

        MemoryImage aFrame = aApp.mReanimatorCache.makeCachedPlantFrame(theSeedType, DrawVariation.VARIATION_NORMAL);
        return ModImages.hasVisiblePixels(aFrame) ? aFrame : null;
    }

    /**
     * Puts a frame into the game's plant-frame cache, so every reader of the cache - the cursor
     * preview, the zen garden, the seed chooser - draws this mod's art for the seed type instead of
     * the frame the game would have built.
     *
     * <p>Quietly does nothing while there is no running application or no cache, and refuses a frame
     * that paints nothing (installing it would make the preview blank rather than the art the game
     * would have drawn). Idempotent: installing the frame the cache already holds rewrites nothing.
     *
     * @param theSeedType the seed type the frame belongs to
     * @param theFrame the frame to draw for it
     * @throws IllegalArgumentException if {@code theFrame} is not a {@link MemoryImage}
     */
    public static void installCachedPlantFrame(int theSeedType, Image theFrame) {
        if (!(SexyAppBase.current instanceof LawnApp)) {
            return;
        }

        LawnApp aApp = (LawnApp) SexyAppBase.current;
        if (aApp.mReanimatorCache == null || theFrame == null) {
            return;
        }
        if (!(theFrame instanceof MemoryImage)) {
            throw new IllegalArgumentException(
                    "the plant-frame cache holds MemoryImages, got " + theFrame.getClass().getName());
        }
        if (!ModImages.hasVisiblePixels((MemoryImage) theFrame)) {
            return;
        }
        if (aApp.mReanimatorCache.mPlantImages[theSeedType] == theFrame) {
            return;
        }

        aApp.mReanimatorCache.mPlantImages[theSeedType] = (MemoryImage) theFrame;
    }
}
