package com.codex.blacklight.world;

import com.codex.blacklight.city.CityTravel;
import com.codex.blacklight.city.MotherPlazaStructure;
import com.codex.blacklight.disguise.DisguiseSystem;
import com.codex.blacklight.entity.EncounterKind;
import com.codex.blacklight.entity.EncounterMob;
import com.codex.blacklight.entity.MilitarySoldier;
import com.codex.blacklight.entity.MilitaryVehicle;
import com.codex.blacklight.military.vehicle.VehicleKinematics;
import com.codex.blacklight.registry.ModEntities;
import com.codex.blacklight.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** One local shift at the seeded city plaza. Vehicles keep vanilla entity identity through hijacking. */
public final class FutianDispatch {
    private static final String HOME = "FutianDispatchOrigin";
    private static final String ALERT = "FutianWitnessUntil";
    private static final int[][] STOPS = {{21,69},{21,55},{11,43},{8,34},{8,20}};
    private FutianDispatch() {}

    private static void tell(ServerPlayer player, String key, Object... args) {
        player.displayClientMessage(Component.translatable("survival.blacklight." + key,args),false);
    }

    private static boolean near(Entity entity, BlockPos origin, double radius) {
        return entity.position().distanceToSqr(Vec3.atCenterOf(origin.offset(16,1,40))) < radius*radius;
    }

    public static boolean allowsTarget(MilitarySoldier guard, ServerPlayer player) {
        var data = guard.getPersistentData();
        if (!data.contains(HOME)) return true;
        return near(player,BlockPos.of(data.getLong(HOME)),68) && wanted(player);
    }

    private static boolean wanted(ServerPlayer player) {
        return player.getPersistentData().getLong(ALERT) > player.level().getGameTime();
    }

    private static void alarm(ServerPlayer player) {
        if (!wanted(player)) tell(player,"dispatch_alarm");
        player.getPersistentData().putLong(ALERT,player.level().getGameTime()+120);
    }

    public static void read(ServerLevel level, BlockPos desk, ServerPlayer player) {
        BlockPos origin=desk.offset(-8,-1,-68);
        if (!MotherPlazaStructure.matchesLot(level.getSeed(),origin)) return;
        if(player.isHolding(ModItems.MOTHER_CORE.get())) {
            startPursuit(level,desk,origin,player);
            return;
        }
        if (!DisguiseSystem.militaryClearance(player) || wanted(player)) {
            tell(player,"dispatch_denied");
            if (guards(level,origin).stream().anyMatch(g -> g.distanceToSqr(player)<24*24 && g.hasLineOfSight(player))) alarm(player);
            return;
        }
        remember(player,origin,"dispatch_log");
        var door = origin.offset(2,1,28);
        if (level.hasChunkAt(door) && level.getBlockState(door).is(Blocks.IRON_DOOR)) {
            ((DoorBlock)Blocks.IRON_DOOR).setOpen(player,level,level.getBlockState(door),door,true);
            var pump = level.getBlockEntity(origin.offset(16,1,28));
            if (pump instanceof PumpStation station) {
                station.shift.putLong("DoorUntil",level.getGameTime()+1200);
                station.setChanged();
            }
        }
    }

    private static void startPursuit(ServerLevel level,BlockPos desk,BlockPos origin,ServerPlayer player) {
        if(level.getDifficulty()==Difficulty.PEACEFUL || player.isSpectator()) { tell(player,"peaceful"); return; }
        var data=BossSiteData.get(level);
        if(data.occupied(desk)) { tell(player,"active"); return; }
        // The service street and its drain lie south of the truck, inside the authored dispatch block.
        for(int z : new int[]{54,48,59,42}) {
            var pad=origin.offset(16,1,z);
            if(!BossSiteBlock.clearPad(level,pad,EncounterKind.ELIZABETH_GREENE,null)) continue;
            var boss=com.codex.blacklight.registry.EncounterEntities.CREATURES.get(EncounterKind.ELIZABETH_GREENE).get().create(level);
            if(boss==null) break;
            boss.moveTo(pad.getX()+.5,pad.getY(),pad.getZ()+.5,0,0);
            boss.yBodyRot=boss.yBodyRotO=boss.yHeadRot=0;
            boss.setPersistenceRequired();
            boss.startGreenePursuit();
            data.reserve(desk,boss,player);
            try {
                if(!level.addFreshEntity(boss) || boss.isRemoved())break;
            } finally {
                if(level.getEntity(boss.getUUID())!=boss || boss.isRemoved())data.release(boss);
            }
            if(!player.isCreative())boss.setTarget(player);
            tell(player,"greene_pursuit_started",pad.getX(),pad.getZ());
            return;
        }
        tell(player,"greene_pursuit_blocked");
    }

    private static void remember(ServerPlayer player, BlockPos origin, String clue) {
        BlockPos destination = origin.offset(16,1,28);
        SurvivalGuide.remember(player,"dispatch_intersection",destination);
        tell(player,clue,destination.getX(),destination.getZ());
    }

