package com.codex.blacklight.entity;

import com.codex.blacklight.combat.ConsumeControl;
import com.codex.blacklight.combat.ConsumeVisual;
import com.codex.blacklight.combat.ImpactControl;
import com.codex.blacklight.infection.BlacklightInfectionSystem;
import com.codex.blacklight.military.MilitaryFaction;
import com.codex.blacklight.military.vehicle.*;
import com.codex.blacklight.registry.ModAttachments;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Mechanical identity, physical passengers and the only durable vehicle inventory.
 * Chassis coordinates are x right/y up/z front: yaw 0 is +Z and yaw +90 is +X.
 * The control boundary accepts native Minecraft player yaw and converts it here.
 */
public final class MilitaryVehicle extends Entity {
    private static final EntityDataAccessor<CompoundTag> STATE = SynchedEntityData.defineId(MilitaryVehicle.class, EntityDataSerializers.COMPOUND_TAG);
    private static final VehicleCatalog CATALOG = VehicleCatalog.loadDefault();
    private final VehicleDefinition definition;
    private final MilitaryFaction faction;
    private VehicleHullPart[] collisionParts;
    private final VehicleCollisionShapes collisionShapes=new VehicleCollisionShapes();
    private VehicleInventory inventory;
    private VehicleSeats seats;
    private VehicleControlAuthority authority;
    private final Map<UUID, UUID> leases = new HashMap<>();
    private final Map<UUID, String> restoredSeatHints = new HashMap<>();
    private final Map<UUID, Integer> previousActions = new HashMap<>();
    private long leaseRevision = -1;
    private boolean reconciling;
    private boolean destroyed;
    private float mechanicalHealth;
    private float turretYaw;
    private float gunPitch;
    private String nativeOrigin = "";
    private String nativeDimension = "";
    private String currentHome = "";
    private UUID playerOwner;
    private long primaryShot=-1,secondaryShot=-1;
    private String selectedPrimary;
    private VehicleKinematics.StopReason stopReason = VehicleKinematics.StopReason.NONE;

