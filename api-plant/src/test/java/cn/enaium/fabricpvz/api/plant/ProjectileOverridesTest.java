package cn.enaium.fabricpvz.api.plant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.lawn.Projectile;
import cn.enaium.fabricpvz.portable.lawn.ProjectileType;

/**
 * Tests {@link ProjectileOverrides}: the handle lookup, the damage landing in the row the game reads
 * at impact time, and what {@link ProjectileOverrides#reset()} puts back.
 */
class ProjectileOverridesTest {

	@AfterEach
	void clearOverrides() {
		ProjectileOverrides.reset();
	}

	@Test
	void handlesAreIdempotentAndOrdered() {
		ProjectileOverride star = ProjectileOverrides.of(ProjectileType.PROJECTILE_STAR);
		ProjectileOverride spike = ProjectileOverrides.of(ProjectileType.PROJECTILE_SPIKE);

		assertSame(star, ProjectileOverrides.of(ProjectileType.PROJECTILE_STAR));
		assertEquals(ProjectileType.PROJECTILE_STAR, star.projectileType());
		assertEquals(List.of(ProjectileType.PROJECTILE_STAR, ProjectileType.PROJECTILE_SPIKE),
				List.copyOf(ProjectileOverrides.registered().keySet()));
		assertNull(spike.damage());
	}

	@Test
	void onlyProjectileTypesAreAccepted() {
		assertThrows(IllegalArgumentException.class, () -> ProjectileOverrides.of(-1));
		assertThrows(IllegalArgumentException.class, () -> ProjectileOverrides.of(ProjectileType.NUM_PROJECTILES));
		assertThrows(IllegalArgumentException.class, () -> ProjectileOverrides.of(ProjectileType.PROJECTILE_SPIKE + 100));
	}

	@Test
	void theDamageLandsOnTheTableTheGameReads() {
		ProjectileOverrides.of(ProjectileType.PROJECTILE_STAR).damage(40);
		ProjectileOverrides.of(ProjectileType.PROJECTILE_SPIKE).damage(1);

		assertEquals(40, Projectile.gProjectileDefinition[ProjectileType.PROJECTILE_STAR].mDamage);
		assertEquals(1, Projectile.gProjectileDefinition[ProjectileType.PROJECTILE_SPIKE].mDamage);
		assertEquals(20, Projectile.gProjectileDefinition[ProjectileType.PROJECTILE_PEA].mDamage,
				"a type nobody overrode keeps the retail damage");
	}

	@Test
	void negativeDamageIsRefused() {
		ProjectileOverride override = ProjectileOverrides.of(ProjectileType.PROJECTILE_PEA);
		assertThrows(IllegalArgumentException.class, () -> override.damage(-1));
		assertNull(override.damage());
		assertEquals(20, Projectile.gProjectileDefinition[ProjectileType.PROJECTILE_PEA].mDamage);
	}

	@Test
	void resetPutsTheGameValueBack() {
		ProjectileOverrides.of(ProjectileType.PROJECTILE_STAR).damage(40);
		assertEquals(40, Projectile.gProjectileDefinition[ProjectileType.PROJECTILE_STAR].mDamage);

		ProjectileOverrides.reset();

		assertEquals(20, Projectile.gProjectileDefinition[ProjectileType.PROJECTILE_STAR].mDamage);
		assertTrue(ProjectileOverrides.registered().isEmpty());
	}

	@Test
	void theDefinitionIsReadThroughTheHandle() {
		ProjectileOverride override = ProjectileOverrides.of(ProjectileType.PROJECTILE_COBBIG);
		assertNull(override.damage());
		override.damage(300);
		assertEquals(300, override.damage());
		assertEquals("ProjectileOverride[projectileType=11, damage=300]", override.toString());
	}

	@Test
	void torchwoodConversionsDefaultToTheGamesOwn() {
		assertTrue(ProjectileOverrides.torchwoodConversions().isEmpty());
		assertEquals(ProjectileType.PROJECTILE_FIREBALL,
				ProjectileOverrides.torchwoodTarget(ProjectileType.PROJECTILE_PEA,
						ProjectileType.PROJECTILE_FIREBALL),
				"a type nobody registered keeps the game's own answer");
		assertEquals(ProjectileType.PROJECTILE_PEA,
				ProjectileOverrides.torchwoodTarget(ProjectileType.PROJECTILE_SNOWPEA,
						ProjectileType.PROJECTILE_PEA));
	}

	@Test
	void torchwoodConversionsTakeTheValuesTheyAreSetTo() {
		ProjectileOverrides.torchwoodConversion(ProjectileType.PROJECTILE_PEA, ProjectileType.PROJECTILE_SNOWPEA);
		ProjectileOverrides.torchwoodConversion(ProjectileType.PROJECTILE_SNOWPEA, ProjectileType.PROJECTILE_FIREBALL);

		assertEquals(ProjectileType.PROJECTILE_SNOWPEA,
				ProjectileOverrides.torchwoodTarget(ProjectileType.PROJECTILE_PEA,
						ProjectileType.PROJECTILE_FIREBALL));
		assertEquals(ProjectileType.PROJECTILE_FIREBALL,
				ProjectileOverrides.torchwoodTarget(ProjectileType.PROJECTILE_SNOWPEA,
						ProjectileType.PROJECTILE_PEA));
		assertEquals(ProjectileType.PROJECTILE_SPIKE,
				ProjectileOverrides.torchwoodTarget(ProjectileType.PROJECTILE_STAR,
						ProjectileType.PROJECTILE_SPIKE),
				"a type without a row keeps the game's own answer");
		assertEquals(Map.of(ProjectileType.PROJECTILE_PEA, ProjectileType.PROJECTILE_SNOWPEA,
				ProjectileType.PROJECTILE_SNOWPEA, ProjectileType.PROJECTILE_FIREBALL),
				ProjectileOverrides.torchwoodConversions());

		ProjectileOverrides.torchwoodConversion(ProjectileType.PROJECTILE_PEA, ProjectileType.PROJECTILE_PEA);

		assertEquals(ProjectileType.PROJECTILE_PEA,
				ProjectileOverrides.torchwoodTarget(ProjectileType.PROJECTILE_PEA,
						ProjectileType.PROJECTILE_FIREBALL),
				"the last row written for a type is the one asked");

		ProjectileOverrides.reset();

		assertTrue(ProjectileOverrides.torchwoodConversions().isEmpty());
		assertEquals(ProjectileType.PROJECTILE_FIREBALL,
				ProjectileOverrides.torchwoodTarget(ProjectileType.PROJECTILE_PEA,
						ProjectileType.PROJECTILE_FIREBALL));
	}

	@Test
	void onlyProjectileTypesAreAcceptedAsTorchwoodConversions() {
		assertThrows(IllegalArgumentException.class,
				() -> ProjectileOverrides.torchwoodConversion(ProjectileType.PROJECTILE_PEA, -1));
		assertThrows(IllegalArgumentException.class,
				() -> ProjectileOverrides.torchwoodConversion(-1, ProjectileType.PROJECTILE_SNOWPEA));
		assertThrows(IllegalArgumentException.class, () -> ProjectileOverrides
				.torchwoodConversion(ProjectileType.PROJECTILE_PEA, ProjectileType.NUM_PROJECTILES));
		assertTrue(ProjectileOverrides.torchwoodConversions().isEmpty(), "a refused row is not recorded");
	}
}
