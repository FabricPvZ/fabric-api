package cn.enaium.fabricpvz.api.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.Color;

import org.junit.jupiter.api.Test;

import cn.enaium.fabricpvz.portable.sexy.graphics.MemoryImage;

/**
 * Tests the pure image helpers: class-path loading, canvas painting and the hue mapping's pixel
 * semantics. The mapping is asserted on synthetic pixels, so the test needs neither a game nor a
 * running application.
 */
class ModImagesTest {

    @Test
    void loadsAPngFromTheClassPath() {
        MemoryImage anImage = ModImages.load(ModImagesTest.class, "art/sample.png");

        assertEquals(2, anImage.mWidth);
        assertEquals(2, anImage.mHeight);
        int[] aBits = anImage.getBits();
        assertEquals(0xFFFF0000, aBits[0], "the opaque red pixel must survive the round trip");
        assertEquals(0x8000FF00, aBits[1], "the half-transparent green pixel keeps its alpha");
        assertEquals(0x000000FF, aBits[2], "the transparent blue pixel stays transparent");
    }

    @Test
    void rejectsAMissingResource() {
        assertThrows(IllegalArgumentException.class,
                () -> ModImages.load(ModImagesTest.class, "art/does-not-exist.png"));
    }

    @Test
    void rejectsAResourceThatIsNotAnImage() {
        // Any non-image resource on the same class path stands in for a file of the wrong kind.
        assertThrows(IllegalArgumentException.class,
                () -> ModImages.load(ModImagesTest.class, "fabric.mod.json"));
    }

    @Test
    void blankIsFullyTransparent() {
        MemoryImage anImage = ModImages.blank(3, 2);

        assertEquals(3, anImage.mWidth);
        assertEquals(2, anImage.mHeight);
        for (int i = 0; i < 3 * 2; i++) {
            assertEquals(0, anImage.getBits()[i]);
        }
    }

    @Test
    void blankRejectsADegenerateSize() {
        assertThrows(IllegalArgumentException.class, () -> ModImages.blank(0, 4));
        assertThrows(IllegalArgumentException.class, () -> ModImages.blank(4, -1));
    }

    @Test
    void paintedRunsThePainterOverABlankCanvas() {
        MemoryImage anImage = ModImages.painted(4, 4, g -> {
            g.setColor(new cn.enaium.fabricpvz.portable.sexy.Color(255, 0, 0, 255));
            g.fillRect(0, 0, 4, 4);
        });

        assertEquals(4, anImage.mWidth);
        assertEquals(0xFFFF0000, anImage.getBits()[0], "the painter must have drawn");
    }

    @Test
    void remapHueWithRotationPutsThePivotHueOnRed() {
        // The source hue is 120 degrees (green); pivoting on 120 with no compression maps it to 0.
        MemoryImage aSource = pixels(0xFF00FF00);

        MemoryImage aMapped = ModImages.remapHue(aSource, 120.0f, 1.0f);

        assertNotNull(aMapped);
        assertEquals(0.0f, hueOf(aMapped.getBits()[0]), 0.5f);
        assertEquals(1.0f, saturationOf(aMapped.getBits()[0]), 0.01f, "saturation is kept");
        assertEquals(1.0f, brightnessOf(aMapped.getBits()[0]), 0.01f, "brightness is kept");
    }

    @Test
    void remapHueCompressesTheHuesTowardsRed() {
        // Green (120) and a blue-green (180): around a pivot of 90 at 0.25 they become 7.5 and 22.5
        // degrees, i.e. the red band, while their order and their distance apart is kept.
        MemoryImage aSource = pixels(0xFF00FF00, 0xFF00FFFF);

        MemoryImage aMapped = ModImages.remapHue(aSource, 90.0f, 0.25f);

        assertNotNull(aMapped);
        int[] aBits = aMapped.getBits();
        assertEquals(7.5f, hueOf(aBits[0]), 0.5f);
        assertEquals(22.5f, hueOf(aBits[1]), 0.5f);
    }

    @Test
    void remapHueCollapsesEveryHueOntoRedAtCompressionZero() {
        MemoryImage aSource = pixels(0xFF00FF00, 0xFF00FFFF);

        MemoryImage aMapped = ModImages.remapHue(aSource, 90.0f, 0.0f);

        assertNotNull(aMapped);
        int[] aBits = aMapped.getBits();
        assertEquals(0.0f, hueOf(aBits[0]), 0.5f);
        assertEquals(0.0f, hueOf(aBits[1]), 0.5f);
    }

    @Test
    void remapHueKeepsAlphaAndLeavesTransparentPixelsAlone() {
        MemoryImage aSource = pixels(0x00010203, 0x4000FF00);

        MemoryImage aMapped = ModImages.remapHue(aSource, 90.0f, 0.25f);

        assertNotNull(aMapped);
        int[] aBits = aMapped.getBits();
        assertEquals(0x00010203, aBits[0], "a fully transparent pixel is copied untouched");
        assertEquals(0x40, aBits[1] >>> 24, "the alpha of a visible pixel is kept");
        assertEquals(1.0f, saturationOf(aBits[1]), 0.01f, "saturation is kept");
        assertEquals(1.0f, brightnessOf(aBits[1]), 0.01f, "brightness is kept");
    }

    @Test
    void remapHueIsNullWithoutPixelData() {
        MemoryImage anEmpty = new MemoryImage();
        anEmpty.mWidth = 0;
        anEmpty.mHeight = 0;

        assertNull(ModImages.remapHue(anEmpty, 90.0f, 0.25f));
    }

    private static MemoryImage pixels(int... thePixels) {
        MemoryImage anImage = ModImages.blank(thePixels.length, 1);
        anImage.setBits(thePixels, thePixels.length, 1, false);
        return anImage;
    }

    private static float hueOf(int thePixel) {
        float[] anHsb = hsb(thePixel);
        return anHsb[0] * 360.0f;
    }

    private static float saturationOf(int thePixel) {
        return hsb(thePixel)[1];
    }

    private static float brightnessOf(int thePixel) {
        return hsb(thePixel)[2];
    }

    private static float[] hsb(int thePixel) {
        float[] anHsb = new float[3];
        Color.RGBtoHSB((thePixel >> 16) & 0xFF, (thePixel >> 8) & 0xFF, thePixel & 0xFF, anHsb);
        return anHsb;
    }
}
