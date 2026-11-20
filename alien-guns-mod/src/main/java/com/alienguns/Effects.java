package com.alienguns;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Все анимированные эффекты. Каждый тик Fx.tickAll() вызывает tick() у активных. */
public final class Effects {
    private Effects() {}

    private static final double TAU = Math.PI * 2;
    private static final Vec3 UP = new Vec3(0, 1, 0);

    // =====================================================================
    // Снаряд: летит, оставляет спиральный след, по попаданию вызывает onHit
    // =====================================================================
    public static final class Bolt implements Fx.Effect {
        private final ServerLevel l;
        private final ServerPlayer owner;
        private Vec3 pos;
        private final Vec3 dir;
        private final double speed, maxDist, helixR;
        private final ParticleOptions core, trail;
        private final Consumer<Vec3> onHit;
        private final Vec3[] bs;
        private double traveled;
        private int age;

        public Bolt(ServerLevel l, ServerPlayer owner, Vec3 start, Vec3 dir, double speed, double maxDist,
                    ParticleOptions core, ParticleOptions trail, double helixR, Consumer<Vec3> onHit) {
            this.l = l; this.owner = owner; this.pos = start; this.dir = dir.normalize();
            this.speed = speed; this.maxDist = maxDist; this.core = core; this.trail = trail;
            this.helixR = helixR; this.onHit = onHit; this.bs = Fx.basis(this.dir);
        }

        @Override
        public boolean tick() {
            age++;
            int steps = 3;
            double h = speed / steps;
            for (int i = 0; i < steps; i++) {
                Vec3 next = pos.add(dir.scale(h));
                if (Fx.solid(l, next) || Fx.livingAt(l, next, owner) != null) {
                    onHit.accept(pos);
                    return true;
                }
                pos = next;
                traveled += h;
                double a = age * 0.9 + i * 0.8;
                Fx.dot(l, core, pos);
                Fx.dot(l, trail, pos.add(bs[0].scale(Math.cos(a) * helixR)).add(bs[1].scale(Math.sin(a) * helixR)));
                Fx.dot(l, trail, pos.add(bs[0].scale(Math.cos(a + Math.PI) * helixR)).add(bs[1].scale(Math.sin(a + Math.PI) * helixR)));
                if (traveled >= maxDist) {
                    onHit.accept(pos);
                    return true;
                }
            }
            return false;
        }
    }

    // =====================================================================
    // ЧЁРНАЯ ДЫРА: горизонт событий, аккреционный диск, джеты, спиральная
    // материя, затягивает всё вокруг, в конце — коллапс и взрывная волна.
    // =====================================================================
    public static final class BlackHole implements Fx.Effect {
        private static final int LIFE = 140;
        private static final double R = 12;
        private static final int N = 20;

        private final ServerLevel l;
        private final ServerPlayer owner;
        private final Vec3 c;
        private final Vec3 axis = new Vec3(0.35, 1, 0.15).normalize();
        private final Vec3[] bs = Fx.basis(axis);
        private final double[] ang = new double[N], rad = new double[N], hgt = new double[N];
        private int age;

        public BlackHole(ServerLevel l, ServerPlayer owner, Vec3 c) {
            this.l = l; this.owner = owner; this.c = c;
            ThreadLocalRandom r = ThreadLocalRandom.current();
            for (int i = 0; i < N; i++) {
                ang[i] = r.nextDouble() * TAU;
                rad[i] = 1 + r.nextDouble() * (R - 1);
                hgt[i] = (r.nextDouble() - 0.5) * 2;
            }
            Fx.sound(l, c, SoundEvents.END_PORTAL_SPAWN, 1.0f, 0.5f);
        }

