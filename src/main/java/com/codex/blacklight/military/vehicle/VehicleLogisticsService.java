package com.codex.blacklight.military.vehicle;

import com.codex.blacklight.city.MilitaryBaseAccess;
import com.codex.blacklight.entity.BlackwatchSquadSystem;
import com.codex.blacklight.entity.MilitarySoldier;
import com.codex.blacklight.entity.MilitaryVehicle;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;

/** One bounded native worker per garage. The real ItemEntity is the only cargo inventory. */
@EventBusSubscriber(modid="blacklight")
public final class VehicleLogisticsService {
    public static final String JOB_TAG="BlacklightVehicleLogistics",CARGO_TAG="BlacklightVehicleCargo";
    private static final Map<ServerLevel,Map<String,UUID>> ACTIVE=new WeakHashMap<>();
    private static final Map<MilitarySoldier,Boolean> GOALS=new WeakHashMap<>();
    private static final Map<MilitarySoldier,java.lang.ref.WeakReference<ItemEntity>> HELD=new WeakHashMap<>();
    private static CompoundTag job(MilitarySoldier soldier){return soldier.getPersistentData().getCompound(JOB_TAG);}
    private static void goal(MilitarySoldier soldier){if(GOALS.put(soldier,true)==null)soldier.goalSelector.addGoal(1,new CarryGoal(soldier));}
    static Set<UUID> reserved(ServerLevel level){return Set.copyOf(ACTIVE.getOrDefault(level,Map.of()).values());}
    public static boolean servicing(ServerLevel level,MilitaryVehicle vehicle){
        return reserved(level).stream().anyMatch(id->level.getEntity(id) instanceof MilitarySoldier s&&job(s).hasUUID("Vehicle")&&job(s).getUUID("Vehicle").equals(vehicle.getUUID()));
    }
    static boolean request(ServerLevel level,MilitaryVehicle vehicle,VehicleGarageData.Garage garage,BlockPos box,Set<UUID> reserved){
        var active=ACTIVE.computeIfAbsent(level,ignored->new LinkedHashMap<>());
        if(active.containsKey(garage.id()))return servicing(level,vehicle);
        if(active.size()>=8||!(level.getBlockEntity(box) instanceof Container inventory)||!stock(level,vehicle,inventory))return false;
        var excluded=new HashSet<>(reserved);excluded.addAll(active.values());
        var candidates=new LinkedHashMap<UUID,Vec3>();
        MilitaryBaseAccess.idleCrew(level,garage.baseId(),32,excluded).stream().filter(MilitarySoldier::equipped).filter(s->s.ammoDeficit()==0).forEach(s->candidates.put(s.getUUID(),s.position()));
        for(UUID id:VehicleGarageService.prioritizeCrew(candidates,Vec3.atBottomCenterOf(box))){
            if(!(level.getEntity(id) instanceof MilitarySoldier soldier))continue;
            if(interactionPoint(level,soldier,box)==null||soldier.distanceToSqr(vehicle)>256*256)continue;
            var tag=new CompoundTag();tag.putString("Garage",garage.id());tag.putString("Base",garage.baseId());
            tag.putUUID("Vehicle",vehicle.getUUID());tag.putLong("Box",box.asLong());tag.putLong("Until",level.getGameTime()+1200);
            soldier.getPersistentData().put(JOB_TAG,tag);active.put(garage.id(),soldier.getUUID());goal(soldier);return true;
        }
        return false;
    }
    private static int wanted(ServerLevel level,MilitaryVehicle vehicle,net.minecraft.world.item.ItemStack stack){
        int normal=VehicleGarageService.needed(vehicle,stack);if(normal==0)return 0;
        if(!VehicleFleetSystem.returningForSupplies(level,vehicle))return normal;
        String kind=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        return Math.min(normal,VehicleSupplyRules.resupplyDeficit(vehicle.definition(),vehicle.inventory(),vehicle.health(),kind));
    }
    private static boolean stock(ServerLevel level,MilitaryVehicle vehicle,Container inventory){
        for(int slot=0;slot<Math.min(64,inventory.getContainerSize());slot++)if(wanted(level,vehicle,inventory.getItem(slot))>0)return true;
        return false;
    }
    public static boolean hasResumeStock(ServerLevel level,MilitaryVehicle vehicle,String garage){
        var at=VehicleGarageService.supplyBox(level,vehicle,garage);
        return at!=null&&level.hasChunkAt(at)&&level.getBlockEntity(at) instanceof Container box&&stock(level,vehicle,box);
    }
    private static MilitaryVehicle vehicle(MilitarySoldier soldier){
        if(!(soldier.level() instanceof ServerLevel level)||!job(soldier).hasUUID("Vehicle"))return null;
        return level.getEntity(job(soldier).getUUID("Vehicle")) instanceof MilitaryVehicle v?v:null;
    }
    public static ItemEntity carried(MilitarySoldier soldier){
        if(!(soldier.level() instanceof ServerLevel level)||!job(soldier).hasUUID("Cargo"))return null;
        ItemEntity item=level.getEntity(job(soldier).getUUID("Cargo")) instanceof ItemEntity found?found:null;
        if(item==null&&HELD.containsKey(soldier))item=HELD.get(soldier).get();
        return item!=null&&!item.isRemoved()&&item.level()==level&&item.getUUID().equals(job(soldier).getUUID("Cargo"))?item:null;
    }
    private static boolean valid(MilitarySoldier soldier,MilitaryVehicle vehicle){
        if(!(soldier.level() instanceof ServerLevel level)||vehicle==null||!soldier.equipped()||!soldier.tacticsAvailable()||soldier.getTarget()!=null||soldier.ammoDeficit()>0)return false;
        var tag=job(soldier);String garage=tag.getString("Garage");
        return soldier.getUUID().equals(ACTIVE.getOrDefault(level,Map.of()).get(garage))&&level.getGameTime()<=tag.getLong("Until")&&VehicleGarageService.automatic(vehicle)&&vehicle.currentHome().equals(garage)
                &&VehicleFleetSystem.serviceReady(level,vehicle)&&VehicleGarageService.parked(level,vehicle,garage)&&BlockPos.of(tag.getLong("Box")).equals(VehicleGarageService.supplyBox(level,vehicle,garage))
                &&MilitaryBaseAccess.idleCrew(level,tag.getString("Base"),32,Set.of()).contains(soldier);
    }
    /** Cancellation releases the original physical stack locally; no refund or replacement copy. */
    public static void cancel(MilitarySoldier soldier){
        if(!(soldier.level() instanceof ServerLevel level))return;
        var tag=job(soldier);var item=carried(soldier);if(item!=null)release(item);
        var active=ACTIVE.get(level);if(active!=null)active.remove(tag.getString("Garage"),soldier.getUUID());
        HELD.remove(soldier);
        soldier.getPersistentData().remove(JOB_TAG);soldier.getNavigation().stop();
    }
    private static void release(ItemEntity item){
        item.getPersistentData().remove(CARGO_TAG);item.setNoGravity(false);item.setDefaultPickUpDelay();
        if(item.getItem().isEmpty())item.discard();
    }
    static void tick(ServerLevel level){
        var active=ACTIVE.get(level);if(active==null)return;
        for(var entry:List.copyOf(active.entrySet())){
            if(!(level.getEntity(entry.getValue()) instanceof MilitarySoldier soldier)){active.remove(entry.getKey());continue;}
            if(!valid(soldier,vehicle(soldier))){cancel(soldier);continue;}
            var item=carried(soldier);
            if(item!=null){
                if(!item.getPersistentData().hasUUID(CARGO_TAG)||!item.getPersistentData().getUUID(CARGO_TAG).equals(soldier.getUUID())
                        ||item.distanceToSqr(soldier)>16||item.getItem().isEmpty()){cancel(soldier);continue;}
                // Only a nearby, continuously loaded worker moves the original physical bundle.
                item.setDeltaMovement(Vec3.ZERO);item.setPos(soldier.getX(),soldier.getY()+1.1,soldier.getZ());
            }
        }
    }
    public static String diagnostic(ServerLevel level,String garageId){
        var id=ACTIVE.getOrDefault(level,Map.of()).get(garageId);var garage=VehicleGarageService.data(level).garage(garageId).orElse(null);
        if(id==null)return "service=NONE readyWorkers="+(garage==null?List.of():MilitaryBaseAccess.idleCrew(level,garage.baseId(),32,Set.of()).stream().filter(s->s.equipped()&&s.ammoDeficit()==0).limit(6).map(s->s.getUUID()+"@"+s.position()).toList());
        if(!(level.getEntity(id) instanceof MilitarySoldier soldier))return "worker="+id+" UNLOADED";
        var tag=job(soldier);var cargo=carried(soldier);var path=soldier.getNavigation().getPath();
        var goals=soldier.goalSelector.getAvailableGoals().stream().filter(w->w.isRunning()).map(w->w.getPriority()+":"+w.getGoal().getClass().getSimpleName()).toList();
        var route=soldier.goalSelector.getAvailableGoals().stream().filter(w->w.getGoal() instanceof CarryGoal).map(w->{var c=(CarryGoal)w.getGoal();return "running="+w.isRunning()+", destination="+c.destination+", routeSize="+c.route.size()+", next="+c.route.peek()+", ticks="+c.ticks;}).toList();
        return "worker="+id+" pos="+soldier.position()+" valid="+valid(soldier,vehicle(soldier))+" ammoDeficit="+soldier.ammoDeficit()+" target="+soldier.getTarget()+" goals="+goals+" job="+tag+" cargo="+(cargo==null?"NONE":cargo.getUUID()+"/"+cargo.getItem())
                +" boxLOS="+visible(level,soldier,Vec3.atCenterOf(BlockPos.of(tag.getLong("Box"))),BlockPos.of(tag.getLong("Box")))+" route="+route+" navigation="+(path==null?"NONE":path.canReach()+"/"+path.getNextNodeIndex()+"/"+path.getNodeCount());
    }
    private static boolean visible(ServerLevel level,MilitarySoldier soldier,Vec3 target,BlockPos box){
        if(!VehicleKinematics.loaded(level,new AABB(soldier.getEyePosition(),target).inflate(.1)))return false;
        var hit=level.clip(new ClipContext(soldier.getEyePosition(),target,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,soldier));
        return hit.getType()==HitResult.Type.MISS||box!=null&&hit.getBlockPos().equals(box);
    }
    private static Vec3 interactionPoint(ServerLevel level,MilitarySoldier soldier,BlockPos box){
        for(var side:Direction.Plane.HORIZONTAL){
            var feet=box.relative(side);if(!level.hasChunkAt(feet)||!level.hasChunkAt(feet.above())||!level.getWorldBorder().isWithinBounds(feet))continue;
            var point=Vec3.atBottomCenterOf(feet);
            if(level.getBlockState(feet.below()).isFaceSturdy(level,feet.below(),Direction.UP)&&level.getFluidState(feet).isEmpty()
                    &&level.noCollision(soldier,soldier.getBoundingBox().move(point.subtract(soldier.position()))))return point;
        }
        return null;
    }
    private static final class CarryGoal extends Goal {
        final MilitarySoldier soldier;final Deque<Vec3> route=new ArrayDeque<>();Vec3 destination;int ticks;
        CarryGoal(MilitarySoldier soldier){this.soldier=soldier;setFlags(EnumSet.of(Flag.MOVE));}
        @Override public boolean canUse(){return soldier.getPersistentData().contains(JOB_TAG);}
        @Override public boolean canContinueToUse(){return canUse();}
        @Override public boolean requiresUpdateEveryTick(){return true;}
        @Override public void start(){route.clear();destination=null;ticks=0;}
        @Override public void stop(){if(soldier.getPersistentData().contains(JOB_TAG))cancel(soldier);}
        @Override public void tick(){
            var vehicle=vehicle(soldier);if(!valid(soldier,vehicle)){cancel(soldier);return;}
            var level=(ServerLevel)soldier.level();var tag=job(soldier);var box=BlockPos.of(tag.getLong("Box"));
            var cargo=carried(soldier);
            if(tag.hasUUID("Cargo")){
                if(cargo==null)return; // Unknown/unloaded cargo is never reconstructed from metadata.
                if(!cargo.getPersistentData().hasUUID(CARGO_TAG)||!cargo.getPersistentData().getUUID(CARGO_TAG).equals(soldier.getUUID())){cancel(soldier);return;}
                if(cargo.distanceToSqr(soldier)>16){cancel(soldier);return;}
                var body=vehicle.position().add(0,1,0);
                if(com.codex.blacklight.combat.CombatTargeting.distanceSquared(soldier.getEyePosition(),vehicle.getBoundingBox())<=9&&visible(level,soldier,body,null)){
                    int taken=wanted(level,vehicle,cargo.getItem())==0?0:VehicleGarageService.accept(vehicle,cargo.getItem());
                    if(taken>0){
                        cargo.getItem().shrink(taken);vehicle.syncVehicleState();
                        release(cargo);HELD.remove(soldier);tag.remove("Cargo");
                        // Finish this vehicle's finite service before releasing its worker to crew recruitment.
                        if(level.getBlockEntity(box) instanceof Container inventory&&stock(level,vehicle,inventory)){
                            tag.putLong("Until",level.getGameTime()+1200);return;
                        }
                    }
                    cancel(soldier);return;
                }
                var side=VehicleDefinition.rotateYaw(new VehicleDefinition.Vec3(vehicle.definition().width()/2+.9,0,0),vehicle.getYRot());
                walk(vehicle.position().add(side.x(),0,side.z()));
            }else{
                if(!(level.getBlockEntity(box) instanceof Container inventory)){cancel(soldier);return;}
                if(soldier.distanceToSqr(Vec3.atCenterOf(box))<=9&&visible(level,soldier,Vec3.atCenterOf(box),box)){
                    for(int slot=0;slot<Math.min(64,inventory.getContainerSize());slot++){
                        int count=Math.min(8,Math.min(inventory.getItem(slot).getCount(),wanted(level,vehicle,inventory.getItem(slot))));if(count==0)continue;
                        var stack=inventory.removeItem(slot,count);inventory.setChanged();
                        var item=new ItemEntity(level,soldier.getX(),soldier.getY()+1.1,soldier.getZ(),stack);item.setNoGravity(true);item.setNeverPickUp();
                        if(!level.addFreshEntity(item)){
                            var original=inventory.getItem(slot);if(original.isEmpty())inventory.setItem(slot,stack);else original.grow(stack.getCount());
                            inventory.setChanged();cancel(soldier);return;
                        }
                        item.getPersistentData().putUUID(CARGO_TAG,soldier.getUUID());tag.putUUID("Cargo",item.getUUID());
                        // Real pickup starts one bounded delivery stage after the walk to the box.
                        tag.putLong("Until",level.getGameTime()+1200);
                        HELD.put(soldier,new java.lang.ref.WeakReference<>(item));return;
                    }
                    cancel(soldier);return;
                }
                var point=interactionPoint(level,soldier,box);if(point==null){cancel(soldier);return;}walk(point);
            }
        }
        private void walk(Vec3 point){
            if(!VehicleKinematics.loaded(soldier.level(),new AABB(soldier.position(),point).inflate(.5))){cancel(soldier);return;}
            if(destination==null||destination.distanceToSqr(point)>1){
                destination=point;route.clear();var tag=job(soldier);var level=(ServerLevel)soldier.level();
                var garage=VehicleGarageService.data(level).garage(tag.getString("Garage")).orElse(null);
                if(garage!=null){
                    var axis=VehicleDefinition.rotateYaw(new VehicleDefinition.Vec3(1,0,0),-garage.site().rotation()*90);var right=new Vec3(axis.x(),0,axis.z());
                    if(!tag.hasUUID("Cargo")){
                        double shift=vehicle(soldier).definition().width()/2+1;
                        if(!com.codex.blacklight.city.VehicleGaragePiece.bounds(garage.site()).isInside(soldier.blockPosition())){
                            route.addAll(VehicleGarageService.garageApproach(level,soldier,garage,vehicle(soldier).chassisId()));
                            var apron=route.peekLast();route.add(apron.add(right.scale(shift)));route.add(point.add(right.scale(shift-point.subtract(apron).dot(right))));
                        }else{
                            var box=Vec3.atBottomCenterOf(BlockPos.of(tag.getLong("Box")));if(soldier.position().subtract(box).dot(right)<0)shift=-shift;
                            var start=soldier.position();var forward=new Vec3(-right.z,0,right.x);var offset=start.subtract(vehicle(soldier).position());
                            // Native navigation rounds nodes to block centers: clear the end face before turning sideways.
                            if(Math.abs(offset.dot(right))<=vehicle(soldier).definition().width()/2+.5&&Math.abs(offset.dot(forward))>=vehicle(soldier).definition().length()/2){
                                start=start.add(forward.scale(Math.copySign(1,offset.dot(forward))));route.add(start);
                            }
                            route.add(start.add(right.scale(shift-start.subtract(box).dot(right))));
                            route.add(point.add(right.scale(shift-point.subtract(box).dot(right))));
                        }
                    }else route.add(soldier.position().add(right.scale(point.subtract(soldier.position()).dot(right))));
                }
                route.add(point);
            }
            while(!route.isEmpty()&&soldier.distanceToSqr(route.peek())<1)route.remove();
            if(ticks++%10==0&&!route.isEmpty())BlackwatchSquadSystem.navigate(soldier,route.peek(),1);
        }
    }
    @SubscribeEvent public static void joined(EntityJoinLevelEvent event){
        if(!(event.getLevel() instanceof ServerLevel level))return;
        if(event.getEntity() instanceof ItemEntity item&&item.getPersistentData().hasUUID(CARGO_TAG)){
            var carrier=level.getEntity(item.getPersistentData().getUUID(CARGO_TAG));
            if(carrier instanceof MilitarySoldier s&&job(s).hasUUID("Cargo")&&job(s).getUUID("Cargo").equals(item.getUUID()))HELD.put(s,new java.lang.ref.WeakReference<>(item));else release(item);
        }
        if(event.getEntity() instanceof MilitarySoldier soldier&&soldier.getPersistentData().contains(JOB_TAG)){
            // A handoff can bind only the original loaded item; absent cargo is never recreated.
            goal(soldier);ACTIVE.computeIfAbsent(level,ignored->new LinkedHashMap<>()).putIfAbsent(job(soldier).getString("Garage"),soldier.getUUID());
        }
    }
    @SubscribeEvent public static void died(LivingDeathEvent event){if(event.getEntity() instanceof MilitarySoldier s&&!s.level().isClientSide&&s.getPersistentData().contains(JOB_TAG))cancel(s);}
    @SubscribeEvent public static void left(EntityLeaveLevelEvent event){if(event.getEntity() instanceof MilitarySoldier s&&!s.level().isClientSide&&s.getPersistentData().contains(JOB_TAG))cancel(s);}
    @SubscribeEvent public static void stopping(ServerStoppingEvent event){
        for(var level:event.getServer().getAllLevels())for(UUID id:reserved(level))if(level.getEntity(id) instanceof MilitarySoldier s)cancel(s);
        ACTIVE.clear();GOALS.clear();HELD.clear();
    }
    private VehicleLogisticsService() { }
}
