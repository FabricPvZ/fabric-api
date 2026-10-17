package cn.enaium.fabricpvz.api.resource;

import java.util.function.Function;

import cn.enaium.fabricpvz.portable.lawn.LawnApp;
import cn.enaium.fabricpvz.portable.lawn.Plant;
import cn.enaium.fabricpvz.portable.lawn.ReanimationID;
import cn.enaium.fabricpvz.portable.lawn.ReanimLoopType;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimation;
import cn.enaium.fabricpvz.portable.pvzp.reanim.ReanimatorTrack;
import cn.enaium.fabricpvz.portable.pvzp.reanim.ReanimatorTransform;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;

/**
 * Changes the look of a plant that uses a retail reanimation, without re-authoring the animation.
 *
 * <p>Hand-authoring a {@code .reanim} is not something a mod can do, so a mod that wants a different
 * plant of an existing family reuses the retail skeleton and replaces the images it draws. This class
 * does the two halves of that: {@link #overrideTrackArt} swaps a track's image for a mapped copy, and
 * {@link #giveSeparateHeadLayer} creates the extra head reanimation the game builds only for its own
 * peashooter seed types.
 */
public final class ModReanims {

    /** Render order offset of the head reanimation, the game's own for a plant of this family. */
    private static final int HEAD_RENDER_ORDER_OFFSET = 2;

    private ModReanims() {
    }

    /**
     * Replaces the art of the named tracks with a mapped copy of the image each track draws.
     *
     * <p>The image is read from the track's own frames (the definition is the table the game loads
     * from its {@code .reanim}) rather than from {@code Reanimation#getCurrentTrackImage}: the latter
     * answers {@code null} for frames the game packed into the reanimation's atlas - which it does
     * for the peashooter family ({@code Reanimator::ReanimationCreateAtlas}, no {@code REANIM_NO_ATLAS}
     * flag) - and, being the frame the plant happens to be on, it also misses tracks that are blank in
     * this pose (for example the blink the plant draws later).
     *
     * <p>A track that does not exist, one with no image of its own, or a mapper that returns
     * {@code null} for an image, leaves that track as it is; a track whose override is already the
     * mapped image is skipped. Both make the call idempotent, which is what a per-tick or per-blink
     * re-application needs.
     *
     * @param theReanim the reanimation to change, typically a plant's body, head or blink reanimation
     * @param theTrackNames the tracks to look at, by name
     * @param theMapper maps a track's current image to the image it should draw instead; may return
     *     {@code null} to keep the original
     * @return how many tracks were (re-)coloured
     */
    public static int overrideTrackArt(Reanimation theReanim, String[] theTrackNames,
            Function<Image, Image> theMapper) {
        if (theReanim == null || theTrackNames == null || theMapper == null) {
            return 0;
        }

        int aChanged = 0;
        for (String aTrackName : theTrackNames) {
            Image anImage = trackImage(theReanim, aTrackName);
            Image aMapped = anImage == null ? null : theMapper.apply(anImage);
            if (aMapped == null || theReanim.getImageOverride(aTrackName) == aMapped) {
                continue;  // no such track, no art in it, or already the mapped image
            }

            theReanim.setImageOverride(aTrackName, aMapped);
            aChanged++;
        }
        return aChanged;
    }

