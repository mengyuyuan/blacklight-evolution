package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.power.*;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.state.BlacklightState;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class BulletdiveAimGameTest {
    private static ServerPlayer player(GameTestHelper h){
        var profile=new GameProfile(java.util.UUID.randomUUID(),"DiveAimQA");
        var p=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),profile,ClientInformation.createDefault());
        p.connection=FakePlayerFactory.get(h.getLevel(),profile).connection;p.setGameMode(GameType.SURVIVAL);
        var state=new BlacklightState();state.infect();state.setPrototypeMode(true);state.activate(PowerForm.CLAWS);state.addEvolutionPoints(10000);
        state.buyMovementUpgrade(MovementUpgrade.GLIDE_1);state.buyCombatSkill(CombatSkill.BULLETDIVE_DROP);
        p.setData(ModAttachments.PLAYER_STATE,state);p.setData(ModAttachments.SKILL_SETTINGS,new SkillSettings(false));
        return p;
    }
    private static net.minecraft.world.entity.animal.Cow victim(GameTestHelper h,Vec3 position){
        var cow=EntityType.COW.create(h.getLevel());cow.setNoAi(true);cow.setNoGravity(true);
        cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1_000_000);cow.setHealth(cow.getMaxHealth());
        cow.getPersistentData().putLong(com.codex.blacklight.infection.InfectionRules.NEXT_CONTACT_EXPOSURE,Long.MAX_VALUE);
        cow.setPos(position);h.getLevel().addFreshEntity(cow);return cow;
    }
    @GameTest(template="wall_test",timeoutTicks=40,batch="dive_lock")
    public static void explicitLockSurvivesAltitudeButNotCancelDeathOrHorizontalEscape(GameTestHelper h){
        var p=player(h);var origin=h.absolutePos(new BlockPos(4,30,4)).getBottomCenter();
        p.moveTo(origin.x,origin.y,origin.z,0,0);p.setOnGround(false);var target=victim(h,origin.add(12,0,0));
        try{
            CombatTargeting.requestLock(p,target.getId());h.assertTrue(CombatTargeting.locked(p)==target,"ordinary visible lock acquired first");
            p.setPos(origin.add(0,9_999_999,0));h.assertTrue(CombatTargeting.locked(p)==target,"ten-million-block altitude retains the selected target without a giant ray or world search");
            CombatTargeting.requestLock(p,-1);h.assertTrue(CombatTargeting.locked(p)==null,"explicit cancellation releases altitude lock");
            p.setPos(origin);CombatTargeting.requestLock(p,target.getId());p.setPos(origin.add(0,60000,0));
            target.setPos(origin.add(CombatTargeting.viewRange(p)+10,0,0));h.assertTrue(CombatTargeting.locked(p)==null,"horizontal escape still releases lock");
            p.setPos(origin);target.setPos(origin.add(12,0,0));CombatTargeting.requestLock(p,target.getId());p.setPos(origin.add(0,60000,0));
            target.setHealth(0);h.assertTrue(CombatTargeting.locked(p)==null,"dead target cannot retain guidance");
        }finally{CombatTargeting.requestLock(p,-1);p.discard();target.discard();}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=520,batch="dive_flight")
    public static void fullSixtyThousandBlockDiveGuidesToMovingTargetAndUsesRealCollision(GameTestHelper h){
        var p=player(h);var ground=h.absolutePos(new BlockPos(4,120,4)).getBottomCenter();
        // This long flight spans farther than the shared template. Previous
        // batches may leave blocks outside its cleanup bounds; prepare an actual
        // open impact volume instead of landing on another fixture's ceiling.
        for(int x=-4;x<=40;x++)for(int z=-4;z<=12;z++){
            h.getLevel().setBlock(BlockPos.containing(ground.add(x,-1,z)),Blocks.BEDROCK.defaultBlockState(),2);
            for(int y=0;y<=32;y++)h.getLevel().setBlock(BlockPos.containing(ground.add(x,y,z)),Blocks.AIR.defaultBlockState(),2);
        }
        p.moveTo(ground.x,ground.y,ground.z,-90,0);p.setOnGround(false);var target=victim(h,ground.add(20,0,0));
        var bystander=victim(h,ground.add(30,0,-3));
        var closed=new java.util.concurrent.atomic.AtomicBoolean();
        Runnable cleanup=()->{if(closed.compareAndSet(false,true)){
            DevastatorSystem.cancel(p);CombatTargeting.requestLock(p,-1);target.discard();bystander.discard();
            h.getLevel().removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);
        }};
        h.testInfo.addListener(new GameTestListener(){
            public void testStructureLoaded(GameTestInfo info){}
            public void testPassed(GameTestInfo info,GameTestRunner runner){cleanup.run();}
            public void testFailed(GameTestInfo info,GameTestRunner runner){cleanup.run();}
        });
        CombatTargeting.requestLock(p,target.getId());
        h.assertTrue(CombatTargeting.locked(p)==target,"initial visible dive target is acquired: actor="+p.position()+" target="+target.position()
                +" selectable="+CombatTargeting.selectable(p,target)+" visible="+CombatTargeting.visible(p,target));
        p.setPos(ground.add(0,60000,0));p.setData(ModAttachments.GLIDE_UNTIL,h.getLevel().getGameTime()+40);
        h.getLevel().addNewPlayer(p);
        boolean[] landed={false};double[] offset={Double.NaN},height={0},peak={0};String[] landing={"not landed"};
        for(int t=2;t<470;t++){final int time=t;h.runAfterDelay(t,()->{
            if(landed[0]){p.tickCount++;DevastatorSystem.tick(new PlayerTickEvent.Post(p));return;}
            p.tickCount++;
            if(time==120)target.setPos(ground.add(28,0,4));
            DevastatorSystem.acceptInput(p,false,time<85,false,false);
            DevastatorSystem.tick(new PlayerTickEvent.Post(p));
            var state=DevastatorSystem.snapshot(p);
            if(state!=null){
                peak[0]=Math.max(peak[0],state.peakDown());height[0]=Math.max(height[0],state.fallHeight());
                if(state.phase()==DevastatorSystem.FLIGHT){
                    var visual=p.getData(ModAttachments.DEVASTATOR_VISUAL);
                    h.assertTrue(visual.targetId()==target.getId()&&visual.diveAim()!=null,
                            "real observer snapshot contains the selected server target and aim coordinate: visual="+visual
                            +" state="+state+" selected="+p.getData(ModAttachments.TARGET_LOCK_ID)+" expected="+target.getId()
                            +" locked="+CombatTargeting.locked(p)+" actor="+p.position()+" target="+target.position()
                            +" alive="+target.isAlive()+" removed="+target.isRemoved()+" tick="+time);
                }
                if(state.landed()){
                    landed[0]=true;offset[0]=Math.hypot(p.getX()-target.getX(),p.getZ()-target.getZ());
                    landing[0]=state+" damageable="+CombatTargeting.damageable(p,target)+" visible="+CombatTargeting.visible(p,target)
                            +" timing="+DevastatorTiming.of(WeaponMove.BULLETDIVE_DROP)+" actor="+p.position()+" target="+target.position();
                    h.assertTrue(p.onGround(),"real swept block collision, not an invented impact height");return;
                }
            }
            // No real client drives this fake actor. Use vanilla's actual collision
            // move, while dispatching the production action tick on its own clock.
            p.move(MoverType.SELF,p.getDeltaMovement());
        });}
        h.runAfterDelay(480,()->{try{
            h.assertTrue(landed[0]&&offset[0]<1,"guided moving-target landing is within one block, actual="+offset[0]);
            h.assertTrue(height[0]>59999&&peak[0]>100,"uncapped vertical acceleration and the prepared impact floor are retained");
            h.assertTrue(target.getHealth()<target.getMaxHealth()&&bystander.getHealth()<bystander.getMaxHealth(),
                    "guidance retains real area damage on both the locked victim and a nearby non-locked victim: locked="
                            +target.getHealth()+" bystander="+bystander.getHealth()+" state="+DevastatorSystem.snapshot(p)+" landing="+landing[0]);
            h.succeed();
        }finally{cleanup.run();}});
    }
}
