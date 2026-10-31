package com.codex.blacklight.infection;

import com.codex.blacklight.city.*;
import com.codex.blacklight.config.GameplayBalanceConfig;
import com.codex.blacklight.entity.BlacklightInfectedHost;
import com.codex.blacklight.registry.*;
import com.codex.blacklight.world.TissueAttachmentBlock;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
@EventBusSubscriber(modid="blacklight")
public final class WorldOutbreakSystem {
    private static final java.util.Map<ServerLevel,BirthWindow> BIRTHS=new java.util.WeakHashMap<>();
    private static final java.util.Map<ServerLevel,Long> PROMOTED_AT=new java.util.WeakHashMap<>();
    private static final java.util.Map<ServerLevel,Integer> PROMOTED_AFTER=new java.util.WeakHashMap<>();
    private static final class BirthWindow {
        long window;int births;
        // EntityJoinLevel fires before the new mob is visible in the level. Keep
        // its slot reserved while other producers run inside that callback.
        final java.util.Map<Mob,Boolean> pending=new java.util.IdentityHashMap<>();
        BirthWindow(long window){this.window=window;}
    }
    private static BirthWindow births(ServerLevel level){
        long window=Math.floorDiv(level.getGameTime(),OutbreakConfig.INTERVAL.get());
        var value=BIRTHS.computeIfAbsent(level,l->new BirthWindow(window));
        if(value.window!=window){value.window=window;value.births=0;}
        return value;
    }
    private WorldOutbreakSystem(){}
    public static boolean active(ServerLevel level){return OutbreakConfig.ENABLED.get()&&OutbreakData.get(level.getServer()).active();}
    public static int stage(ServerLevel level){return OutbreakRules.stage(OutbreakData.get(level.getServer()).elapsed(level.getServer().overworld().getGameTime()),OutbreakConfig.DAY_TICKS.get());}
    public static void release(ServerLevel level,BlockPos pos){
        if(!OutbreakConfig.ENABLED.get())return;
        if(OutbreakData.get(level.getServer()).start(level.getServer().overworld().getGameTime()))
            for(var p:level.getServer().getPlayerList().getPlayers())p.sendSystemMessage(Component.translatable("message.blacklight.outbreak_started"));
        CityEcology.release(level,pos);seed(level,pos);
    }
    public static boolean sanctuary(ServerLevel level,BlockPos pos){
        if(!CityTravel.isCity(level.dimension()))return false;
        return CitySystem.futian(level)?FutianMap.inside(pos.getX(),pos.getZ())&&FutianLayout.sanctuary(pos.getX(),pos.getZ()):
                CityLayout.inside(pos.getX(),pos.getZ())&&CityLayout.sanctuary(Math.floorDiv(pos.getX(),64),Math.floorDiv(pos.getZ(),64));
    }
    private static boolean nativeMob(Mob mob){return BlacklightInfectionSystem.isInfected(mob);}
    public static OutbreakRules.Tier tierForPopulation(ServerLevel level,BlockPos pos,int roll){
        return tierForPopulation(level,pos,Math.floorMod(roll,100)/100D);
    }
    public static OutbreakRules.Tier tierForPopulation(ServerLevel level,BlockPos pos,double roll){
        var local=level.getEntitiesOfClass(Mob.class,new AABB(pos).inflate(96),m->m.isAlive()&&nativeMob(m));
        int common=0,developed=0,mature=0,ancient=0;
        for(var mob:local){
            if(mob instanceof com.codex.blacklight.entity.BlacklightCommonInfected)common++;
            if(mob instanceof BlacklightInfectedHost host&&!host.hasMultipartBody()){
                developed++;if(host.tissueAge()>=TissueGrowth.MATURITY_TICKS)mature++;
                if(host.tissueAge()>=TissueGrowth.ANCIENT_TICKS)ancient++;
            }
        }
        return OutbreakRules.populationTier(CityEcology.stage(level,pos),common,developed,mature,ancient,
                OutbreakConfig.COMMON_RESERVE.get(),CityEcology.populationCap(level,pos),roll);
    }
    /** Repair saturated early cohorts without culling, adding population, or awarding captures. */
    public static BlacklightInfectedHost developSurplusCommon(ServerLevel level,BlockPos pos){
        if(!GameplayBalanceConfig.LINEAGE_GUARANTEES.get()||!level.getServer().isSameThread()||!active(level)||level.getDifficulty()==Difficulty.PEACEFUL
                ||!level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)||sanctuary(level,pos)||!ready(level,pos)
                ||!level.isPositionEntityTicking(pos)||PROMOTED_AT.getOrDefault(level,Long.MIN_VALUE)==level.getGameTime()
                ||CityTravel.isCity(level.dimension())&&(!CityConfig.SPAWNS.get()||!CityData.get(level).population))return null;
        var local=level.getEntitiesOfClass(Mob.class,new AABB(pos).inflate(96),m->m.isAlive()&&nativeMob(m));
        var common=local.stream().filter(m->m instanceof com.codex.blacklight.entity.BlacklightCommonInfected).map(m->(com.codex.blacklight.entity.BlacklightCommonInfected)m).toList();
        long developed=local.stream().filter(m->m instanceof BlacklightInfectedHost host&&!host.hasMultipartBody()).count();
        int reserve=Math.min(OutbreakConfig.COMMON_RESERVE.get(),OutbreakConfig.LOCAL_CAP.get());
        if(common.size()<=reserve||developed>=OutbreakRules.lineageFloor(CityEcology.stage(level,pos),reserve,OutbreakConfig.LOCAL_CAP.get()))return null;
        var candidates=common.stream().filter(candidate->candidate.tickCount>=200&&!candidate.isNoAi()&&!candidate.hasCustomName()&&candidate.getPersistentData().getBoolean("blacklight:outbreak_spawn"))
                .sorted(java.util.Comparator.comparingInt(Entity::getId)).toList();
        if(!candidates.isEmpty()){
            int after=PROMOTED_AFTER.getOrDefault(level,Integer.MIN_VALUE);
            var candidate=candidates.stream().filter(m->m.getId()>after).findFirst().orElse(candidates.get(0));
            // Reserve before the real join event, including vetoed attempts and reentrant callbacks.
            PROMOTED_AT.put(level,level.getGameTime());
            PROMOTED_AFTER.put(level,candidate.getId());
            return candidate.promote();
        }
        return null;
    }
    /** All scheduled brood and ambient births share the actual loaded population. */
    public static boolean populationRoom(ServerLevel level,BlockPos pos){
        if(!active(level)||level.getDifficulty()==Difficulty.PEACEFUL||sanctuary(level,pos)
                ||!level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)
                ||births(level).births+pendingInfected(level)>=OutbreakConfig.BATCH.get())return false;
        if(CityTravel.isCity(level.dimension())&&(!CityConfig.SPAWNS.get()||!CityData.get(level).population))return false;
        int infected=0,mobs=0;
        for(var entity:level.getAllEntities())if(entity instanceof Mob mob&&mob.isAlive()){
            mobs++;if(nativeMob(mob))infected++;
        }
        var area=new AABB(pos).inflate(96);int localPending=0;
        for(var entry:births(level).pending.entrySet())if(unpublished(level,entry.getKey())){
            mobs++;if(entry.getValue()){infected++;if(entry.getKey().getBoundingBox().intersects(area))localPending++;}
        }
        if(infected>=OutbreakConfig.DIMENSION_CAP.get()
                ||CityTravel.isCity(level.dimension())&&com.codex.blacklight.city.CityPopulationRules.slots(mobs,infected,CityConfig.MOB_LIMIT.get(),OutbreakConfig.DIMENSION_CAP.get(),true)==0)return false;
        return localPending+level.getEntitiesOfClass(Mob.class,area,m->m.isAlive()&&nativeMob(m)).size()<CityEcology.populationCap(level,pos);
    }
    /** Includes unpublished reservations held through real entity-join callbacks. */
    public static int cityPopulationSlots(ServerLevel level,boolean infectedBirth){
        int total=0,infected=0;
        for(var entity:level.getAllEntities())if(entity instanceof Mob mob&&mob.isAlive()){total++;if(nativeMob(mob))infected++;}
        for(var entry:births(level).pending.entrySet())if(unpublished(level,entry.getKey())){total++;if(entry.getValue())infected++;}
        return com.codex.blacklight.city.CityPopulationRules.slots(total,infected,CityConfig.MOB_LIMIT.get(),OutbreakConfig.DIMENSION_CAP.get(),infectedBirth);
    }
    private static boolean unpublished(ServerLevel level,Mob mob){return mob.isAlive()&&!mob.isRemoved()&&level.getEntity(mob.getUUID())!=mob;}
    private static int pendingInfected(ServerLevel level){int count=0;for(boolean infected:births(level).pending.values())if(infected)count++;return count;}
    /** All scheduled producers commit or release the same slot around the real join event. */
    public static boolean addScheduled(ServerLevel level,Mob mob){
        if(!level.getServer().isSameThread()||mob==null||mob.level()!=level||!mob.isAlive()||mob.isRemoved()
                ||level.getEntity(mob.getUUID())!=null||!level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING))return false;
        var box=mob.getBoundingBox();
        if(!level.isInWorldBounds(mob.blockPosition())||!level.getWorldBorder().isWithinBounds(box))return false;
        int minX=net.minecraft.util.Mth.floor(box.minX)>>4,maxX=net.minecraft.util.Mth.floor(box.maxX)>>4;
        int minZ=net.minecraft.util.Mth.floor(box.minZ)>>4,maxZ=net.minecraft.util.Mth.floor(box.maxZ)>>4;
        if(maxX-minX>8||maxZ-minZ>8)return false;
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++)if(level.getChunkSource().getChunkNow(x,z)==null)return false;
        var window=births(level);if(window.pending.containsKey(mob))return false;
        boolean infected=nativeMob(mob);
        if(infected){if(!populationRoom(level,mob.blockPosition()))return false;}
        else if(CityTravel.isCity(level.dimension())){
            if(!CityConfig.SPAWNS.get()||!CityData.get(level).population)return false;
            if(cityPopulationSlots(level,false)==0)return false;
        }
        window.pending.put(mob,infected);boolean accepted=false;
        try{accepted=level.addFreshEntity(mob);return accepted;}
        finally{window.pending.remove(mob);if(accepted&&infected)births(level).births++;}
    }
    private static EntityType<? extends Mob> hostSpecies(int roll){
        return switch(Math.floorMod(roll,10)){
            case 0->EntityType.COW;case 1->EntityType.SHEEP;case 2->EntityType.SPIDER;
            case 3->EntityType.PIG;case 4->EntityType.CHICKEN;case 5->EntityType.RABBIT;
            case 6->EntityType.GOAT;case 7->EntityType.WOLF;case 8->EntityType.SKELETON;
            default->EntityType.ZOMBIE;
        };
    }
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.HIGH)
    public static void naturalGuard(net.minecraftforge.event.entity.EntityJoinLevelEvent event){
        if(!(event.getLevel() instanceof ServerLevel level)||event.loadedFromDisk()
                ||!(event.getEntity() instanceof Mob mob)||mob instanceof com.codex.blacklight.entity.BlacklightHive
                ||!mob.getPersistentData().getBoolean("BlacklightColossalHive"))return;
        // Chunk decoration can run off-thread. Never read SavedData there;
        // the loaded-area scheduler can populate this dormant building later.
        if(!level.getServer().isSameThread()||level.getServer().overworld()==null||!active(level)
                ||!level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)||level.getDifficulty()==Difficulty.PEACEFUL
                ||sanctuary(level,mob.blockPosition())
                ||mob.getPersistentData().getBoolean("BlacklightDevourer")&&CityEcology.stage(level,mob.blockPosition())<3)event.setCanceled(true);
    }
    private static boolean ready(ServerLevel level,BlockPos pos){
        if(!level.isInWorldBounds(pos)||!level.getWorldBorder().isWithinBounds(pos))return false;
        for(int x:new int[]{-1,1})for(int z:new int[]{-1,1})if(level.getChunkSource().getChunkNow((pos.getX()+x)>>4,(pos.getZ()+z)>>4)==null)return false;
        return true;
    }
    public static void seed(ServerLevel level,BlockPos pos){
        if(!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)||!BuildingInfectionConfig.ENABLED.get()||sanctuary(level,pos)||!ready(level,pos))return;
        for(var p:new BlockPos[]{pos,pos.above(),pos.below(),pos.east(2),pos.west(2),pos.north(2),pos.south(2)}){
            if(!ready(level,p)||!level.getBlockState(p).canBeReplaced()||level.getBlockState(p).hasBlockEntity()||!level.getFluidState(p).isEmpty()
                    ||!level.getBlockState(p.below()).isFaceSturdy(level,p.below(),Direction.UP))continue;
            // A newly born host can occupy the seeding point. Growing a solid
            // root inside its feet would trap the creature immediately.
            if(!level.getEntitiesOfClass(LivingEntity.class,new AABB(p),LivingEntity::isAlive).isEmpty())continue;
            if(NaturalInfectionPlacement.place(level,p,ModBlocks.TISSUE_ATTACHMENT.get().defaultBlockState().setValue(TissueAttachmentBlock.VARIANT,level.random.nextInt(3))))
                BuildingInfectionSystem.considerNaturalCore(level,p,level.random);
            return;
        }
    }
    /** Bounded caller-selected spawn; still validates real support, fluid and whole-body collision. */
    public static Mob spawnAt(ServerLevel level,BlockPos pos,OutbreakRules.Tier tier){
        return spawnAt(level,pos,tier,false);
    }
    public static Mob spawnFromBuilding(ServerLevel level,BlockPos pos,OutbreakRules.Tier tier){return spawnAt(level,pos,tier,false,true);}
    public static Mob spawnHydraAt(ServerLevel level,BlockPos pos){return CityEcology.stage(level,pos)<3?null:spawnAt(level,pos,OutbreakRules.Tier.ANCIENT,true);}
    private static Mob spawnAt(ServerLevel level,BlockPos pos,OutbreakRules.Tier tier,boolean hydra){
        return spawnAt(level,pos,tier,hydra,false);
    }
    private static Mob spawnAt(ServerLevel level,BlockPos pos,OutbreakRules.Tier tier,boolean hydra,boolean requireExit){
        if(!active(level)||level.getDifficulty()==Difficulty.PEACEFUL||!level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)
                ||sanctuary(level,pos)||!ready(level,pos)||!populationRoom(level,pos)
                ||!level.getBlockState(pos.below()).isFaceSturdy(level,pos.below(),Direction.UP))return null;
        Mob mob;
        if(hydra)mob=ModEntities.BLACKLIGHT_HYDRA.get().create(level);
        else if(tier==OutbreakRules.Tier.HUNTER)mob=ModEntities.BLACKLIGHT_HUNTER.get().create(level);
        else if(tier==OutbreakRules.Tier.FRESH)mob=ModEntities.COMMON_INFECTED.get().create(level);
        else{
            var host=ModEntities.INFECTED_HOST.get().create(level);if(host==null)return null;
            // Growth fits the body to its surroundings. Fit at the real birth
            // location, never the entity constructor's temporary world origin.
            host.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);
            EntityType<? extends Mob> species=hostSpecies(level.random.nextInt(10));
            Mob donor=species.create(level);if(donor==null)return null;host.inherit(donor);host.advanceTissue(OutbreakRules.tissueAge(tier));mob=host;
        }
        if(mob==null)return null;mob.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,level.random.nextFloat()*360,0);
        var box=mob.getBoundingBox();int minX=net.minecraft.util.Mth.floor(box.minX)>>4,maxX=net.minecraft.util.Mth.floor(box.maxX)>>4;
        int minZ=net.minecraft.util.Mth.floor(box.minZ)>>4,maxZ=net.minecraft.util.Mth.floor(box.maxZ)>>4;
        if(maxX-minX>8||maxZ-minZ>8)return null;
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++)if(level.getChunkSource().getChunkNow(x,z)==null)return null;
        if(!level.noCollision(mob)||level.containsAnyLiquid(mob.getBoundingBox())){
            com.codex.blacklight.BlacklightNeoMod.LOGGER.debug("Outbreak spawn rejected tier={} pos={} body={} collision={} fluid={}",tier,pos,mob.getBoundingBox(),!level.noCollision(mob),level.containsAnyLiquid(mob.getBoundingBox()));
            return null;
        }
        if(requireExit&&!hasWalkout(level,mob))return null;
        mob.getPersistentData().putBoolean("blacklight:outbreak_spawn",true);
        return addScheduled(level,mob)?mob:null;
    }
    /** A structural hatch cannot publish into a body-sized sealed pocket. No chunk requests. */
    public static boolean hasWalkout(ServerLevel level,Mob mob){
        double distance=Math.max(2,Math.min(4,mob.getBbWidth()+.5));
        for(var direction:Direction.Plane.HORIZONTAL){
            double dx=direction.getStepX()*distance,dz=direction.getStepZ()*distance;var swept=mob.getBoundingBox().expandTowards(dx,0,dz);
            if(!level.getWorldBorder().isWithinBounds(swept))continue;
            boolean loaded=true;for(int x=net.minecraft.util.Mth.floor(swept.minX)>>4;x<=net.minecraft.util.Mth.floor(swept.maxX)>>4;x++)for(int z=net.minecraft.util.Mth.floor(swept.minZ)>>4;z<=net.minecraft.util.Mth.floor(swept.maxZ)>>4;z++)if(level.getChunkSource().getChunkNow(x,z)==null)loaded=false;
            if(!loaded||!level.noCollision(mob,swept)||level.containsAnyLiquid(swept))continue;
            boolean supported=true;for(double step=.5;step<=distance;step+=.5){var floor=BlockPos.containing(mob.getX()+direction.getStepX()*step,mob.getY()-.05,mob.getZ()+direction.getStepZ()*step);if(!level.getBlockState(floor).isFaceSturdy(level,floor,Direction.UP)){supported=false;break;}}
            if(supported)return true;
        }return false;
    }
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.LevelTickEvent event){if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        if(!(event.level instanceof ServerLevel level)||!active(level)||level.players().isEmpty()
                ||level.getGameTime()%OutbreakConfig.INTERVAL.get()!=0||level.getDifficulty()==Difficulty.PEACEFUL
                ||!level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING))return;
        if(CityTravel.isCity(level.dimension())&&(!CityConfig.SPAWNS.get()||!CityData.get(level).population))return;
        int total=0,allMobs=0;for(var e:level.getAllEntities())if(e instanceof Mob m&&m.isAlive()){allMobs++;if(nativeMob(m))total++;}
        var players=level.players().stream().filter(p->p.isAlive()&&!p.isSpectator()).toList();if(players.isEmpty())return;
        CityEcology.advanceBatch(level,players.stream().map(Entity::blockPosition).toList());
        int births=0,offset=level.random.nextInt(players.size());
        var visited=new java.util.HashSet<CityEcologyData.Key>();
        for(int index=0;index<players.size()&&births<OutbreakConfig.BATCH.get();index++){
            var player=players.get((index+offset)%players.size());
            if(!visited.add(CityEcology.key(level,player.blockPosition())))continue;
            var local=level.getEntitiesOfClass(Mob.class,player.getBoundingBox().inflate(96),m->m.isAlive()&&nativeMob(m));
            if(developSurplusCommon(level,player.blockPosition())!=null)
                local=level.getEntitiesOfClass(Mob.class,player.getBoundingBox().inflate(96),m->m.isAlive()&&nativeMob(m));
            if(total>=OutbreakConfig.DIMENSION_CAP.get()
                    ||CityTravel.isCity(level.dimension())&&com.codex.blacklight.city.CityPopulationRules.slots(allMobs,total,CityConfig.MOB_LIMIT.get(),OutbreakConfig.DIMENSION_CAP.get(),true)==0)continue;
            if(local.size()>=CityEcology.populationCap(level,player.blockPosition()))continue;
            long common=local.stream().filter(m->m instanceof com.codex.blacklight.entity.BlacklightCommonInfected).count();
            int developed=0,mature=0,ancient=0;
            for(var mob:local)if(mob instanceof BlacklightInfectedHost host&&!host.hasMultipartBody()){
                developed++;if(host.tissueAge()>=TissueGrowth.MATURITY_TICKS)mature++;
                if(host.tissueAge()>=TissueGrowth.ANCIENT_TICKS)ancient++;
            }
            int playerBirths=0,quota=GameplayBalanceConfig.birthsPerPlayer(CityEcology.zone(level,player.blockPosition()));
            for(int attempt=0;attempt<GameplayBalanceConfig.SPAWN_ATTEMPTS.get()&&playerBirths<quota;attempt++){
            int minimum=GameplayBalanceConfig.SPAWN_MIN.get(),maximum=Math.max(minimum,GameplayBalanceConfig.SPAWN_MAX.get());
            double angle=level.random.nextDouble()*Math.PI*2;int distance=minimum+level.random.nextInt(maximum-minimum+1);
            int x=player.getBlockX()+(int)Math.round(Math.cos(angle)*distance),z=player.getBlockZ()+(int)Math.round(Math.sin(angle)*distance);
            var chunk=level.getChunkSource().getChunkNow(x>>4,z>>4);if(chunk==null)continue;
            var near=new BlockPos(x,player.getBlockY(),z);
            BlockPos pos=ready(level,near)&&level.getBlockState(near).isAir()&&level.getBlockState(near.below()).isFaceSturdy(level,near.below(),Direction.UP)
                    ?near:new BlockPos(x,chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x&15,z&15)+1,z);
            int safety=GameplayBalanceConfig.SPAWN_SAFETY.get();
            if(!ready(level,pos)||!level.isPositionEntityTicking(pos)||!CityEcology.ambientAllowed(level,pos,level.random.nextInt(100))||Math.abs(pos.getY()-player.getY())>GameplayBalanceConfig.SPAWN_HEIGHT.get()
                    ||players.stream().anyMatch(p->p.distanceToSqr(pos.getCenter())<(double)safety*safety))continue;
            var tier=OutbreakRules.ambientTier(CityEcology.stage(level,pos),(int)common,developed,mature,ancient,
                    OutbreakConfig.COMMON_RESERVE.get(),CityEcology.populationCap(level,pos),level.random.nextDouble());
            if(spawnAt(level,pos,tier)!=null){births++;playerBirths++;total++;allMobs++;if(CityEcology.zone(level,pos)!=DistrictThreat.Zone.GREEN&&level.random.nextDouble()<GameplayBalanceConfig.SEED_CHANCE.get())seed(level,pos);if(births>=OutbreakConfig.BATCH.get())break;
                var newborn=level.getEntitiesOfClass(Mob.class,player.getBoundingBox().inflate(96),m->m.isAlive()&&nativeMob(m));
                common=newborn.stream().filter(m->m instanceof com.codex.blacklight.entity.BlacklightCommonInfected).count();developed=0;mature=0;ancient=0;
                for(var body:newborn)if(body instanceof BlacklightInfectedHost host&&!host.hasMultipartBody()){developed++;if(host.tissueAge()>=TissueGrowth.MATURITY_TICKS)mature++;if(host.tissueAge()>=TissueGrowth.ANCIENT_TICKS)ancient++;}
            }
            }
        }
        // One exposure per scheduled batch; never scan or generate unloaded chunks.
        var player=players.get(offset);int stage=CityEcology.stage(level,player.blockPosition());
        var targets=level.getEntitiesOfClass(Mob.class,player.getBoundingBox().inflate(GameplayBalanceConfig.EXPOSURE_RADIUS.get()),m->BlacklightInfectionSystem.canInfect(m)&&!sanctuary(level,m.blockPosition()));
        if(!targets.isEmpty()&&level.random.nextDouble()<GameplayBalanceConfig.exposureChance(stage)){var target=targets.get(level.random.nextInt(targets.size()));if(CityEcology.ambientAllowed(level,target.blockPosition(),level.random.nextInt(100)))BlacklightInfectionSystem.infect(target,null);}
    }
}