    public MilitaryVehicle(EntityType<? extends MilitaryVehicle> type, Level level, String chassis, MilitaryFaction faction) {
        super(type, level);
        definition = CATALOG.require(chassis);
        this.faction = Objects.requireNonNull(faction);
        inventory = new VehicleInventory(definition);
        seats = new VehicleSeats(definition);
        mechanicalHealth = (float) definition.maxHealth();
        selectedPrimary = defaultPrimary();
        setNoGravity(true);
        setPos(getX(), getY(), getZ());
        collisionParts=new VehicleHullPart[]{new VehicleHullPart(this)};
        collisionParts[0].setId(getId()+1);updateHullPart();
        syncVehicleState();
    }
    public String chassisId() { return definition.id(); }
    public MilitaryFaction faction() { return faction; }
    public VehicleDefinition definition() { return definition; }
    public VehicleInventory inventory() {
        if (level().isClientSide) {
            try { return readInventory(entityData.get(STATE).getCompound("Inventory")); }
            catch (IllegalArgumentException failure) { return new VehicleInventory(definition); }
        }
        return inventory;
    }
    public float health() { return level().isClientSide ? entityData.get(STATE).getFloat("Health") : mechanicalHealth; }
    public float maxHealth() { return (float) definition.maxHealth(); }
    public long fuelTicks() { return level().isClientSide ? entityData.get(STATE).getCompound("Inventory").getLong("FuelTicks") : inventory.fuelTicks(); }
    public float turretYaw() { return level().isClientSide ? entityData.get(STATE).getFloat("TurretYaw") : turretYaw; }
    public float gunPitch() { return level().isClientSide ? entityData.get(STATE).getFloat("GunPitch") : gunPitch; }
    public String selectedPrimaryWeapon() { return level().isClientSide ? entityData.get(STATE).getString("SelectedPrimary") : selectedPrimary; }
    public String secondaryWeapon() { return chassisId().equals("apc") ? "vehicle_rocket" : chassisId().equals("tank") ? "coaxial_machine_gun" : ""; }
    public String nativeOrigin() { return nativeOrigin; }
    public String nativeDimension() { return nativeDimension; }
    public String currentHome() { return currentHome; }
    public long primaryShot(){return level().isClientSide?(entityData.get(STATE).contains("PrimaryShot")?entityData.get(STATE).getLong("PrimaryShot"):-1):primaryShot;}
    public long secondaryShot(){return level().isClientSide?(entityData.get(STATE).contains("SecondaryShot")?entityData.get(STATE).getLong("SecondaryShot"):-1):secondaryShot;}
    public void recordShot(String weapon){if(level().isClientSide)return;if(weapon.equals(secondaryWeapon()))secondaryShot=level().getGameTime();else primaryShot=level().getGameTime();syncVehicleState();}
    public boolean restoreResourcesIfUnchanged(VehicleInventory.Snapshot before,VehicleInventory.Snapshot expected){
        if(level().isClientSide||!inventory.snapshot().equals(expected))return false;
        inventory=VehicleInventory.restore(definition,before);syncVehicleState();return true;
    }
    /** Trusted server service restores real remaining durability, never revives a removed chassis. */
    public float repair(float amount){
        if(level().isClientSide||destroyed||isRemoved()||mechanicalHealth<=0||!Float.isFinite(amount)||amount<=0)return 0;
        float before=mechanicalHealth;mechanicalHealth=Math.min(maxHealth(),mechanicalHealth+amount);
        if(mechanicalHealth!=before)syncVehicleState();return mechanicalHealth-before;
    }
    public boolean controlLocked(){return VehicleHijackSystem.claimed(this);}
    public boolean aimAiWeapon(MilitarySoldier actor,LivingEntity target){
        if(level().isClientSide||controlLocked()||playerOwner!=null||target==null||target.level()!=level()||!target.isAlive()
                ||actor==null||weaponOperator()!=actor||actor.getVehicle()!=this||!crewOperational(actor)
                ||getPassengers().stream().anyMatch(Player.class::isInstance)||!actor.validTarget(target)||selectedPrimary.isEmpty())return false;
        var origin=new VehicleDefinition.Vec3(getX(),getY(),getZ());
        var anchor=definition.mountWorld("breech_primary",origin,getYRot(),turretYaw,gunPitch);
        var delta=target.getBoundingBox().getCenter().subtract(anchor.x(),anchor.y(),anchor.z());
        float desiredYaw=net.minecraft.util.Mth.wrapDegrees((float)Math.toDegrees(Math.atan2(delta.x,delta.z))-getYRot());
        float desiredPitch=(float)-Math.toDegrees(Math.atan2(delta.y,Math.sqrt(delta.x*delta.x+delta.z*delta.z)));
        if(desiredPitch < -25||desiredPitch > 15)return false;
        float nextYaw=turretYaw+net.minecraft.util.Mth.clamp(net.minecraft.util.Mth.wrapDegrees(desiredYaw-turretYaw),-3,3);
        float nextPitch=gunPitch+net.minecraft.util.Mth.clamp(desiredPitch-gunPitch,-2,2);
        for(var envelope:VehicleHullShape.sweepArticulation(definition,origin,getYRot(),turretYaw,nextYaw,gunPitch,nextPitch))
            if(VehicleKinematics.checkEnvelope(this,envelope)!=VehicleKinematics.StopReason.NONE)return false;
        turretYaw=net.minecraft.util.Mth.wrapDegrees(nextYaw);gunPitch=nextPitch;setBoundingBox(makeBoundingBox());updateHullPart();syncVehicleState();
        return Math.abs(net.minecraft.util.Mth.wrapDegrees(desiredYaw-turretYaw))<.5 && Math.abs(desiredPitch-gunPitch)<.5;
    }
    public UUID playerOwner(){return playerOwner;}
    public void setPlayerOwner(UUID owner){if(!level().isClientSide)playerOwner=owner;}
    /** Native issuance belongs to a separate garage ledger; changing current home cannot rewrite it. */
    public boolean initializeNativeOrigin(String base, String dimension) {
        if (level().isClientSide || !nativeOrigin.isEmpty() || !validReference(base) || !validReference(dimension)) return false;
        nativeOrigin = base; nativeDimension = dimension; return true;
    }
    public boolean setCurrentHome(String base) {
        if (level().isClientSide || base == null || base.length() > 256) return false;
        currentHome = base; return true;
    }
    private static boolean validReference(String value) { return value != null && !value.isBlank() && value.length() <= 256; }
    public VehicleKinematics.StopReason stopReason() { return level().isClientSide ? parseStop(entityData.get(STATE).getString("StopReason")) : stopReason; }
    private static VehicleKinematics.StopReason parseStop(String id) {
        try { return VehicleKinematics.StopReason.valueOf(id); } catch (IllegalArgumentException failure) { return VehicleKinematics.StopReason.INVALID; }
    }
    public void setStopReason(VehicleKinematics.StopReason reason) { if (!level().isClientSide) stopReason = Objects.requireNonNull(reason); }