    /**
     * Replaces the art of <em>every</em> track of a reanimation that carries an image with a mapped
     * copy, so a mod that recolours a whole plant does not have to keep a list of track names.
     *
     * <p>The set of tracks is read from the reanimation's own definition (the {@code .reanim} the game
     * loaded) and filtered by art: a motion track ({@code anim_idle}, {@code anim_stem}, ...) has no
     * image of its own and is left alone. Like the named variant this is idempotent - a track already
     * holding the mapped image is skipped, and a mapper returning {@code null} leaves the track as it
     * is - which a per-tick or per-blink re-application needs.
     *
     * @param theReanim the reanimation to change, typically a plant's body, head or blink reanimation
     * @param theMapper maps a track's current image to the image it should draw instead; may return
     *     {@code null} to keep the original
     * @return how many tracks were (re-)coloured
     */
    public static int overrideTrackArt(Reanimation theReanim, Function<Image, Image> theMapper) {
        if (theReanim == null || theReanim.mDefinition == null || theReanim.mDefinition.mTracks == null
                || theMapper == null) {
            return 0;
        }

        int aChanged = 0;
        for (Object aTrackObject : theReanim.mDefinition.mTracks.mArrayData) {
            ReanimatorTrack aTrack = (ReanimatorTrack) aTrackObject;
            Image anImage = firstImage(aTrack);
            Image aMapped = anImage == null ? null : theMapper.apply(anImage);
            if (aMapped == null || theReanim.getImageOverride(aTrack.mName) == aMapped) {
                continue;  // no art in the track, or already the mapped image
            }

            theReanim.setImageOverride(aTrack.mName, aMapped);
            aChanged++;
        }
        return aChanged;
    }

    /**
     * Gives a plant the head reanimation of its family, the way {@code Plant::PlantInitialize} does it
     * for its own peashooter seed types: a second reanimation of the plant's own type, playing
     * {@code anim_head_idle} and attached to the body's stem. Without it a mod plant of that family
     * would draw leaves and stalk and no head at all - the body reanimation's head tracks are blank.
     *
     * <p>Idempotent: a plant that already has a head reanimation is left alone. Does nothing while the
     * plant has no body reanimation (no retail {@code .reanim} data loaded) or that reanimation has no
     * head layer.
     *
     * @param thePlant the plant to give a head to
     */
    public static void giveSeparateHeadLayer(Plant thePlant) {
        if (thePlant == null || thePlant.mApp == null
                || thePlant.mHeadReanimID != ReanimationID.REANIMATIONID_NULL) {
            return;
        }

        LawnApp aApp = thePlant.mApp;
        Reanimation aBody = aApp.reanimationTryToGet(thePlant.mBodyReanimID);
        if (aBody == null || aBody.mTrackInstances == null || !aBody.trackExists("anim_head_idle")) {
            return;
        }

        Reanimation aHead = aApp.addReanimation(0.0f, 0.0f, thePlant.mRenderOrder + HEAD_RENDER_ORDER_OFFSET,
                aBody.mReanimationType);
        aHead.mLoopType = ReanimLoopType.REANIM_LOOP;
        aHead.mAnimRate = aBody.mAnimRate;
        aHead.setFramesForLayer("anim_head_idle");
        thePlant.mHeadReanimID = aApp.reanimationGetID(aHead);

        if (aBody.trackExists("anim_stem")) {
            aHead.attachToAnotherReanimation(aBody, "anim_stem");
        } else if (aBody.trackExists("anim_idle")) {
            aHead.attachToAnotherReanimation(aBody, "anim_idle");
        }
    }

    /**
     * @param theReanim a reanimation
     * @param theTrackName the track to look for
     * @return the image the track draws, or {@code null} when it has no such track or no art
     */
    private static Image trackImage(Reanimation theReanim, String theTrackName) {
        if (theReanim.mDefinition == null) {
            return null;
        }

        for (Object aTrackObject : theReanim.mDefinition.mTracks.mArrayData) {
            ReanimatorTrack aTrack = (ReanimatorTrack) aTrackObject;
            if (!theTrackName.equalsIgnoreCase(aTrack.mName)) {
                continue;
            }

            return firstImage(aTrack);
        }
        return null;
    }

    /**
     * @param theTrack a track
     * @return the image the track draws, or {@code null} when it is a motion track with no art
     */
    private static Image firstImage(ReanimatorTrack theTrack) {
        // A track draws its frames' images in order; the blink tracks alternate between two, and one
        // override serves the whole track, so the first one is the one every frame shows.
        for (Object aTransformObject : theTrack.mTransforms.mArrayData) {
            Image anImage = ((ReanimatorTransform) aTransformObject).mImage;
            if (anImage != null) {
                return anImage;
            }
        }
        return null;
    }
}
