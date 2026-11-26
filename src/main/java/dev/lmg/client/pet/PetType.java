package dev.lmg.client.pet;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Every kind of pet you can have. MINI_PLAYER is a little copy of a player's avatar. */
public enum PetType {
	CAT("Cat", EntityTypes.CAT, Items.CAT_SPAWN_EGG, false),
	WOLF("Dog", EntityTypes.WOLF, Items.WOLF_SPAWN_EGG, false),
	FOX("Fox", EntityTypes.FOX, Items.FOX_SPAWN_EGG, false),
	PARROT("Parrot", EntityTypes.PARROT, Items.PARROT_SPAWN_EGG, true),
	AXOLOTL("Axolotl", EntityTypes.AXOLOTL, Items.AXOLOTL_SPAWN_EGG, false),
	BEE("Bee", EntityTypes.BEE, Items.BEE_SPAWN_EGG, true),
	FROG("Frog", EntityTypes.FROG, Items.FROG_SPAWN_EGG, false),
	RABBIT("Bunny", EntityTypes.RABBIT, Items.RABBIT_SPAWN_EGG, false),
	CHICKEN("Chicken", EntityTypes.CHICKEN, Items.CHICKEN_SPAWN_EGG, false),
	PIG("Pig", EntityTypes.PIG, Items.PIG_SPAWN_EGG, false),
	SHEEP("Sheep", EntityTypes.SHEEP, Items.SHEEP_SPAWN_EGG, false),
	COW("Cow", EntityTypes.COW, Items.COW_SPAWN_EGG, false),
	MOOSHROOM("Mooshroom", EntityTypes.MOOSHROOM, Items.MOOSHROOM_SPAWN_EGG, false),
	PANDA("Panda", EntityTypes.PANDA, Items.PANDA_SPAWN_EGG, false),
	POLAR_BEAR("Polar bear", EntityTypes.POLAR_BEAR, Items.POLAR_BEAR_SPAWN_EGG, false),
	OCELOT("Ocelot", EntityTypes.OCELOT, Items.OCELOT_SPAWN_EGG, false),
	GOAT("Goat", EntityTypes.GOAT, Items.GOAT_SPAWN_EGG, false),
	LLAMA("Llama", EntityTypes.LLAMA, Items.LLAMA_SPAWN_EGG, false),
	TURTLE("Turtle", EntityTypes.TURTLE, Items.TURTLE_SPAWN_EGG, false),
	ARMADILLO("Armadillo", EntityTypes.ARMADILLO, Items.ARMADILLO_SPAWN_EGG, false),
	SNIFFER("Sniffer", EntityTypes.SNIFFER, Items.SNIFFER_SPAWN_EGG, false),
	ALLAY("Allay", EntityTypes.ALLAY, Items.ALLAY_SPAWN_EGG, true),
	BAT("Bat", EntityTypes.BAT, Items.BAT_SPAWN_EGG, true),
	COPPER_GOLEM("Copper golem", EntityTypes.COPPER_GOLEM, Items.COPPER_GOLEM_SPAWN_EGG, false),
	HAPPY_GHAST("Happy ghast", EntityTypes.HAPPY_GHAST, Items.HAPPY_GHAST_SPAWN_EGG, true),
	MINI_PLAYER("Mini player", null, Items.PLAYER_HEAD, false);

	public final String label;
	public final EntityType<?> entity;
	public final Item icon;
	public final boolean flying;

	PetType(String label, EntityType<?> entity, Item icon, boolean flying) {
		this.label = label;
		this.entity = entity;
		this.icon = icon;
		this.flying = flying;
	}

	public static PetType of(String name) {
		try {
			return valueOf(name);
		} catch (Exception e) {
			return CAT;
		}
	}
}