        @Override
        public boolean tick() {
            age++;
            double grow = Math.min(1.0, age / 12.0);
            double fade = age > LIFE - 20 ? Math.max(0, (LIFE - age) / 20.0) : 1.0;
            double s = grow * fade;

            if (s > 0.02) {
                // горизонт событий
                Fx.burst(l, ParticleTypes.SQUID_INK, c, 4, 0.35 * s, 0.0);
                // аккреционный диск (3 вращающихся кольца)
                double spin = age * 0.4;
                Fx.ring(l, ParticleTypes.END_ROD, c, 1.3 * s, 8, spin, axis);
                Fx.ring(l, ParticleTypes.FLAME, c, 2.1 * s, 10, -spin * 0.8, axis);
                Fx.ring(l, ParticleTypes.PORTAL, c, 3.0 * s, 12, spin * 0.6, axis);
                // релятивистские джеты
                if (age % 3 == 0) {
                    for (int k = 1; k <= 5; k++) {
                        Vec3 j = axis.scale(k * 0.9 * s);
                        Fx.dot(l, ParticleTypes.END_ROD, c.add(j));
                        Fx.dot(l, ParticleTypes.END_ROD, c.subtract(j));
                    }
                }
                // материя, по спирали падающая внутрь
                ThreadLocalRandom r = ThreadLocalRandom.current();
                for (int i = 0; i < N; i++) {
                    rad[i] -= 0.10 + 0.35 / (rad[i] + 0.6);
                    ang[i] += 0.35 + 1.2 / (rad[i] + 1.0);
                    if (rad[i] < 0.6) {
                        rad[i] = R * (0.6 + 0.4 * r.nextDouble());
                        ang[i] = r.nextDouble() * TAU;
                        hgt[i] = (r.nextDouble() - 0.5) * 2;
                    }
                    if (i >= N * s) continue;
                    Vec3 p = c.add(bs[0].scale(Math.cos(ang[i]) * rad[i]))
                              .add(bs[1].scale(Math.sin(ang[i]) * rad[i]))
                              .add(axis.scale(hgt[i] * rad[i] * 0.12));
                    Fx.dot(l, i % 2 == 0 ? ParticleTypes.PORTAL : ParticleTypes.SMOKE, p);
                }
            }

            // гравитация
            for (Entity e : Fx.near(l, c, R, owner)) {
                Vec3 to = c.subtract(Fx.center(e));
                double d = to.length();
                if (d < 0.05) continue;
                double str = (0.06 + 0.20 * (1.0 - d / R)) * s;
                Fx.impulse(e, to.scale(str / d));
                if (d < 3.0 && age % 10 == 0 && e instanceof LivingEntity le) {
                    Fx.hurt(l, le, owner, 5f);
                }
            }

            if (age >= LIFE) {
                collapse();
                return true;
            }
            return false;
        }

        private void collapse() {
            Fx.sound(l, c, SoundEvents.WARDEN_SONIC_BOOM, 1.0f, 0.6f);
            Fx.sound(l, c, SoundEvents.LIGHTNING_BOLT_THUNDER, 1.2f, 0.7f);
            Fx.burst(l, ParticleTypes.EXPLOSION_EMITTER, c, 1, 0, 0);
            Fx.burst(l, ParticleTypes.SQUID_INK, c, 60, 2.5, 0.3);
            Fx.add(new Shockwave(l, owner, c, 16, 14, 2.2, 10f, ParticleTypes.END_ROD, false, true, null));
        }
    }

    // =====================================================================
    // Ударная волна: расширяется (отбрасывает/бьёт) или схлопывается (затягивает)
    // =====================================================================
    public static final class Shockwave implements Fx.Effect {
        private final ServerLevel l;
        private final ServerPlayer owner;
        private final Vec3 c;
        private final double maxR, push;
        private final int life;
        private final float dmg;
        private final ParticleOptions particle;
        private final boolean contract, sphere;
        private final Runnable onEnd;
        private final Set<Integer> hit = new HashSet<>();
        private int age;

        public Shockwave(ServerLevel l, ServerPlayer owner, Vec3 c, double maxR, int life, double push, float dmg,
                         ParticleOptions particle, boolean contract, boolean sphere, Runnable onEnd) {
            this.l = l; this.owner = owner; this.c = c; this.maxR = maxR; this.life = life; this.push = push;
            this.dmg = dmg; this.particle = particle; this.contract = contract; this.sphere = sphere; this.onEnd = onEnd;
        }

        @Override
        public boolean tick() {
            age++;
            double t = Math.min(1.0, (double) age / life);
            double r = Math.max(0.4, contract ? maxR * (1 - t) : maxR * t);

            if (sphere) {
                Fx.sphere(l, particle, c, r, (int) (r * r * 2.5) + 12, age * 0.25);
            } else {
                Fx.ring(l, particle, c, r, (int) (r * 7) + 10, age * 0.3, UP);
                Fx.ring(l, ParticleTypes.CLOUD, c.add(0, 0.3, 0), r * 0.92, (int) (r * 4) + 6, -age * 0.2, UP);
            }

            for (Entity e : Fx.near(l, c, maxR + 1, owner)) {
                Vec3 off = Fx.center(e).subtract(c);
                double d = off.length();
                if (d < 0.01) { off = UP; d = 1; }
                Vec3 out = off.scale(1 / d);
                if (contract) {
                    Fx.impulse(e, out.scale(-push * 0.12));
                } else if (Math.abs(d - r) < 1.6 && hit.add(e.getId())) {
                    Fx.impulse(e, out.scale(push).add(0, 0.25 * push, 0));
                    if (dmg > 0 && e instanceof LivingEntity le) Fx.hurt(l, le, owner, dmg);
                }
            }

            if (age >= life) {
                if (onEnd != null) onEnd.run();
                return true;
            }
            return false;
        }
    }

