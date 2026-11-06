package com.codex.blacklight.gametest;

import com.codex.blacklight.config.InfectionEcologyConfig;
import com.codex.blacklight.infection.*;
import com.codex.blacklight.registry.ModBlocks;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;

final class NativeVascularArena implements AutoCloseable {
    static final class StationarySourceGoal extends Goal {
        int ticks;StationarySourceGoal(){setFlags(EnumSet.of(Flag.MOVE));}
        public boolean canUse(){return true;}public boolean canContinueToUse(){return true;}
        public boolean requiresUpdateEveryTick(){return true;}public void tick(){ticks++;}
    }

        final GameTestHelper h;final ServerLevel level;final NativeTestArena arena;final BlockPos origin;
        final BuildingInfectionData data;final BuildingInfectionData.Colony colony;final OutbreakData oldOutbreak;
        final List<BuildingInfectionData.Colony> oldColonies;final Set<Long> oldCleared;
        final List<? extends String> preserve;final boolean grief,spawns,volume;final List<Mob> actors=new ArrayList<>();
        final Map<Mob,StationarySourceGoal> goals=new IdentityHashMap<>();boolean closed;
        NativeVascularArena(GameTestHelper h){
            this.h=h;level=h.getLevel();arena=NativeTestArena.open(h,0,15);data=BuildingInfectionData.get(level);oldColonies=new ArrayList<>(data.colonies);oldCleared=new LinkedHashSet<>(data.clearedSites);data.colonies.clear();data.clearedSites.clear();
            preserve=InfectionEcologyConfig.PRESERVE.get();InfectionEcologyConfig.PRESERVE.set(List.of("minecraft:cow"));
            oldOutbreak=OutbreakData.get(level.getServer());var outbreak=new OutbreakData();outbreak.start(level.getServer().overworld().getGameTime());level.getServer().overworld().getDataStorage().set("blacklight_outbreak",outbreak);
            grief=level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);spawns=level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING);volume=BuildingInfectionConfig.VOLUME.get();BuildingInfectionConfig.VOLUME.set(false);
            level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true,level.getServer());level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,level.getServer());
            for(int x=1;x<=11;x++)for(int z=1;z<=11;z++){h.setBlock(new BlockPos(x,1,z),Blocks.STONE);for(int y=2;y<=8;y++)h.setBlock(new BlockPos(x,y,z),Blocks.AIR);}
            origin=h.absolutePos(new BlockPos(5,2,6));for(int x=1;x<=9;x++)for(int y=2;y<=7;y++)h.setBlock(new BlockPos(x,y,7),Blocks.STONE_BRICKS);
            level.setBlock(origin,ModBlocks.BUILDING_CORE.get().defaultBlockState().setValue(com.codex.blacklight.world.BuildingInfectionCoreBlock.FACING,Direction.NORTH),3);
            colony=data.colonies.stream().filter(c->c.origin.equals(origin)&&!c.dead).findFirst().orElseThrow();colony.frontier.clear();h.runAfterDelay(150,this::close);
        }
        Mob actor(){return actor(EntityType.COW);}
        Mob actor(EntityType<?> type){
            var actor=(Mob)type.create(level);var at=Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(5,2,2)));actor.moveTo(at.x,at.y,at.z,0,0);actor.setOnGround(true);
            actor.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);var goal=new StationarySourceGoal();actor.goalSelector.addGoal(0,goal);goals.put(actor,goal);level.addFreshEntity(actor);actors.add(actor);
            h.assertTrue(BlacklightInfectionSystem.infect(actor,null)&&NativeInfection.active(actor),"source body stays the actual infected type");
            var tag=new CompoundTag();tag.putInt("DevelopmentSchema",1);tag.putLong("Age",216000);tag.putString("Core",TissueCore.VASCULAR.key());tag.putInt("Nutrition",400);
            actor.getPersistentData().put("BlacklightNativeInfection",tag);NativeInfection.tick(actor,level);NativeTissueVascular.forget(actor);
            tag=actor.getPersistentData().getCompound("BlacklightNativeInfection");tag.remove("Vascular");tag.putInt("Nutrition",400);actor.setHealth(actor.getMaxHealth());return actor;
        }
        void start(Mob a){h.assertTrue(NativeTissueVascular.startAt(a,origin),"mature source organ reaches a real ticking colony port; age="+NativeInfection.age(a)+" power="+TissueActions.power(a,TissueCore.VASCULAR)+" food="+NativeTissue.nutrition(a)+" free="+NativeTissueFeeding.free(a)+" alive="+a.isAlive()+" noai="+a.isNoAi()+" hp="+a.getHealth()+"/"+a.getMaxHealth()+" target="+a.getTarget()+" vel="+a.getDeltaMovement()+" coreTicking="+level.isPositionEntityTicking(origin)+" outbreak="+WorldOutbreakSystem.active(level)+" colony="+ColonyNutrients.resolve(level,origin)+" visual="+NativeTissue.snapshot(a).getCompound("Vascular"));h.assertTrue(NativeTissue.nutrition(a)==398,"opening pays exactly two existing nutrients");}
        public void close(){if(closed)return;closed=true;for(var a:actors)a.discard();colony.dead=true;level.removeBlock(origin,false);data.colonies.clear();data.colonies.addAll(oldColonies);data.clearedSites.clear();data.clearedSites.addAll(oldCleared);data.setDirty();arena.close();InfectionEcologyConfig.PRESERVE.set(preserve);level.getServer().overworld().getDataStorage().set("blacklight_outbreak",oldOutbreak);level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(grief,level.getServer());level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(spawns,level.getServer());BuildingInfectionConfig.VOLUME.set(volume);}
    }
