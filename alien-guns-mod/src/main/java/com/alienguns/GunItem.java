package com.alienguns;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** Пушка: ПКМ -> выстрел (вся логика на сервере, клиентский код не нужен). */
public class GunItem extends Item {
    public interface Shot {
        void fire(ServerLevel level, ServerPlayer player);
    }

    private final Shot shot;
    private final int cooldownTicks;

    public GunItem(Properties props, int cooldownTicks, Shot shot) {
        super(props);
        this.cooldownTicks = cooldownTicks;
        this.shot = shot;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
            shot.fire(sl, sp);
            player.getCooldowns().addCooldown(player.getItemInHand(hand), cooldownTicks);
        }
        return InteractionResult.SUCCESS;
    }
}
