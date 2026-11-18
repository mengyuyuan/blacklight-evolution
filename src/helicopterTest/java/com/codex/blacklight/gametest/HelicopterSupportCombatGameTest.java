package com.codex.blacklight.gametest;

import com.codex.blacklight.entity.*;
import com.codex.blacklight.firearms.HellfireBlastEntity;
import com.codex.blacklight.military.aircraft.HelicopterRappel;
import com.codex.blacklight.firearms.VehicleOrdnanceProjectile;
import com.codex.blacklight.registry.ModEntities;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.*;
import net.minecraftforge.gametest.*;

/** The complete public support mission: no phase/NBT shortcut, manual combat
 * tick, weapon invocation or invulnerable target. Infantry damage is excluded. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="blacklight")
public final class HelicopterSupportCombatGameTest {
    private static final Map<UUID,Scene> WATCHED=new HashMap<>();

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void acceptedDamage(com.codex.blacklight.combat.ForgeDamageEvent.Post event){
        var scene=WATCHED.get(event.getEntity().getUUID());
        if(scene==null||scene.closed||event.getEntity()!=scene.target||event.getNewDamage()<=0)return;
        var source=event.getSource();var direct=source.getDirectEntity();
        if(source.getEntity() instanceof MilitarySoldier operator&&operator==scene.craft.combat().operator()
                &&operator.getVehicle()==scene.craft&&(direct==scene.craft||direct instanceof HellfireBlastEntity)){
            // This is positive post-health-loss feedback, not an attempted hurt.
            // A ground soldier's bullet/grenade cannot satisfy this attribution.
            if(scene.departed&&scene.landed.size()==6&&scene.craft.phase()==BlackwatchHelicopter.Phase.STRAFE){
                if(direct==scene.craft)scene.gunHits++;else scene.missileHits++;
                scene.airDamage+=event.getNewDamage();scene.airHitAt=scene.area.level.getGameTime();
            }
        }else scene.otherDamage+=event.getNewDamage();
    }

    private static final class Scene implements AutoCloseable {
        final HelicopterPilotGameTest.Fixture area;
        final BlackwatchHelicopter craft;
        final BlacklightCommonInfected target;
        final Vec3 targetAt;
        final boolean hillside;
        final Map<Long,Boolean> extra=new HashMap<>();
        final Set<UUID> firstWave=new LinkedHashSet<>(),airborne=new HashSet<>(),landed=new HashSet<>();
        final EnumMap<BlackwatchHelicopter.Phase,Long> phases=new EnumMap<>(BlackwatchHelicopter.Phase.class);
        final Set<String> tactics=new LinkedHashSet<>();
        boolean started,departed,closed;
        int gunHits,missileHits;
        double airDamage,otherDamage,highestCombatOffset;
        long airHitAt=-1;
        float startingHealth;

        Scene(GameTestHelper h,boolean hillside){
            this.hillside=hillside;area=new HelicopterPilotGameTest.Fixture(h,hillside?331024:330000);
            for(int cx=(area.x-80)>>4;cx<=(area.x+80)>>4;cx++)for(int cz=-5;cz<=5;cz++){
                long key=net.minecraft.world.level.ChunkPos.asLong(cx,cz);
                extra.put(key,area.level.getForcedChunks().contains(key));area.level.setChunkForced(cx,cz,true);
            }
            // The source terrain samples and natural maneuvers need more room
            // than the cockpit-input fixture. All changes stay in this QA site.
            for(int dx=-64;dx<=64;dx++)for(int z=-64;z<=64;z++){
                area.level.setBlock(new BlockPos(area.x+dx,80,z),Blocks.STONE.defaultBlockState(),2);
                for(int y=81;y<=139;y++){
                    var p=new BlockPos(area.x+dx,y,z);
                    if(!area.level.getBlockState(p).isAir())area.level.setBlock(p,Blocks.AIR.defaultBlockState(),2);
                }
            }
            if(hillside)for(int dx=32;dx<=48;dx++)for(int z=-12;z<=12;z++){
                int rise=Math.max(0,18-2*Math.max(Math.max(0,Math.abs(dx-40)-2),Math.max(0,Math.abs(z)-6)));
                for(int y=81;y<81+rise;y++)area.level.setBlock(new BlockPos(area.x+dx,y,z),Blocks.STONE.defaultBlockState(),2);
            }
            craft=area.craft(0);
            for(int i=0;i<2;i++){
                var crew=ModEntities.BLACKWATCH_SOLDIER.get().create(area.level);
                crew.setPos(craft.position());crew.setAutomaticUnitJoin(false);crew.setPersistenceRequired();
                crew.initializeNativeLoadout("support-flight-qa:"+i);area.level.addFreshEntity(crew);area.entities.add(crew);
                h.assertTrue(crew.startRiding(craft,true),"two operational native crew board before the mission");
            }
            targetAt=new Vec3(area.x+16,81,0);
            target=ModEntities.COMMON_INFECTED.get().create(area.level);
            target.setPos(targetAt);target.setNoAi(true);target.setNoGravity(true);target.setPersistenceRequired();
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10000);
            target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
            area.level.addFreshEntity(target);target.setHealth(target.getMaxHealth());area.entities.add(target);
            WATCHED.put(target.getUUID(),this);
            h.runAfterDelay(30,()->{
                if(closed)return;
                try{
                    h.assertTrue(!target.isInvulnerable()&&target.getHealth()>500,"durable ground infection remains damageable");
                    h.assertTrue(area.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,area.x+16,0)==81,"target stands on the actual valley floor");
                    if(hillside){
                        h.assertTrue(area.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,area.x+40,0)==99,"nearby ridge raises the real 60-wide terrain sample by eighteen blocks");
                        h.assertTrue(area.level.clip(new net.minecraft.world.level.ClipContext(new Vec3(area.x,99,0),target.getBoundingBox().getCenter(),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,craft)).getType()==HitResult.Type.MISS,"ridge remains to the side of the actual western firing corridor");
                    }
                    h.assertTrue(craft.deployRappellingTo(new Vec3(area.x,81,0)),"public air-deployment mission accepts the natural loaded site");
                    h.assertTrue(craft.phase()==BlackwatchHelicopter.Phase.SPIN_UP,"grounded fixture enters the actual rotor-start phase");
                    var caller=ModEntities.BLACKWATCH_OFFICER.get().create(area.level);
                    caller.setPos(area.x-10,81,0);caller.setAutomaticUnitJoin(false);area.level.addFreshEntity(caller);caller.setTarget(target);
                    craft.combat().supportCall(caller);caller.discard();
                    h.assertTrue(craft.combat().start()&&craft.combat().hostile(target),"real crew and support designation accept the ground infected target");
                    startingHealth=target.getHealth();started=true;
                }catch(RuntimeException error){close();throw error;}
            });
        }
        List<MilitarySoldier> troops(){
            return area.level.getEntitiesOfClass(MilitarySoldier.class,new AABB(area.x-120,60,-120,area.x+120,150,120),s->{
                var tag=s.getPersistentData();return tag.hasUUID("HelicopterDeliveredBy")&&tag.getUUID("HelicopterDeliveredBy").equals(craft.getUUID());
            });
        }
        void observe(GameTestHelper h){
            if(closed||!started)return;
            h.assertTrue(!craft.isRemoved()&&craft.health()>120,"support aircraft stays operational: "+diagnostic());
            h.assertTrue(target.isAlive(),"durable target survives until aircraft damage is verified: "+diagnostic());
            // A stationary receiver isolates flight/aim behavior; health and
            // every incoming infantry/aircraft damage event are left untouched.
            target.setPos(targetAt);target.setDeltaMovement(Vec3.ZERO);
            long now=area.level.getGameTime();var phase=craft.phase();phases.putIfAbsent(phase,now);
            for(var troop:troops()){
                if(!area.entities.contains(troop))area.entities.add(troop);
                if(firstWave.size()<6)firstWave.add(troop.getUUID());
                if(!firstWave.contains(troop.getUUID()))continue;
                if(HelicopterRappel.active(troop)&&troop.getY()>85){
                    h.assertTrue(!troop.isPassenger()&&craft.getY()>90,"each observed rope soldier descends independently below an airborne craft");
                    airborne.add(troop.getUUID());
                }
                if(!HelicopterRappel.active(troop)&&troop.onGround()&&!troop.isNoAi()&&!troop.isNoGravity()&&troop.tacticsAvailable())landed.add(troop.getUUID());
            }
            if(phase==BlackwatchHelicopter.Phase.DEPART&&landed.size()==6)departed=true;
            if(phase==BlackwatchHelicopter.Phase.PURSUIT||phase==BlackwatchHelicopter.Phase.STRAFE){
                tactics.add(craft.combat().tactic());highestCombatOffset=Math.max(highestCombatOffset,craft.getY()-target.getY());
            }
        }
        boolean ready(){return departed&&airborne.size()==6&&landed.size()==6&&airDamage>0&&target.getHealth()<startingHealth;}
        void verify(GameTestHelper h){
            var required=List.of(BlackwatchHelicopter.Phase.SPIN_UP,BlackwatchHelicopter.Phase.CLIMB,BlackwatchHelicopter.Phase.APPROACH,
                    BlackwatchHelicopter.Phase.DESCEND,BlackwatchHelicopter.Phase.UNLOAD,BlackwatchHelicopter.Phase.DEPART,
                    BlackwatchHelicopter.Phase.PURSUIT,BlackwatchHelicopter.Phase.STRAFE);
            long previous=Long.MIN_VALUE;
            for(var phase:required){
                h.assertTrue(phases.containsKey(phase)&&phases.get(phase)>previous,"natural ordered phase "+phase+" observed: "+diagnostic());
                previous=phases.get(phase);
            }
            h.assertTrue(firstWave.size()==6&&airborne.size()==6&&landed.size()==6&&departed,"six distinct physical soldiers complete rope descent and AI restoration before combat: "+diagnostic());
            h.assertTrue(airDamage>0&&gunHits+missileHits>0&&airHitAt>=previous&&target.getHealth()<startingHealth,
                    "post-deployment aircraft itself must inflict accepted damage; ground rifles cannot satisfy this: "+diagnostic());
            h.assertTrue(craft.combat().gunShots()+craft.combat().rocketShots()>0&&craft.getPassengers().size()==2,"real aircraft weapon counter advances with its two flight crew retained");
            System.out.println("HELICOPTER_SUPPORT_CHAIN_VERIFIED "+diagnostic());
        }
        String diagnostic(){return "hill="+hillside+" phase="+craft.phase()+" craftTick="+craft.tickCount+" phases="+phases+" airborne="+airborne.size()+" landed="+landed.size()
                +" tactics="+tactics+" altitude="+highestCombatOffset+" gun="+craft.combat().gunShots()+" rocket="+craft.combat().rocketShots()+" airDamage="+airDamage+" otherDamage="+otherDamage+" targetHP="+target.getHealth()+" pos="+craft.position();}
        public void close(){
            if(closed)return;closed=true;WATCHED.remove(target.getUUID());troops().forEach(Entity::discard);
            for(var e:area.level.getEntitiesOfClass(Entity.class,new AABB(area.x-120,60,-120,area.x+120,150,120),e->e instanceof VehicleOrdnanceProjectile||e instanceof HellfireBlastEntity))e.discard();
            extra.forEach((key,value)->{var p=new net.minecraft.world.level.ChunkPos(key);area.level.setChunkForced(p.x,p.z,value);});area.close();
        }
    }
    private static void run(GameTestHelper h,boolean hillside){
        var scene=new Scene(h,hillside);
        h.onEachTick(()->{
            if(scene.closed)return;
            try{
                scene.observe(h);
                if(scene.ready()){scene.verify(h);scene.close();h.succeed();}
            }catch(RuntimeException error){scene.close();throw error;}
        });
        h.runAfterDelay(1200,()->{
            if(scene.closed)return;
            try{scene.verify(h);h.succeed();}finally{scene.close();}
        });
    }
    @GameTest(template="helicopter_arena",batch="airSupportGroundChain",timeoutTicks=1250)
    public static void naturalSixPersonRappelThenAircraftWeaponsDamageGroundInfected(GameTestHelper h){run(h,false);}
    @GameTest(template="helicopter_arena",batch="airSupportHillChain",timeoutTicks=1250)
    public static void naturalRappelBesideHighTerrainStillLeadsToAircraftDamageInTheValley(GameTestHelper h){run(h,true);}
    private HelicopterSupportCombatGameTest(){}
}
