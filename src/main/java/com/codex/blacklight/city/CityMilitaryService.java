package com.codex.blacklight.city;

import com.codex.blacklight.entity.MilitarySoldier;
import com.codex.blacklight.entity.BlackwatchSquadSystem;
import com.codex.blacklight.military.*;
import com.codex.blacklight.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Main-thread discovery, finite garrisons and reachable physical ammunition stores. */
public final class CityMilitaryService {
    public static final String BASE_TAG="BlacklightMilitaryBase";
    public static final String POST_TAG="BlacklightMilitaryPost";
    public static String register(ServerLevel level,CityMilitaryPlan.Site site){
        return register(level,site,null);
    }
    public static String register(ServerLevel level,CityMilitaryPlan.Site site,CityMilitaryPlan.Checkpoint checkpoint){
        var data=CityMilitaryData.get(level);String id=VehicleGaragePiece.Planner.baseId(site);
        if(data.bases.size()>=2048&&!data.bases.containsKey(id))return "";
        if(!data.bases.containsKey(id)){var base=new CityMilitaryData.Base(id,site);base.checkpoint=checkpoint;data.bases.put(id,base);data.setDirty();}
        return id;
    }
    @SubscribeEvent public static void joined(EntityJoinLevelEvent event){
        if(!event.getLevel().isClientSide&&event.getEntity() instanceof MilitarySoldier soldier){soldier.goalSelector.addGoal(1,new SupplyGoal(soldier));soldier.goalSelector.addGoal(5,new GuardPostGoal(soldier));MilitaryHomeSystem.joined(soldier);}
    }
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.LevelTickEvent event){if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        if(!(event.level instanceof ServerLevel level))return;
        long time=level.getGameTime();
        if(time%20==0){refreshCores(level);MilitaryHomeSystem.tick(level);}
        if(level.players().isEmpty())return;
        // Eight cheap loaded-chunk lookups per tick; never locate/load distant starts.
        var player=level.players().get((int)Math.floorMod(time/169,level.players().size()));
        for(int i=0;i<8;i++){
            int cursor=(int)Math.floorMod(time*8+i,169);int x=(player.getBlockX()>>4)+cursor%13-6,z=(player.getBlockZ()>>4)+cursor/13-6;
            var chunk=level.getChunkSource().getChunkNow(x,z);if(chunk==null)continue;
            for(var start:chunk.getAllStarts().values())if(start.isValid())discoverPieces(level,start.getPieces());
        }
        if(time%10==0&&CityConfig.SPAWNS.get()&&(!CityTravel.isCity(level.dimension())||CityData.get(level).population))maintain(level,level.players().stream().map(p->p.position()).toList());
    }
    /** Discover only saved native pieces. This never adds pieces or places blocks. */
    public static void discoverPieces(ServerLevel level,List<net.minecraft.world.level.levelgen.structure.StructurePiece> pieces){
        for(var piece:pieces)if(piece instanceof CityMilitaryPiece base)register(level,base.site(),base.checkpoint());
        // Base registration comes first even if another implementation reordered pieces.
        for(var piece:pieces)if(piece instanceof VehicleGaragePiece garage){
            var base=MilitaryBaseAccess.find(level,garage.baseId()).orElse(null);
            if(base!=null&&base.faction()==garage.faction())com.codex.blacklight.military.vehicle.VehicleGarageService.registerNatural(level,garage.baseId(),garage.site());
        }
    }
    static void maintain(ServerLevel level,List<Vec3> observers){
        var data=CityMilitaryData.get(level);
        int budget=2,count=loadedGarrisons(level,data),limit=CityConfig.MILITARY_GARRISON_LIMIT.get();if(count>=limit)return;
        for(var base:data.bases.values()){
            if(budget<=0||count>=limit)break;
            if(observers.stream().noneMatch(p->p.distanceToSqr(base.site.x()+base.site.width()/2.,base.site.floor()+1,base.site.z()+base.site.depth()/2.)<128*128))continue;
            observeCore(level,base);if(!base.health.operational()||MilitaryCampaignSystem.disabled(level,base.id))continue;
            var definition=LargeBaseBlueprint.get(base.site.blackwatch());
            for(int slot=0;slot<base.slots.length&&budget>0&&count<limit;slot++){
                if(base.slots[slot]!=null)continue;
                var g=definition.guards().get(slot);var at=postPosition(base,slot);
                if(postBlocker(level,base,slot)!=null)continue;
                MilitarySoldier soldier=guardType(base,slot).create(level);if(soldier==null)continue;
                UUID id=UUID.nameUUIDFromBytes((level.dimension().location()+":"+base.id+":"+slot).getBytes(StandardCharsets.UTF_8));
                if(level.getEntity(id)!=null){base.slots[slot]=id;data.setDirty();count=loadedGarrisons(level,data);continue;}
                soldier.setUUID(id);soldier.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,180+base.site.rotation()*90,0);soldier.setPersistenceRequired();
                var startingRank=slot==0?MilitaryRank.SERGEANT:g.heavy()?MilitaryRank.CORPORAL:MilitaryRank.PRIVATE;
                if(soldier.rank().ordinal()<startingRank.ordinal())soldier.setRank(startingRank);
                soldier.initializeNativeLoadout("base:"+base.id+":"+slot);
                var tag=soldier.saveWithoutId(new CompoundTag());
                tag.putLong("BlackwatchHome",new BlockPos(base.site.x()+base.site.width()/2,base.site.floor()+1,base.site.z()+base.site.depth()/2).asLong());
                tag.putString("BlackwatchHomeDimension",level.dimension().location().toString());soldier.load(tag);
                soldier.getPersistentData().putString(BASE_TAG,base.id);
                soldier.getPersistentData().putLong(POST_TAG,at.asLong());
                MilitaryHomeSystem.nativeHome(level,soldier,base);
                if(!level.noCollision(soldier)||!level.addFreshEntity(soldier))continue;
                base.slots[slot]=id;data.setDirty();budget--;count++;
                UUID unit=UUID.nameUUIDFromBytes(("unit:"+level.dimension().location()+":"+base.id).getBytes(StandardCharsets.UTF_8));
                boolean[] created={false};
                boolean assigned=MilitaryOrganizationSystem.transaction(level,org->{
                    if(org.unit(unit).isEmpty()){
                        if(!org.create(unit,soldier.faction(),MilitaryUnitType.TACTICAL_SQUAD,"base-"+unit.toString().substring(0,8),base.slots.length,
                                new MilitaryOrder.Point(base.site.x()+base.site.width()/2.,base.site.floor()+1,base.site.z()+base.site.depth()/2.)))return false;
                        created[0]=true;
                    }
                    if(!org.assign(unit,id,soldier.rank()))return false;
                    if(created[0]||!MilitaryOrganizationSystem.data(level).commanderManual(unit))MilitaryOrganizationSystem.appointHighest(org,unit);
                    return true;
                });
                if(assigned){
                    if(created[0])MilitaryOrganizationSystem.data(level).setCommanderManual(unit,false);
                    soldier.setUnitId(unit);soldier.setAutomaticUnitJoin(false);
                }
            }
        }
    }
    /** Count authoritative saved slots, not ambient mobs, tags, or unassigned spawn-egg personnel. */
    private static int loadedGarrisons(ServerLevel level,CityMilitaryData data){
        var loaded=new HashSet<UUID>();
        for(var base:data.bases.values())for(var id:base.slots)
            if(id!=null&&level.getEntity(id) instanceof MilitarySoldier soldier&&soldier.isAlive())loaded.add(id);
        for(var entity:level.getAllEntities())if(entity instanceof MilitarySoldier soldier&&soldier.isAlive()&&MilitaryCampaignSystem.ambient(soldier))loaded.add(soldier.getUUID());
        return loaded.size();
    }
    private static BlockPos postPosition(CityMilitaryData.Base base,int slot){
        if(slot<2&&base.checkpoint!=null){int[] point=base.checkpoint.world(slot==0?3:13,6);return new BlockPos(point[0],base.checkpoint.floor()+1,point[1]);}
        var guard=LargeBaseBlueprint.get(base.site.blackwatch()).guards().get(slot);int[] point=base.site.world(guard.x(),guard.z());
        return new BlockPos(point[0],base.site.floor()+guard.y(),point[1]);
    }
    private static net.minecraft.world.entity.EntityType<? extends MilitarySoldier> guardType(CityMilitaryData.Base base,int slot){
        var g=LargeBaseBlueprint.get(base.site.blackwatch()).guards().get(slot);
        var role=slot==0?MilitaryRole.TEAM_LEADER:"command".equals(g.role())?MilitaryRole.OFFICER:"operations".equals(g.role())?MilitaryRole.COMMANDER:MilitaryRole.TROOPER;
        return base.site.blackwatch()?switch(role){
            case TEAM_LEADER->ModEntities.BLACKWATCH_TEAM_LEADER.get();case OFFICER->ModEntities.BLACKWATCH_OFFICER.get();case COMMANDER->ModEntities.BLACKWATCH_COMMANDER.get();
            default->g.heavy()?ModEntities.BLACKWATCH_HEAVY.get():ModEntities.BLACKWATCH_SOLDIER.get();
        }:switch(role){
            case TEAM_LEADER->ModEntities.MILITARY_TEAM_LEADER.get();case OFFICER->ModEntities.MILITARY_OFFICER.get();case COMMANDER->ModEntities.MILITARY_COMMANDER.get();
            default->g.heavy()?ModEntities.MILITARY_HEAVY.get():ModEntities.MILITARY_SOLDIER.get();
        };
    }
    /** Read only: never request a chunk or create an entity merely to explain a blocked post. */
    private static String postBlocker(ServerLevel level,CityMilitaryData.Base base,int slot){
        var at=postPosition(base,slot);
        if(!level.hasChunkAt(at))return "POST_CHUNK_UNLOADED";
        if(!level.isPositionEntityTicking(at))return "POST_NOT_TICKING";
        if(!level.getBlockState(at.below()).isSolid()||!level.getBlockState(at).isAir()||!level.getBlockState(at.above()).isAir())return "POST_BLOCKED";
        return null;
    }
    private static String musterReason(ServerLevel level,CityMilitaryData.Base base,int issued,int count,int limit){
        if(!base.health.operational())return "CORE_"+base.health.label();
        if(issued==base.slots.length)return "COMPLETE";
        if(!CityConfig.SPAWNS.get())return "CONFIG_POPULATION_DISABLED";
        if(CityTravel.isCity(level.dimension())&&!CityData.get(level).population)return "CITY_POPULATION_DISABLED";
        if(count>=limit)return "GARRISON_LIMIT";
        if(level.players().isEmpty())return "NO_PLAYERS";
        if(level.players().stream().noneMatch(p->p.distanceToSqr(base.site.x()+base.site.width()/2.,base.site.floor()+1,base.site.z()+base.site.depth()/2.)<128*128))return "NO_NEARBY_PLAYER";
        var blockers=new LinkedHashSet<String>();
        for(int slot=0;slot<base.slots.length;slot++)if(base.slots[slot]==null){
            var reason=postBlocker(level,base,slot);
            if(reason==null){var at=postPosition(base,slot);if(level.noCollision(null,guardType(base,slot).getDimensions().makeBoundingBox(at.getX()+.5,at.getY(),at.getZ()+.5)))return "READY";reason="POST_COLLISION";}
            blockers.add(reason);
        }
        return String.join("|",blockers);
    }
    @SubscribeEvent public static void left(EntityLeaveLevelEvent event){
        if(!(event.getLevel() instanceof ServerLevel level)||!(event.getEntity() instanceof MilitarySoldier soldier))return;
        MilitaryHomeSystem.left(level,soldier);var reason=soldier.getRemovalReason();if(reason==null||!reason.shouldDestroy())return;
        var origin=MilitaryHomeSystem.originLevel(level,soldier);if(origin==null)return;
        var data=CityMilitaryData.get(origin);var base=data.bases.get(soldier.getPersistentData().getString(BASE_TAG));if(base==null)return;
        for(int slot=0;slot<base.slots.length;slot++)if(soldier.getUUID().equals(base.slots[slot])){base.losses|=1<<slot;data.setDirty();break;}
    }
    public static List<String> status(ServerLevel level,Vec3 observer){
        refreshCores(level);var data=CityMilitaryData.get(level);int count=loadedGarrisons(level,data),limit=CityConfig.MILITARY_GARRISON_LIMIT.get();
        return data.bases.values().stream().sorted(Comparator.comparingDouble(b->observer.distanceToSqr(b.site.x(),b.site.floor(),b.site.z()))).limit(8).map(base->{
            int issued=0,loaded=0,lost=0;boolean engaged=false;for(int i=0;i<base.slots.length;i++){
                if(base.slots[i]!=null){issued++;if(level.getEntity(base.slots[i]) instanceof MilitarySoldier s&&s.isAlive()){loaded++;if(s.validTarget(s.getTarget()))engaged=true;}}
                if((base.losses&(1<<i))!=0)lost++;
            }
            return (base.site.blackwatch()?"Blackwatch":"Military")+" ["+base.site.x()+", "+base.site.floor()+", "+base.site.z()+"] "
                    +"issued "+issued+"/"+base.slots.length+", loaded "+loaded+", lost "+lost+", unloaded "+Math.max(0,issued-loaded-lost)+" state "
                    +(lost==base.slots.length?"FALLEN":engaged?"ENGAGED":loaded>0?"WATCH":issued>lost?"UNLOADED":"MUSTERING")
                    +", muster "+musterReason(level,base,issued,count,limit)+", loaded_garrison "+count+"/"+limit+", command_core "+base.health.label();
        }).toList();
    }
    public static Optional<Vec3> post(MilitarySoldier soldier){
        if(MilitaryCampaignSystem.active(soldier))return Optional.empty();
        if(!(soldier.level() instanceof ServerLevel level)||!soldier.getPersistentData().contains(POST_TAG))return Optional.empty();
        var base=CityMilitaryData.get(level).bases.get(soldier.getPersistentData().getString(BASE_TAG));if(base==null||!MilitaryHomeSystem.originalPostApplies(soldier,base))return Optional.empty();
        if(soldier.unitId()!=null&&MilitaryOrganizationSystem.organization(level).effectiveOrder(soldier.unitId()).isPresent())return Optional.empty();
        return Optional.of(Vec3.atBottomCenterOf(BlockPos.of(soldier.getPersistentData().getLong(POST_TAG))));
    }
    private static final class GuardPostGoal extends Goal {
        private final MilitarySoldier soldier;private int ticks;private Deque<Vec3> waypoints=new ArrayDeque<>();
        GuardPostGoal(MilitarySoldier soldier){this.soldier=soldier;setFlags(EnumSet.of(Flag.MOVE));}
        @Override public boolean canUse(){return soldier.tacticsAvailable()&&soldier.getTarget()==null&&post(soldier).isPresent();}
        @Override public void start(){waypoints=route(soldier,post(soldier).orElse(soldier.position()));ticks=0;}
        @Override public boolean requiresUpdateEveryTick(){return true;}
        @Override public void tick(){post(soldier).ifPresent(point->{if(soldier.position().distanceToSqr(point)>2.25){if(ticks++%20==0){if(waypoints.isEmpty())waypoints=route(soldier,point);follow(soldier,waypoints,.9);}}else soldier.getNavigation().stop();});}
        @Override public void stop(){soldier.getNavigation().stop();}
    }
    /** Door and central-lane waypoints prevent a 16-block local search from targeting
     * the inside of an unrelated building on the straight line to a distant post. */
    static Deque<Vec3> route(MilitarySoldier soldier,Vec3 destination){
        var result=new ArrayDeque<Vec3>();var level=(ServerLevel)soldier.level();
        var base=MilitaryHomeSystem.currentBase(soldier).orElseGet(()->CityMilitaryData.get(level).bases.get(soldier.getPersistentData().getString(BASE_TAG)));
        if(base==null){result.add(destination);return result;}
        for(var source:CityMilitaryData.get(level).bases.values())if(source!=base&&contains(source,soldier.getBlockX(),soldier.getBlockZ())){
            var from=source.site.local(soldier.getBlockX(),soldier.getBlockZ());var definition=LargeBaseBlueprint.get(source.site.blackwatch());var exit=moduleExit(definition,from[0],from[1]);
            if(exit!=null)add(result,source.site,exit[0],exit[1]);add(result,source.site,32,exit==null?from[1]:exit[1]);add(result,source.site,32,source.site.templateDepth()-2);add(result,source.site,32,source.site.templateDepth()+2);break;
        }
        if(loadedBetween(level,soldier.position(),destination)&&level.clip(new ClipContext(soldier.position().add(0,1,0),destination.add(0,1,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,soldier)).getType()==HitResult.Type.MISS){result.add(destination);return result;}
        var site=base.site;var def=LargeBaseBlueprint.get(site.blackwatch());int[] from=site.local(soldier.getBlockX(),soldier.getBlockZ()),to=site.local(net.minecraft.util.Mth.floor(destination.x),net.minecraft.util.Mth.floor(destination.z));
        boolean fromOutside=from[0]<0||from[0]>64||from[1]<0||from[1]>=site.templateDepth(),toOutside=to[0]<0||to[0]>64||to[1]<0||to[1]>=site.templateDepth();
        int[] exit=moduleExit(def,from[0],from[1]);if(exit!=null)add(result,site,exit[0],exit[1]);
        if(fromOutside){add(result,site,32,site.templateDepth()+2);add(result,site,32,site.templateDepth()-2);from=new int[]{32,site.templateDepth()-2};}
        add(result,site,32,exit==null?from[1]:exit[1]);
        if(toOutside){add(result,site,32,site.templateDepth()-2);add(result,site,32,site.templateDepth()+2);}
        else {int[] entry=moduleExit(def,to[0],to[1]);add(result,site,32,entry==null?to[1]:entry[1]);if(entry!=null)add(result,site,entry[0],entry[1]);}
        result.add(destination);return result;
    }
    private static boolean loadedBetween(ServerLevel level,Vec3 from,Vec3 to){
        int minX=net.minecraft.util.Mth.floor(Math.min(from.x,to.x))>>4,maxX=net.minecraft.util.Mth.floor(Math.max(from.x,to.x))>>4;
        int minZ=net.minecraft.util.Mth.floor(Math.min(from.z,to.z))>>4,maxZ=net.minecraft.util.Mth.floor(Math.max(from.z,to.z))>>4;
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++)if(level.getChunkSource().getChunkNow(x,z)==null)return false;return true;
    }
    private static boolean contains(CityMilitaryData.Base base,int x,int z){return x>=base.site.x()&&x<base.site.x()+base.site.width()&&z>=base.site.z()&&z<base.site.z()+base.site.depth();}
    private static int[] moduleExit(LargeBaseBlueprint definition,int x,int z){
        for(var module:definition.modules()){
            int[] b=module.bounds(),e=module.entry();if(x<b[0]||x>b[3]||z<b[2]||z>b[5])continue;
            int[] distances={e[0]-b[0],b[3]-e[0],e[2]-b[2],b[5]-e[2]};int side=0;for(int i=1;i<4;i++)if(distances[i]<distances[side])side=i;
            return switch(side){case 0->new int[]{b[0]-2,e[2]};case 1->new int[]{b[3]+2,e[2]};case 2->new int[]{e[0],b[2]-2};default->new int[]{e[0],b[5]+2};};
        }return null;
    }
    private static void add(Deque<Vec3> route,CityMilitaryPlan.Site site,int x,int z){int[] point=site.world(x,z);route.add(new Vec3(point[0]+.5,site.floor()+1,point[1]+.5));}
    static void follow(MilitarySoldier soldier,Deque<Vec3> route,double speed){while(!route.isEmpty()&&soldier.position().distanceToSqr(route.peek())<2.25)route.remove();if(!route.isEmpty())BlackwatchSquadSystem.navigate(soldier,route.peek(),speed);}
    public static int transfer(ServerLevel level,MilitarySoldier soldier,BlockPos position){
        if(!soldier.equipped()||!soldier.tacticsAvailable()||!level.hasChunkAt(position)||soldier.distanceToSqr(Vec3.atCenterOf(position))>9)return 0;
        var hit=level.clip(new ClipContext(soldier.getEyePosition(),Vec3.atCenterOf(position),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,soldier));
        if(hit.getType()==HitResult.Type.BLOCK&&!hit.getBlockPos().equals(position))return 0;
        if(!(level.getBlockEntity(position) instanceof Container inventory))return 0;
        int transferred=0;
        for(int slot=0;slot<inventory.getContainerSize()&&soldier.ammoDeficit()>0;slot++){
            var stack=inventory.getItem(slot);if(!stack.is(soldier.requiredAmmoItem()))continue;
            int accepted=soldier.receiveAmmo(Math.min(stack.getCount(),soldier.ammoDeficit()));
            if(accepted>0){stack.shrink(accepted);transferred+=accepted;inventory.setChanged();}
        }
        return transferred;
    }
    public static final class SupplyGoal extends Goal {
        private final MilitarySoldier soldier;private BlockPos target;private Vec3 approach;private long retryAt;private int ticks;private Deque<Vec3> waypoints=new ArrayDeque<>();
        SupplyGoal(MilitarySoldier soldier){this.soldier=soldier;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
        @Override public boolean canUse(){
            if(!(soldier.level() instanceof ServerLevel level)||!soldier.equipped()||!soldier.tacticsAvailable()||MilitaryHomeSystem.ordered(soldier)||MilitaryCampaignSystem.active(soldier)||level.getGameTime()<retryAt)return false;
            if(soldier.reserveAmmo()>0&&(soldier.getTarget()!=null||soldier.ammoDeficit()==0))return false;
            var base=MilitaryHomeSystem.currentBase(soldier).orElse(null);if(base==null||!base.health.operational())return false;
            target=null;approach=null;double nearest=128*128;
            for(int[] local:LargeBaseBlueprint.get(base.site.blackwatch()).ammunition()){
                int[] point=base.site.world(local[0],local[2]);var at=new BlockPos(point[0],base.site.floor()+local[1],point[1]);
                if(!level.hasChunkAt(at)||!(level.getBlockEntity(at) instanceof Container box))continue;
                boolean available=false;for(int slot=0;slot<box.getContainerSize();slot++)if(box.getItem(slot).is(soldier.requiredAmmoItem())&&!box.getItem(slot).isEmpty()){available=true;break;}
                double distance=soldier.distanceToSqr(Vec3.atCenterOf(at));
                if(available&&distance<nearest){var accessible=interactionPoint(level,soldier,at);if(accessible!=null){nearest=distance;target=at;approach=accessible;}}
            }
            if(target==null){retryAt=level.getGameTime()+100;return false;}return true;
        }
        @Override public void start(){ticks=0;waypoints=route(soldier,approach);}
        @Override public boolean canContinueToUse(){return target!=null&&supplyTargetValid()&&ticks<640&&soldier.tacticsAvailable()&&soldier.equipped()&&!MilitaryHomeSystem.ordered(soldier)&&soldier.ammoDeficit()>0&&(soldier.getTarget()==null||soldier.reserveAmmo()==0&&soldier.magazineAmmo()==0);}
        @Override public boolean requiresUpdateEveryTick(){return true;}
        @Override public void tick(){
            if(target==null||!supplyTargetValid()){target=null;soldier.getNavigation().stop();return;}
            ticks++;var level=(ServerLevel)soldier.level();
            soldier.getLookControl().setLookAt(target.getX()+.5,target.getY()+.5,target.getZ()+.5);
            if(soldier.distanceToSqr(Vec3.atCenterOf(target))<=9){transfer(level,soldier,target);target=null;return;}
            if(ticks%20==1)follow(soldier,waypoints,1);
        }
        /** A container occupies its own cell. Walk to a loaded ground-level interaction point,
         * never ask the generic height-map fallback to stand on that container or its building roof. */
        private static Vec3 interactionPoint(ServerLevel level,MilitarySoldier soldier,BlockPos box){
            Vec3 nearest=null;double distance=Double.MAX_VALUE;
            // The shared waypoint follower can finish within 1.5 blocks of its final point.
            // Keeping this interaction point within 1.5 of the box center preserves the real 3-block transfer radius.
            for(int[] offset:new int[][]{{1,0},{-1,0},{0,1},{0,-1},{1,1},{-1,1},{1,-1},{-1,-1}}){
                var feet=box.offset(offset[0],0,offset[1]);if(!level.hasChunkAt(feet)||!level.getWorldBorder().isWithinBounds(feet))continue;
                if(!level.getBlockState(feet.below()).isFaceSturdy(level,feet.below(),net.minecraft.core.Direction.UP)||!level.getFluidState(feet).isEmpty()||!level.getFluidState(feet.above()).isEmpty())continue;
                var point=Vec3.atBottomCenterOf(feet);if(point.distanceToSqr(Vec3.atCenterOf(box))>2.25)continue;
                if(!level.noCollision(soldier,soldier.getBoundingBox().move(point.subtract(soldier.position()))))continue;
                if(!loadedBetween(level,point,Vec3.atCenterOf(box)))continue;
                var hit=level.clip(new ClipContext(point.add(0,soldier.getEyeHeight(),0),Vec3.atCenterOf(box),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,soldier));
                if(hit.getType()==HitResult.Type.BLOCK&&!hit.getBlockPos().equals(box))continue;
                double squared=soldier.distanceToSqr(point);if(squared<distance){distance=squared;nearest=point;}
            }
            return nearest;
        }
        private boolean supplyTargetValid(){
            var base=MilitaryHomeSystem.currentBase(soldier).orElse(null);if(base==null||!base.health.operational())return false;
            for(var local:LargeBaseBlueprint.get(base.site.blackwatch()).ammunition()){int[] point=base.site.world(local[0],local[2]);if(target.equals(new BlockPos(point[0],base.site.floor()+local[1],point[1])))return true;}return false;
        }
        @Override public void stop(){soldier.getNavigation().stop();retryAt=soldier.level().getGameTime()+100;target=null;}
    }
    static void refreshCores(ServerLevel level){for(var base:CityMilitaryData.get(level).bases.values())observeCore(level,base);}
    private static void observeCore(ServerLevel level,CityMilitaryData.Base base){
        boolean loaded=true,present=false;
        for(var local:LargeBaseBlueprint.get(base.site.blackwatch()).commandCores()){
            int[] point=base.site.world(local[0],local[2]);var at=new BlockPos(point[0],base.site.floor()+local[1],point[1]);
            if(!level.hasChunkAt(at)){loaded=false;continue;}
            if(level.getBlockState(at).is(com.codex.blacklight.registry.MilitaryFacilityBlocks.COMMAND_TERMINAL.get()))present=true;
        }
        boolean inactive=base.health.inactive();
        if(base.health.observe(level.getGameTime(),loaded,present))CityMilitaryData.get(level).setDirty();
        if(inactive!=base.health.inactive()||base.health.inactive()&&!MilitaryCampaignData.get(level).bases.containsKey(base.id))
            MilitaryCampaignSystem.baseCoreStateChanged(level,base.id,base.health.inactive());
    }
    @SubscribeEvent public static void homeCommands(net.minecraftforge.event.RegisterCommandsEvent event){MilitaryHomeSystem.commands(event.getDispatcher());}
    @SubscribeEvent public static void homeUnload(net.minecraftforge.event.level.LevelEvent.Unload event){if(event.getLevel() instanceof ServerLevel level)MilitaryHomeSystem.unload(level);}
    @SubscribeEvent public static void homeStopped(net.minecraftforge.event.server.ServerStoppedEvent event){MilitaryHomeSystem.stop();}
    private CityMilitaryService(){}
}
