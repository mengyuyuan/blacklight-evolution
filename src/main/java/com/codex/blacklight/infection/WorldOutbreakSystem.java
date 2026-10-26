package com.codex.blacklight.infection;

import com.codex.blacklight.city.*;
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
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@EventBusSubscriber(modid="blacklight")
public final class WorldOutbreakSystem {
    private WorldOutbreakSystem(){}
    public static boolean active(ServerLevel level){return OutbreakConfig.ENABLED.get()&&OutbreakData.get(level.getServer()).active();}
    public static int stage(ServerLevel level){return OutbreakRules.stage(OutbreakData.get(level.getServer()).elapsed(level.getServer().overworld().getGameTime()),OutbreakConfig.DAY_TICKS.get());}
    public static void release(ServerLevel level,BlockPos pos){
        if(!OutbreakConfig.ENABLED.get())return;
        if(OutbreakData.get(level.getServer()).start(level.getServer().overworld().getGameTime()))
            for(var p:level.getServer().getPlayerList().getPlayers())p.sendSystemMessage(Component.translatable("message.blacklight.outbreak_started"));
        seed(level,pos);
    }
    public static boolean sanctuary(ServerLevel level,BlockPos pos){
        if(!CityTravel.isCity(level.dimension()))return false;
        return CitySystem.futian(level)?FutianMap.inside(pos.getX(),pos.getZ())&&FutianLayout.sanctuary(pos.getX(),pos.getZ()):
                CityLayout.inside(pos.getX(),pos.getZ())&&CityLayout.sanctuary(Math.floorDiv(pos.getX(),64),Math.floorDiv(pos.getZ(),64));
    }
    private static boolean nativeMob(Mob mob){return BlacklightInfectionSystem.isInfected(mob);}
    @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.HIGH)
    public static void naturalGuard(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event){
        if(!(event.getLevel() instanceof ServerLevel level)||event.loadedFromDisk()
                ||!(event.getEntity() instanceof Mob mob)||mob instanceof com.codex.blacklight.entity.BlacklightHive
                ||!mob.getPersistentData().getBoolean("BlacklightColossalHive"))return;
        // Chunk decoration can run off-thread. Never read SavedData there;
        // the loaded-area scheduler can populate this dormant building later.
        if(!level.getServer().isSameThread()||level.getServer().overworld()==null||!active(level)
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
            if(CityEcology.stage(level,p)>=1&&BuildingInfectionConfig.NATURAL_SEEDS.get()&&BuildingInfectionSystem.canRegister(level,p)
                    &&NaturalInfectionPlacement.place(level,p,ModBlocks.BUILDING_CORE.get().defaultBlockState()))return;
            if(NaturalInfectionPlacement.place(level,p,ModBlocks.TISSUE_ATTACHMENT.get().defaultBlockState().setValue(TissueAttachmentBlock.VARIANT,level.random.nextInt(3))))
                BuildingInfectionSystem.considerNaturalCore(level,p,level.random);
            return;
        }
    }
    /** Bounded caller-selected spawn; still validates real support, fluid and whole-body collision. */
    public static Mob spawnAt(ServerLevel level,BlockPos pos,OutbreakRules.Tier tier){
        if(!active(level)||level.getDifficulty()==Difficulty.PEACEFUL||!level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)
                ||sanctuary(level,pos)||!ready(level,pos)||!level.getBlockState(pos.below()).isFaceSturdy(level,pos.below(),Direction.UP))return null;
        Mob mob;
        if(tier==OutbreakRules.Tier.HUNTER)mob=ModEntities.BLACKLIGHT_HUNTER.get().create(level);
        else if(tier==OutbreakRules.Tier.FRESH)mob=ModEntities.COMMON_INFECTED.get().create(level);
        else{
            var host=ModEntities.INFECTED_HOST.get().create(level);if(host==null)return null;
            // Growth fits the body to its surroundings. Fit at the real birth
            // location, never the entity constructor's temporary world origin.
            host.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);
            EntityType<? extends Mob> species=switch(level.random.nextInt(6)){case 0->EntityType.COW;case 1->EntityType.SHEEP;case 2->EntityType.SPIDER;case 3->EntityType.PIG;default->EntityType.ZOMBIE;};
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
        mob.getPersistentData().putBoolean("blacklight:outbreak_spawn",true);
        return level.addFreshEntity(mob)?mob:null;
    }
    @SubscribeEvent public static void tick(LevelTickEvent.Post event){
        if(!(event.getLevel() instanceof ServerLevel level)||!active(level)||level.players().isEmpty()
                ||level.getGameTime()%OutbreakConfig.INTERVAL.get()!=0||level.getDifficulty()==Difficulty.PEACEFUL
                ||!level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING))return;
        if(CityTravel.isCity(level.dimension())&&(!CityConfig.SPAWNS.get()||!CityData.get(level).population))return;
        int total=0,allMobs=0;for(var e:level.getAllEntities())if(e instanceof Mob m&&m.isAlive()){allMobs++;if(nativeMob(m))total++;}
        var players=level.players().stream().filter(p->p.isAlive()&&!p.isSpectator()).toList();if(players.isEmpty())return;
        for(var player:players)CityEcology.advance(level,player.blockPosition());
        int births=0,offset=level.random.nextInt(players.size());
        var visited=new java.util.HashSet<CityEcologyData.Key>();
        for(int index=0;index<players.size()&&births<OutbreakConfig.BATCH.get();index++){
            var player=players.get((index+offset)%players.size());
            if(!visited.add(CityEcology.key(level,player.blockPosition())))continue;
            if(total>=OutbreakConfig.DIMENSION_CAP.get()
                    ||CityTravel.isCity(level.dimension())&&allMobs>=CityConfig.MOB_LIMIT.get())break;
            var local=level.getEntitiesOfClass(Mob.class,player.getBoundingBox().inflate(96),m->m.isAlive()&&nativeMob(m));
            if(local.size()>=OutbreakConfig.LOCAL_CAP.get())continue;
            long common=local.stream().filter(m->m instanceof com.codex.blacklight.entity.BlacklightCommonInfected).count();
            for(int attempt=0;attempt<6;attempt++){
            double angle=level.random.nextDouble()*Math.PI*2;int distance=28+level.random.nextInt(37);
            int x=player.getBlockX()+(int)Math.round(Math.cos(angle)*distance),z=player.getBlockZ()+(int)Math.round(Math.sin(angle)*distance);
            var chunk=level.getChunkSource().getChunkNow(x>>4,z>>4);if(chunk==null)continue;
            var near=new BlockPos(x,player.getBlockY(),z);
            BlockPos pos=ready(level,near)&&level.getBlockState(near).isAir()&&level.getBlockState(near.below()).isFaceSturdy(level,near.below(),Direction.UP)
                    ?near:new BlockPos(x,chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x&15,z&15)+1,z);
            if(!ready(level,pos)||!level.isPositionEntityTicking(pos)||Math.abs(pos.getY()-player.getY())>96
                    ||players.stream().anyMatch(p->p.distanceToSqr(pos.getCenter())<24*24))continue;
            var tier=common<Math.min(OutbreakConfig.COMMON_RESERVE.get(),OutbreakConfig.LOCAL_CAP.get())
                    ?OutbreakRules.Tier.FRESH:OutbreakRules.choose(CityEcology.stage(level,pos),level.random.nextInt(100));
            if(spawnAt(level,pos,tier)!=null){births++;total++;allMobs++;if(level.random.nextInt(4)==0)seed(level,pos);break;}
            }
        }
        // One exposure per scheduled batch; never scan or generate unloaded chunks.
        var player=players.get(offset);int stage=CityEcology.stage(level,player.blockPosition());
        var targets=level.getEntitiesOfClass(Mob.class,player.getBoundingBox().inflate(64),m->BlacklightInfectionSystem.canInfect(m)&&!sanctuary(level,m.blockPosition()));
        if(!targets.isEmpty()&&level.random.nextInt(5)<stage+1)BlacklightInfectionSystem.infect(targets.get(level.random.nextInt(targets.size())),null);
    }
}
