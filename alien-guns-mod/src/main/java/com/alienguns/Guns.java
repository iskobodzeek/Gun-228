package com.alienguns;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Логика выстрела каждой пушки. */
public final class Guns {
    private Guns() {}

    private static Vec3 muzzle(ServerPlayer p) {
        return p.getEyePosition().add(p.getLookAngle().scale(1.0)).add(0, -0.2, 0);
    }

    /** 1. Сингулярная пушка: снаряд -> чёрная дыра на 7 секунд -> коллапс. */
    public static void blackHoleCannon(ServerLevel l, ServerPlayer p) {
        Vec3 o = muzzle(p), d = p.getLookAngle();
        Fx.sound(l, o, SoundEvents.WARDEN_SONIC_BOOM, 0.8f, 0.7f);
        Fx.burst(l, ParticleTypes.SQUID_INK, o, 10, 0.15, 0.05);
        Fx.add(new Effects.Bolt(l, p, o, d, 1.3, 48, ParticleTypes.SQUID_INK, ParticleTypes.PORTAL, 0.4,
                hit -> Fx.add(new Effects.BlackHole(l, p, hit))));
    }

    /** 2. Плазменный дезинтегратор: быстрый пробивной луч, поджигает. */
    public static void plasmaRifle(ServerLevel l, ServerPlayer p) {
        Vec3 o = muzzle(p), d = p.getLookAngle();
        Vec3 end = Fx.rayEnd(l, o, d, 40, p, false);
        Fx.sound(l, o, SoundEvents.BLAZE_SHOOT, 1.0f, 1.6f);
        Fx.add(new Effects.BeamAnim(l, p, o, end, ParticleTypes.SOUL_FIRE_FLAME, ParticleTypes.ELECTRIC_SPARK, 9f, true));
    }

    /** 3. Гравитационный импульсник: волна затягивает внутрь, потом выбрасывает наружу. */
    public static void gravityPulser(ServerLevel l, ServerPlayer p) {
        Vec3 c = p.position().add(0, 0.9, 0);
        Fx.sound(l, c, SoundEvents.BEACON_ACTIVATE, 1.0f, 0.6f);
        Fx.add(new Effects.Shockwave(l, p, c, 10, 12, 1.0, 0f, ParticleTypes.PORTAL, true, false, () -> {
            Fx.sound(l, c, SoundEvents.WARDEN_SONIC_BOOM, 0.8f, 1.2f);
            Fx.burst(l, ParticleTypes.SONIC_BOOM, c, 1, 0, 0);
            Fx.add(new Effects.Shockwave(l, p, c, 15, 12, 2.0, 6f, ParticleTypes.END_ROD, false, false, null));
        }));
    }

    /** 4. Бластер разлома: открывает два портала и телепортирует тебя в точку попадания. */
    public static void riftBlaster(ServerLevel l, ServerPlayer p) {
        Vec3 o = muzzle(p), d = p.getLookAngle();
        Vec3 end = Fx.rayEnd(l, o, d, 36, p, false);
        Vec3 dest = end.subtract(d.scale(0.4));
        Fx.add(new Effects.RiftTravel(l, p, dest));
    }

    /** 5. Излучатель сверхновой: медленный шар, на попадании звёздный коллапс и взрыв. */
    public static void novaEmitter(ServerLevel l, ServerPlayer p) {
        Vec3 o = muzzle(p), d = p.getLookAngle();
        Fx.sound(l, o, SoundEvents.FIRECHARGE_USE, 1.0f, 0.5f);
        Fx.add(new Effects.Bolt(l, p, o, d, 1.0, 45, ParticleTypes.FIREWORK, ParticleTypes.END_ROD, 0.5,
                hit -> Fx.add(new Effects.Supernova(l, p, hit))));
    }

    /** 6. Дуговая винтовка: цепная молния до 6 целей. */
    public static void arcRifle(ServerLevel l, ServerPlayer p) {
        Vec3 o = muzzle(p), d = p.getLookAngle();
        Fx.sound(l, o, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.6f, 1.8f);
        List<Vec3> pts = new ArrayList<>();
        List<LivingEntity> targets = new ArrayList<>();
        pts.add(o);
        LivingEntity first = Fx.firstLiving(l, o, d, 32, p);
        if (first == null) {
            pts.add(Fx.rayEnd(l, o, d, 32, p, false));
            targets.add(null);
        } else {
            Set<Integer> seen = new HashSet<>();
            LivingEntity cur = first;
            for (int i = 0; i < 6 && cur != null; i++) {
                seen.add(cur.getId());
                pts.add(Fx.center(cur));
                targets.add(cur);
                LivingEntity next = null;
                double best = 8.0;
                for (Entity e : Fx.near(l, Fx.center(cur), 8, p)) {
                    if (e instanceof LivingEntity le && !seen.contains(le.getId())) {
                        double dd = Fx.center(le).distanceTo(Fx.center(cur));
                        if (dd < best) { best = dd; next = le; }
                    }
                }
                cur = next;
            }
        }
        Fx.add(new Effects.ArcAnim(l, p, pts, targets, 7f));
    }

    /** 7. Копьё пустоты: луч оставляет «разрыв», который 4 секунды тянет и калечит. */
    public static void voidLance(ServerLevel l, ServerPlayer p) {
        Vec3 o = muzzle(p), d = p.getLookAngle();
        Vec3 end = Fx.rayEnd(l, o, d, 36, p, false);
        Fx.sound(l, o, SoundEvents.END_PORTAL_SPAWN, 0.8f, 1.8f);
        Fx.add(new Effects.BeamAnim(l, p, o, end, ParticleTypes.SQUID_INK, ParticleTypes.PORTAL, 6f, false));
        Fx.add(new Effects.VoidTear(l, p, o.add(d.scale(1.5)), end));
    }

    /** 8. Луч стазиса: ледяная сфера замораживает всех на 5 секунд, потом разбивается. */
    public static void stasisRay(ServerLevel l, ServerPlayer p) {
        Vec3 o = muzzle(p), d = p.getLookAngle();
        Fx.sound(l, o, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 0.6f);
        Fx.add(new Effects.Bolt(l, p, o, d, 1.6, 40, ParticleTypes.END_ROD, ParticleTypes.SNOWFLAKE, 0.3,
                hit -> Fx.add(new Effects.StasisField(l, p, hit))));
    }
}
