package cn.enaium.fabricpvz.api.plant;

import cn.enaium.fabricpvz.portable.lawn.Plant;
import cn.enaium.fabricpvz.portable.sexy.Rect;

/**
 * Behaviour callbacks of a mod-registered plant, called by {@link PlantHooks} from the Mixins that
 * replace the game's own custom-content branches.
 *
 * <p>This is the game-facing half of the plant content extension point, next to
 * {@link cn.enaium.fabricpvz.portable.lawn.hooks.LawnHooks} for gameplay events: a mod implements
 * {@link PlantType.Builder}'s callbacks, the builder adapts them into this interface, and
 * {@link PlantHooks} — not the game — decides when they run.
 *
 * <p>Every method has a default that preserves the upstream behaviour of an unmodified plant, so an
 * implementation only overrides what it actually needs. All callbacks run on the game thread, inside
 * the plant's own update pass.
 */
public interface PlantBehaviour {

    /**
     * Called once per plant tick while the plant is being updated, mirroring {@code Plant::Update}
     * (Plant.cpp:2856).
     *
     * <p>{@code plant.mBoard} and {@code plant.mApp} are guaranteed to be set; the plant may be on
     * the board or in a conveyor/packet preview.
     *
     * @param plant the plant being updated
     */
    default void onUpdate(Plant plant) {
    }

    /**
     * Called right after the plant finished initialising on the board, mirroring
     * {@code Plant::PlantInitialize} (Plant.cpp:111) through {@code Board::AddPlant} (Board.cpp:2071).
     *
     * <p>This is the point where a behaviour may adjust state that depends on the board (for example
     * pick a target or a facing direction). The plant is already placed in the grid.
     *
     * @param plant the newly placed plant
     */
    default void onPlanted(Plant plant) {
    }

    /**
     * Collision rectangle of the plant, replacing {@code Plant::GetPlantRect} (Plant.cpp:5136).
     *
     * @param plant the plant being tested
     * @return the rectangle to use, or {@code null} to keep the upstream rectangle
     */
    default Rect plantRect(Plant plant) {
        return null;
    }

    /**
     * Attack rectangle of the plant, replacing {@code Plant::GetPlantAttackRect} (Plant.cpp:5160).
     *
     * @param plant the plant being tested
     * @param thePlantWeapon {@code PlantWeapon} of the weapon being used
     * @return the rectangle to use, or {@code null} to keep the upstream rectangle
     */
    default Rect plantAttackRect(Plant plant, int thePlantWeapon) {
        return null;
    }
}
