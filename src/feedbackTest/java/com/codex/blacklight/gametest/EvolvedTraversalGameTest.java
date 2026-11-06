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
import net.minecraftforge.gametest.*;

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

    private static void run(GameTestHelper h,String kind,int terrain,boolean playerTarget){
        var level=h.getLevel();
        for(int x=1;x<=20;x++)for(int z=1;z<=9;z++)for(int y=29;y<=35;y++)
            level.setBlock(h.absolutePos(new BlockPos(x,y,z)),Blocks.AIR.defaultBlockState(),2);
        for(int x=1;x<=20;x++)for(int z=1;z<=9;z++){
            if(terrain==2&&z!=5||terrain==3&&x>7)continue;
            level.setBlock(h.absolutePos(new BlockPos(x,29,z)),Blocks.STONE.defaultBlockState(),2);
            if((terrain==0||terrain==4)&&x>=8||terrain==1&&x<=7)
                level.setBlock(h.absolutePos(new BlockPos(x,30,z)),Blocks.STONE.defaultBlockState(),2);
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
        host.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(5,terrain==1?31:30,5))));host.setOnGround(true);
        level.addFreshEntity(host);
        LivingEntity target;
        if(playerTarget){
            var p=net.minecraftforge.common.util.FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"StepTarget"));
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
        }finally{host.discard();if(!(target instanceof net.minecraft.world.entity.player.Player))target.discard();}h.succeed();});
    }
}