    public String seatOf(UUID occupant) {
        if (!level().isClientSide) return seats.seatOf(occupant);
        var saved = entityData.get(STATE).getCompound("Seats");
        for (String id : saved.getAllKeys()) if (saved.hasUUID(id) && saved.getUUID(id).equals(occupant)) return id;
        return null;
    }
    public UUID controlLease(UUID occupant) {
        if (occupant == null) return null;
        if (level().isClientSide) { var tag = entityData.get(STATE).getCompound("Leases"); return tag.hasUUID(occupant.toString()) ? tag.getUUID(occupant.toString()) : null; }
        reconcileCrew(); return leases.get(occupant);
    }
    public Entity occupant(VehicleDefinition.Job job) {
        for (var seat : definition.seats()) if (seat.job() == job) {
            UUID id = seats.occupant(seat.id());
            for (Entity passenger : getPassengers()) if (passenger.getUUID().equals(id)) return passenger;
        }
        return null;
    }
    public Entity weaponOperator() {
        Entity gunner = occupant(VehicleDefinition.Job.GUNNER);
        return gunner != null ? gunner : occupant(VehicleDefinition.Job.DRIVER);
    }
    /** Mounted infantry tactics are suspended, but riding itself is not a crew disability. */
    public static boolean crewOperational(Entity occupant) {
        if (!(occupant instanceof LivingEntity living) || !living.isAlive() || living.isRemoved()) return false;
        if (living instanceof Player player && player.isSpectator()) return false;
        if (living instanceof Mob mob && (mob.isNoAi() || ImpactControl.stunned(mob))) return false;
        return (living instanceof Player || !BlacklightInfectionSystem.isInfected(living))
                && !ModAttachments.peekData(living, ModAttachments.CONSUME_VISUAL).active()
                && !ConsumeControl.ownsMotion(living) && !ConsumeControl.protectedEntity(living);
    }
    private Set<UUID> actualPassengers() {
        Set<UUID> result = new HashSet<>();
        for (Entity passenger : getPassengers()) if (passenger.getVehicle() == this && !passenger.isRemoved()) result.add(passenger.getUUID());
        return result;
    }
    private VehicleControlAuthority authority() {
        if (authority == null) authority = new VehicleControlAuthority(getUUID(), level().dimension().location().toString(), seats);
        return authority;
    }
    private VehicleControlAuthority.Session session(ServerPlayer player) {
        return new VehicleControlAuthority.Session(player.getUUID(), player.level().dimension().location().toString(),
                player.getVehicle() == this && player.level() == level() ? getUUID() : null, crewOperational(player));
    }
    private void reconcileCrew() {
        if (level().isClientSide || reconciling || seats == null) return;
        reconciling = true;
        try {
            var actual = actualPassengers(); seats.reconcile(actual);
            for (Entity passenger : List.copyOf(getPassengers())) if (seats.seatOf(passenger.getUUID()) == null) {
                String hinted = restoredSeatHints.remove(passenger.getUUID());
                boolean assigned = hinted != null && seats.board(passenger.getUUID(), hinted, actual);
                if (!assigned) for (var seat : definition.seats()) if (seats.board(passenger.getUUID(), seat.id(), actual)) { assigned = true; break; }
                if (!assigned) passenger.stopRiding();
            }
            if (leaseRevision != seats.revision()) {
                authority().revokeAll(); leases.clear(); previousActions.keySet().retainAll(actual); leaseRevision = seats.revision();
            }
            Set<UUID> operational = new HashSet<>();
            for (Entity passenger : getPassengers()) if (passenger instanceof ServerPlayer player && crewOperational(player)) {
                operational.add(player.getUUID());
                if (!leases.containsKey(player.getUUID())) { UUID token = authority().grant(session(player)); if (token != null) leases.put(player.getUUID(), token); }
            }
            for (UUID id : List.copyOf(leases.keySet())) if (!operational.contains(id)) { authority().revoke(id); leases.remove(id); previousActions.remove(id); }
        } finally { reconciling = false; }
    }
    /** Inputs contain no client position, player identity, ammunition or damage. aimYaw is native MC yaw. */
    public boolean acceptControl(ServerPlayer player, UUID lease, long sequence, double forward, double steering,
                                 double aimYaw, double aimPitch, int actions) {
        if (level().isClientSide || destroyed || controlLocked() || player == null || player.level() != level() || player.getVehicle() != this || !crewOperational(player)) return false;
        reconcileCrew();
        if (!Double.isFinite(aimYaw) || !Double.isFinite(aimPitch)) return false;
        double vehicleYaw = net.minecraft.util.Mth.wrapDegrees(-aimYaw);
        var input = new VehicleControlAuthority.Input(getUUID(), lease, sequence, forward, steering, vehicleYaw, aimPitch, actions);
        if (!authority().accept(session(player), input, level().getGameTime())) return false;
        int previous = previousActions.getOrDefault(player.getUUID(), 0); previousActions.put(player.getUUID(), actions);
        if ((actions & VehicleControlAuthority.NEXT_AMMUNITION) != 0 && (previous & VehicleControlAuthority.NEXT_AMMUNITION) == 0
                && weaponOperator() == player && chassisId().equals("tank")) selectedPrimary = selectedPrimary.equals("tank_he") ? "tank_ap" : "tank_he";
        if ((actions & VehicleControlAuthority.NEXT_SEAT) != 0 && (previous & VehicleControlAuthority.NEXT_SEAT) == 0) requestNextSeat(player);
        syncVehicleState(); return true;
    }
    public VehicleControlAuthority.State controlState(ServerPlayer player) {
        reconcileCrew(); return authority().state(session(player), level().getGameTime());
    }
    public boolean switchSeat(ServerPlayer player, String seatId) {
        if (level().isClientSide || controlLocked() || player == null || player.level() != level() || player.getVehicle() != this || !crewOperational(player)) return false;
        reconcileCrew();
        if (!seats.switchSeat(player.getUUID(), seatId, actualPassengers())) return false;
        reconcileCrew(); positionRider(player); syncVehicleState(); return true;
    }
    public boolean requestNextSeat(ServerPlayer player) {
        if (player == null || player.getVehicle() != this) return false;
        String current = seatOf(player.getUUID()); int index = -1;
        for (int i = 0; i < definition.seats().size(); i++) if (definition.seats().get(i).id().equals(current)) index = i;
        for (int n = 1; n <= definition.seats().size(); n++) if (switchSeat(player, definition.seats().get((index+n) % definition.seats().size()).id())) return true;
        return false;
    }
    @Override protected boolean canAddPassenger(Entity passenger) { return !destroyed && !controlLocked() && getPassengers().size() < definition.seats().size() && passenger instanceof LivingEntity; }
    @Override protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        if (!level().isClientSide) { reconcileCrew(); if (passenger instanceof MilitarySoldier soldier) soldier.suspendTactics(); syncVehicleState(); }
    }
    @Override protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (!level().isClientSide) { seats.remove(passenger.getUUID()); leases.remove(passenger.getUUID()); previousActions.remove(passenger.getUUID()); authority().revoke(passenger.getUUID()); reconcileCrew(); syncVehicleState(); }
    }
    @Override protected void positionRider(Entity passenger, Entity.MoveFunction callback) {
        String id = seatOf(passenger.getUUID());
        var seat = definition.seats().stream().filter(s -> s.id().equals(id)).findFirst().orElse(null);
        if (seat == null) { super.positionRider(passenger, callback); return; }
        var point = definition.mountWorld(seat.anchorId(), new VehicleDefinition.Vec3(getX(), getY(), getZ()), getYRot(), turretYaw(), gunPitch());
        callback.accept(passenger, point.x(), point.y(), point.z());
        if(passenger instanceof LivingEntity living){
            float heading=net.minecraft.util.Mth.wrapDegrees(-getYRot());
            living.setYBodyRot(heading);living.yBodyRotO=heading;
            if(passenger instanceof MilitarySoldier){living.setYRot(heading);living.setYHeadRot(heading);}
        }
    }
    /** Returning a player here would authorize vanilla client-position MoveVehicle packets. */
    @Override public LivingEntity getControllingPassenger() { return null; }
    @Override public InteractionResult interact(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || destroyed || player.distanceToSqr(this) > 36) return InteractionResult.PASS;
        if (level().isClientSide) return InteractionResult.SUCCESS;
        var supply=com.codex.blacklight.military.vehicle.VehicleRegistry.supply(player,this,hand);
        if(supply!=InteractionResult.PASS)return supply;
        if(player instanceof ServerPlayer server && player.isShiftKeyDown() && player.getMainHandItem().isEmpty()) return VehicleHijackSystem.request(server,this)?InteractionResult.CONSUME:InteractionResult.FAIL;
        if (player.getVehicle() == this) return player instanceof ServerPlayer server && requestNextSeat(server) ? InteractionResult.CONSUME : InteractionResult.PASS;
        return player.startRiding(this) ? InteractionResult.CONSUME : InteractionResult.PASS;
    }
    /** Trusted server movement primitive used by actual AI and terrain QA; never accepts a client position. */
    public VehicleKinematics.MoveResult moveVehicle(double distance, float yawDelta) { if(controlLocked())return new VehicleKinematics.MoveResult(0,0,VehicleKinematics.StopReason.BLOCKED); return VehicleKinematics.advance(this, distance, yawDelta); }
    @Override public void tick() {
        super.tick();
        if (level().isClientSide) { setBoundingBox(makeBoundingBox()); updateHullPart(); return; }
        if (destroyed || mechanicalHealth <= 0) { destroyOnce(); return; }
        reconcileCrew();
        if(controlLocked()){setDeltaMovement(Vec3.ZERO);syncVehicleState();return;}
        Entity driver = occupant(VehicleDefinition.Job.DRIVER);
        if (driver instanceof ServerPlayer player && crewOperational(player)) {
            var input = controlState(player);
            if (input.throttle() != 0 || input.steering() != 0) moveVehicle(input.throttle() * definition.maxSpeed(), (float)(input.steering() * 3));
            else setDeltaMovement(Vec3.ZERO);
        } else setDeltaMovement(Vec3.ZERO);
        Entity operator = weaponOperator();
        if (operator instanceof ServerPlayer player && crewOperational(player) && !definition.weapons().isEmpty()) {
            var input = controlState(player);
            // Body steering cannot aim the gun instantaneously. Every turret/pitch transition is swept.
            if (!input.brake()) {
                float desired = net.minecraft.util.Mth.wrapDegrees((float)input.aimYaw() - getYRot());
                float nextYaw = turretYaw + net.minecraft.util.Mth.clamp(net.minecraft.util.Mth.wrapDegrees(desired-turretYaw), -3, 3);
                float nextPitch = gunPitch + net.minecraft.util.Mth.clamp((float)input.aimPitch()-gunPitch, -2, 2);
                nextPitch = net.minecraft.util.Mth.clamp(nextPitch, -25, 15);
                var origin = new VehicleDefinition.Vec3(getX(),getY(),getZ());
                boolean clear = true;
                for (var envelope : VehicleHullShape.sweepArticulation(definition,origin,getYRot(),turretYaw,nextYaw,gunPitch,nextPitch))
                    if (VehicleKinematics.checkEnvelope(this,envelope) != VehicleKinematics.StopReason.NONE) { clear = false; break; }
                if (clear) { turretYaw = net.minecraft.util.Mth.wrapDegrees(nextYaw); gunPitch = nextPitch; setBoundingBox(makeBoundingBox()); updateHullPart(); }
            }
            if(input.primaryFire()&&!selectedPrimary.isEmpty())VehicleAttackService.fire(this,player,selectedPrimary);
            if(input.secondaryFire()&&!secondaryWeapon().isEmpty())VehicleAttackService.fire(this,player,secondaryWeapon());
        }
        syncVehicleState();
    }
    public net.minecraft.world.phys.shapes.VoxelShape contactCollisionShape(AABB query){return collisionShapes.query(this,query);}
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key){
        super.onSyncedDataUpdated(key);
        if(STATE.equals(key)&&definition!=null){setBoundingBox(makeBoundingBox());updateHullPart();}
    }
    @Override protected AABB makeBoundingBox() {
        if (definition == null) return super.makeBoundingBox();
        var b = VehicleHullShape.from(definition,turretYaw(),gunPitch()).bounds(new VehicleDefinition.Vec3(getX(),getY(),getZ()),getYRot());
        return new AABB(b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ());
    }
    @Override public void setYRot(float yaw) { super.setYRot(yaw); if (definition != null) {setBoundingBox(makeBoundingBox());updateHullPart();} }
    @Override public void setPos(double x,double y,double z) {super.setPos(x,y,z);updateHullPart();}
    @Override public void setId(int id) {super.setId(id);if(collisionParts!=null)collisionParts[0].setId(id+1);}
    @Override public boolean isMultipartEntity() {return true;}
    @Override public VehicleHullPart[] getParts() {return collisionParts==null?new VehicleHullPart[0]:collisionParts;}
    private void updateHullPart() {if(collisionParts!=null)collisionParts[0].followParent();}
    @Override public boolean canCollideWith(Entity other) {return !other.is(this)&&super.canCollideWith(other);}

    @Override public boolean isPickable() { return !isRemoved(); }
    @Override public boolean canBeCollidedWith() { return !isRemoved(); }
    @Override public boolean isPushable() { return false; }
    @Override public void push(double x, double y, double z) { /* Mechanical movement goes through the terrain sweep. */ }
    @Override public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || destroyed || isRemoved() || isInvulnerableTo(source) || !Float.isFinite(amount) || amount <= 0) return false;
        for(Entity passenger:getPassengers())if(passenger instanceof Player victim){
            if(victim.isCreative()||victim.isSpectator())return false;
            if(source.getEntity() instanceof Player attacker&&victim!=attacker&&(!attacker.canHarmPlayer(victim)
                    ||level() instanceof net.minecraft.server.level.ServerLevel server&&!server.getServer().isPvpAllowed()))return false;
        }
        mechanicalHealth = Math.max(0, mechanicalHealth-amount); markHurt();
        if (mechanicalHealth == 0) destroyOnce(); else syncVehicleState();
        return true;
    }
    private void destroyOnce() {
        if (destroyed) return;
        destroyed = true; mechanicalHealth = 0;
        if(level() instanceof net.minecraft.server.level.ServerLevel server)VehicleGarageService.onDestroyed(server,this);
        if (authority != null) authority.revokeAll(); leases.clear(); previousActions.clear();
        ejectPassengers(); syncVehicleState(); discard();
    }
    /** Real safe exit query shared by dismount and the later validated extraction transaction. */
    public Optional<Vec3> safeExit(LivingEntity passenger) {
        double[][] points = {{-definition.width()/2-1,0},{definition.width()/2+1,0},{0,-definition.length()/2-1},{0,definition.length()/2+1}};
        double angle = Math.toRadians(getYRot()), c = Math.cos(angle), s = Math.sin(angle);
        for (double[] point : points) {
            double x=getX()+point[0]*c+point[1]*s,z=getZ()+point[1]*c-point[0]*s;
            for (int dy=1;dy>=-1;dy--) {
                Vec3 candidate=new Vec3(x,Math.floor(getY())+dy,z);
                AABB box=passenger.getDimensions(passenger.getPose()).makeBoundingBox(candidate);
                if (!VehicleKinematics.loaded(level(),box.inflate(.01)) || !level().noCollision(passenger,box)) continue;
                var at=BlockPos.containing(x,candidate.y-.05,z);
                if (!level().getBlockState(at).getCollisionShape(level(),at).isEmpty() && level().getFluidState(at.above()).isEmpty()) return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }
    @Override public Vec3 getDismountLocationForPassenger(LivingEntity passenger) { return safeExit(passenger).orElseGet(() -> new Vec3(getX(),getBoundingBox().maxY+.05,getZ())); }
    @Override protected void defineSynchedData() { entityData.define(STATE,new CompoundTag()); }
    public void syncVehicleState() {
        if (definition == null || level().isClientSide) return;
        var tag=new CompoundTag();tag.putLong("PrimaryShot",primaryShot);tag.putLong("SecondaryShot",secondaryShot);tag.putFloat("Health",mechanicalHealth);tag.putFloat("TurretYaw",turretYaw);tag.putFloat("GunPitch",gunPitch);
        tag.putString("SelectedPrimary",selectedPrimary);tag.putString("StopReason",stopReason.name());tag.put("Inventory",writeInventory());
        var occupants=new CompoundTag();seats.occupants().forEach(occupants::putUUID);tag.put("Seats",occupants);
        var tokens=new CompoundTag();leases.forEach((id,token)->tokens.putUUID(id.toString(),token));tag.put("Leases",tokens);entityData.set(STATE,tag);
    }
    private String defaultPrimary() { return definition.id().equals("tank") ? "tank_he" : definition.id().equals("apc") ? "autocannon" : ""; }
    private CompoundTag writeInventory() {
        var snapshot=inventory.snapshot();var tag=new CompoundTag();tag.putInt("SchemaVersion",snapshot.schemaVersion());tag.putString("VehicleId",snapshot.vehicleId());
        tag.putBoolean("InitialLoadoutIssued",snapshot.initialLoadoutIssued());tag.putLong("FuelTicks",snapshot.fuelTicks());
        var ammo=new CompoundTag();snapshot.ammunition().forEach(ammo::putInt);tag.put("Ammunition",ammo);
        var cooldown=new CompoundTag();snapshot.cooldownUntil().forEach(cooldown::putLong);tag.put("CooldownUntil",cooldown);return tag;
    }
    private VehicleInventory readInventory(CompoundTag tag) {
        var ammo=new HashMap<String,Integer>();var a=tag.getCompound("Ammunition");for(String id:a.getAllKeys())ammo.put(id,a.getInt(id));
        var cooldown=new HashMap<String,Long>();var c=tag.getCompound("CooldownUntil");for(String id:c.getAllKeys())cooldown.put(id,c.getLong(id));
        return VehicleInventory.restore(definition,new VehicleInventory.Snapshot(tag.getInt("SchemaVersion"),tag.getString("VehicleId"),tag.getBoolean("InitialLoadoutIssued"),tag.getLong("FuelTicks"),ammo,cooldown));
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("VehicleSchemaVersion",1);tag.putString("VehicleChassis",chassisId());tag.putString("VehicleFaction",faction.id());tag.putFloat("VehicleHealth",mechanicalHealth);
        if(playerOwner!=null)tag.putUUID("VehiclePlayerOwner",playerOwner);
        tag.putFloat("VehicleTurretYaw",turretYaw);tag.putFloat("VehicleGunPitch",gunPitch);tag.putString("VehicleSelectedPrimary",selectedPrimary);tag.put("VehicleInventory",writeInventory());
        tag.putString("VehicleNativeOrigin",nativeOrigin);tag.putString("VehicleNativeDimension",nativeDimension);tag.putString("VehicleCurrentHome",currentHome);
        var occupants=new CompoundTag();seats.occupants().forEach(occupants::putUUID);tag.put("VehicleSeatHints",occupants);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        // Vanilla supplies no owned vehicle fields for an ordinary new /summon.
        // Any partial vehicle record still takes the strict schema path below.
        if(tag.getAllKeys().stream().noneMatch(key->key.startsWith("Vehicle"))){
            setNoGravity(true);setBoundingBox(makeBoundingBox());syncVehicleState();return;
        }
        boolean valid=tag.getInt("VehicleSchemaVersion")==1 && chassisId().equals(tag.getString("VehicleChassis")) && faction.id().equals(tag.getString("VehicleFaction"));
        float hp=tag.getFloat("VehicleHealth");mechanicalHealth=valid && Float.isFinite(hp)?net.minecraft.util.Mth.clamp(hp,0,maxHealth()):0;
        try { inventory=valid?readInventory(tag.getCompound("VehicleInventory")):new VehicleInventory(definition); }
        catch (IllegalArgumentException failure) { inventory=new VehicleInventory(definition); }
        float yaw=tag.getFloat("VehicleTurretYaw"),pitch=tag.getFloat("VehicleGunPitch");turretYaw=Float.isFinite(yaw)?net.minecraft.util.Mth.wrapDegrees(yaw):0;gunPitch=Float.isFinite(pitch)?net.minecraft.util.Mth.clamp(pitch,-25,15):0;
        selectedPrimary=tag.getString("VehicleSelectedPrimary");if(!definition.weapons().containsKey(selectedPrimary)||selectedPrimary.equals(secondaryWeapon()))selectedPrimary=defaultPrimary();
        nativeOrigin=tag.getString("VehicleNativeOrigin");nativeDimension=tag.getString("VehicleNativeDimension");currentHome=tag.getString("VehicleCurrentHome");
        if(nativeOrigin.length()>256||nativeDimension.length()>256){nativeOrigin="";nativeDimension="";}if(currentHome.length()>256)currentHome="";
        seats=new VehicleSeats(definition);authority=null;leases.clear();previousActions.clear();restoredSeatHints.clear();leaseRevision=-1;
        var hints=tag.getCompound("VehicleSeatHints");for(var seat:definition.seats())if(hints.hasUUID(seat.id()))restoredSeatHints.put(hints.getUUID(seat.id()),seat.id());
        playerOwner=tag.hasUUID("VehiclePlayerOwner")?tag.getUUID("VehiclePlayerOwner"):null;
        destroyed=false;setNoGravity(true);setBoundingBox(makeBoundingBox());syncVehicleState();
    }
}
