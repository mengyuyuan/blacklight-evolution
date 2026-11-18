package com.codex.blacklight.gametest;

import com.codex.blacklight.entity.*;
import com.codex.blacklight.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class HelicopterCircleGameTest {
    private static void run(GameTestHelper h,int index,boolean hydra,boolean raised){
        var area=new HelicopterPilotGameTest.Fixture(h,320000+index*512);Vec3 targetAt=new Vec3(area.x+12,81,0);
        if(raised)for(int y=81;y<105;y++)area.level.setBlock(new BlockPos(area.x+12,y,20),Blocks.BEDROCK.defaultBlockState(),2);
        BlackwatchHelicopter[] aircraft={null};Mob[] enemy={null};int[] age={0},samples={0};double[] speed={0},low={Double.POSITIVE_INFINITY},high={Double.NEGATIVE_INFINITY};Vec3[] first={null};boolean[] done={false},restored={false};
        h.onEachTick(()->{
            if(done[0])return;
            try{
                if(aircraft[0]==null){
                    if(!area.level.isPositionEntityTicking(BlockPos.containing(targetAt)))return;
                    Mob target=hydra?ModEntities.BLACKLIGHT_HYDRA.get().create(area.level):ModEntities.COMMON_INFECTED.get().create(area.level);
                    target.setPos(targetAt);target.setNoAi(true);target.setNoGravity(true);target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10000);target.setHealth(10000);area.level.addFreshEntity(target);area.entities.add(target);enemy[0]=target;
                    var craft=area.craft(raised?32:hydra?17.5:8);craft.setPos(targetAt.add(-25,raised?32:hydra?17.5:8,0));aircraft[0]=craft;var crew=area.crew(craft);for(int i=0;i<crew.size();i++)if(i<2)crew.get(i).setNoAi(false);else crew.get(i).discard();
                    var caller=ModEntities.BLACKWATCH_OFFICER.get().create(area.level);caller.setPos(area.x-12,81,0);caller.setAutomaticUnitJoin(false);area.level.addFreshEntity(caller);caller.setTarget(target);
                    h.assertTrue(craft.deployTo(new Vec3(area.x,81,12)),"source circle sortie");craft.combat().supportCall(caller);caller.discard();
                    // This fixture isolates the CircleStrafe movement adapter. Bank
                    // selection and its 200-tick opportunity are covered separately.
                    craft.combat().circle().begin(.5);
                    var tag=new CompoundTag();craft.saveWithoutId(tag);tag.putInt("FlightPhase",BlackwatchHelicopter.Phase.PURSUIT.ordinal());
                    var combat=tag.getCompound("AirCombat");combat.putUUID("Target",target.getUUID());
                    var tactic=new CompoundTag();tactic.putInt("Version",1);tactic.putString("Tactic","CIRCLE");tactic.putInt("Age",0);combat.put("Tactics",tactic);
                    craft.load(tag);return;
                }
                var craft=aircraft[0];enemy[0].setPos(targetAt);enemy[0].setDeltaMovement(Vec3.ZERO);int tick=++age[0];
                h.assertTrue(craft.clearAt(craft.position(),craft.getYRot()),"real airframe and rotor remain clear");
                if(tick==100){speed[0]=craft.combat().circle().speed();h.assertTrue(speed[0]>=.6&&speed[0]<=.8,"source speed actually selected");var tag=new CompoundTag();craft.saveWithoutId(tag);craft.load(tag);restored[0]=true;}
                if(tick>100)h.assertTrue(craft.combat().circle().speed()==speed[0],"actual save/load does not reroll movement speed");
                if(tick>=30){
                    h.assertTrue(craft.combat().tactic().equals("CIRCLE"),"saved circle remains active before its ten-second opportunity");
                    double distance=craft.position().subtract(targetAt).horizontalDistance(),height=craft.getY()-targetAt.y;
                    low[0]=Math.min(low[0],height);high[0]=Math.max(high[0],height);
                    h.assertTrue(distance>=19&&distance<=31,"actual circle distance band: "+distance);
                    double min=raised?30:hydra?15:6,max=raised?34:hydra?20:10;
                    h.assertTrue(height>=min-1&&height<=max+1,"actual source terrain/profile height: "+height);
                    if(first[0]==null)first[0]=craft.position();samples[0]++;
                }
                if(tick==190){
                    h.assertTrue(restored[0]&&samples[0]>=100&&craft.position().distanceTo(first[0])>12,"native orbit continues through reload");
                    h.assertTrue(craft.phase()==BlackwatchHelicopter.Phase.STRAFE&&craft.combat().rocketShots()==0,"circle before source Hellfire avoidance completes");
                    h.assertTrue(craft.combat().rounds()==240&&craft.combat().rockets()==8,"AI circle preserves player stores");
                    if(!raised)h.assertTrue(craft.combat().gunShots()>0,"source circle fires real chin gun against ordinary and multipart targets");
                    if(hydra){var body=(BlacklightHydra)enemy[0];h.assertTrue(java.util.Arrays.stream(body.getParts()).allMatch(p->p.isAlive()&&p.isPickable()),"native multipart proxies remain alive");h.assertTrue(body.getHealth()<body.getMaxHealth()&&body.incomingEffects()>0,"actual animated hydra receives accepted gun contact");}
                    com.codex.blacklight.BlacklightNeoMod.LOGGER.info("HELI_CIRCLE_VERIFIED hydra={} raised={} samples={} height={}/{} speed={} gunShots={} travel={} health={} max={} contacts={}",hydra,raised,samples[0],low[0],high[0],speed[0],craft.combat().gunShots(),craft.position().distanceTo(first[0]),enemy[0].getHealth(),enemy[0].getMaxHealth(),hydra?((BlacklightHydra)enemy[0]).incomingEffects():0);
                    done[0]=true;area.close();h.succeed();
                }
            }catch(RuntimeException failure){done[0]=true;area.close();throw failure;}
        });
    }
    @GameTest(template="helicopter_arena",batch="circleOrdinary",timeoutTicks=600)
    public static void ordinaryCircleUsesDistanceAndHeightBandsAcrossActualReload(GameTestHelper h){run(h,0,false,false);}
    @GameTest(template="helicopter_arena",batch="circleHydra",timeoutTicks=600)
    public static void hydraCircleUsesItsSeparateHigherFlightBand(GameTestHelper h){run(h,1,true,false);}
    @GameTest(template="helicopter_arena",batch="circleTerrain",timeoutTicks=600)
    public static void nearbyTerrainRaisesTheWholeCircleHeightBand(GameTestHelper h){run(h,2,false,true);}
}
