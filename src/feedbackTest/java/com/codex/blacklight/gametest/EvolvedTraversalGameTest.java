package com.codex.blacklight.gametest;

import com.codex.blacklight.entity.BlacklightInfectedHost;
import com.codex.blacklight.infection.InfectionRules;
import com.codex.blacklight.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class EvolvedTraversalGameTest {
    @GameTest(template="wall_test",timeoutTicks=240,batch="traversal_blade")
    public static void bladePursuesAPlayerUpOneBlock(GameTestHelper h){run(h,"blade",0,true);}
    @GameTest(template="wall_test",timeoutTicks=240,batch="traversal_strength")
    public static void strengthClimbsOneBlock(GameTestHelper h){run(h,"strength",0,false);}
    @GameTest(template="wall_test",timeoutTicks=240,batch="traversal_armored")
    public static void armoredClimbsOneBlock(GameTestHelper h){run(h,"armored",0,false);}
    @GameTest(template="wall_test",timeoutTicks=240,batch="traversal_predator")
    public static void predatorClimbsOneBlock(GameTestHelper h){run(h,"predator",0,false);}
    @GameTest(template="wall_test",timeoutTicks=240,batch="traversal_tendril")
    public static void tendrilClimbsOneBlock(GameTestHelper h){run(h,"tendril",0,false);}
    @GameTest(template="wall_test",timeoutTicks=240,batch="traversal_skirmisher")
    public static void skirmisherClimbsOneBlock(GameTestHelper h){run(h,"skirmisher",0,false);}
    @GameTest(template="wall_test",timeoutTicks=240,batch="traversal_sensory")
    public static void sensoryClimbsOneBlock(GameTestHelper h){run(h,"sensory",0,false);}
    @GameTest(template="wall_test",timeoutTicks=240,batch="traversal_regenerator")
    public static void regeneratorClimbsOneBlock(GameTestHelper h){run(h,"regenerator",0,false);}
    @GameTest(template="wall_test",timeoutTicks=240,batch="traversal_down")
    public static void followsAPathDownOneBlock(GameTestHelper h){run(h,"blade",1,false);}
    @GameTest(template="wall_test",timeoutTicks=240,batch="traversal_narrow")
    public static void wideEliteCrossesSupportedNarrowWalkway(GameTestHelper h){run(h,"blade",2,false);}
    @GameTest(template="wall_test",timeoutTicks=240,batch="traversal_cliff")
    public static void unsupportedCliffStillStopsChase(GameTestHelper h){run(h,"blade",3,false);}
    @GameTest(template="wall_test",timeoutTicks=240,batch="traversal_stairs")
    public static void followsTwoSuccessiveSteps(GameTestHelper h){run(h,"blade",4,false);}

    @GameTest(template="wall_test",timeoutTicks=240,batch="traversal_descending")
    public static void followsSlabsAndSuccessiveDrops(GameTestHelper h){run(h,"blade",5,false);}
    @GameTest(template="wall_test",timeoutTicks=240,batch="traversal_turn")
    public static void clearsASolidCorner(GameTestHelper h){run(h,"blade",6,false);}
    @GameTest(template="wall_test",timeoutTicks=240,batch="traversal_range")
    public static void acquiresAndKeepsADistantSurvivor(GameTestHelper h){run(h,"blade",7,true);}
    private static void run(GameTestHelper h,String kind,int terrain,boolean playerTarget){
        var level=h.getLevel();
        // The course spans chunk borders; keep every chunk entity-ticking so the host
        // never freezes mid-stride when it walks into a neighbouring chunk.
        var lo=h.absolutePos(new BlockPos(0,30,0));var hi=h.absolutePos(new BlockPos(21,30,10));
        var forced=new java.util.ArrayList<net.minecraft.world.level.ChunkPos>();
        for(int cx=Math.min(lo.getX(),hi.getX())>>4;cx<=Math.max(lo.getX(),hi.getX())>>4;cx++)for(int cz=Math.min(lo.getZ(),hi.getZ())>>4;cz<=Math.max(lo.getZ(),hi.getZ())>>4;cz++)
            if(level.setChunkForced(cx,cz,true))forced.add(new net.minecraft.world.level.ChunkPos(cx,cz));
        for(int x=1;x<=20;x++)for(int z=1;z<=9;z++)for(int y=29;y<=35;y++)
            level.setBlock(h.absolutePos(new BlockPos(x,y,z)),Blocks.AIR.defaultBlockState(),2);
        for(int x=1;x<=20;x++)for(int z=1;z<=9;z++){
            if(terrain==2&&z!=5||terrain==3&&x>7)continue;
            level.setBlock(h.absolutePos(new BlockPos(x,29,z)),Blocks.STONE.defaultBlockState(),2);
            if((terrain==0||terrain==4)&&x>=8||terrain==1&&x<=7)
                level.setBlock(h.absolutePos(new BlockPos(x,30,z)),Blocks.STONE.defaultBlockState(),2);
            if(terrain==5 && x<=9)level.setBlock(h.absolutePos(new BlockPos(x,30,z)),Blocks.STONE.defaultBlockState(),2);
            if(terrain==5 && x<=7)level.setBlock(h.absolutePos(new BlockPos(x,31,z)),x==7?Blocks.STONE_SLAB.defaultBlockState():Blocks.STONE.defaultBlockState(),2);
            if(terrain==6 && x>=8 && x<=10 && z>=3 && z<=6)for(int y=30;y<=33;y++)
                level.setBlock(h.absolutePos(new BlockPos(x,y,z)),Blocks.STONE.defaultBlockState(),2);
            if(terrain==4&&x>=10)level.setBlock(h.absolutePos(new BlockPos(x,31,z)),Blocks.STONE.defaultBlockState(),2);
        }
        BlacklightInfectedHost host=ModEntities.INFECTED_HOST.get().create(level);
        var tag=new CompoundTag();host.saveWithoutId(tag);
        var body=new CompoundTag();body.putString("Type","minecraft:zombie");body.putString("Family","ground");
        body.putBoolean("Evolved",true);body.putBoolean("Humanoid",true);body.putFloat("Width",1.064F);body.putFloat("Height",2.4192F);
        body.putString("NativeCore","blacklight:cutting_blade");
        var choice=new CompoundTag();choice.putString("Type","blacklight:"+kind+"_evolved");body.put("EvolvedChoice",choice);
        tag.put("BlacklightHost",body);tag.putLong("BlacklightTissueAge",504000);tag.putInt("BlacklightTissueNutrition",0);host.load(tag);
        host.setPersistenceRequired();host.setNoAi(false);
        host.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(5,terrain==1?31:terrain==5?32:30,5))));host.setOnGround(true);
        level.addFreshEntity(host);
        if(terrain==7){
            // The range fixture moves the target along world +X, beyond the small
            // obstacle course. Give the host a loaded, continuous route to chase on.
            var lane=host.blockPosition().above(60);
            for(int cx=(lane.getX()-3)>>4;cx<=(lane.getX()+196)>>4;cx++){
                for(int cz=(lane.getZ()-4)>>4;cz<=(lane.getZ()+4)>>4;cz++){
                    if(level.setChunkForced(cx,cz,true))forced.add(new net.minecraft.world.level.ChunkPos(cx,cz));
                    level.getChunk(cx,cz);
                }
            }
            for(var floor:BlockPos.betweenClosed(lane.offset(-3,-1,-4),lane.offset(196,-1,4)))
                level.setBlock(floor,Blocks.STONE.defaultBlockState(),2);
            host.setPos(Vec3.atBottomCenterOf(lane));
            var profile=new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"DistantPrey");
            var player=new net.minecraft.server.level.ServerPlayer(level.getServer(),level,profile,net.minecraft.server.level.ClientInformation.createDefault());
            player.connection=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,profile).connection;level.addNewPlayer(player);
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);player.setNoGravity(true);
            var origin=host.position();player.setPos(origin.add(83,0,0));
            h.runAfterDelay(52,()->{
                h.assertTrue(host.getTarget()==player,"natural target acquisition at 83 blocks");
                player.setPos(host.position().add(115,0,0));
            });
            int[] chaseTick={0};
            h.runAfterDelay(72,()->{
                h.assertTrue(host.getTarget()==player,"keeps a visible target beyond detection range");
                chaseTick[0]=host.tickCount;
                player.setPos(host.position().add(149,0,0));
            });
            h.runAfterDelay(89,()->{try{
                String state=" host="+host.position()+" player="+player.position()+" distance="+host.distanceTo(player)
                    +" elapsedHostTicks="+(host.tickCount-chaseTick[0]);
                h.assertTrue(host.isAlive()&&host.tickCount>chaseTick[0],"host remains active on pursuit lane"+state);
                h.assertTrue(host.distanceTo(player)>com.codex.blacklight.infection.EvolvedBalance.PURSUIT_RANGE,"prey beyond pursuit range"+state);
                h.assertTrue(host.getTarget()!=player,"releases out-of-range prey"+state);
            }finally{host.discard();level.removePlayerImmediately(player,net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);for(var c:forced)level.setChunkForced(c.x,c.z,false);}h.succeed();});
            return;
        }
        LivingEntity target;
        if(playerTarget){
            var p=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"StepTarget"));
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);p.setInvulnerable(true);target=p;
        }else{
            var p=EntityType.HUSK.create(level);p.setNoAi(true);p.setPersistenceRequired();p.setInvulnerable(true);
            p.getPersistentData().putLong(InfectionRules.NEXT_CONTACT_EXPOSURE,Long.MAX_VALUE);level.addFreshEntity(p);target=p;
        }
        target.setNoGravity(true);
        int targetX=terrain==0?8:terrain==4?12:16;
        int targetY=terrain==0?31:terrain==4?32:30;
        target.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(targetX,targetY,5))));
        var start=host.position();double[] highest={start.y},closest={host.distanceTo(target)},lowest={start.y};
        for(int t=1;t<=210;t++)h.runAfterDelay(t,()->{
            host.setTarget(target);highest[0]=Math.max(highest[0],host.getY());lowest[0]=Math.min(lowest[0],host.getY());closest[0]=Math.min(closest[0],host.distanceTo(target));
        });
        h.runAfterDelay(215,()->{try{
            String detail="type="+kind+" terrain="+terrain+" start="+start+" end="+host.position()+" maxY="+highest[0]+" closest="+closest[0]+" action="+host.evolvedAction();
            h.assertTrue(host.isAlive(),"elite remains alive: "+detail);
            if(terrain==3){h.assertTrue(lowest[0]>=start.y-.35,"cliff safety retained: "+detail);h.assertTrue(closest[0]>4,"cannot cross unsupported air: "+detail);}
            else if(terrain==0||terrain==4){h.assertTrue(highest[0]>=start.y+(terrain==4?1.8:.8),"must physically climb to target footing: "+detail);h.assertTrue(closest[0]<3,"closes height separation: "+detail);}
            else {h.assertTrue(host.getX()>start.x+5,"must make real chase progress: "+detail);h.assertTrue(closest[0]<4,"approaches target using collision movement: "+detail);}
        }finally{for(var c:forced)level.setChunkForced(c.x,c.z,false);host.discard();if(!(target instanceof net.minecraft.world.entity.player.Player))target.discard();}h.succeed();});
    }
}
