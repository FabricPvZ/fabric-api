package cn.enaium.fabricpvz.api.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import cn.enaium.fabricpvz.portable.pvzp.DefinitionArrayDef;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimation;
import cn.enaium.fabricpvz.portable.pvzp.reanim.ReanimatorDefinition;
import cn.enaium.fabricpvz.portable.pvzp.reanim.ReanimatorTrack;
import cn.enaium.fabricpvz.portable.pvzp.reanim.ReanimatorTrackInstance;
import cn.enaium.fabricpvz.portable.pvzp.reanim.ReanimatorTransform;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;
import cn.enaium.fabricpvz.portable.sexy.graphics.MemoryImage;

/**
 * Tests the per-track recolouring on a hand-built reanimation: which tracks are touched, what happens
 * to a track that is missing or has no art, and that a mapper returning {@code null} keeps the
 * original image. The real retail data is covered by the content mod's end-to-end run.
 */
class ModReanimsTest {

    private static final String[] TRACKS = {"anim_face", "anim_blink"};

    @Test
    void replacesTheArtOfEveryNamedTrackThatExists() {
        MemoryImage aSource = ModImages.blank(2, 2);
        Reanimation aReanim = reanimWith("anim_face", aSource);
        MemoryImage aMapped = ModImages.blank(2, 2);

        int aChanged = ModReanims.overrideTrackArt(aReanim, TRACKS, image -> aMapped);

        assertEquals(1, aChanged, "only the track that exists and carries art is changed");
        assertSame(aMapped, aReanim.getImageOverride("anim_face"));
    }

    @Test
    void isIdempotentWhenTheOverrideIsAlreadyTheMappedImage() {
        MemoryImage aMapped = ModImages.blank(2, 2);
        Reanimation aReanim = reanimWith("anim_face", ModImages.blank(2, 2));

        assertEquals(1, ModReanims.overrideTrackArt(aReanim, TRACKS, image -> aMapped));
        assertEquals(0, ModReanims.overrideTrackArt(aReanim, TRACKS, image -> aMapped),
                "a track already holding the mapped image is not counted again");
    }

    @Test
    void keepsTheOriginalWhenTheMapperReturnsNull() {
        Reanimation aReanim = reanimWith("anim_face", ModImages.blank(2, 2));

        int aChanged = ModReanims.overrideTrackArt(aReanim, TRACKS, image -> null);

        assertEquals(0, aChanged, "a null mapping leaves the track as it is");
        assertNull(aReanim.getImageOverride("anim_face"));
    }

    @Test
    void skipsMissingTracksAndTracksWithoutArt() {
        Reanimation aReanim = reanimWith("anim_face", null);

        int aChanged = ModReanims.overrideTrackArt(aReanim, TRACKS, image -> ModImages.blank(2, 2));

        assertEquals(0, aChanged, "no track here has art to map");
    }

    @Test
    void mapsEveryTrackOfTheDefinitionThatCarriesArt() {
        MemoryImage aFace = ModImages.blank(2, 2);
        Reanimation aReanim = reanimWith(2, track("anim_face", aFace), track("anim_idle", null));
        MemoryImage aMapped = ModImages.blank(2, 2);

        int aChanged = ModReanims.overrideTrackArt(aReanim, image -> aMapped);

        assertEquals(1, aChanged, "only the track that carries art is mapped");
        assertSame(aMapped, aReanim.getImageOverride("anim_face"));
        assertNull(aReanim.getImageOverride("anim_idle"), "a motion track with no art stays untouched");

        assertEquals(0, ModReanims.overrideTrackArt(aReanim, image -> aMapped),
                "a track already holding the mapped image is not counted again");
    }

    @Test
    void mapsNothingFromANullMappingOrAMissingDefinition() {
        Reanimation aReanim = reanimWith(1, track("anim_face", ModImages.blank(2, 2)));

        assertEquals(0, ModReanims.overrideTrackArt(aReanim, image -> null), "a null mapping keeps the track");
        assertNull(aReanim.getImageOverride("anim_face"));

        Reanimation aEmpty = new Reanimation();
        assertEquals(0, ModReanims.overrideTrackArt(aEmpty, image -> ModImages.blank(2, 2)));

        assertEquals(0, ModReanims.overrideTrackArt((Reanimation) null, image -> ModImages.blank(2, 2)));
    }

    /** @return a track with the given name whose first frame carries {@code theImage} (possibly none) */
    private static ReanimatorTrack track(String theName, Image theImage) {
        ReanimatorTransform aTransform = new ReanimatorTransform();
        aTransform.mImage = theImage;

        ReanimatorTrack aTrack = new ReanimatorTrack();
        aTrack.mName = theName;
        aTrack.mTransforms = new DefinitionArrayDef(new ReanimatorTransform[] {aTransform});
        return aTrack;
    }

    /** @return a reanimation over the given tracks, with one track instance each */
    private static Reanimation reanimWith(int theTrackCount, ReanimatorTrack... theTracks) {
        ReanimatorDefinition aDefinition = new ReanimatorDefinition();
        aDefinition.mTracks = new DefinitionArrayDef(theTracks);

        ReanimatorTrackInstance[] aInstances = new ReanimatorTrackInstance[theTrackCount];
        for (int i = 0; i < theTrackCount; i++) {
            aInstances[i] = new ReanimatorTrackInstance();
        }

        Reanimation aReanim = new Reanimation();
        aReanim.mDefinition = aDefinition;
        aReanim.mTrackInstances = aInstances;
        return aReanim;
    }

    /** @return a reanimation with the named track carrying {@code theImage} (possibly none) */
    private static Reanimation reanimWith(String theTrackName, Image theImage) {
        ReanimatorTransform aTransform = new ReanimatorTransform();
        aTransform.mImage = theImage;

        ReanimatorTrack aTrack = new ReanimatorTrack();
        aTrack.mName = theTrackName;
        aTrack.mTransforms = new DefinitionArrayDef(new ReanimatorTransform[] {aTransform});

        ReanimatorDefinition aDefinition = new ReanimatorDefinition();
        aDefinition.mTracks = new DefinitionArrayDef(new ReanimatorTrack[] {aTrack});

        Reanimation aReanim = new Reanimation();
        aReanim.mDefinition = aDefinition;
        aReanim.mTrackInstances = new ReanimatorTrackInstance[] {new ReanimatorTrackInstance()};
        return aReanim;
    }
}