    // =====================================================================
    // Луч: быстро «прорисовывается» вдоль линии с винтовой спиралью, бьёт всех на пути
    // =====================================================================
    public static final class BeamAnim implements Fx.Effect {
        private static final int DUR = 5;
        private final ServerLevel l;
        private final Vec3 a, b, dir;
        private final double len;
        private final ParticleOptions main, accent;
        private final Vec3[] bs;
        private int age;

        public BeamAnim(ServerLevel l, ServerPlayer owner, Vec3 a, Vec3 b, ParticleOptions main,
                        ParticleOptions accent, float dmg, boolean ignite) {
            this.l = l; this.a = a; this.b = b; this.main = main; this.accent = accent;
            Vec3 dv = b.subtract(a);
            this.len = dv.length();
            this.dir = len > 0.01 ? dv.scale(1 / len) : new Vec3(0, 0, 1);
            this.bs = Fx.basis(dir);
            if (dmg > 0) {
                Vec3 mid = a.add(b).scale(0.5);
                for (Entity e : Fx.near(l, mid, len / 2 + 2, owner)) {
                    if (!(e instanceof LivingEntity le)) continue;
                    Vec3 ec = Fx.center(e);
                    if (Fx.closestOnSegment(ec, a, b).distanceTo(ec) <= 1.2) {
                        Fx.hurt(l, le, owner, dmg);
                        if (ignite) le.setRemainingFireTicks(100);
                    }
                }
            }
        }

        @Override
        public boolean tick() {
            age++;
            double from = (age - 1.0) / DUR * len;
            double to = Math.min(1.0, (double) age / DUR) * len;
            for (double d = from; d < to; d += 0.45) {
                Vec3 p = a.add(dir.scale(d));
                Fx.dot(l, main, p);
                double ph = d * 1.3;
                Fx.dot(l, accent, p.add(bs[0].scale(Math.cos(ph) * 0.28)).add(bs[1].scale(Math.sin(ph) * 0.28)));
            }
            if (age >= DUR) {
                Fx.burst(l, main, b, 14, 0.3, 0.12);
                Fx.burst(l, accent, b, 14, 0.4, 0.2);
                return true;
            }
            return false;
        }
    }

    // =====================================================================
    // Цепная молния: прыгает от цели к цели по очереди
    // =====================================================================
    public static final class ArcAnim implements Fx.Effect {
        private final ServerLevel l;
        private final ServerPlayer owner;
        private final List<Vec3> pts;
        private final List<LivingEntity> targets;
        private final float dmg;
        private int age, idx;

        public ArcAnim(ServerLevel l, ServerPlayer owner, List<Vec3> pts, List<LivingEntity> targets, float dmg) {
            this.l = l; this.owner = owner; this.pts = pts; this.targets = targets; this.dmg = dmg;
        }

        @Override
        public boolean tick() {
            age++;
            if (age % 2 == 1 && idx < targets.size()) {
                Vec3 a = pts.get(idx), b = pts.get(idx + 1);
                Fx.line(l, ParticleTypes.ELECTRIC_SPARK, a, b, 0.25, 0.18);
                Fx.line(l, ParticleTypes.END_ROD, a, b, 1.0, 0.1);
                Fx.burst(l, ParticleTypes.ELECTRIC_SPARK, b, 14, 0.4, 0.25);
                Fx.sound(l, b, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.5f, 1.8f + idx * 0.1f);
                LivingEntity t = targets.get(idx);
                if (t != null && t.isAlive()) {
                    Fx.hurt(l, t, owner, dmg * (float) Math.pow(0.8, idx));
                }
                idx++;
            }
            return idx >= targets.size();
        }
    }

