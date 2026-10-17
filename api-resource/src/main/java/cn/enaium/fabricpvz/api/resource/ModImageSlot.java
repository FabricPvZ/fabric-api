package cn.enaium.fabricpvz.api.resource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import cn.enaium.fabricpvz.portable.lawn.ImageID;
import cn.enaium.fabricpvz.portable.lawn.Resources;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;

/**
 * One image slot a mod can put its own art into: a retail {@link ImageID} the port never draws,
 * claimed from a fixed pool and kept bound to the mod's image across resource (re-)loads.
 *
 * <p>The published content API takes an {@code ImageID} for a seed packet's art
 * ({@code PlantType.Builder#packetArt(int, int)}), but the game reserves no image-id range for mods
 * and has no "resources are (re-)loaded" event. A mod that ships its own packet art therefore has to
 * put it into an id the game owns, and put it back whenever the game rebinds that id. This class is
 * that slot: {@link #claim(String, String)} hands out an id the game never reads, and
 * {@link #setImage(Image)} installs the mod's image into it and keeps it installed - the module's own
 * entry point ({@link ResourceInitializer}) re-installs it after every initialization, resource group
 * extraction and gameplay tick, so nothing of that bookkeeping is left to the mod.
 *
 * <h2>The pool</h2>
 *
 * <p>The ids come from {@code LoadingImages}, a group the game's loading thread binds after mods
 * initialize, so a mod cannot race the game for its slot: the id is in the resource table and the
 * module's reinstall puts the mod's image there after the group load clobbers it. Every id below was
 * checked against this port with
 * {@code grep -rn '\bIMAGE_<NAME>\b' --include=*.java} over {@code PvZ-Portable/src/main/java}
 * (matching the {@code ImageID} constant, the {@code Resources.<NAME>_ID} constant and any numeric
 * literal passed to {@code Resources.getImageById}); each has no reader in the port - the upstream
 * particle system that drew them was not ported - while still being a real resource entry, so the id
 * is in range. Ids are handed out in pool order (the first claim gets {@code IMAGE_PEA_SPLATS}, the
 * id this API's own first consumer used).
 *
 * <p>Instances are created only by {@link #claim(String, String)}. Claiming is process-wide and
 * never reuses an id, so a claimed id keeps meaning the same slot for the whole process.
 */
public final class ModImageSlot {

    /**
     * Image ids the port never reads, all from the {@code LoadingImages} resource group, in claim
     * order. See the class javadoc for how each was verified.
     */
    private static final int[] POOL = {
        ImageID.IMAGE_PEA_SPLATS,           // 218  particles/pea_splats.png
        ImageID.IMAGE_STAR_PARTICLES,       // 219  particles/star_particles.png
        ImageID.IMAGE_STAR_SPLATS,          // 220  particles/star_splats.png
        ImageID.IMAGE_PEA_PARTICLES,        // 221  particles/pea_particles.png
        ImageID.IMAGE_SNOWPEA_SPLATS,       // 222  particles/snowpea_splats.png
        ImageID.IMAGE_SNOWPEA_PARTICLES,    // 223  particles/snowpea_particles.png
        ImageID.IMAGE_SNOWPEA_PUFF,         // 224  particles/snowpea_puff.png
        ImageID.IMAGE_SNOWFLAKES,           // 225  particles/snowflakes.png
    };

    private static final Object LOCK = new Object();
    private static final Map<Integer, ModImageSlot> CLAIMED = new LinkedHashMap<>();
    private static int firstFree;

    private final int id;
    private final String modId;
    private final String name;
    private Image image;

    private ModImageSlot(int theId, String theModId, String theName) {
        this.id = theId;
        this.modId = theModId;
        this.name = theName;
    }

    /**
     * Claims one image slot for a mod.
     *
     * @param theModId the id of the claiming mod, for diagnostics and exception messages
     * @param theName a name the mod knows the slot by, for diagnostics and exception messages
     * @return the claimed slot
     * @throws IllegalStateException if the pool is exhausted
     * @throws NullPointerException if {@code theModId} or {@code theName} is {@code null}
     */
    public static ModImageSlot claim(String theModId, String theName) {
        if (theModId == null || theName == null) {
            throw new NullPointerException("a slot claim needs a mod id and a name");
        }

        synchronized (LOCK) {
            if (firstFree >= POOL.length) {
                throw new IllegalStateException("No image slots left: all " + POOL.length
                        + " retail ids this module reserves are claimed ('" + theModId + ":" + theName + "')");
            }

            ModImageSlot slot = new ModImageSlot(POOL[firstFree], theModId, theName);
            firstFree++;
            CLAIMED.put(slot.id, slot);
            return slot;
        }
    }

    /**
     * Puts an image into this slot and keeps it there. The image is bound immediately when the game's
     * resource table already exists, and re-bound by the module after every initialization, resource
     * group extraction and gameplay tick, so the slot cannot be taken over by the retail resource it
     * shadows.
     *
     * <p>Idempotent: re-installing the image the slot already holds is free.
     *
     * @param theImage the image to install
     * @throws NullPointerException if {@code theImage} is {@code null}
     */
    public void setImage(Image theImage) {
        if (theImage == null) {
            throw new NullPointerException("slot '" + name() + "' needs an image, not null");
        }

        synchronized (LOCK) {
            image = theImage;
        }
        bind();
        ResourceDiagnostics.noteInstall();
    }

    /** @return the id this slot claimed, for {@code PlantType.Builder#packetArt(int, int)} and friends */
    public int id() {
        return id;
    }

    /** @return the image currently installed in this slot, or {@code null} before the first install */
    public Image image() {
        synchronized (LOCK) {
            return image;
        }
    }

    /** @return whether the game's resource table currently holds this slot's image under {@link #id()} */
    public boolean bound() {
        Image aImage = image();
        return aImage != null && Resources.getImageById(id) == aImage;
    }

    /** @return the mod id and name this slot was claimed with, as a diagnostic label */
    public String name() {
        return modId + ":" + name;
    }

    /**
     * Re-installs this slot's image when a resource (re-)load took it over. Called by the module's own
     * hooks; nothing of it is the mod's business.
     */
    void reinstall() {
        bind();
    }

    private void bind() {
        Image aImage = image();
        if (aImage != null && Resources.getImageById(id) != aImage) {
            Resources.setImageById(id, aImage);
        }
    }

    /** @return every claimed slot, in claim order */
    static List<ModImageSlot> all() {
        synchronized (LOCK) {
            return new ArrayList<>(CLAIMED.values());
        }
    }

    /**
     * Clears every claim and hands the pool back; used by tests and by a loader that reloads mods
     * in-process. Like {@code PlantRegistry.reset()}, it is not something a running game calls.
     */
    public static void reset() {
        synchronized (LOCK) {
            CLAIMED.clear();
            firstFree = 0;
        }
    }
}
