package com.alienguns;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

public final class ModItems {
    private ModItems() {}

    public static final List<Item> ALL = new ArrayList<>();

    public static final Item BLACK_HOLE_CANNON = gun("black_hole_cannon", 60, Guns::blackHoleCannon);
    public static final Item PLASMA_RIFLE      = gun("plasma_rifle",      12, Guns::plasmaRifle);
    public static final Item GRAVITY_PULSER    = gun("gravity_pulser",    80, Guns::gravityPulser);
    public static final Item RIFT_BLASTER      = gun("rift_blaster",      40, Guns::riftBlaster);
    public static final Item NOVA_EMITTER      = gun("nova_emitter",     100, Guns::novaEmitter);
    public static final Item ARC_RIFLE         = gun("arc_rifle",         20, Guns::arcRifle);
    public static final Item VOID_LANCE        = gun("void_lance",        50, Guns::voidLance);
    public static final Item STASIS_RAY        = gun("stasis_ray",        90, Guns::stasisRay);

    private static Item gun(String name, int cooldown, GunItem.Shot shot) {
        Identifier id = Identifier.fromNamespaceAndPath(AlienGuns.MOD_ID, name);
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
        Item item = new GunItem(new Item.Properties().setId(key).stacksTo(1), cooldown, shot);
        Registry.register(BuiltInRegistries.ITEM, key, item);
        ALL.add(item);
        return item;
    }

    public static void init() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> {
            for (Item item : ALL) {
                output.accept(item);
            }
        });
    }
}
