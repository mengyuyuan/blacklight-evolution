package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.entity.*;
import com.codex.blacklight.registry.*;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="blacklight")
public final class SpitterContactGameTest {
    private record Shot(int age,Vec3 at,Vec3 velocity){}
    private static final Map<UUID,List<Shot>> SHOTS=new HashMap<>();
    private static final Map<UUID,List<Vec3>> CONTACTS=new HashMap<>(),SPLASHES=new HashMap<>();
    private static final Map<UUID,BlacklightMutant> ACTORS=new HashMap<>();
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void hurt(com.codex.blacklight.combat.ForgeDamageEvent.Post e){
        if(e.getNewDamage()>0&&e.getSource().getDirectEntity() instanceof BlacklightSpit spit&&spit.getOwner() instanceof BlacklightMutant m&&CONTACTS.containsKey(m.getUUID()))CONTACTS.get(m.getUUID()).add(spit.damageContact());
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void sound(net.minecraftforge.event.PlayLevelSoundEvent.AtPosition e){
        if(e.getLevel().isClientSide||e.getSound()==null||!e.getSound().value().getLocation().toString().equals("blacklight:infection.burst"))return;
        for(var m:ACTORS.values())if(m.level()==e.getLevel()&&m.position().distanceToSqr(e.getPosition())<18*18)SPLASHES.get(m.getUUID()).add(e.getPosition());
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void join(net.minecraftforge.event.entity.EntityJoinLevelEvent e){
        if(e.getEntity() instanceof BlacklightSpit s&&s.getOwner() instanceof BlacklightMutant m){var rows=SHOTS.get(m.getUUID());if(rows!=null)rows.add(new Shot((int)m.attackAge(0),s.position(),s.getDeltaMovement()));}
    }
    private record Scene(BlacklightMutant m,Mob v){}
    private static Scene scene(GameTestHelper h,double distance){
        h.getLevel().setDayTime(18000);
        for(int x=0;x<16;x++)for(int z=0;z<16;z++){h.setBlock(x,1,z,Blocks.STONE);for(int y=2;y<10;y++)h.setBlock(x,y,z,Blocks.AIR);}
        var m=ModEntities.BLACKLIGHT_SPITTER.get().create(h.getLevel());m.setPos(h.absoluteVec(new Vec3(7.5,2,4.5)));m.setPersistenceRequired();
        m.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);m.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(.1);h.getLevel().addFreshEntity(m);
        var v=EntityType.COW.create(h.getLevel());v.setPos(m.position().add(0,0,distance));v.setNoAi(true);h.getLevel().addFreshEntity(v);
        v.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);v.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);v.setHealth(1000);
        SHOTS.put(m.getUUID(),new ArrayList<>());CONTACTS.put(m.getUUID(),new ArrayList<>());SPLASHES.put(m.getUUID(),new ArrayList<>());ACTORS.put(m.getUUID(),m);return new Scene(m,v);
    }
    private static void begin(GameTestHelper h,Scene s,MutantMove move){h.assertTrue(s.m.beginAttack(move,s.v),"attack starts");}
    private static void done(GameTestHelper h,Scene s){SHOTS.remove(s.m.getUUID());CONTACTS.remove(s.m.getUUID());SPLASHES.remove(s.m.getUUID());ACTORS.remove(s.m.getUUID());for(var p:h.getLevel().getEntitiesOfClass(BlacklightSpit.class,s.m.getBoundingBox().inflate(30)))if(p.getOwner()==s.m)p.discard();s.m.discard();s.v.discard();h.succeed();}
    private static void checkShots(GameTestHelper h,Scene s,MutantMove move,int... ages){
        var rows=SHOTS.get(s.m.getUUID());h.assertTrue(rows.stream().map(Shot::age).toList().equals(Arrays.stream(ages).boxed().toList()),"release ticks "+rows);
        for(var shot:rows){var mouth=SpitterMouth.sample(move,shot.age,s.m.position(),0);h.assertTrue(shot.at.distanceTo(mouth.lip())<1e-5,"shot starts at animated mouth");h.assertTrue(Math.abs(shot.velocity.length()-.95)<1e-5,"launch speed");}
    }
    @GameTest(template="blackwatch_arena",batch="spitter_shot",timeoutTicks=60) public static void shotStartsAtAnimatedMouthAndHits(GameTestHelper h){var s=scene(h,9);begin(h,s,MutantMove.SPITTER_SHOT);h.runAfterDelay(32,()->{checkShots(h,s,MutantMove.SPITTER_SHOT,14);h.assertTrue(s.v.getHealth()<1000,"shot reaches target");done(h,s);});}
    @GameTest(template="blackwatch_arena",batch="spitter_volley",timeoutTicks=70) public static void allVolleyReleasesUseAnimatedMouth(GameTestHelper h){var s=scene(h,9);begin(h,s,MutantMove.SPITTER_VOLLEY);h.runAfterDelay(49,()->{checkShots(h,s,MutantMove.SPITTER_VOLLEY,14,24,34);h.assertTrue(s.v.getHealth()<=992,"multiple physical impacts");done(h,s);});}
    @GameTest(template="blackwatch_arena",batch="spitter_surface",timeoutTicks=60) public static void acceptedShotHasOneSurfaceSplash(GameTestHelper h){var s=scene(h,9);begin(h,s,MutantMove.SPITTER_SHOT);h.runAfterDelay(32,()->{
        var hits=CONTACTS.get(s.m.getUUID());var sounds=SPLASHES.get(s.m.getUUID());h.assertTrue(hits.size()==1&&sounds.size()==1,"one damage contact and one splash: "+hits+" / "+sounds);
        var p=hits.get(0);h.assertTrue(p!=null&&Math.abs(p.z-s.v.getBoundingBox().minZ)<.001&&p.y>s.v.getY()+.1,"flight hits front surface rather than feet/center");h.assertTrue(p.distanceTo(sounds.get(0))<1e-6,"sound uses physical contact");done(h,s);
    });}
    @GameTest(template="blackwatch_arena",batch="spitter_immune",timeoutTicks=60) public static void invulnerableTargetStillInterceptsOneSplash(GameTestHelper h){var s=scene(h,9);begin(h,s,MutantMove.SPITTER_SHOT);s.v.setInvulnerable(true);h.runAfterDelay(32,()->{h.assertTrue(s.v.getHealth()==1000&&CONTACTS.get(s.m.getUUID()).isEmpty(),"immune target takes no accepted hit");h.assertTrue(SPLASHES.get(s.m.getUUID()).size()==1,"physical splash remains audible once");done(h,s);});}
    @GameTest(template="blackwatch_arena",batch="spitter_cover",timeoutTicks=60) public static void newCoverInterceptsReleasedProjectile(GameTestHelper h){var s=scene(h,9);begin(h,s,MutantMove.SPITTER_SHOT);
        h.runAfterDelay(5,()->{for(int x=5;x<=9;x++)for(int y=2;y<7;y++)h.setBlock(x,y,8,Blocks.STONE);});
        h.runAfterDelay(32,()->{checkShots(h,s,MutantMove.SPITTER_SHOT,14);h.assertTrue(s.v.getHealth()==1000,"new cover intercepts committed shot");h.assertTrue(h.getLevel().getEntitiesOfClass(BlacklightSpit.class,s.m.getBoundingBox().inflate(18),p->p.getOwner()==s.m).isEmpty(),"projectile splashes on wall");done(h,s);});}
    @GameTest(template="blackwatch_arena",batch="spitter_muzzle",timeoutTicks=60) public static void blockedNozzleCannotSpawnThroughBlock(GameTestHelper h){var s=scene(h,9);begin(h,s,MutantMove.SPITTER_SHOT);
        h.runAfterDelay(5,()->{var socket=SpitterMouth.sample(MutantMove.SPITTER_SHOT,14,s.m.position(),0);h.getLevel().setBlock(net.minecraft.core.BlockPos.containing(socket.throat().lerp(socket.lip(),.65)),Blocks.STONE.defaultBlockState(),3);});
        h.runAfterDelay(32,()->{checkShots(h,s,MutantMove.SPITTER_SHOT);h.assertTrue(s.v.getHealth()==1000,"no through-wall dose");done(h,s);});}
    @GameTest(template="blackwatch_arena",batch="spitter_aim",timeoutTicks=65) public static void volleyDoesNotTrackTargetAfterCommit(GameTestHelper h){var s=scene(h,9);var aim=s.v.getBoundingBox().getCenter();begin(h,s,MutantMove.SPITTER_VOLLEY);
        h.runAfterDelay(5,()->s.v.setPos(s.v.position().add(4,0,0)));
        h.runAfterDelay(49,()->{checkShots(h,s,MutantMove.SPITTER_VOLLEY,14,24,34);h.assertTrue(s.v.getHealth()==1000,"dodged committed volley");for(var shot:SHOTS.get(s.m.getUUID())){var expected=BlockThrowBallistics.solve(shot.at,aim,Vec3.ZERO,.95,.03);h.assertTrue(shot.velocity.distanceTo(expected.velocity())<1e-5,"original aim retained");}done(h,s);});}
    @GameTest(template="blackwatch_arena",batch="spitter_retreat",timeoutTicks=60) public static void retreatMovesBackWithoutAttacking(GameTestHelper h){var s=scene(h,2);var start=s.m.position();begin(h,s,MutantMove.SPITTER_RETREAT);h.runAfterDelay(29,()->{h.assertTrue(s.m.getZ()<start.z-1.5&&Math.abs(s.m.getY()-start.y)<.01,"grounded retreat");checkShots(h,s,MutantMove.SPITTER_RETREAT);h.assertTrue(s.v.getHealth()==1000,"defensive retreat");done(h,s);});}
    @GameTest(template="blackwatch_arena",batch="spitter_cliff",timeoutTicks=60) public static void retreatStopsBeforeCliff(GameTestHelper h){var s=scene(h,2);var start=s.m.position();begin(h,s,MutantMove.SPITTER_RETREAT);
        for(int x=0;x<16;x++)for(int z=0;z<4;z++)for(int y=0;y<2;y++)h.setBlock(x,y,z,Blocks.AIR);
        h.runAfterDelay(29,()->{h.assertTrue(s.m.getZ()>=h.absoluteVec(new Vec3(0,0,4)).z&&s.m.getZ()<start.z,"retreat approaches but does not cross cliff");h.assertTrue(Math.abs(s.m.getY()-start.y)<.01,"does not fall");done(h,s);});}
    @GameTest(template="blackwatch_arena",batch="spitter_retreatwall",timeoutTicks=60) public static void retreatStopsAtWall(GameTestHelper h){var s=scene(h,2);begin(h,s,MutantMove.SPITTER_RETREAT);for(int x=5;x<10;x++)for(int y=2;y<6;y++)h.setBlock(x,y,3,Blocks.STONE);
        h.runAfterDelay(29,()->{h.assertTrue(s.m.getBoundingBox().minZ>=h.absoluteVec(new Vec3(0,0,4)).z-.001,"retreat cannot enter wall");done(h,s);});}
}
