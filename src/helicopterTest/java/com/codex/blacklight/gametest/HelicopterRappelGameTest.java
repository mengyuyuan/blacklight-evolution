package com.codex.blacklight.gametest;

import com.codex.blacklight.entity.*;
import com.codex.blacklight.military.aircraft.HelicopterRappel;
import com.codex.blacklight.registry.ModEntities;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class HelicopterRappelGameTest {
    private static final class Fixture implements AutoCloseable {
        final HelicopterPilotGameTest.Fixture area;final BlackwatchHelicopter craft;final BlacklightCommonInfected target;
        final Map<Long,Boolean> extra=new HashMap<>();final Set<UUID> observed=new HashSet<>();
        Fixture(GameTestHelper h,int index){
            area=new HelicopterPilotGameTest.Fixture(h,294000+index*512);
            for(int x=(area.x-64)>>4;x<=(area.x+64)>>4;x++)for(int z=-4;z<=4;z++){
                long key=net.minecraft.world.level.ChunkPos.asLong(x,z);extra.put(key,area.level.getForcedChunks().contains(key));area.level.setChunkForced(x,z,true);
            }
            for(int x=-48;x<=48;x++)for(int z=-48;z<=48;z++){
                area.level.setBlock(new BlockPos(area.x+x,80,z),Blocks.STONE.defaultBlockState(),2);
                for(int y=81;y<=111;y++)area.level.setBlock(new BlockPos(area.x+x,y,z),Blocks.AIR.defaultBlockState(),2);
            }
            craft=area.craft(12);
            for(int i=0;i<2;i++){
                var pilot=ModEntities.BLACKWATCH_SOLDIER.get().create(area.level);pilot.setPos(craft.position());pilot.setAutomaticUnitJoin(false);
                area.level.addFreshEntity(pilot);area.entities.add(pilot);h.assertTrue(pilot.startRiding(craft,true),"two real crew boarded");
            }
            target=ModEntities.COMMON_INFECTED.get().create(area.level);target.setPos(area.x+16,81,0);target.setNoAi(true);target.setInvulnerable(true);
            area.level.addFreshEntity(target);area.entities.add(target);
            h.runAfterDelay(30,()->{
                h.assertTrue(craft.deployRappellingTo(new Vec3(area.x,81,0)),"loaded supported rope site accepted");
                var caller=ModEntities.BLACKWATCH_OFFICER.get().create(area.level);caller.setPos(area.x-10,81,0);caller.setAutomaticUnitJoin(false);
                area.level.addFreshEntity(caller);caller.setTarget(target);craft.combat().supportCall(caller);caller.discard();
                craft.setFlightPhase(BlackwatchHelicopter.Phase.UNLOAD); // Isolate actual deployment after ingress, without calling unload directly.
            });
        }
        List<MilitarySoldier> troops(){
            return area.level.getEntitiesOfClass(MilitarySoldier.class,new AABB(area.x-120,60,-120,area.x+120,140,120),s->s.getPersistentData().hasUUID("HelicopterDeliveredBy")&&s.getPersistentData().getUUID("HelicopterDeliveredBy").equals(craft.getUUID()));
        }
        public void close(){troops().forEach(Entity::discard);extra.forEach((key,value)->{var c=new net.minecraft.world.level.ChunkPos(key);area.level.setChunkForced(c.x,c.z,value);});area.close();}
    }
    @GameTest(template="helicopter_arena",batch="airRappel",timeoutTicks=1900)
    public static void sourceWavesHoverDetachLandAndRepeatAfterCooldown(GameTestHelper h){
        var f=new Fixture(h,0);boolean[] airborne={false},handoff={false},firstDone={false};long[] firstAt={-1};
        h.onEachTick(()->{
            if(f.craft.isRemoved())return;
            f.target.setPos(f.area.x+16,81,0);f.target.setDeltaMovement(Vec3.ZERO);
            for(var troop:f.troops()){
                f.observed.add(troop.getUUID());
                if(HelicopterRappel.active(troop)&&troop.getY()>84){
                    airborne[0]=true;h.assertTrue(!troop.isPassenger(),"rope descent uses an independent soldier, not a low riding seat");
                    h.assertTrue(f.craft.getY()>90,"carrier stays aloft while infantry descend");
                }
                if(!HelicopterRappel.active(troop)&&troop.onGround()&&troop.tacticsAvailable()&&troop.getTarget()==f.target)handoff[0]=true;
            }
            if(!firstDone[0]&&f.observed.size()==6&&f.craft.phase()!=BlackwatchHelicopter.Phase.UNLOAD&&f.troops().stream().noneMatch(HelicopterRappel::active)){
                firstDone[0]=true;firstAt[0]=f.area.level.getGameTime();
                var tag=new CompoundTag();f.craft.saveWithoutId(tag);h.assertTrue(tag.getCompound("AirDeployment").getInt("Cooldown")>0,"completed source wave installs a real cooldown");
            }
            if(firstDone[0]&&f.observed.size()>6)h.assertTrue(f.area.level.getGameTime()-firstAt[0]>=390,"next batch cannot bypass 20 second cooldown");
        });
        h.succeedWhen(()->{
            h.assertTrue(airborne[0]&&handoff[0]&&f.observed.size()>=12&&f.troops().stream().noneMatch(HelicopterRappel::active),"two real six-person waves, detached descent and live ground target handoff");
            h.assertTrue(f.craft.getPassengers().size()==2,"only flight crew remain aboard");
            h.assertTrue(f.troops().size()<=16,"population budget prevents per-tick unlimited generation");f.close();
        });
    }
    @GameTest(template="helicopter_arena",batch="airRappelSave",timeoutTicks=650)
    public static void midWaveSaveRestoresReceiptAndIndependentSoldierWithoutReplay(GameTestHelper h){
        var f=new Fixture(h,1);boolean[] saved={false};Set<UUID> before=new HashSet<>();
        h.onEachTick(()->{
            if(saved[0]||f.troops().size()!=2)return;
            var troop=f.troops().get(0);if(!HelicopterRappel.active(troop)||troop.getY()>90)return;
            var actorTag=new CompoundTag();troop.saveWithoutId(actorTag);double y=troop.getY();
            var carrierTag=new CompoundTag();f.craft.saveWithoutId(carrierTag);int slot=carrierTag.getCompound("AirDeployment").getInt("Slot");
            f.troops().forEach(t->before.add(t.getUUID()));saved[0]=true;
            f.craft.load(carrierTag);troop.load(actorTag); // Same object restore also exercises previous transient control release.
            var roundTrip=new CompoundTag();f.craft.saveWithoutId(roundTrip);
            h.assertTrue(roundTrip.getCompound("AirDeployment").getInt("Slot")==slot&&Math.abs(troop.getY()-y)<1e-6,"load preserves consumed spawn slots and descending position");
        });
        h.succeedWhen(()->{
            h.assertTrue(saved[0]&&f.troops().size()==6&&f.troops().stream().noneMatch(HelicopterRappel::active),"saved wave finishes once with six physical soldiers");
            h.assertTrue(f.troops().stream().map(Entity::getUUID).toList().containsAll(before),"load does not replace the original soldiers");
            h.assertTrue(f.troops().stream().allMatch(s->!s.isNoAi()&&!s.isNoGravity()&&!s.isPassenger()),"transient rappel flags do not leak into ground AI");f.close();
        });
    }
    @GameTest(template="helicopter_arena",batch="airRappelAbort",timeoutTicks=300)
    public static void destroyedCarrierReleasesDetachedTroopsAndCapacityRejectsAnotherWave(GameTestHelper h){
        var f=new Fixture(h,2);boolean[] interrupted={false};List<MilitarySoldier> falling=new ArrayList<>();
        h.onEachTick(()->{
            if(interrupted[0]||f.troops().size()!=2)return;
            for(int i=0;i<12;i++){
                var guard=ModEntities.BLACKWATCH_SOLDIER.get().create(f.area.level);guard.setNoAi(true);guard.setPos(f.area.x-18+i,81,18);guard.setAutomaticUnitJoin(false);
                f.area.level.addFreshEntity(guard);f.area.entities.add(guard);
            }
            h.assertTrue(!f.craft.deployment().roomForWave(),"real nearby population blocks another reserved batch");
            falling.addAll(f.troops());interrupted[0]=true;h.assertTrue(f.craft.hurt(f.craft.damageSources().generic(),400),"carrier destruction entered real path");
        });
        h.succeedWhen(()->{
            h.assertTrue(interrupted[0]&&falling.size()==2&&falling.stream().noneMatch(HelicopterRappel::active),"destroyed carrier cannot retain rope control");
            h.assertTrue(falling.stream().allMatch(s->!s.isNoAi()&&!s.isNoGravity()&&!s.isPassenger()),"surviving detached infantry restore AI/gravity without staying in aircraft");f.close();
        });
    }
}
