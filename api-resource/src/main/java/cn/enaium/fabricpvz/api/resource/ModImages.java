package cn.enaium.fabricpvz.api.resource;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Consumer;

import javax.imageio.ImageIO;

import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.sexy.graphics.MemoryImage;

/**
 * Image helpers: load one from a mod's jar, make a blank or painted canvas, and re-map hues.
 *
 * <p>These are plain functions over pixels - none of them binds anything into the game - so a mod can
 * build its art in whatever order fits it and only then hand the result to a {@link ModImageSlot} or
 * {@link GameArt}. The images are {@link MemoryImage}s created with the running application (the
 * game's own image type, so it can draw and release them like any other cel).
 */
public final class ModImages {

    private ModImages() {
    }

    /**
     * Loads a PNG from the classpath of {@code theOwner}.
     *
     * @param theOwner the class whose class path (jar, mods directory or test output) holds the image
     * @param thePath the resource path, absolute within the class path, for example
     *     {@code "art/red_peashooter.png"}
     * @return the decoded image, with its alpha as stored
     * @throws IllegalArgumentException if the resource does not exist or is not a decodable image
     */
    public static MemoryImage load(Class<?> theOwner, String thePath) {
        String aResource = thePath.startsWith("/") ? thePath : "/" + thePath;
        try (InputStream anIn = theOwner.getResourceAsStream(aResource)) {
            if (anIn == null) {
                throw new IllegalArgumentException(
                        "no image resource '" + thePath + "' on the class path of " + theOwner.getName());
            }

            BufferedImage anImage = ImageIO.read(anIn);
            if (anImage == null) {
                throw new IllegalArgumentException("'" + thePath + "' is not a decodable image");
            }

            int aWidth = anImage.getWidth();
            int aHeight = anImage.getHeight();
            int[] aBits = anImage.getRGB(0, 0, aWidth, aHeight, null, 0, aWidth);
            return fromBits(aBits, aWidth, aHeight);
        } catch (IOException e) {
            throw new IllegalArgumentException("cannot read image resource '" + thePath + "'", e);
        }
    }

    /**
     * A fully transparent canvas of the given size.
     *
     * @param theWidth the width in pixels, positive
     * @param theHeight the height in pixels, positive
     * @return the blank image
     * @throws IllegalArgumentException if either dimension is not positive
     */
    public static MemoryImage blank(int theWidth, int theHeight) {
        if (theWidth <= 0 || theHeight <= 0) {
            throw new IllegalArgumentException("a canvas needs a positive size, got " + theWidth + "x" + theHeight);
        }

        return fromBits(new int[theWidth * theHeight], theWidth, theHeight);
    }

    /**
     * A blank canvas with a painter run over it: the shortcut for "the art this mod draws", for example
     * {@code ModImages.painted(50, 70, g -> MyArt.drawIcon(g))}.
     *
     * @param theWidth the width in pixels, positive
     * @param theHeight the height in pixels, positive
     * @param thePainter the drawing, given the canvas' graphics
     * @return the painted image
     * @throws IllegalArgumentException if either dimension is not positive
     */
    public static MemoryImage painted(int theWidth, int theHeight, Consumer<Graphics> thePainter) {
        MemoryImage anImage = blank(theWidth, theHeight);
        Graphics aGraphics = new Graphics(anImage);
        aGraphics.setLinearBlend(true);
        thePainter.accept(aGraphics);
        return anImage;
    }

    /**
     * Re-maps every pixel's hue, keeping its saturation, brightness and alpha.
     *
     * <p>A pixel at hue {@code h} moves to {@code ((h - pivot) * compression) mod 360}: {@code pivot}
     * is the source hue that ends up red (0 degrees), and {@code compression} is how much of each
     * pixel's distance from it is kept - {@code 1} is a rotation that puts the pivot hue on red,
     * {@code 0} paints every pixel red, and a small value is the usual "turn this band of hues into
     * red" mapping that keeps the hue differences, and therefore the shading, of the source. Fully
     * transparent pixels are copied untouched, so the shape of the art never changes.
     *
     * @param theSource the image to recolour
     * @param thePivotDegrees the hue in degrees that maps onto red, {@code 0..360}
     * @param theCompression how much of each pixel's distance from the pivot is kept
     * @return a new image, or {@code null} when the source carries no pixel data
     */
    public static MemoryImage remapHue(MemoryImage theSource, float thePivotDegrees, float theCompression) {
        int aWidth = theSource.mWidth;
        int aHeight = theSource.mHeight;
        int[] aSourceBits = theSource.getBits();
        if (aSourceBits == null || aWidth <= 0 || aHeight <= 0) {
            return null;
        }

        int[] aMappedBits = new int[aWidth * aHeight];
        float[] anHsb = new float[3];
        for (int i = 0; i < aMappedBits.length; i++) {
            int aPixel = aSourceBits[i];
            int anAlpha = aPixel >>> 24;
            if (anAlpha == 0) {
                aMappedBits[i] = aPixel;  // keep fully transparent pixels untouched
                continue;
            }

            Color.RGBtoHSB((aPixel >> 16) & 0xFF, (aPixel >> 8) & 0xFF, aPixel & 0xFF, anHsb);
            float aHue = ((anHsb[0] * 360.0f - thePivotDegrees) * theCompression) % 360.0f;
            if (aHue < 0.0f) {
                aHue += 360.0f;
            }

            int aMapped = Color.HSBtoRGB(aHue / 360.0f, anHsb[1], anHsb[2]);
            aMappedBits[i] = (anAlpha << 24) | (aMapped & 0x00FFFFFF);
        }

        return fromBits(aMappedBits, aWidth, aHeight);
    }

    /**
     * @param theImage an image
     * @return whether it paints at least one non-transparent pixel; a game frame that is still blank
     *     means the art it is made of has not been loaded yet
     */
    static boolean hasVisiblePixels(MemoryImage theImage) {
        if (theImage == null) {
            return false;
        }

        int[] aBits = theImage.getBits();
        if (aBits == null) {
            return false;
        }

        // Only the pixel words, not the canary word the port keeps after them (MemoryImage.MEMORYCHECK_ID).
        for (int i = 0; i < theImage.mWidth * theImage.mHeight; i++) {
            if ((aBits[i] >>> 24) != 0) {
                return true;
            }
        }
        return false;
    }

    /** @return a {@link MemoryImage} over {@code theBits}, with transparency and alpha enabled */
    private static MemoryImage fromBits(int[] theBits, int theWidth, int theHeight) {
        MemoryImage anImage = new MemoryImage();
        anImage.setBits(theBits, theWidth, theHeight, false);
        anImage.setImageMode(true, true);
        return anImage;
    }
}
