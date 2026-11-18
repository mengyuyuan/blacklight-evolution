package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.entity.*;
import com.codex.blacklight.registry.*;
import com.codex.blacklight.power.PowerForm;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;

/** Native pick/projectile and real weapon sessions against animated receiver surfaces. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class FlyerHurtGameTest {
    private static EncounterMob hydra(GameTestHelper h){
        var m=EncounterEntities.CREATURES.get(EncounterKind.FLYER).get().create(h.getLevel());m.setNoAi(true);m.setNoGravity(true);
        m.setPos(h.absoluteVec(new Vec3(14,5,12)));m.setYRot(0);m.yBodyRot=0;m.yBodyRotO=0;m.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(2000);m.setHealth(2000);h.getLevel().addFreshEntity(m);m.updateParts();return m;
    }
    private static ServerPlayer player(GameTestHelper h){
        var profile=new GameProfile(UUID.randomUUID(),"FlyerReceiverQA");
        var p=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),profile);
        p.connection=FakePlayerFactory.get(h.getLevel(),profile).connection;p.setNoGravity(true);h.getLevel().addNewPlayer(p);return p;
    }
    private static HydraContact.Region head(EncounterMob m){return m.hurtRegions(0).stream().filter(r->r.bone().equals("left_hand")).findFirst().orElseThrow();}
    private static void aim(ServerPlayer p,Vec3 eye,Vec3 at){
        p.setPos(eye.add(0,-p.getEyeHeight(),0));var d=at.subtract(eye);p.setYRot((float)Math.toDegrees(Math.atan2(-d.x,d.z)));p.setXRot((float)-Math.toDegrees(Math.atan2(d.y,d.horizontalDistance())));p.yBodyRot=p.yBodyRotO=p.getYRot();p.yRotO=p.getYRot();
    }
    private static void cleanup(EncounterMob m,ServerPlayer p){WeaponCombatSystem.cancel(p);m.discard();p.serverLevel().removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);}
    private static EntityHitResult pick(ServerPlayer p,Vec3 a,Vec3 b,java.util.function.Predicate<Entity> filter){return ProjectileUtil.getEntityHitResult(p,a,b,new AABB(a,b).inflate(.1),filter,a.distanceToSqr(b));}
    @GameTest(template="blackwatch_facility_arena",batch="flyer_incoming_ray",timeoutTicks=30)
    public static void nativeHeadRayDamageAndSharedInvulnerability(GameTestHelper h){
        var m=hydra(h);var p=player(h);
        try{
            var center=head(m).boxes().get(0).center;var from=center.add(3,0,0);aim(p,from,center);
            var hit=pick(p,from,center,e->e instanceof FlyerPart part&&part.getParent()==m);
            h.assertTrue(hit!=null,"native ray selects animated wing outside root");
            h.assertTrue(m.getBoundingBox().clip(from,center).isEmpty(),"fixture misses the obsolete root column");
            var part=(FlyerPart)hit.getEntity();float hp=m.getHealth();
            h.assertTrue(part.hurt(p.damageSources().playerAttack(p),4),"native part forwards accepted damage");
            h.assertTrue(m.getHealth()<hp&&m.incomingEffects()==1,"one real health change and one local receipt");
            h.assertTrue(m.incomingPoint().distanceTo(hit.getLocation())<.001,"blood originates at exact ray entry");
            h.assertTrue(!part.hurt(p.damageSources().playerAttack(p),4)&&m.incomingEffects()==1,"overlap cannot bypass parent immunity");
            int id=m.getId();for(int i=0;i<m.flyerParts().length;i++)h.assertTrue(m.flyerParts()[i].getId()==id+i+1&&!m.flyerParts()[i].shouldBeSaved(),"deterministic proxies never save as extra creatures");
            h.succeed();
        }finally{cleanup(m,p);}
    }
    @GameTest(template="blackwatch_facility_arena",batch="flyer_incoming_projectile",timeoutTicks=30)
    public static void actualArrowHitsExtendedHeadAndEmitsOnce(GameTestHelper h){
        var m=hydra(h);var p=player(h);var arrow=EntityType.ARROW.create(h.getLevel());
        try{
            var center=head(m).boxes().get(0).center;var from=center.add(4,0,0);aim(p,from.add(1,0,0),center);
            arrow.setPos(from);arrow.setOwner(p);arrow.setNoGravity(true);arrow.setDeltaMovement(center.subtract(from).normalize().scale(5));arrow.setBaseDamage(2);h.getLevel().addFreshEntity(arrow);
            float hp=m.getHealth();arrow.tick();h.assertTrue(m.getHealth()<hp&&m.incomingEffects()==1,"native arrow tick damages the visible wing exactly once");
            h.assertTrue(m.hurtRegions(0).stream().flatMap(r->r.boxes().stream()).anyMatch(b->b.nearest(m.incomingPoint()).distanceToSqr(m.incomingPoint())<.0001),"projectile receipt lies on the first actual receiving surface");h.succeed();
        }finally{arrow.discard();cleanup(m,p);}
    }
    @GameTest(template="blackwatch_facility_arena",batch="flyer_incoming_slow",timeoutTicks=30)
    public static void slowUnownedArrowUsesTheSameNativeRayOnLaterTicks(GameTestHelper h){
        var m=hydra(h);var p=player(h);var arrow=EntityType.ARROW.create(h.getLevel());
        try{
            m.updateParts();var center=head(m).boxes().get(0).center;
            arrow.setPos(center.add(5,0,0));arrow.setNoGravity(true);arrow.setDeltaMovement(-1.5,0,0);arrow.setBaseDamage(2);h.getLevel().addFreshEntity(arrow);
            for(int t=0;t<6&&!arrow.isRemoved();t++){
                var source=m.damageSources().arrow(arrow,arrow);var hit=ProjectileUtil.getEntityHitResult(h.getLevel(),arrow,arrow.position(),arrow.position().add(arrow.getDeltaMovement()),arrow.getBoundingBox().expandTowards(arrow.getDeltaMovement()).inflate(1),e->e instanceof FlyerPart);
                if(hit!=null){var part=(FlyerPart)hit.getEntity();com.codex.blacklight.BlacklightNeoMod.LOGGER.info("Slow Hydra arrow tick={} part={} position={} speed={} selected={} receiver={}",t,part.slot,arrow.position(),arrow.getDeltaMovement(),hit.getLocation(),HydraHurtContact.nativeHit(m,part.slot,source));}
                arrow.tick();
            }
            h.assertTrue(m.incomingEffects()==1,"later slow arrow tick accepted once, final position="+arrow.position()+" speed="+arrow.getDeltaMovement());h.succeed();
        }finally{arrow.discard();cleanup(m,p);}
    }
    @GameTest(template="blackwatch_facility_arena",batch="flyer_incoming_sword",timeoutTicks=30)
    public static void vanillaSwordAttackUsesNativePartAndConfirmedWound(GameTestHelper h){
        var m=hydra(h);var p=player(h);
        try{
            var center=head(m).boxes().get(0).center;aim(p,center.add(3,0,0),center);p.setItemSlot(EquipmentSlot.MAINHAND,Items.IRON_SWORD.getDefaultInstance());
            for(int i=0;i<25;i++)p.tick();aim(p,center.add(3,0,0),center);
            var hit=pick(p,p.getEyePosition(),center,e->e instanceof FlyerPart part&&part.getParent()==m);h.assertTrue(hit!=null,"native sword can select head");
            float hp=m.getHealth();p.attack(hit.getEntity());h.assertTrue(m.getHealth()<hp&&m.incomingEffects()==1,"real Player.attack reaches shared parent damage and local feedback");
            h.assertTrue(m.incomingPoint().distanceTo(hit.getLocation())<.001,"sword feedback uses aimed surface");h.succeed();
        }finally{cleanup(m,p);}
    }
    @GameTest(template="blackwatch_facility_arena",batch="flyer_incoming_miss",timeoutTicks=30)
    public static void emptyBroadCornerDoesNotOccludeAnotherEntity(GameTestHelper h){
        var m=hydra(h);var p=player(h);net.minecraft.world.entity.animal.Cow behind=null;
        try{
            Vec3 from=null,to=null;
            outer:for(var part:m.flyerParts()){
                var b=part.getBoundingBox();for(double fx:new double[]{.01,.99})for(double fy:new double[]{.01,.99}){
                    var a=new Vec3(b.minX+b.getXsize()*fx,b.minY+b.getYsize()*fy,b.minZ-.2);var z=new Vec3(a.x,a.y,b.maxZ+.2);
                    if(Arrays.stream(m.flyerParts()).allMatch(q->q.clip(a,z).isEmpty())){from=a;to=z;break outer;}
                }
            }
            h.assertTrue(from!=null,"fixture crosses a broad proxy through an actual empty corner");
            aim(p,from,to);h.assertTrue(pick(p,from,to,e->e instanceof FlyerPart part&&part.getParent()==m)==null,"native picking rejects empty corner");
            h.assertTrue(ProjectileUtil.getEntityHitResult(h.getLevel(),p,from,to,new AABB(from,to).inflate(.1),e->e instanceof FlyerPart part&&part.getParent()==m)==null,"projectile overload also rejects empty corner before arrows bounce off it");
            behind=EntityType.COW.create(h.getLevel());behind.setNoAi(true);behind.setNoGravity(true);behind.setPos(to.add(0,-.5,1));h.getLevel().addFreshEntity(behind);
            var cow=behind;var hit=pick(p,from,to.add(0,0,2),e->e==cow||e instanceof FlyerPart part&&part.getParent()==m);
            h.assertTrue(hit!=null&&hit.getEntity()==cow,"transparent corner retains native nearest-target ordering");h.succeed();
        }finally{if(behind!=null)behind.discard();cleanup(m,p);}
    }
    @GameTest(template="blackwatch_facility_arena",batch="flyer_incoming_block",timeoutTicks=30)
    public static void wallAndInvulnerabilityCannotEmitBlood(GameTestHelper h){
        var m=hydra(h);var p=player(h);net.minecraft.core.BlockPos wall=null;
        try{
            var center=head(m).boxes().get(0).center;var from=center.add(3,0,0);aim(p,from,center);
            var hit=pick(p,from,center,e->e instanceof FlyerPart part&&part.getParent()==m);h.assertTrue(hit!=null,"head visible before obstruction");
            var part=(FlyerPart)hit.getEntity();float hp=m.getHealth();m.setInvulnerable(true);
            h.assertTrue(!part.hurt(p.damageSources().playerAttack(p),8)&&m.incomingEffects()==0,"immune contact has no blood");m.setInvulnerable(false);
            wall=net.minecraft.core.BlockPos.containing(from.lerp(hit.getLocation(),.5));h.getLevel().setBlock(wall,Blocks.STONE.defaultBlockState(),2);
            h.assertTrue(!part.hurt(p.damageSources().playerAttack(p),8)&&m.getHealth()==hp&&m.incomingEffects()==0,"wall blocks damage and feedback");h.succeed();
        }finally{if(wall!=null)h.getLevel().setBlock(wall,Blocks.AIR.defaultBlockState(),2);cleanup(m,p);}
    }
    @GameTest(template="blackwatch_facility_arena",batch="flyer_incoming_forms",timeoutTicks=60)
    public static void clawsBladeAndHammerReachAnimatedSkin(GameTestHelper h) throws Exception {
        var m=hydra(h);var p=player(h);boolean old=WeaponCombatSystem.actorHitstop(false);
        try{
            var state=ModAttachments.getData(p,ModAttachments.PLAYER_STATE);state.infect();state.setPrototypeMode(true);
            var start=WeaponCombatSystem.class.getDeclaredMethod("start",ServerPlayer.class,WeaponMove.class,boolean.class,WeaponMove.Input.class,float.class);start.setAccessible(true);
            for(var move:new WeaponMove[]{WeaponMove.CLAW_AUTO1,WeaponMove.BLADE_CUT,WeaponMove.HAMMER_JAB}){
                WeaponCombatSystem.cancel(p);state.unlock(move.form);state.activate(move.form);m.setHealth(m.getMaxHealth());m.invulnerableTime=0;
                var center=head(m).boxes().get(0).center;
                // Calibrated stance: the claw's outer fingers and the blade's side
                // enter the thin wing rather than passing below an eye-height target.
                double lateral=move==WeaponMove.CLAW_AUTO1?-1.2:move==WeaponMove.BLADE_CUT?-.6:0;
                aim(p,center.add(lateral,.65,1.5),center.add(lateral,.65,0));
                p.setOnGround(true);p.tickCount+=100;
                int effects=m.incomingEffects();start.invoke(null,p,move,false,WeaponMove.Input.ATTACK,0F);int began=p.tickCount;
                for(int t=0;t<=move.duration+3;t++){p.tickCount=began+t;p.setOnGround(true);WeaponCombatSystem.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,p));}
                h.assertTrue(m.getHealth()<m.getMaxHealth()&&m.incomingEffects()>effects,"real "+move.id+" session reaches actual wing");
                h.assertTrue(m.hurtRegions(0).stream().flatMap(r->r.boxes().stream()).anyMatch(b->b.nearest(m.incomingPoint()).distanceTo(m.incomingPoint())<.08),"weapon feedback stays on an actual animated receiving surface");
            }h.succeed();
        }finally{WeaponCombatSystem.actorHitstop(old);cleanup(m,p);}
    }

    @GameTest(template="blackwatch_facility_arena",batch="flyer_controlled_hurt",timeoutTicks=80)
    public static void weaponStunPreservesSourceHurtAndRecovers(GameTestHelper h){
        var m=hydra(h);var p=player(h);m.setPersistenceRequired();m.setNoAi(false);
        m.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE).setBaseValue(.05);
        m.hurt(m.damageSources().generic(),1);
        ImpactControl.begin(m,new ImpactProfile(HitReaction.HEAVY,ImpactProfile.BODY,40,.2,0,false),0);
        h.runAtTickTime(5,()->{
            h.assertTrue(ImpactControl.stunned(m),"actual victim controller is active");
            h.assertTrue(m.flyerStage()==FlyerMotion.HIT_FRONT||m.flyerStage()==FlyerMotion.HIT_BACK,"disabling attack AI must preserve the source hurt pose");
            h.assertTrue(m.sourcePoseCommitted(),"receiver and renderer must agree on authored flinch");
        });
        h.runAtTickTime(10,()->{m.invulnerableTime=0;h.assertTrue(m.hurt(m.damageSources().generic(),1)&&m.flyerAge(0)==0,"a second accepted hit restarts its reaction");});
        h.runAtTickTime(32,()->h.assertTrue(m.flyerStage()==FlyerMotion.RECOVER,"source hurt advances once into recovery while AI is stunned"));
        h.runAtTickTime(62,()->{
            h.assertTrue(!ImpactControl.stunned(m)&&m.flyerStage()==FlyerMotion.IDLE,"stun and source hurt recover without replaying a dive");
            cleanup(m,p);h.succeed();
        });
    }
    @GameTest(template="blackwatch_facility_arena",batch="flyer_physical_receiver",timeoutTicks=110)
    public static void heavyBodyFallsWithReceiverAndAcceptsFollowup(GameTestHelper h){
        for(int x=8;x<=20;x++)for(int z=6;z<=18;z++)for(int y=2;y<=12;y++)h.setBlock(new net.minecraft.core.BlockPos(x,y,z),y==2?Blocks.STONE:Blocks.AIR);
        var m=hydra(h);var p=player(h);m.setPersistenceRequired();
        double start=m.getY(),floor=h.absolutePos(new net.minecraft.core.BlockPos(14,2,12)).getY()+1;
        m.hurt(m.damageSources().generic(),1);
        ImpactControl.begin(m,new ImpactProfile(HitReaction.HEAVY,ImpactProfile.FALL,80,.3,.2,true),0);
        RagdollImpact.send(m,ImpactProfile.FALL,80,false,new Vec3(.02,.05,0));
        h.assertTrue(m.flyerPhysics().active()&&FlyerPhysics.hasPose(m),"accepted heavy hit owns a synchronized physical body");
        h.runAtTickTime(12,()->{
            h.assertTrue(m.getY()<start-.8,"server carrier falls with visible body, y="+m.getY()+" start="+start);
            m.updateParts();var center=head(m).boxes().get(0).center;var from=center.add(4,0,0);aim(p,from.add(1,0,0),center);
            var arrow=EntityType.ARROW.create(h.getLevel());arrow.setPos(from);arrow.setOwner(p);arrow.setNoGravity(true);arrow.setDeltaMovement(center.subtract(from).normalize().scale(5));arrow.setBaseDamage(2);h.getLevel().addFreshEntity(arrow);m.invulnerableTime=0;float hp=m.getHealth();int count=m.incomingEffects();arrow.tick();arrow.discard();
            h.assertTrue(m.getHealth()<hp&&m.incomingEffects()==count+1,"real follow-up arrow reaches the fallen animated wing");
            h.assertTrue(m.hurtRegions(0).stream().flatMap(r->r.boxes().stream()).anyMatch(b->b.nearest(m.incomingPoint()).distanceTo(m.incomingPoint())<.001),"follow-up blood remains on the physical surface");
        });
        h.runAtTickTime(26,()->{
            double lowest=m.hurtRegions(0).stream().flatMap(r->r.boxes().stream()).mapToDouble(b->b.bounds.minY).min().orElseThrow();
            h.assertTrue(lowest>=floor-.015&&lowest<floor+.5,"physical mesh rests on real terrain, min="+lowest+" floor="+floor);
            h.assertTrue(ModAttachments.peekData(m,ModAttachments.DISMEMBER,0)==0,"nonlethal heavy hit cannot sever living wings");
        });
        h.runAtTickTime(100,()->{h.assertTrue(!m.flyerPhysics().active()&&!FlyerPhysics.hasPose(m),"physical recovery returns ownership to source flight");cleanup(m,p);h.succeed();});
    }
    @GameTest(template="blackwatch_facility_arena",batch="flyer_physical_hold",timeoutTicks=25)
    public static void hitstopRepeatedImpulseAndLethalHandoffRetainPose(GameTestHelper h){
        var m=hydra(h);var p=player(h);m.setPersistenceRequired();
        var point=head(m).boxes().get(0).center;
        m.flyerPhysics().impact(new Vec3(.25,.1,.1),point,40,2);
        var held=m.flyerBodyData().getIntArray("pose").clone();var position=m.position();
        m.flyerPhysics().tick();
        h.assertTrue(Arrays.equals(held,m.flyerBodyData().getIntArray("pose"))&&m.position().distanceTo(position)<1e-8,"hitstop freezes actual articulated body and carrier together");
        h.runAtTickTime(6,()->{
            h.assertTrue(m.position().distanceTo(position)>.1,"stored impulse resumes after freeze");
            var before=m.flyerBodyData().getIntArray("pose").clone();var at=m.position();
            m.flyerPhysics().impact(new Vec3(-.3,.2,0),head(m).boxes().get(0).center,40,0);
            h.assertTrue(Arrays.equals(before,m.flyerBodyData().getIntArray("pose"))&&m.position().distanceTo(at)<1e-8,"second impulse changes momentum without rebuilding the flying pose");
        });
        h.runAtTickTime(10,()->{
            var before=m.flyerBodyData().getIntArray("pose").clone();m.setHealth(0);m.flyerPhysics().tick();
            h.assertTrue(!m.flyerPhysics().active()&&!FlyerPhysics.ownsLive(m)&&Arrays.equals(before,m.flyerBodyData().getIntArray("pose")),"lethal handoff retains the last skin while ending live physics ownership");
            cleanup(m,p);h.succeed();
        });
    }
    @GameTest(template="blackwatch_facility_arena",batch="flyer_incoming_pose",timeoutTicks=30)
    public static void bodyProxiesFollowPitchYawAndRetainSingleParent(GameTestHelper h){
        var m=hydra(h);var p=player(h);
        try{
            var before=head(m).bounds().getCenter();
            h.assertTrue(m.hurtRegions(-1).stream().allMatch(r->r.bounds().getCenter().distanceTo(m.position())<4),"spawn cannot create a sweep from the world origin");m.flightPitch(35);m.setYRot(80);m.yBodyRot=m.yBodyRotO=80;m.flyerStage(FlyerMotion.RAM);m.updateParts();
            var after=head(m).bounds().getCenter();h.assertTrue(before.distanceTo(after)>.3,"receiver follows flight pitch and heading");
            h.assertTrue(!m.isPickable()&&CombatTargeting.multipart(m)&&!CombatTargeting.anchoredBody(m),"query parts do not create a second root hit or heavy control immunity");
            for(var part:m.flyerParts())h.assertTrue(CombatTargeting.resolveTarget(part)==m&&!part.shouldBeSaved(),"all parts resolve to one living parent");
            h.assertTrue(!ConsumeSystem.grappleProtected(m),"receiving proxies do not prohibit grabbing a living flyer");h.succeed();
        }finally{cleanup(m,p);}
    }
}