    private static java.util.List<MilitarySoldier> guards(ServerLevel level, BlockPos origin) {
        return level.getEntitiesOfClass(MilitarySoldier.class,new AABB(origin.offset(16,1,40)).inflate(80),
                s -> s.getPersistentData().contains(HOME) && s.getPersistentData().getLong(HOME)==origin.asLong());
    }

    private static MilitarySoldier deploy(ServerLevel level, BlockPos at, BlockPos origin, boolean commander) {
        MilitarySoldier soldier = (commander ? ModEntities.BLACKWATCH_COMMANDER.get() : ModEntities.BLACKWATCH_SOLDIER.get()).create(level);
        if (soldier == null) return null;
        soldier.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,180,0);
        if (!level.noCollision(soldier,soldier.getBoundingBox())) return null;
        soldier.getPersistentData().putLong(HOME,origin.asLong());
        soldier.restrictTo(origin.offset(16,1,45),58);
        soldier.initializeWeapon();
        soldier.setPersistenceRequired();
        return level.addFreshEntity(soldier) ? soldier : null;
    }

    static void tick(ServerLevel level, PumpStation station) {
        if (level.dimension()!=CityTravel.WORLD || level.getGameTime()%20!=0 || level.getDifficulty()==Difficulty.PEACEFUL) return;
        var origin=station.getBlockPos().offset(-16,-1,-28);
        if (!MotherPlazaStructure.matchesLot(level.getSeed(),origin) || level.players().stream().noneMatch(p -> !p.isSpectator() && near(p,origin,105))) return;
        var shift=station.shift;
        var now=level.getGameTime();
        var door=origin.offset(2,1,28);
        if (shift.contains("DoorUntil") && now>=shift.getLong("DoorUntil") && level.hasChunkAt(door)) {
            if (level.getBlockState(door).is(Blocks.IRON_DOOR))
                ((DoorBlock)Blocks.IRON_DOOR).setOpen(null,level,level.getBlockState(door),door,false);
            shift.remove("DoorUntil"); station.setChanged();
        }
        var guards=guards(level,origin);
        var activeMother=level.getEntitiesOfClass(EncounterMob.class,new AABB(origin.offset(16,1,16)).inflate(36),
                boss -> boss.isAlive() && boss.kind()==EncounterKind.ELIZABETH_GREENE).stream().findFirst();
        activeMother.ifPresent(boss -> {
            for(var guard:guards) if(!guard.isPassenger() && guard.distanceToSqr(boss)<40*40 && guard.hasLineOfSight(boss))
                guard.setTarget(boss);
        });
        if (now>=shift.getLong("NextShift") && level.isPositionEntityTicking(origin.offset(15,1,64))
                && level.isPositionEntityTicking(origin.offset(9,1,66)) && guards.stream().filter(g -> !g.isPassenger()).count()<2) {
            deploy(level,origin.offset(15,1,64),origin,false);
            if (guards.stream().noneMatch(g -> g.commandRole()==com.codex.blacklight.military.MilitaryRole.COMMANDER))
                deploy(level,origin.offset(9,1,66),origin,true);
            shift.putLong("NextShift",now+2400); station.setChanged();
        }
        if (now >= shift.getLong("NextTruck") && java.util.Arrays.stream(STOPS)
                .allMatch(stop -> level.isPositionEntityTicking(origin.offset(stop[0],1,stop[1])))) {
            var existing=shift.hasUUID("Truck")?level.getEntity(shift.getUUID("Truck")):null;
            if (existing==null || existing instanceof MilitaryVehicle v && !v.getPersistentData().contains(HOME)) {
                var truck=ModEntities.BLACKWATCH_TRUCK.get().create(level);
                if (truck!=null) {
                    var start=origin.offset(STOPS[0][0],1,STOPS[0][1]);
                    truck.moveTo(start.getX()+.5,start.getY(),start.getZ()+.5,180,0);
                    if (VehicleKinematics.placementReady(truck)) {
                        var driver=deploy(level,start.east(4),origin,false);
                        if (driver!=null) {
                            truck.inventory().issueInitialLoadout();
                            truck.getPersistentData().putLong(HOME,origin.asLong());
                            truck.getPersistentData().putInt("DispatchStop",1);
                            truck.getPersistentData().putLong("DispatchWait",now+100);
                            truck.setCustomName(Component.translatable("survival.blacklight.dispatch_truck"));
                            if (level.addFreshEntity(truck) && driver.startRiding(truck,true))
                                shift.putUUID("Truck",truck.getUUID());
                            else {truck.discard();driver.discard();}
                        }
                    }
                }
            }
            shift.putLong("NextTruck",now+600); station.setChanged();
        }
        for (var guard:guards) {
            if (guard.getTarget() instanceof ServerPlayer pursued && !allowsTarget(guard,pursued)) {
                guard.setTarget(null);
                guard.getNavigation().stop();
            }
        }
        for (var player:level.players()) {
            if (player.isSpectator() || player.isCreative() || !near(player,origin,68)) continue;
            boolean seized=player.getVehicle() instanceof MilitaryVehicle v && player.getUUID().equals(v.playerOwner());
            for(var guard:guards) {
                if (!guard.hasLineOfSight(player) || guard.distanceToSqr(player)>36*36) continue;
                if (wanted(player) || seized || DisguiseSystem.exposedIdentity(player)) {
                    alarm(player);
                    if (!guard.isPassenger()) {
                        if (activeMother.isEmpty() || guard.getTarget()!=activeMother.get()) guard.setTarget(player);
                    }
                }
            }
        }
    }

    public static void drive(MilitaryVehicle truck, Entity driver) {
        CompoundTag tag=truck.getPersistentData();
        if (!tag.contains(HOME) || !(truck.level() instanceof ServerLevel level)) return;
        if (!(driver instanceof MilitarySoldier) || truck.playerOwner()!=null) {
            tag.remove(HOME);
            return;
        }
        long now=level.getGameTime();
        if (now<tag.getLong("DispatchWait")) return;
        var origin=BlockPos.of(tag.getLong(HOME));
        int index=Mth.clamp(tag.getInt("DispatchStop"),0,STOPS.length-1);
        var stop=origin.offset(STOPS[index][0],1,STOPS[index][1]);
        var delta=Vec3.atBottomCenterOf(stop).subtract(truck.position());
        if (truck.tickCount%10==0) {
            for (var player:level.players()) {
                var notes=player.getPersistentData();
                boolean tracking=notes.hasUUID("DispatchTail") && truck.getUUID().equals(notes.getUUID("DispatchTail"));
                if (tracking && (tag.getBoolean("Returning") || now-notes.getLong("DispatchSeen")>120)) {
                    notes.remove("DispatchTail");
                    tell(player,"dispatch_lost");
                    tracking=false;
                }
                if (tag.getBoolean("Returning") || player.isSpectator() || player.isPassenger()
                        || player.distanceToSqr(truck)>34*34 || !player.hasLineOfSight(truck)) continue;
                if (index==1) {
                    if (!tracking) tell(player,"dispatch_depart");
                    notes.putUUID("DispatchTail",truck.getUUID()); notes.putLong("DispatchSeen",now);
                } else if (tracking) {
                    notes.putLong("DispatchSeen",now);
                    if (index==STOPS.length-1 && delta.horizontalDistanceSqr()<64) {
                        remember(player,origin,"dispatch_roof");
                        notes.remove("DispatchTail");
                    }
                }
            }
        }
        if (delta.horizontalDistanceSqr()<.9) {
            boolean returning=tag.getBoolean("Returning");
            if(index==STOPS.length-1 || index==0) {
                returning=index!=0;
                tag.putBoolean("Returning",returning);
                tag.putLong("DispatchWait",now+(returning?220:140));
                if (!returning) tag.remove("DispatchUnloaded");
            }
            tag.putInt("DispatchStop",index+(returning?-1:1));
            return;
        }
        float heading=(float)Math.toDegrees(Math.atan2(delta.x,delta.z));
        // The tender unloads in a narrow bay. Back out along the same clear lane.
        boolean reverse=tag.getBoolean("Returning");
        float turn=Mth.wrapDegrees(heading+(reverse?180:0)-truck.getYRot());
        double speed=Math.abs(turn)>12?0:Math.min(.21,truck.definition().maxSpeed());
        truck.moveVehicle(reverse?-speed:speed,Mth.clamp(turn,-3,3));
    }

    public static boolean intercept(MilitaryVehicle truck, ServerPlayer player) {
        var tag=truck.getPersistentData();
        if (!tag.contains(HOME) || tag.getBoolean("DispatchUnloaded") || !player.isShiftKeyDown()) return false;
        // The rear hatch holds supplies. The cab interaction remains available for a hijack.
        var offset=player.position().subtract(truck.position());
        double yaw=Math.toRadians(truck.getYRot());
        if (offset.x*Math.sin(yaw)+offset.z*Math.cos(yaw)>-truck.definition().length()*.15) return false;
        tag.putBoolean("DispatchUnloaded",true);
        var circuit=new net.minecraft.world.item.ItemStack(ModItems.MILITARY_CIRCUIT.get());
        if(!player.getInventory().add(circuit)) player.drop(circuit,false);
        if (!DisguiseSystem.militaryClearance(player) && guards(player.serverLevel(),BlockPos.of(tag.getLong(HOME))).stream()
                .anyMatch(g -> g.distanceToSqr(player)<24*24 && g.hasLineOfSight(player))) alarm(player);
        tell(player,"dispatch_cargo");
        return true;
    }
}
