package com.codex.blacklight.gametest;

import com.codex.blacklight.firearms.VehicleOrdnanceProjectile;
import com.codex.blacklight.military.aircraft.HelicopterHellfireRack;
import com.codex.blacklight.military.vehicle.VehicleControlAuthority;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class HelicopterRackGameTest {
    @GameTest(template="helicopter_arena",batch="fixedHellfireRacks",timeoutTicks=450)
    public static void actualManualMissilesUseFixedRailsBodyHeadingAndPersistTheNextSlot(GameTestHelper h){
        var f=new HelicopterPilotGameTest.Fixture(h,296000);var craft=f.craft(16);var p=HelicopterPilotGameTest.player(h,f,false);h.assertTrue(p.startRiding(craft,true),"actual manual aircraft pilot");
        Set<UUID> seen=new HashSet<>();List<Vec3> launches=new ArrayList<>();int[] age={0};long[] sequence={0};boolean[] done={false},restored={false};
        h.onEachTick(()->{
            if(done[0])return;
            try{
                int now=++age[0];h.assertTrue(now<400,"real fixed-rack sortie finishes before native timeout");
                if(now>=100&&now<282&&now%2==0)h.assertTrue(craft.pilot().accept(p,craft.pilot().lease(p.getUUID()),sequence[0]++,0,0,25,20,VehicleControlAuthority.SECONDARY_FIRE),"real fixed-rack input accepted");
                for(var missile:f.level.getEntitiesOfClass(VehicleOrdnanceProjectile.class,craft.getBoundingBox().inflate(8),m->m.hellfire()&&m.flightAge()<=2)){
                    if(!seen.add(missile.getUUID()))continue;f.entities.add(missile);int shot=launches.size(),side=shot%2==0?-1:1,slot=shot/2;
                    var tag=new CompoundTag();missile.saveWithoutId(tag);
                    Vec3 launch=new Vec3(tag.getDouble("VehicleShotX"),tag.getDouble("VehicleShotY"),tag.getDouble("VehicleShotZ"));
                    h.assertTrue(launch.distanceTo(craft.hellfireLaunchPoint(side,slot))<.001,"actual projectile originates at fixed rail despite off-axis camera aim: "+launch+" expected="+craft.hellfireLaunchPoint(side,slot));
                    h.assertTrue(missile.flightHeading().distanceTo(new Vec3(0,0,1))<1e-5,"source player zero-yaw/zero-pitch launch follows body heading, not camera aim");
                    launches.add(launch);h.assertTrue(craft.combat().rockets()==8-launches.size(),"one finite round consumed per real launch");
                    h.assertTrue(!HelicopterHellfireRack.loaded(side,slot,craft.combat().rockets()),"spent rail becomes empty");
                }
                if(launches.size()==2&&!restored[0]){var tag=new CompoundTag();craft.saveWithoutId(tag);craft.load(tag);restored[0]=true;}
                if(launches.size()==4){h.assertTrue(restored[0]&&new HashSet<>(launches).size()==4&&craft.combat().rockets()==4,"real reload preserves magazine and advances to distinct lower rails");
                    com.codex.blacklight.BlacklightNeoMod.LOGGER.info("HELI_RACK_VERIFIED launches={} remaining={} restored={} heading=body",launches.size(),craft.combat().rockets(),restored[0]);done[0]=true;f.close();h.succeed();}
            }catch(RuntimeException ex){done[0]=true;f.close();throw ex;}
        });
    }
}
