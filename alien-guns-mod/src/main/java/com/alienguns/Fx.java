package com.alienguns;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Менеджер эффектов + утилиты для частиц/лучей/физики. Всё на сервере, ваниль-частицы. */
public final class Fx {
    private Fx() {}

    /** Множитель плотности частиц. Если лагает на сервере — поставь 0.5. */
    public static double DENSITY = 1.0;

    public interface Effect {
        /** @return true когда эффект закончился */
        boolean tick();
    }

    private static final List<Effect> ACTIVE = new ArrayList<>();
    private static final List<Effect> PENDING = new ArrayList<>();

    public static void add(Effect e) { PENDING.add(e); }

    public static void clear() { ACTIVE.clear(); PENDING.clear(); }

    public static void tickAll() {
        if (!PENDING.isEmpty()) {
            ACTIVE.addAll(PENDING);
            PENDING.clear();
        }
        ACTIVE.removeIf(e -> {
            try {
                return e.tick();
            } catch (Throwable t) {
                AlienGuns.LOGGER.error("Effect failed", t);
                return true;
            }
        });
    }

    // ---------- частицы ----------

    public static void dot(ServerLevel l, ParticleOptions t, Vec3 p) {
        l.sendParticles(t, p.x, p.y, p.z, 1, 0, 0, 0, 0);
    }

    /** Много частиц одним пакетом (дёшево). */
    public static void burst(ServerLevel l, ParticleOptions t, Vec3 p, int count, double spread, double speed) {
        l.sendParticles(t, p.x, p.y, p.z, count, spread, spread, spread, speed);
    }

    /** Частица с заданной скоростью (count = 0 -> offset считается вектором скорости). */
    public static void fly(ServerLevel l, ParticleOptions t, Vec3 p, Vec3 v) {
        l.sendParticles(t, p.x, p.y, p.z, 0, v.x, v.y, v.z, 1.0);
    }

    public static Vec3[] basis(Vec3 axis) {
        Vec3 a = axis.normalize();
        Vec3 t = Math.abs(a.y) < 0.99 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 u = a.cross(t).normalize();
        Vec3 v = a.cross(u).normalize();
        return new Vec3[] {u, v};
    }

    public static void ring(ServerLevel l, ParticleOptions t, Vec3 c, double r, int n, double phase, Vec3 axis) {
        n = Math.max(3, (int) (n * DENSITY));
        Vec3[] b = basis(axis);
        for (int i = 0; i < n; i++) {
            double a = phase + i * Math.PI * 2 / n;
            dot(l, t, c.add(b[0].scale(Math.cos(a) * r)).add(b[1].scale(Math.sin(a) * r)));
        }
    }

    public static void sphere(ServerLevel l, ParticleOptions t, Vec3 c, double r, int n, double rot) {
        n = Math.max(6, (int) (n * DENSITY));
        double ga = Math.PI * (3 - Math.sqrt(5));
        for (int i = 0; i < n; i++) {
            double y = 1 - 2.0 * (i + 0.5) / n;
            double rr = Math.sqrt(1 - y * y);
            double th = i * ga + rot;
            dot(l, t, c.add(Math.cos(th) * rr * r, y * r, Math.sin(th) * rr * r));
        }
    }

    public static void line(ServerLevel l, ParticleOptions t, Vec3 a, Vec3 b, double step, double jitter) {
        Vec3 dv = b.subtract(a);
        double len = dv.length();
        if (len < 0.01) return;
        Vec3 dir = dv.scale(1 / len);
        int n = (int) (len / step);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i <= n; i++) {
            Vec3 p = a.add(dir.scale(i * step));
            if (jitter > 0 && i > 0 && i < n) {
                p = p.add(r.nextGaussian() * jitter, r.nextGaussian() * jitter, r.nextGaussian() * jitter);
            }
            dot(l, t, p);
        }
    }

    // ---------- звук ----------

    public static void sound(ServerLevel l, Vec3 p, SoundEvent ev, float vol, float pitch) {
        l.playSound(null, p.x, p.y, p.z, ev, SoundSource.PLAYERS, vol, pitch);
    }

    // ---------- лучи и поиск целей ----------

    public static boolean solid(ServerLevel l, Vec3 p) {
        BlockPos bp = BlockPos.containing(p);
        if (l.isOutsideBuildHeight(bp)) return true;
        BlockState s = l.getBlockState(bp);
        return !s.getCollisionShape(l, bp).isEmpty();
    }

    public static LivingEntity livingAt(ServerLevel l, Vec3 p, Entity owner) {
        List<LivingEntity> list = l.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(p, 1.0, 1.0, 1.0),
                e -> e != owner && e.isAlive() && !e.isSpectator());
        return list.isEmpty() ? null : list.get(0);
    }

    /** Конец луча: блок (точка перед ним) или первая живая цель (если stopAtEntity). */
    public static Vec3 rayEnd(ServerLevel l, Vec3 from, Vec3 dir, double max, Entity owner, boolean stopAtEntity) {
        for (double d = 0.5; d <= max; d += 0.5) {
            Vec3 p = from.add(dir.scale(d));
            if (solid(l, p)) return from.add(dir.scale(d - 0.5));
            if (stopAtEntity && d % 1.0 == 0 && livingAt(l, p, owner) != null) return p;
        }
        return from.add(dir.scale(max));
    }

    public static LivingEntity firstLiving(ServerLevel l, Vec3 from, Vec3 dir, double max, Entity owner) {
        for (double d = 0.5; d <= max; d += 0.5) {
            Vec3 p = from.add(dir.scale(d));
            if (solid(l, p)) return null;
            if (d % 1.0 == 0) {
                LivingEntity e = livingAt(l, p, owner);
                if (e != null) return e;
            }
        }
        return null;
    }

    public static Vec3 center(Entity e) { return e.getBoundingBox().getCenter(); }

    /** Все существа/предметы в радиусе (кроме владельца, спектаторов и креативных игроков). */
    public static List<Entity> near(ServerLevel l, Vec3 c, double r, Entity exclude) {
        AABB box = new AABB(c.x - r, c.y - r, c.z - r, c.x + r, c.y + r, c.z + r);
        List<Entity> list = new ArrayList<>(l.getEntities((Entity) null, box,
                e -> e.isAlive() && !e.isSpectator() && e != exclude && !(e instanceof Player pl && pl.isCreative())));
        list.removeIf(e -> center(e).distanceTo(c) > r);
        return list;
    }

    public static void hurt(ServerLevel l, LivingEntity e, Entity owner, float amount) {
        e.hurtServer(l, owner != null ? l.damageSources().indirectMagic(owner, owner) : l.damageSources().magic(), amount);
    }

    public static void impulse(Entity e, Vec3 v) {
        Vec3 nv = e.getDeltaMovement().add(v);
        double len = nv.length();
        if (len > 2.5) nv = nv.scale(2.5 / len);
        e.setDeltaMovement(nv);
        if (e instanceof ServerPlayer sp) {
            sp.connection.send(new ClientboundSetEntityMotionPacket(sp));
        }
    }

    public static Vec3 closestOnSegment(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double l2 = ab.lengthSqr();
        if (l2 < 1e-6) return a;
        double t = Math.max(0, Math.min(1, p.subtract(a).dot(ab) / l2));
        return a.add(ab.scale(t));
    }
}
