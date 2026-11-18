package com.codex.blacklight.gametest;

import com.codex.blacklight.firearms.VehicleOrdnanceProjectile;
import com.codex.blacklight.military.aircraft.HelicopterHellfireRack;
import com.codex.blacklight.military.vehicle.VehicleControlAuthority;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class HelicopterRapidGameTest {
    @GameTest(template="helicopter_arena",batch="rapidPods",timeoutTicks=1000)
    public static void actualPlayerSwitchesFiresBothFinitePodsReloadsAndReturnsToHellfire(GameTestHelper h){
        var f=new HelicopterPilotGameTest.Fixture(h,300000);var craft=f.craft(16);var player=HelicopterPilotGameTest.player(h,f,false);
        h.assertTrue(player.startRiding(craft,true),"actual rapid pilot boards");
        for(int x=-8;x<=8;x++)for(int y=90;y<=103;y++)f.level.setBlock(new BlockPos(f.x+x,y,22),Blocks.STONE.defaultBlockState(),2);
        Set<UUID> seen=new HashSet<>();int[] age={0},lastLaunch={-1},firstSteps={0};long[] sequence={0};boolean[] restored={false},done={false};
        h.onEachTick(()->{if(done[0])return;try{
            int now=++age[0];h.assertTrue(now<970,"real finite rapid sortie ends before native timeout");
            if(now>=100&&now<=920&&now%2==0){int actions=VehicleControlAuthority.SECONDARY_FIRE;if(now==100||now==904)actions|=VehicleControlAuthority.NEXT_AMMUNITION;
                h.assertTrue(new com.codex.blacklight.network.HelicopterControlPayload(craft.getUUID(),craft.pilot().lease(player.getUUID()),sequence[0]++,0,0,0,0,actions).handle(player),"real packet handler authorizes rapid input");}
            for(var rocket:f.level.getEntitiesOfClass(VehicleOrdnanceProjectile.class,craft.getBoundingBox().inflate(12),p->p.rapidRocket()&&p.flightAge()<=1)){
                if(rocket.flightAge()==1){h.assertTrue(Math.abs(rocket.getDeltaMovement().length()-6.25)<1e-5,"actual ordinary first update uses125/sec without native drag or cold drop");firstSteps[0]++;}
                if(!seen.add(rocket.getUUID()))continue;f.entities.add(rocket);int number=seen.size();int side=number%2==1?-1:1;
                var tag=new CompoundTag();rocket.saveWithoutId(tag);var actual=new Vec3(tag.getDouble("VehicleShotX"),tag.getDouble("VehicleShotY"),tag.getDouble("VehicleShotZ"));
                var expected=craft.weaponPoint(HelicopterHellfireRack.rapidPod(side).add(0,0,.748163));h.assertTrue(actual.distanceTo(expected)<.001,"real rocket exits outer pod, alternating left/right");
                if(lastLaunch[0]>=0)h.assertTrue(now-lastLaunch[0]==5,"player rapid cadence waits for source fireRate4 cooldown: "+(now-lastLaunch[0]));lastLaunch[0]=now;
                h.assertTrue(!tag.hasUUID("HellfireTarget")&&!tag.contains("HellfireFlight")&&tag.contains("RapidFlight"),"unguided state never acquires Hellfire tracking");
            }
            if(craft.combat().rapid().shots()==75&&!restored[0]){var tag=new CompoundTag();craft.saveWithoutId(tag);craft.load(tag);restored[0]=true;h.assertTrue(craft.combat().rapidSelected(),"selection survives actual aircraft reload");}
            if(now==900){h.assertTrue(restored[0]&&seen.size()==150&&firstSteps[0]==150&&craft.combat().rapid().left()==0&&craft.combat().rapid().right()==0,"both actual75-round pods exhausted with150 native flight updates across reload: "+seen.size()+"/"+firstSteps[0]);
                h.assertTrue(craft.combat().rockets()==8&&craft.combat().rounds()==240,"rapid fire never consumes Hellfire or gun stores");}
            if(now>=930){h.assertTrue(!craft.combat().rapidSelected()&&craft.combat().rockets()==7&&craft.combat().rapid().shots()==150,"actual switching back fires one Hellfire without refilling rapid pods");
                com.codex.blacklight.BlacklightNeoMod.LOGGER.info("HELI_RAPID_VERIFIED launches={} stores=0/0 restored={} hellfire={} firstSteps={}",seen.size(),restored[0],craft.combat().rockets(),firstSteps[0]);done[0]=true;f.close();h.succeed();}
        }catch(RuntimeException ex){done[0]=true;f.close();throw ex;}});
    }
    @GameTest(template="helicopter_arena",batch="rapidGroundInfantry",timeoutTicks=190)
    public static void actualAirbornePlayerRocketDamagesGroundInfantry(GameTestHelper h){ground(h,false);}
    @GameTest(template="helicopter_arena",batch="rapidGroundArmor",timeoutTicks=190)
    public static void actualAirbornePlayerRocketDamagesGroundTank(GameTestHelper h){ground(h,true);}
    private static void ground(GameTestHelper h,boolean armor){
        var f=new HelicopterPilotGameTest.Fixture(h,armor?301600:300800);var craft=f.craft(8);var player=HelicopterPilotGameTest.player(h,f,false);
        h.assertTrue(player.startRiding(craft,true),"ground attack pilot boards real elevated craft");
        var tank=armor?com.codex.blacklight.registry.ModEntities.BLACKWATCH_TANK.get().create(f.level):null;
        var infantry=armor?null:com.codex.blacklight.registry.ModEntities.COMMON_INFECTED.get().create(f.level);
        net.minecraft.world.entity.Entity target=armor?tank:infantry;target.setPos(f.x-2,81,17);if(infantry!=null){infantry.setNoAi(true);infantry.setPersistenceRequired();}
        f.level.addFreshEntity(target);f.entities.add(target);float health=armor?tank.health():infantry.getHealth();float pilotHealth=player.getHealth();
        int[] age={0};long[] sequence={0};boolean[] seen={false},done={false},delayed={false};float[] directHealth={Float.NaN};
        h.onEachTick(()->{if(done[0])return;try{
            int now=++age[0];h.assertTrue(now<170,"ground attack completes before native timeout");
            if(now>=100&&now%2==0)h.assertTrue(new com.codex.blacklight.network.HelicopterControlPayload(craft.getUUID(),craft.pilot().lease(player.getUUID()),sequence[0]++,0,0,0,30,now==100?6:0).handle(player),"real downward aim/fire packet");
            for(var rocket:f.level.getEntitiesOfClass(VehicleOrdnanceProjectile.class,craft.getBoundingBox().inflate(18),VehicleOrdnanceProjectile::rapidRocket)){
                if(!f.entities.contains(rocket))f.entities.add(rocket);seen[0]=true;h.assertTrue(rocket.getDeltaMovement().y<0,"actual rocket descends toward ground");
            }
            for(var blast:f.level.getEntitiesOfClass(com.codex.blacklight.firearms.HellfireBlastEntity.class,craft.getBoundingBox().inflate(32),com.codex.blacklight.firearms.HellfireBlastEntity::rapidBlast)){
                if(!f.entities.contains(blast))f.entities.add(blast);float remaining=armor?tank.health():infantry.getHealth();
                if(Float.isNaN(directHealth[0])){h.assertTrue(blast.blastAge()<9&&remaining<health,"real direct contact precedes delayed area damage");directHealth[0]=remaining;}
                if(blast.blastAge()<9)h.assertTrue(Math.abs(remaining-directHealth[0])<.01,"no early generic area blast during source delay");
                if(blast.blastAge()>=9&&remaining<directHealth[0])delayed[0]=true;
            }
            if(now>=130){float remaining=armor?tank.health():infantry.getHealth();
                h.assertTrue(seen[0]&&delayed[0]&&remaining<directHealth[0],"actual70mm direct and later area damage hit grounded "+(armor?"tank":"infantry")+": "+remaining+"/"+directHealth[0]+"/"+health);
                h.assertTrue(craft.getY()>88&&craft.health()==360&&player.getHealth()==pilotHealth,"ground strike leaves firing aircraft and pilot intact");
                h.assertTrue(craft.combat().rapid().shots()==1&&craft.combat().rapid().left()==74&&craft.combat().rapid().right()==75&&craft.combat().rockets()==8,"one real left-pod shot, separate stores");
                com.codex.blacklight.BlacklightNeoMod.LOGGER.info("HELI_RAPID_GROUND_VERIFIED target={} damage={} altitude={} shots=1 direct={} delayed=true",armor?"tank":"infantry",health-remaining,craft.getY()-81,health-directHealth[0]);done[0]=true;f.close();h.succeed();}
        }catch(RuntimeException ex){done[0]=true;f.close();throw ex;}});
    }
}
