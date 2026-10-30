package com.codex.blacklight.city;

import com.codex.blacklight.infection.BlacklightInfectionSystem;
import com.codex.blacklight.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** All runtime work is server-side, budgeted and restricted to chunks already ticking. */
public final class CitySystem {
    private static final String POPULATION="BlacklightCityPopulation";
    // Seven exterior apertures; front doorway stays clear.
    private static final int[][] PORTS=CityLayout.HATCHES;
    private CitySystem(){}
    @SubscribeEvent public static void placed(net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent event){
        if(!(event.getLevel() instanceof ServerLevel level)||!CityTravel.isCity(level.dimension()))return;
        if(event instanceof net.neoforged.neoforge.event.level.BlockEvent.EntityMultiPlaceEvent multi){
            for(var snapshot:multi.getReplacedBlockSnapshots())protect(level,snapshot.getPos());
        }else protect(level,event.getPos());
    }
    private static void protect(ServerLevel level,BlockPos p){
        if(inside(level,p.getX(),p.getZ())&&p.getY()>=63&&p.getY()<=72){var data=CityData.get(level);if(data.editedBlocks.add(p.asLong()))data.setDirty();}
    }
    public static BlockPos port(long seed,int hive,int index){return new BlockPos(CityLayout.hiveX(seed,hive)*64+PORTS[index][0],65,CityLayout.hiveZ(seed,hive)*64+PORTS[index][1]);}
    public static boolean futian(ServerLevel level){return level.getChunkSource().getGenerator() instanceof BlacklightWorldGenerator||level.getChunkSource().getGenerator() instanceof CityChunkGenerator g&&g.futian();}
    public static BlockPos port(ServerLevel level,int hive,int index){return futian(level)?FutianLayout.hive(level.getSeed(),hive).ports().get(index).pos():port(level.getSeed(),hive,index);}
    private static int cell(ServerLevel level){return futian(level)?FutianMap.CELL:64;}
    private static boolean inside(ServerLevel level,int x,int z){return futian(level)?FutianMap.inside(x,z):CityLayout.inside(x,z);}
    private static int hiveX(ServerLevel level,int hive){return futian(level)?FutianLayout.hive(level.getSeed(),hive).cellX():CityLayout.hiveX(level.getSeed(),hive);}
    private static int hiveZ(ServerLevel level,int hive){return futian(level)?FutianLayout.hive(level.getSeed(),hive).cellZ():CityLayout.hiveZ(level.getSeed(),hive);}
    private static boolean sanctuary(ServerLevel level,int x,int z){return futian(level)?FutianLayout.sanctuary(x*cell(level)+cell(level)/2,z*cell(level)+cell(level)/2):CityLayout.sanctuary(x,z);}
    private static CityLayout.Zone zone(ServerLevel level,int x,int z){return futian(level)?FutianLayout.zone(level.getSeed(),x*cell(level)+cell(level)/2,z*cell(level)+cell(level)/2):CityLayout.zone(level.getSeed(),x,z);}
    private static net.minecraft.world.level.block.state.BlockState original(ServerLevel level,BlockPos p){return futian(level)?FutianLayout.state(level.getSeed(),p.getX(),p.getY(),p.getZ()):CityChunkGenerator.state(level.getSeed(),p.getX(),p.getY(),p.getZ());}
    private static BlockPos populationPoint(ServerLevel level,int x,int z){
        if(!futian(level))return new BlockPos(x*64+32,64,z*64+10);
        int s=cell(level);
        for(int dz:new int[]{0,-48,48,-80,80})for(int dx:new int[]{0,-48,48,-80,80}){
            BlockPos p=new BlockPos(x*s+s/2+dx,64,z*s+s/2+dz);
            if(!inside(level,p.getX(),p.getZ())||!loaded(level,p))continue;
            for(int y=64;y<=68;y++){
                p=new BlockPos(p.getX(),y,p.getZ());
                if(level.getBlockState(p).isAir()&&level.getBlockState(p.above()).isAir()&&level.getBlockState(p.below()).isSolid())return p;
            }
        }
        return null;
    }
    private static boolean loaded(ServerLevel level,BlockPos pos){return level.hasChunkAt(pos)&&level.isPositionEntityTicking(pos);}
    @SubscribeEvent public static void tick(LevelTickEvent.Post event){
        if(!(event.getLevel() instanceof ServerLevel level)||!CityTravel.isCity(level.dimension()))return;
        CityPregenerator.tick(level);
        if(level.players().isEmpty())return;
        long time=level.getGameTime();if(time%20!=0)return;
        updateLoadedCity(level,time);
        if(level.dimension().equals(CityTravel.WORLD)&&time%CityConfig.populationIntervalTicks()==0)populateWorld(level);
    }
    @SubscribeEvent public static void unloaded(net.neoforged.neoforge.event.level.LevelEvent.Unload event){
        if(event.getLevel() instanceof ServerLevel level)CityPregenerator.unload(level);
    }
    static void updateLoadedCity(ServerLevel level,long time){
        CityData data=CityData.get(level);
        for(int hive=0;hive<2;hive++){
            inspectHive(level,data,hive);
            CityHiveSupply.tick(level,data,hive);
            if(data.destroyed(hive)){if(data.facade&&CityConfig.DESTRUCTION.get())woundFacade(level,data,hive);}
            else if(data.population&&CityConfig.SPAWNS.get()&&CityHiveSupply.ready(data,hive))spawnHive(level,data,hive);
        }
        if(time%CityConfig.populationIntervalTicks()==0&&data.population&&CityConfig.SPAWNS.get())populate(level,data);
        if(data.spread&&CityConfig.SPREAD.get()&&level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING)&&com.codex.blacklight.infection.WorldOutbreakSystem.active(level)){
            if(time>=data.nextSpread){expand(level,data);data.nextSpread=time+CityConfig.SPREAD_SECONDS.get()*20L;data.setDirty();}
            paintSpread(level,data);
        }
    }
    private static void inspectHive(ServerLevel level,CityData data,int hive){
        if(data.destroyed(hive))return;
        int old=data.brokenPorts[hive];
        for(int i=0;i<PORTS.length;i++){
            BlockPos p=port(level,hive,i);
            // Missing in an unloaded chunk is never damage. Handles explosions and mod block destruction too.
            // Damage inspection needs a loaded chunk, not an entity-ticking one:
            // a large building may span beyond simulation distance after a ranged blast.
            if(level.hasChunkAt(p)&&!level.getBlockState(p).is(CityBlocks.HATCH.get()))data.brokenPorts[hive]|=1<<i;
            else if(level.hasChunkAt(p)&&level.getBlockState(p).is(CityBlocks.HATCH.get())&&level.getBlockState(p).getValue(CityHatchBlock.OPEN))
                level.setBlock(p,level.getBlockState(p).setValue(CityHatchBlock.OPEN,false),3);
        }
        if(old!=data.brokenPorts[hive])data.setDirty();
        if(Integer.bitCount(data.brokenPorts[hive])==PORTS.length){
            data.destroyedMask|=1<<hive;data.setDirty();
            BlockPos center=port(level,hive,0);
            CityEcology.cleared(level,center);
            level.playSound(null,center,SoundEvents.GENERIC_EXPLODE.value(),SoundSource.HOSTILE,3,.65f);
            for(var player:level.players())if(player.distanceToSqr(center.getX(),65,center.getZ())<160*160)
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("city.blacklight.hive_destroyed"),false);
        }
    }
    static boolean spawn(ServerLevel level,EntityType<? extends Mob> type,BlockPos pos,boolean infected){
        if(!level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING))return false;
        if((infected||type==ModEntities.BLACKLIGHT_HUNTER.get()||type==ModEntities.BLACKLIGHT_HYDRA.get())&&!com.codex.blacklight.infection.WorldOutbreakSystem.active(level))return false;
        if((infected||type==ModEntities.BLACKLIGHT_HUNTER.get()||type==ModEntities.BLACKLIGHT_HYDRA.get())
                &&!com.codex.blacklight.infection.WorldOutbreakSystem.populationRoom(level,pos))return false;
        if(infected&&type==EntityType.ZOMBIE)type=ModEntities.COMMON_INFECTED.get();
        if(!loaded(level,pos)||!level.getBlockState(pos.below()).isSolid()||com.codex.blacklight.infection.WorldOutbreakSystem.cityPopulationSlots(level,infected||type==ModEntities.BLACKLIGHT_HUNTER.get()||type==ModEntities.BLACKLIGHT_HYDRA.get())==0)return false;
        Mob mob=type.create(level);if(mob==null)return false;
        mob.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,level.random.nextFloat()*360,0);
        if(!level.noCollision(mob)||level.containsAnyLiquid(mob.getBoundingBox()))return false;
        mob.finalizeSpawn(level,level.getCurrentDifficultyAt(pos),MobSpawnType.EVENT,null);
        // Finalization may change the body dimensions; validate the complete body again.
        if(!level.noCollision(mob)||level.containsAnyLiquid(mob.getBoundingBox()))return false;
        mob.getPersistentData().putBoolean(POPULATION,true);
        mob.addTag(POPULATION); // Infection replacements preserve vanilla entity tags.
        // Residents persist in their district; hostile vanilla mobs may despawn normally.
        if(type==EntityType.VILLAGER)mob.setPersistenceRequired();
        if(infected){if(!CityEcology.ambientAllowed(level,pos,level.random.nextInt(100)))return false;if(!BlacklightInfectionSystem.isInfected(mob))BlacklightInfectionSystem.infect(mob,null);}
        if(mob instanceof Zombie zombie)zombie.setBaby(false);
        return com.codex.blacklight.infection.WorldOutbreakSystem.addScheduled(level,mob);
    }
    private static void populateWorld(ServerLevel level){
        if(!CityConfig.SPAWNS.get()||!CityData.get(level).population)return;
        // One spawn attempt per five seconds for the entire dimension, only in ticking chunks.
        var player=level.players().get(level.random.nextInt(level.players().size()));
        int x=player.getBlockX()+level.random.nextInt(129)-64,z=player.getBlockZ()+level.random.nextInt(129)-64;
        if(FutianMap.inside(x,z)||!level.isPositionEntityTicking(new BlockPos(x,64,z)))return;
        var column=WorldTerrain.column(level.getSeed(),x,z);
        if(column.road==0||column.water>0||!column.parts.isEmpty())return;
        var chunk=level.getChunkSource().getChunkNow(Math.floorDiv(x,16),Math.floorDiv(z,16));if(chunk==null)return;
        int y=chunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x&15,z&15)+1;
        var p=new BlockPos(x,y,z);
        var zone=CityEcology.zone(level,p);
        if(zone!=DistrictThreat.Zone.RED&&level.getEntitiesOfClass(Mob.class,new AABB(p).inflate(48),m->m.isAlive()&&!BlacklightInfectionSystem.isInfected(m)).size()>=8)return;
        if(zone!=DistrictThreat.Zone.GREEN&&level.getDifficulty()==net.minecraft.world.Difficulty.PEACEFUL)return;
        if(zone==DistrictThreat.Zone.RED){if(!CityEcology.ambientAllowed(level,p,level.random.nextInt(100)))return;com.codex.blacklight.infection.WorldOutbreakSystem.spawnAt(level,p,
                com.codex.blacklight.infection.WorldOutbreakSystem.tierForPopulation(level,p,level.random.nextDouble()));return;}
        EntityType<? extends Mob> type=zone==DistrictThreat.Zone.GREEN?EntityType.VILLAGER:
            level.random.nextBoolean()?ModEntities.BLACKWATCH_SOLDIER.get():EntityType.ZOMBIE;
        spawn(level,type,p,type==EntityType.ZOMBIE);
    }
    private static void spawnHive(ServerLevel level,CityData data,int hive){
        if(!CityHiveSupply.ready(data,hive)||level.getDifficulty()==net.minecraft.world.Difficulty.PEACEFUL||!com.codex.blacklight.infection.WorldOutbreakSystem.active(level))return;
        if(level.random.nextDouble()>=com.codex.blacklight.config.GameplayBalanceConfig.BROOD_CHANCE.get()){data.hiveBroodTicks[hive]=0;data.setDirty();return;}
        int start=level.random.nextInt(PORTS.length);
        for(int j=0;j<PORTS.length;j++){
            int i=(start+j)%PORTS.length;BlockPos port=port(level,hive,i);
            if((data.brokenPorts[hive]&(1<<i))!=0||!loaded(level,port)||!level.getBlockState(port).is(CityBlocks.HATCH.get()))continue;
            if(level.players().stream().noneMatch(p->p.distanceToSqr(port.getX(),65,port.getZ())<96*96))continue;
            var facing=level.getBlockState(port).getValue(CityHatchBlock.FACING);
            BlockPos outside=port.relative(facing,3).below();
            var guards=level.getEntitiesOfClass(Mob.class,new AABB(port).inflate(48),Entity::isAlive);
            boolean hasHunter=guards.stream().anyMatch(e->e instanceof com.codex.blacklight.entity.BlacklightHunter);
            int stage=CityEcology.stage(level,port);
            int common=0,developed=0,mature=0,ancient=0;
            for(var mob:guards){if(mob instanceof com.codex.blacklight.entity.BlacklightCommonInfected)common++;
                if(mob instanceof com.codex.blacklight.entity.BlacklightInfectedHost host&&!host.hasMultipartBody()){
                    developed++;if(host.tissueAge()>=com.codex.blacklight.infection.TissueGrowth.MATURITY_TICKS)mature++;if(host.tissueAge()>=com.codex.blacklight.infection.TissueGrowth.ANCIENT_TICKS)ancient++;
                }}
            var tier=com.codex.blacklight.infection.OutbreakRules.populationTier(stage,common,developed,mature,ancient,com.codex.blacklight.infection.OutbreakConfig.COMMON_RESERVE.get(),com.codex.blacklight.infection.OutbreakConfig.LOCAL_CAP.get(),level.random.nextDouble());
            if(com.codex.blacklight.config.GameplayBalanceConfig.LINEAGE_GUARANTEES.get()&&stage>=2&&!hasHunter&&common>=com.codex.blacklight.infection.OutbreakConfig.COMMON_RESERVE.get())tier=com.codex.blacklight.infection.OutbreakRules.Tier.HUNTER;
            boolean hydra=stage>=3&&hasHunter&&tier==com.codex.blacklight.infection.OutbreakRules.Tier.ANCIENT&&level.random.nextDouble()<CityConfig.HYDRA_CHANCE.get()&&level.getEntitiesOfClass(com.codex.blacklight.entity.BlacklightHydra.class,new AABB(port).inflate(64)).isEmpty();
            if((hydra?CityHiveSupply.hatchHydra(level,data,hive,port):CityHiveSupply.hatch(level,data,hive,port,tier))!=null){
                level.setBlock(port,level.getBlockState(port).setValue(CityHatchBlock.OPEN,true),3);
                level.sendParticles(ParticleTypes.CRIMSON_SPORE,outside.getX()+.5,65,outside.getZ()+.5,16,.7,.6,.7,.02);
                level.playSound(null,port,SoundEvents.SLIME_SQUISH,SoundSource.HOSTILE,1,.6f);
                return;
            }
        }
    }
    private static void populate(ServerLevel level,CityData data){
        // At most one district per five seconds, irrespective of player count.
        for(int z=0;z<8;z++)for(int x=0;x<8;x++){
            long bit=1L<<(x+z*8);if((data.populatedMask&bit)!=0)continue;
            BlockPos center=populationPoint(level,x,z);
            if(center==null||!loaded(level,center)||level.players().stream().noneMatch(p->p.distanceToSqr(center.getX(),center.getY(),center.getZ())<100*100))continue;
            var zone=CityEcology.zone(level,center);
            if(!hasPopulationRoom(level,zone))continue;
            if(level.getDifficulty()==net.minecraft.world.Difficulty.PEACEFUL&&zone!=DistrictThreat.Zone.GREEN)continue;
            int spawned=populateDistrict(level,center,zone);
            if(spawned>0){data.populatedMask|=bit;data.setDirty();}return;
        }
    }
    static boolean hasPopulationRoom(ServerLevel level,DistrictThreat.Zone zone){
        int residents=com.codex.blacklight.infection.WorldOutbreakSystem.cityPopulationSlots(level,false);
        int infected=com.codex.blacklight.infection.WorldOutbreakSystem.cityPopulationSlots(level,true);
        return switch(zone){case GREEN->residents>0;case RED->infected>0;case YELLOW->residents>0||infected>0;};
    }
    /** One bounded initial street cohort, sharing the ordinary publication and population guards. */
    static int populateDistrict(ServerLevel level,BlockPos center,DistrictThreat.Zone zone){
        if(!level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING))return 0;
        if(level.getDifficulty()==net.minecraft.world.Difficulty.PEACEFUL&&zone!=DistrictThreat.Zone.GREEN)return 0;
        if(!hasPopulationRoom(level,zone))return 0;
        int attempts=4,spawned=0;
        for(int i=0;i<attempts;i++){
            var pos=center.offset(i*3-5,0,0);
            if(zone==DistrictThreat.Zone.RED){
                if(CityEcology.ambientAllowed(level,pos,level.random.nextInt(100))
                        &&com.codex.blacklight.infection.WorldOutbreakSystem.spawnAt(level,pos,
                        com.codex.blacklight.infection.WorldOutbreakSystem.tierForPopulation(level,pos,level.random.nextDouble()))!=null)spawned++;
                continue;
            }
            EntityType<? extends Mob> type=zone==DistrictThreat.Zone.GREEN?EntityType.VILLAGER:
                    zone==DistrictThreat.Zone.YELLOW&&i<2?ModEntities.BLACKWATCH_SOLDIER.get():EntityType.ZOMBIE;
            if(spawn(level,type,pos,type==EntityType.ZOMBIE))spawned++;
        }
        return spawned;
    }
    private static void expand(ServerLevel level,CityData data){
        if(Long.bitCount(data.infectedMask)>=12)return;
        for(int hive=0;hive<2;hive++){
            if(data.destroyed(hive))continue;
            BlockPos origin=port(level,hive,0);if(!loaded(level,origin))continue;
            int hx=hiveX(level,hive),hz=hiveZ(level,hive);
            for(int distance=2;distance<=3;distance++)for(int z=0;z<8;z++)for(int x=0;x<8;x++){
                if(sanctuary(level,x,z)||data.infected(x,z)||zone(level,x,z)==CityLayout.Zone.INFECTED||Math.abs(x-hx)+Math.abs(z-hz)!=distance)continue;
                BlockPos p=populationPoint(level,x,z);if(p==null||!loaded(level,p)||CityEcology.zone(level,p)!=DistrictThreat.Zone.RED)continue;
                boolean adjacent=false;for(int[] d:new int[][]{{-1,0},{1,0},{0,-1},{0,1}}){int nx=x+d[0],nz=z+d[1];if(nx>=0&&nx<8&&nz>=0&&nz<8&&(zone(level,nx,nz)==CityLayout.Zone.INFECTED||data.infected(nx,nz)))adjacent=true;}
                if(!adjacent)continue;
                data.infectedMask|=1L<<(x+z*8);data.setDirty();
                int size=cell(level);
                for(var e:level.getEntitiesOfClass(net.minecraft.world.entity.npc.Villager.class,new AABB(x*size,63,z*size,x*size+size,100,z*size+size),v->v.getPersistentData().getBoolean(POPULATION)))
                    if(!futian(level)||!FutianLayout.sanctuary(e.getBlockX(),e.getBlockZ()))BlacklightInfectionSystem.infect(e,null);
                return;
            }
        }
    }
    private static void paintSpread(ServerLevel level,CityData data){
        int budget=32;
        int size=cell(level),total=size*size;
        for(int cell=0;cell<64&&budget>0;cell++){
            int cx=cell%8,cz=cell/8;if(!data.infected(cx,cz)||data.spreadProgress[cell]>=total
                    ||CityEcology.zone(level,new BlockPos(cx*size+size/2,64,cz*size+size/2))!=DistrictThreat.Zone.RED)continue;
            while(data.spreadProgress[cell]<total&&budget>0){
                int i=data.spreadProgress[cell];int x=cx*size+i%size,z=cz*size+i/size;
                BlockPos p=new BlockPos(x,63,z);if(!loaded(level,p))break;
                var current=level.getBlockState(p);
                boolean allowed=futian(level)?inside(level,x,z)&&!FutianLayout.sanctuary(x,z)&&(current.is(Blocks.GRASS_BLOCK)||current.is(Blocks.DIRT)||current.is(Blocks.MOSS_BLOCK)):x%64>=8&&z%64>=8;
                if(allowed&&CityLayout.tissue(level.getSeed(),x,z)>.48&&!data.editedBlocks.contains(p.asLong())&&current.equals(original(level,p)))level.setBlock(p,CityBlocks.FLESH.get().defaultBlockState(),3);
                data.spreadProgress[cell]++;budget--;data.setDirty();
            }
        }
    }
    private static void woundFacade(ServerLevel level,CityData data,int hive){
        if(futian(level)){woundFutian(level,data,hive);return;}
        // Gradual visual collapse of the lower facade. No new explosion, drops or block entity deletion.
        int cx=CityLayout.hiveX(level.getSeed(),hive)*64,cz=CityLayout.hiveZ(level.getSeed(),hive)*64;
        int budget=32,total=4*37*7;
        while(data.facadeProgress[hive]<total&&budget-->0){
            int i=data.facadeProgress[hive],side=i/259,n=(i%259)/7,y=64+i%7;
            int x=cx+(side==0?14:side==1?50:14+n),z=cz+(side==2?14:side==3?50:14+n);
            BlockPos p=new BlockPos(x,y,z);if(!loaded(level,p))return;
            var state=level.getBlockState(p);
            if(!data.editedBlocks.contains(p.asLong())&&(state.is(CityBlocks.FLESH.get())||state.is(CityBlocks.VEIN.get())||state.is(CityBlocks.SAC.get()))&&state.equals(CityChunkGenerator.state(level.getSeed(),x,y,z)))
                level.setBlock(p,CityBlocks.WOUNDED.get().defaultBlockState(),3);
            data.facadeProgress[hive]++;data.setDirty();
        }
    }
    private static void woundFutian(ServerLevel level,CityData data,int hive){
        var site=FutianLayout.hive(level.getSeed(),hive);int width=site.maxX()-site.minX()+1,depth=site.maxZ()-site.minZ()+1;
        int total=width*depth*9,budget=32;
        while(data.facadeProgress[hive]<total&&budget-->0){
            int i=data.facadeProgress[hive],column=i/9;
            BlockPos p=new BlockPos(site.minX()+column%width,64+i%9,site.minZ()+column/width);
            if(!loaded(level,p))return;
            var state=level.getBlockState(p);
            if(!data.editedBlocks.contains(p.asLong())&&(state.is(CityBlocks.FLESH.get())||state.is(CityBlocks.VEIN.get())||state.is(CityBlocks.SAC.get()))&&state.equals(original(level,p)))level.setBlock(p,CityBlocks.WOUNDED.get().defaultBlockState(),3);
            data.facadeProgress[hive]++;data.setDirty();
        }
    }
}
