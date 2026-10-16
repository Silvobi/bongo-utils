package pl.bongo.bongoutils;

import java.util.*;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.SynchedEntityData.DataValue;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.*;

/** Temporary poses only. Server sleeping state, respawn points and sleep voting are never changed. */
public final class Rest {
    public enum Mode { SIT, LAY }
    private record Session(ServerPlayer player, ServerLevel level, Vec3 anchor, Mode mode, RestSeat seat) {}
    private static final Map<UUID, Session> sessions = new HashMap<>();
    private static final Map<Integer, Session> entities = new HashMap<>();
    private Rest() {}
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        for (Mode mode : Mode.values()) dispatcher.register(Commands.literal(mode.name().toLowerCase(Locale.ROOT))
                .executes(c -> toggle(c.getSource().getPlayerOrException(), mode)));
    }
    public static boolean laying(Entity player) { Session s = sessions.get(player.getUUID()); return s != null && s.player == player && s.mode == Mode.LAY; }
    public static Vec3 savedPosition(ServerPlayer player) { Session s = sessions.get(player.getUUID()); return s != null && s.player == player ? s.anchor : null; }
    public static int toggle(ServerPlayer player, Mode mode) {
        Session old = sessions.get(player.getUUID());
        if (old != null) {
            stop(player, true);
            if (old.mode == mode) { say(player, "rest_stopped"); return 1; }
        }
        if (!eligible(player) || !player.onGround() || RestSafety.moving(player.getLastClientInput())) { say(player, "rest_unavailable"); return 0; }
        RestSafety.Problem problem = check(player, player.position(), mode);
        if (problem != null) { say(player, "rest_" + problem.name().toLowerCase(Locale.ROOT)); return 0; }
        ServerLevel level = player.level(); Vec3 anchor = player.position();
        RestSeat seat = mode == Mode.SIT ? new RestSeat(level, player.getUUID(), anchor) : null;
        if (seat != null) {
            if (!level.addFreshEntity(seat) || !player.startRiding(seat, false, false)) {
                seat.discard(); say(player, "rest_unavailable"); return 0;
            }
        }
        Session next = new Session(player, level, anchor, mode, seat);
        sessions.put(player.getUUID(), next); entities.put(player.getId(), next);
        player.setDeltaMovement(Vec3.ZERO);
        if (mode == Mode.LAY) player.setPose(Pose.SLEEPING);
        say(player, mode == Mode.SIT ? "rest_sitting" : "rest_laying"); return 1;
    }
    private static boolean eligible(ServerPlayer p) {
        return p.isAlive() && !p.isSpectator() && !p.isPassenger() && !p.isVehicle() && !p.isSleeping() && !p.isFallFlying()
                && !p.getAbilities().flying && !p.isSwimming() && !p.isInWater() && !p.isInLava() && !p.isOnFire()
                && !p.isFreezing() && !p.isChangingDimension() && p.hurtTime == 0 && p.fallDistance < .1;
    }
    private static RestSafety.Problem check(ServerPlayer player, Vec3 feet, Mode mode) {
        var dimensions = player.getDimensions(Pose.STANDING);
        if (dimensions.width() > 8 || dimensions.height() > 8) return RestSafety.Problem.SPACE;
        AABB bounds = RestSafety.occupied(feet, dimensions, mode, player.getYRot()).inflate(.1, 0, .1).expandTowards(0, -1, 0);
        if (!player.level().getWorldBorder().isWithinBounds(bounds)
                || bounds.minY < player.level().getMinY() || bounds.maxY > player.level().getMaxY()) return RestSafety.Problem.HAZARD;
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
                BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ)))
            if (!player.level().hasChunkAt(pos)) return RestSafety.Problem.HAZARD;
        RestSafety.Problem problem = RestSafety.check(player.level(), feet, dimensions, mode, player.getYRot());
        if (problem == null && !player.level().noEntityCollision(player, dimensions.makeBoundingBox(feet).deflate(.001))) return RestSafety.Problem.SPACE;
        return problem;
    }
    public static void input(ServerPlayer player, Input input) {
        if (sessions.containsKey(player.getUUID()) && RestSafety.moving(input)) { stop(player, true); say(player, "rest_stopped"); }
    }
    public static void tick(MinecraftServer server) {
        for (Session s : List.copyOf(sessions.values())) {
            ServerPlayer p = s.player;
            boolean attached = s.seat == null || !s.seat.isRemoved() && p.getVehicle() == s.seat;
            Vec3 expected = s.seat == null ? s.anchor : s.anchor.subtract(p.getVehicleAttachmentPoint(s.seat));
            boolean moved = p.level() != s.level || p.position().distanceToSqr(expected) > .04;
            if (!p.connection.isAcceptingMessages() || !p.isAlive() || p.isSpectator() || p.isChangingDimension() || !attached || moved
                    || p.isSleeping() || p.isFallFlying() || p.isInWater() || p.isInLava() || p.isOnFire() || p.isFreezing()
                    || p.getAbilities().flying || p.hurtTime > 0 || check(p, s.anchor, s.mode) != null) {
                stop(p, !moved && p.level() == s.level); continue;
            }
            if (RestSafety.moving(p.getLastClientInput())) { input(p, p.getLastClientInput()); continue; }
            if (s.mode == Mode.LAY) p.setPose(Pose.SLEEPING);
        }
    }
    public static void stop(ServerPlayer player, boolean safeExit) {
        Session s = sessions.get(player.getUUID());
        if (s == null || s.player != player) return;
        sessions.remove(player.getUUID());
        entities.remove(player.getId());
        if (s.seat != null) {
            if (player.getVehicle() == s.seat) player.stopRiding();
            s.seat.discard();
        }
        if (player.isAlive()) player.setPose(Pose.STANDING);
        if (s.mode == Mode.LAY) player.level().getChunkSource().sendToTrackingPlayersAndSelf(player,
                new ClientboundSetEntityDataPacket(player.getId(), List.of(DataValue.create(LivingEntity.SLEEPING_POS_ID, Optional.empty()),
                        DataValue.create(Entity.DATA_POSE, player.getPose()))));
        if (safeExit && player.isAlive() && player.level() == s.level) {
            Vec3 exit = findExit(player, s.anchor);
            if (exit != null) { player.teleportTo(exit.x, exit.y, exit.z); player.setOnGround(true); }
            else if (!player.level().noBlockCollision(player, player.getDimensions(Pose.STANDING).makeBoundingBox(player.position())))
                player.setPose(Pose.SWIMMING);
        }
    }
    private static Vec3 findExit(ServerPlayer player, Vec3 anchor) {
        if (check(player, anchor, Mode.SIT) == null) return anchor;
        for (int radius = 1; radius <= 2; radius++) for (int dy : new int[]{0, 1, -1, 2, -2})
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                Vec3 candidate = anchor.add(dx, dy, dz);
                if (check(player, candidate, Mode.SIT) == null) return candidate;
            }
        return null;
    }
    /** Clients need a sleeping-position marker to retain the vanilla lying animation. It is never stored on the server entity. */
    public static ClientboundSetEntityDataPacket decorate(ClientboundSetEntityDataPacket packet) {
        Session s = entities.get(packet.id());
        if (s == null || s.mode != Mode.LAY) return packet;
        var data = new ArrayList<DataValue<?>>(packet.packedItems());
        data.removeIf(value -> value.id() == LivingEntity.SLEEPING_POS_ID.id());
        data.add(DataValue.create(LivingEntity.SLEEPING_POS_ID, Optional.of(BlockPos.containing(s.anchor).above())));
        return new ClientboundSetEntityDataPacket(packet.id(), data);
    }
    public static void stopAll(MinecraftServer server) { for (Session s : List.copyOf(sessions.values())) stop(s.player, true); }
    private static void say(ServerPlayer player, String key) { player.sendSystemMessage(Component.literal(Lang.text(player, key)), true); }
}
