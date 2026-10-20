package pl.bongo.bongoutils;

import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/** A zero-size vanilla interaction entity: no model, collisions, drops or mount health bar. */
public final class RestSeat extends Interaction {
    public static final String TAG = "bongoutils_rest_seat";
    private final UUID owner;
    private final Vec3 anchor;
    public RestSeat(ServerLevel level, UUID owner, Vec3 anchor) {
        super(EntityTypes.INTERACTION, level); this.owner = owner; this.anchor = anchor;
        getEntityData().set(DATA_WIDTH_ID, 0.0F); getEntityData().set(DATA_HEIGHT_ID, 0.0F);
        setNoGravity(true); setSilent(true); addTag(TAG); snapTo(anchor);
    }
    @Override protected boolean canAddPassenger(Entity passenger) { return owner.equals(passenger.getUUID()) && !isVehicle(); }
    @Override public Vec3 getDismountLocationForPassenger(net.minecraft.world.entity.LivingEntity passenger) { return anchor; }
    @Override public boolean isPickable() { return false; }
    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean save(ValueOutput output) { return false; }
    @Override public boolean saveAsPassenger(ValueOutput output) { return false; }
}
