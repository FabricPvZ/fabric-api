package cn.enaium.fabricpvz.api.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import cn.enaium.fabricpvz.portable.lawn.ImageID;
import cn.enaium.fabricpvz.portable.lawn.Resources;
import cn.enaium.fabricpvz.portable.sexy.graphics.MemoryImage;

/**
 * Tests the slot pool: allocation order, exhaustion, installation, and that the module restores a slot
 * the game clobbered - the property the whole module exists for.
 */
class ModImageSlotTest {

    @AfterEach
    void resetSlots() {
        ModImageSlot.reset();
    }

    @Test
    void claimsThePoolInOrderStartingWithTheFirstSlot() {
        ModImageSlot aFirst = ModImageSlot.claim("test-mod", "packet");
        ModImageSlot aSecond = ModImageSlot.claim("test-mod", "frame");

        assertEquals(ImageID.IMAGE_PEA_SPLATS, aFirst.id(), "the first claim gets the id the module used first");
        assertNotEquals(aFirst.id(), aSecond.id());
        assertEquals("test-mod:packet", aFirst.name());
    }

    @Test
    void handsOutDistinctIdsUntilThePoolIsEmpty() {
        Set<Integer> anIds = new HashSet<>();
        for (int i = 0; i < 8; i++) {
            anIds.add(ModImageSlot.claim("test-mod", "slot" + i).id());
        }

        assertEquals(8, anIds.size(), "every claim must get its own id");
        assertThrows(IllegalStateException.class, () -> ModImageSlot.claim("test-mod", "one-too-many"));
    }

    @Test
    void resetHandsThePoolBack() {
        ModImageSlot aFirst = ModImageSlot.claim("test-mod", "packet");
        ModImageSlot.reset();

        ModImageSlot aSecond = ModImageSlot.claim("other-mod", "packet");
        assertEquals(aFirst.id(), aSecond.id(), "the pool is refilled from the start");
    }

    @Test
    void setImageBindsTheImageIntoTheGameTable() {
        ModImageSlot aSlot = ModImageSlot.claim("test-mod", "packet");
        MemoryImage anImage = ModImages.blank(2, 2);

        aSlot.setImage(anImage);

        assertSame(anImage, aSlot.image());
        assertTrue(aSlot.bound());
        assertSame(anImage, Resources.getImageById(aSlot.id()));
    }

    @Test
    void aClobberedSlotIsRestoredByTheModule() {
        ModImageSlot aSlot = ModImageSlot.claim("test-mod", "packet");
        MemoryImage anImage = ModImages.blank(2, 2);
        aSlot.setImage(anImage);

        // Exactly what Resources::extractResourcesByName does to the ids of the group it loads.
        Resources.setImageById(aSlot.id(), null);
        assertFalse(aSlot.bound(), "the simulated group load must wipe the slot");

        ResourceInitializer.reinstall();

        assertTrue(aSlot.bound(), "the module must put the image back");
        assertSame(anImage, Resources.getImageById(aSlot.id()));
    }

    @Test
    void setImageRejectsNull() {
        ModImageSlot aSlot = ModImageSlot.claim("test-mod", "packet");

        assertThrows(NullPointerException.class, () -> aSlot.setImage(null));
    }
}