    // =====================================================================
    // Разрыв пустоты: вращающийся «шов» вдоль линии, тянет и калечит
    // =====================================================================
    public static final class VoidTear implements Fx.Effect {
        private static final int LIFE = 80;
        private final ServerLevel l;
        private final ServerPlayer owner;
        private final Vec3 a, b, dir;
        private final double len;
        private final Vec3[] bs;
        private int age;

        public VoidTear(ServerLevel l, ServerPlayer owner, Vec3 a, Vec3 b) {
            this.l = l; this.owner = owner; this.a = a; this.b = b;
            Vec3 dv = b.subtract(a);
            this.len = dv.length();
            this.dir = len > 0.01 ? dv.scale(1 / len) : new Vec3(0, 0, 1);
            this.bs = Fx.basis(dir);
        }

        @Override
        public boolean tick() {
            age++;
            for (double d = 0; d < len; d += 1.5) {
                Vec3 p = a.add(dir.scale(d));
                double ph = age * 0.35 + d;
                double rr = 0.7 + 0.35 * Math.sin(age * 0.2 + d);
                Vec3 q = p.add(bs[0].scale(Math.cos(ph) * rr)).add(bs[1].scale(Math.sin(ph) * rr));
                Fx.dot(l, ParticleTypes.PORTAL, q);
                Fx.burst(l, ParticleTypes.SQUID_INK, p, 1, 0.25, 0.0);
            }
            Vec3 mid = a.add(b).scale(0.5);
            for (Entity e : Fx.near(l, mid, len / 2 + 4, owner)) {
                Vec3 ec = Fx.center(e);
                Vec3 to = Fx.closestOnSegment(ec, a, b).subtract(ec);
                double d = to.length();
                if (d > 4 || d < 0.01) continue;
                Fx.impulse(e, to.scale(0.07 / d));
                if (d < 1.4 && age % 10 == 0 && e instanceof LivingEntity le) {
                    Fx.hurt(l, le, owner, 3f);
                    le.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0));
                }
            }
            if (age >= LIFE) {
                Fx.line(l, ParticleTypes.END_ROD, a, b, 1.5, 0.3);
                Fx.sound(l, mid, SoundEvents.ENDERMAN_TELEPORT, 1.0f, 0.5f);
                return true;
            }
            return false;
        }
    }

    // =====================================================================
    // Поле стазиса: сфера из вращающихся колец, замораживает существ, потом разбивается
    // =====================================================================
    public static final class StasisField implements Fx.Effect {
        private static final int LIFE = 100;
        private static final double R = 6;
        private final ServerLevel l;
        private final ServerPlayer owner;
        private final Vec3 c;
        private int age;

        public StasisField(ServerLevel l, ServerPlayer owner, Vec3 c) {
            this.l = l; this.owner = owner; this.c = c;
            Fx.sound(l, c, SoundEvents.BEACON_ACTIVATE, 1.0f, 1.6f);
        }

        @Override
        public boolean tick() {
            age++;
            double rr = R * Math.min(1.0, age / 10.0);
            if (age % 2 == 0) {
                for (int k = -2; k <= 2; k++) {
                    double phi = k * 0.42 + Math.sin(age * 0.05) * 0.1;
                    Fx.ring(l, ParticleTypes.END_ROD, c.add(0, Math.sin(phi) * rr, 0), Math.cos(phi) * rr, 8,
                            age * 0.12 * (k % 2 == 0 ? 1 : -1), UP);
                }
            }
            Fx.burst(l, ParticleTypes.SNOWFLAKE, c, 6, R * 0.5, 0.0);

            if (age % 10 == 0) {
                for (Entity e : Fx.near(l, c, R, owner)) {
                    if (e instanceof LivingEntity le) {
                        le.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 6, false, false));
                        le.addEffect(new MobEffectInstance(MobEffects.GLOWING, 30, 0, false, false));
                    }
                }
            }

            if (age >= LIFE) {
                Fx.sound(l, c, SoundEvents.GLASS_BREAK, 1.5f, 0.8f);
                double ga = Math.PI * (3 - Math.sqrt(5));
                for (int i = 0; i < 48; i++) {
                    double y = 1 - 2.0 * (i + 0.5) / 48;
                    double r2 = Math.sqrt(1 - y * y);
                    Vec3 dir = new Vec3(Math.cos(i * ga) * r2, y, Math.sin(i * ga) * r2);
                    Fx.fly(l, ParticleTypes.SNOWFLAKE, c.add(dir.scale(R)), dir.scale(0.5));
                }
                Fx.burst(l, ParticleTypes.END_ROD, c, 40, 3, 0.15);
                return true;
            }
            return false;
        }
    }

    // =====================================================================
    // Сверхновая: сфера звёздного света схлопывается и взрывается
    // =====================================================================
    public static final class Supernova implements Fx.Effect {
        private static final int CHARGE = 26;
        private final ServerLevel l;
        private final ServerPlayer owner;
        private final Vec3 c;
        private int age;

        public Supernova(ServerLevel l, ServerPlayer owner, Vec3 c) {
            this.l = l; this.owner = owner; this.c = c;
            Fx.sound(l, c, SoundEvents.BEACON_ACTIVATE, 1.2f, 0.5f);
        }

        @Override
        public boolean tick() {
            age++;
            if (age <= CHARGE) {
                double t = (double) age / CHARGE;
                double r = 5 * (1 - t) + 0.4;
                Fx.sphere(l, ParticleTypes.END_ROD, c, r, 28, age * 0.3);
                Fx.burst(l, ParticleTypes.FIREWORK, c, 2, 0.3, 0.05);
                Fx.burst(l, ParticleTypes.GLOW, c, 3, 0.1 + 0.3 * t, 0.0);
                for (Entity e : Fx.near(l, c, 5, owner)) {
                    Vec3 to = c.subtract(Fx.center(e));
                    double d = to.length();
                    if (d > 0.05) Fx.impulse(e, to.scale(0.08 / d));
                }
                return false;
            }
            Fx.sound(l, c, SoundEvents.WARDEN_SONIC_BOOM, 1.0f, 0.8f);
            Fx.sound(l, c, SoundEvents.LIGHTNING_BOLT_THUNDER, 1.5f, 0.9f);
            Fx.burst(l, ParticleTypes.EXPLOSION_EMITTER, c, 1, 0, 0);
            Fx.burst(l, ParticleTypes.FIREWORK, c, 80, 0.5, 0.3);
            Fx.add(new Shockwave(l, owner, c, 12, 10, 2.2, 18f, ParticleTypes.END_ROD, false, true, null));
            return true;
        }
    }

    // =====================================================================
    // Разлом: два вращающихся портала (старт/финиш), телепорт игрока между ними
    // =====================================================================
    public static final class RiftTravel implements Fx.Effect {
        private static final int OPEN = 14, TOTAL = 24;
        private final ServerLevel l;
        private final ServerPlayer p;
        private final Vec3 fromC, dest, axis;
        private int age;

        public RiftTravel(ServerLevel l, ServerPlayer p, Vec3 dest) {
            this.l = l; this.p = p; this.dest = dest;
            Vec3 look = p.getLookAngle();
            Vec3 h = new Vec3(look.x, 0, look.z);
            this.axis = h.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : h.normalize();
            this.fromC = p.position().add(0, 1.0, 0);
            Fx.sound(l, fromC, SoundEvents.END_PORTAL_FRAME_FILL, 1.0f, 0.7f);
        }

        @Override
        public boolean tick() {
            age++;
            double open = age <= OPEN ? (double) age / OPEN : 1.0 - (double) (age - OPEN) / (TOTAL - OPEN);
            double r = 1.4 * open;
            if (r > 0.1) {
                for (Vec3 c : new Vec3[] {fromC, dest}) {
                    Fx.ring(l, ParticleTypes.REVERSE_PORTAL, c, r, 14, age * 0.5, axis);
                    Fx.ring(l, ParticleTypes.PORTAL, c, r * 0.6, 8, -age * 0.7, axis);
                }
            }
            if (age == 1) Fx.line(l, ParticleTypes.PORTAL, fromC, dest, 1.0, 0.3);

            if (age == OPEN && p.isAlive()) {
                Fx.burst(l, ParticleTypes.REVERSE_PORTAL, fromC, 50, 0.5, 0.4);
                Fx.sound(l, fromC, SoundEvents.ENDERMAN_TELEPORT, 1.0f, 0.8f);
                double[] dys = {-1.6, -1.0, -0.3, 0.4};
                for (double dy : dys) {
                    if (p.randomTeleport(dest.x, dest.y + dy, dest.z, true)) {
                        p.resetFallDistance();
                        break;
                    }
                }
                Fx.burst(l, ParticleTypes.REVERSE_PORTAL, dest, 50, 0.5, 0.4);
                Fx.sound(l, dest, SoundEvents.ENDERMAN_TELEPORT, 1.0f, 1.2f);
            }
            return age >= TOTAL;
        }
    }
}
