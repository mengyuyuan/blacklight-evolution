package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.power.*;
import com.codex.blacklight.registry.ModAttachments;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.*;
import java.util.UUID;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class WeaponCombatGameTest {
    @GameTest(template="wall_test",timeoutTicks=40)
    public static void committedSpecialOwnsMovementUntilRecovery(GameTestHelper h){
        var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"special-lock"));
        p.setPos(h.absoluteVec(new Vec3(4,2,3)));p.setYRot(0);p.setOnGround(true);p.tickCount=100;
        var state=p.getData(ModAttachments.PLAYER_STATE);state.infect();state.setPrototypeMode(true);state.unlock(PowerForm.BLADE);state.activate(PowerForm.BLADE);
        var movement=p.getData(ModAttachments.MOVEMENT_STATE);movement.reset();
        p.setDeltaMovement(.35,-.02,.65);
        ClawCombatSystem.handle(p,com.codex.blacklight.network.ClawActionPayload.Action.WEAPON_SPECIAL);
        h.assertTrue(p.getDeltaMovement().horizontalDistanceSqr()<.000001,"Special replaces incoming sprint inertia once at start");
        h.assertTrue(Math.abs(p.getDeltaMovement().y+.02)<.000001,"ground action takeover preserves vertical velocity");
        for(int t=1;t<WeaponMove.BLADE_RUSH.duration&&WeaponCombatSystem.busy(p);t++){
            p.tickCount++;p.setDeltaMovement(Vec3.ZERO);p.setOnGround(true);
            com.codex.blacklight.movement.PrototypeMovementSystem.acceptInput(p,false,false,1,1,true,false);
            com.codex.blacklight.movement.PrototypeMovementSystem.onPlayerTick(new PlayerTickEvent.Post(p));
            h.assertTrue(movement.chargeTicks()==0&&!movement.consumeDashRequest(),"jump/air-dash cannot queue over Special");
            h.assertTrue(p.getDeltaMovement().horizontalDistanceSqr()<.000001,"regular sprint must not add propulsion on top of action Coord");
            h.assertTrue(p.getData(ModAttachments.WEAPON_VISUAL).spec()==WeaponMove.BLADE_RUSH,"Special retains its authoritative action");
            WeaponCombatSystem.tick(new PlayerTickEvent.Post(p));
        }
        p.tickCount++;WeaponCombatSystem.tick(new PlayerTickEvent.Post(p));
        h.assertTrue(!WeaponCombatSystem.busy(p),"complete recovery releases the movement lock");
        com.codex.blacklight.movement.PrototypeMovementSystem.acceptInput(p,false,false,0,0,false,false);
        com.codex.blacklight.movement.PrototypeMovementSystem.acceptInput(p,true,false,0,0,false,false);
        h.assertTrue(movement.chargeTicks()>0,"a fresh jump after recovery works normally");
        movement.reset();WeaponCombatSystem.cancel(p);h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=40)
    public static void queuedComboLinksFromFollowThroughAndKeepsBothFullHits(GameTestHelper h){
        for(int x=0;x<=12;x++)for(int z=0;z<=12;z++)h.setBlock(x,1,z,net.minecraft.world.level.block.Blocks.STONE);
        for(var form:new PowerForm[]{PowerForm.BLADE,PowerForm.HAMMERFISTS,PowerForm.WHIPFIST,PowerForm.MUSCLE_MASS}){
            var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"combo-qa"));
            var home=h.absoluteVec(new Vec3(4,2,3));p.setPos(home);p.setYRot(0);p.setOnGround(true);p.tickCount=100;
            var state=p.getData(ModAttachments.PLAYER_STATE);state.infect();state.setPrototypeMode(true);state.unlock(form);state.activate(form);
            var first=WeaponMove.choose(form,false,false,false,false);
            var second=WeaponMove.choose(form,false,false,false,true);
            var target=h.spawn(EntityType.IRON_GOLEM,4,2,5);target.setNoAi(true);
            target.setPos(home.add(0,0,20));float before=target.getHealth();int hits=0;
            WeaponCombatSystem.request(p,false);
            int link=first.comboLinkTick();
            for(int t=1;t<=link+second.duration+2;t++){
                if(target.invulnerableTime>0)target.invulnerableTime--;
                p.tickCount++;
                if(t==first.start){
                    // Repeated presses occupy one buffer slot, never create an unrequested third hit.
                    WeaponCombatSystem.request(p,false);WeaponCombatSystem.request(p,false);
                }
                // Enter on the last valid frame: early linking must neither discard this hit
                // nor reduce the next strike to vanilla's damage-difference response.
                if(t==first.end-1)target.setPos(home.add(0,0,2));
                float previous=target.getHealth();WeaponCombatSystem.tick(new PlayerTickEvent.Post(p));
                if(target.getHealth()<previous){
                    hits++;
                    var hitMove=t<link?first:second;int elapsed=t<link?t:t-link;
                    h.assertTrue(hitMove.contactIndex(elapsed)>=0,"queued hit stays inside its own window");
                    h.assertTrue(Math.abs(previous-target.getHealth()-hitMove.damage)<.01,"queued strike deals full declared damage");
                }
                var visual=p.getData(ModAttachments.WEAPON_VISUAL).spec();
                if(t<link)h.assertTrue(visual==first,"first action remains through contact and follow-through");
                else if(t<link+second.duration)h.assertTrue(visual==second&&WeaponCombatSystem.busy(p),"direct active-to-active link with no idle state: form="+form+" t="+t+" link="+link+" duration="+second.duration+" visual="+visual);
            }
            h.assertTrue(hits==2&&Math.abs(before-target.getHealth()-(first.damage+second.damage))<.01,"one full hit per queued action: "+form);
            h.assertTrue(!WeaponCombatSystem.busy(p)&&p.getData(ModAttachments.CLAW_COMBAT).action()==ClawCombatState.Action.IDLE,"queue is consumed once and normal recovery returns to idle");
            WeaponCombatSystem.cancel(p);target.discard();
        }
        h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=40)
    public static void comboHistoryExpiresAndCannotSurviveCancellationOrFormChange(GameTestHelper h){
        MovingCombatGameTest.supportFloor(h,1);
        var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"combo-reset"));
        p.setPos(h.absoluteVec(new Vec3(4,2,3)));p.setYRot(0);p.setOnGround(true);p.tickCount=100;
        var state=p.getData(ModAttachments.PLAYER_STATE);state.infect();state.setPrototypeMode(true);state.unlock(PowerForm.BLADE);state.unlock(PowerForm.HAMMERFISTS);state.activate(PowerForm.BLADE);
        WeaponCombatSystem.request(p,false);advance(p,WeaponMove.BLADE_CUT.duration);
        WeaponCombatSystem.request(p,false);
        h.assertTrue(p.getData(ModAttachments.WEAPON_VISUAL).spec()==WeaponMove.BLADE_RETURN,"brief completed recovery retains second strike");
        WeaponCombatSystem.cancel(p);WeaponCombatSystem.request(p,false);
        h.assertTrue(p.getData(ModAttachments.WEAPON_VISUAL).spec()==WeaponMove.BLADE_CUT,"explicit cancellation restarts the chain");
        advance(p,WeaponMove.BLADE_CUT.start);WeaponCombatSystem.request(p,false);WeaponCombatSystem.cancel(p);
        WeaponCombatSystem.request(p,false);advance(p,WeaponMove.BLADE_CUT.duration);
        h.assertTrue(!WeaponCombatSystem.busy(p),"cancelled queue cannot trigger a later attack");
        p.tickCount+=10;WeaponCombatSystem.request(p,false);
        h.assertTrue(p.getData(ModAttachments.WEAPON_VISUAL).spec()==WeaponMove.BLADE_CUT,"idle grace expiration restarts first strike");
        advance(p,WeaponMove.BLADE_CUT.duration);state.activate(PowerForm.HAMMERFISTS);WeaponCombatSystem.request(p,false);
        h.assertTrue(p.getData(ModAttachments.WEAPON_VISUAL).spec()==WeaponMove.HAMMER_JAB,"weapon change cannot inherit blade second-strike history");
        WeaponCombatSystem.cancel(p);h.succeed();
    }
    private static void advance(net.minecraft.server.level.ServerPlayer p,int ticks){
        for(int i=0;i<ticks;i++){p.tickCount++;WeaponCombatSystem.tick(new PlayerTickEvent.Post(p));}
    }
    @GameTest(template="wall_test",timeoutTicks=40)
    public static void adaptedMotionsKeepSingleServerHitAndRecovery(GameTestHelper h){
        for(var referenced:WeaponMove.values()){
            // Aliases reuse the same dependency wrapper; their input ordering is
            // covered separately by explicit chain and charge tests.
            if(referenced.fallback()!=referenced)continue;
            if(WeaponMotionProfile.forMove(referenced)==null)continue;
            // R26 routes muscle Special to the native knee and ordinary aerial
            // Attack to the shared opener. Exercise those actual input routes.
            var move=referenced==WeaponMove.MUSCLE_UPPERCUT?WeaponMove.COMBO_KNEE:
                    referenced==WeaponMove.MUSCLE_AIR?WeaponMove.AIR_AUTO1:referenced;
            var animation=com.codex.blacklight.animation.MotionLibrary.clip(move.id);
            var definition=LocalWeaponMotion.find(move.id).orElseThrow();
            h.assertTrue(animation.joints().contains("Coord")&&animation.joints().contains("Root"),"own full-body pose and physical coordinate track exist: "+move);
            h.assertTrue(animation.duration()>0&&definition.contactStartTick()==move.start
                    &&definition.contactEndTick()==move.end,"dedicated-server clip shares authoritative contact timing: "+move);
            var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"motion-qa"));
            var home=h.absoluteVec(new Vec3(4,2,3));p.setPos(home);p.setYRot(0);p.setOnGround(true);p.tickCount=100;
            var state=p.getData(ModAttachments.PLAYER_STATE);state.infect();state.setPrototypeMode(true);state.unlock(referenced.form);state.activate(referenced.form);
            if(move==WeaponMove.BLADE_RETURN||move==WeaponMove.HAMMER_HOOK||move==WeaponMove.MUSCLE_CROSS){
                WeaponCombatSystem.request(p,false);
                int firstDuration=WeaponMove.choose(move.form,false,false,false,false).duration;
                for(int i=0;i<firstDuration;i++){p.tickCount++;WeaponCombatSystem.tick(new PlayerTickEvent.Post(p));}
            }
            p.setSprinting(move.rush());p.setOnGround(referenced!=WeaponMove.MUSCLE_AIR);
            var target=h.spawn(EntityType.IRON_GOLEM,4,2,5);target.setNoAi(true);float before=target.getHealth();
            // FakePlayer has no client Coord playback; exercise the near blade contact here.
            if(move==WeaponMove.BLADE_RUSH||move==WeaponMove.BLADE_RETURN)target.setPos(home.add(0,0,1.5));
            WeaponCombatSystem.request(p,referenced==WeaponMove.MUSCLE_UPPERCUT||move==WeaponMove.BLADE_RUSH);
            h.assertTrue(p.getData(ModAttachments.WEAPON_VISUAL).spec()==move,"correct adapted action selected: "+move);
            int hits=0;
            for(int t=1;t<=move.duration+2;t++){
                if(target.invulnerableTime>0)target.invulnerableTime--;
                p.tickCount++;float last=target.getHealth();WeaponCombatSystem.tick(new PlayerTickEvent.Post(p));
                if(target.getHealth()<last){hits++;h.assertTrue(move.contactIndex(t)>=0,"adapted contact is in server window: "+move);}
            }
            h.assertTrue(hits==1&&Math.abs(before-target.getHealth()-move.damageFor(referenced.form))<.01,"one damage event for "+move+", got "+hits);
            h.assertTrue(!WeaponCombatSystem.busy(p),"adapted recovery ends: "+move);
            target.discard();
        }
        h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=40)
    public static void bladeSpecialUsesDashOnceFromStandingAndSprintAndWhipReachesBehind(GameTestHelper h){
        for(int x=0;x<=12;x++)for(int z=0;z<=12;z++)h.setBlock(x,1,z,net.minecraft.world.level.block.Blocks.STONE);
        var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"frenzy-qa"));
        Vec3 home=h.absoluteVec(new Vec3(5,2,5));p.setPos(home);p.setYRot(0);p.setOnGround(true);p.tickCount=100;
        var state=p.getData(ModAttachments.PLAYER_STATE);state.infect();state.setPrototypeMode(true);state.unlock(PowerForm.BLADE);state.activate(PowerForm.BLADE);
        var move=WeaponMove.BLADE_RUSH;
        for(boolean sprint:new boolean[]{false,true}){
            p.setPos(home);p.setOnGround(true);p.setSprinting(sprint);
            var target=h.spawn(EntityType.IRON_GOLEM,5,2,7);target.setNoAi(true);float before=target.getHealth();
            // These synchronous FakePlayer tests run authoritative combat ticks, not the
            // client's EF Coord movement. Keep a fixed near target inside the actual blade
            // envelope; a 3-block moving interception is covered by BladeSpecialPreview.
            // Set an exact position: h.spawn(int,int,int) otherwise adds a half-block offset.
            target.setPos(home.add(0,0,1.5));
            var outsideBlade=LocalWeaponMotion.find(move.id).filter(definition->!definition.contactBounds().isEmpty()).isPresent()
                    ?h.spawn(EntityType.IRON_GOLEM,5,2,9):null;
            float outsideBefore=outsideBlade==null?0:outsideBlade.getHealth();
            if(outsideBlade!=null){outsideBlade.setNoAi(true);outsideBlade.setPos(home.add(0,0,4.5));}
            ClawCombatSystem.handle(p,com.codex.blacklight.network.ClawActionPayload.Action.WEAPON_SPECIAL);
            h.assertTrue(p.getData(ModAttachments.WEAPON_VISUAL).spec()==move,"real Special intent must select dash from standing or sprint");
            int contacts=0;float previous=before;
            for(int t=1;t<=move.duration+2;t++){
                if(target.invulnerableTime>0)target.invulnerableTime--;
                p.tickCount++;
                // A repeated Special packet cannot restart or enqueue the action.
                if(t==move.start)ClawCombatSystem.handle(p,com.codex.blacklight.network.ClawActionPayload.Action.WEAPON_SPECIAL);
                WeaponCombatSystem.tick(new PlayerTickEvent.Post(p));
                float now=target.getHealth();
                if(t<move.start)h.assertTrue(now==before,"Special anticipation cannot damage the near target");
                if(now<previous){contacts++;h.assertTrue(move.contactIndex(t)>=0,"dash damage stays inside its contact window");}
                previous=now;
            }
            h.assertTrue(contacts==1&&Math.abs(before-target.getHealth()-move.damage)<.01,"one hit per target in this Mod's dash policy");
            if(outsideBlade!=null){
                h.assertTrue(outsideBlade.getHealth()==outsideBefore,"broad reach alone cannot hit beyond the blade envelope without Coord displacement");
                outsideBlade.discard();
            }
            h.assertTrue(!WeaponCombatSystem.busy(p)&&p.getData(ModAttachments.WEAPON_VISUAL).spec()==null,"Special fully recovers without an unrequested second action");
            target.discard();
        }
        p.setSprinting(false);state.unlock(PowerForm.WHIPFIST);state.activate(PowerForm.WHIPFIST);
        WeaponCombatSystem.request(p,false);advance(p,WeaponMove.WHIP_LASH.duration);
        var behind=h.spawn(EntityType.IRON_GOLEM,5,2,2);behind.setNoAi(true);float before=behind.getHealth();
        WeaponCombatSystem.request(p,true);
        h.assertTrue(p.getData(ModAttachments.WEAPON_VISUAL).spec()==WeaponMove.WHIP_SWEEP,"Special follows a combo opportunity into sweep");
        for(int t=1;t<=WeaponMove.WHIP_SWEEP.duration;t++){p.tickCount++;WeaponCombatSystem.tick(new PlayerTickEvent.Post(p));}
        h.assertTrue(Math.abs(before-behind.getHealth()-12)<.01,"full sweep reaches a rear target once");
        behind.discard();h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=40)
    public static void eachWeaponHasTimedDamageAndFormSwitchCancelsOldContacts(GameTestHelper h){
        for(var form:new PowerForm[]{PowerForm.BLADE,PowerForm.HAMMERFISTS,PowerForm.WHIPFIST}){
            for(int x=1;x<12;x++)for(int z=1;z<12;z++)h.setBlock(x,1,z,Blocks.STONE);
            var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"weapon-qa"));
            p.setPos(h.absoluteVec(new Vec3(4,2,3)));p.setYRot(0);p.setOnGround(true);p.tickCount=100;
            var state=p.getData(ModAttachments.PLAYER_STATE);state.infect();state.setPrototypeMode(true);state.unlock(form);state.activate(form);
            var mob=h.spawn(EntityType.IRON_GOLEM,4,2,5);mob.setNoAi(true);float before=mob.getHealth();
            WeaponCombatSystem.request(p,false);var move=p.getData(ModAttachments.WEAPON_VISUAL).spec();
            h.assertTrue(move!=null&&move.form==form,"selected weapon owns attack");
            for(int i=1;i<move.start;i++){p.tickCount++;WeaponCombatSystem.tick(new PlayerTickEvent.Post(p));}
            h.assertTrue(mob.getHealth()==before,"anticipation must not deal damage");
            for(int i=move.start;i<=move.duration;i++){p.tickCount++;WeaponCombatSystem.tick(new PlayerTickEvent.Post(p));}
            h.assertTrue(mob.getHealth()<before,"active frames hit the target");
            h.assertTrue(!WeaponCombatSystem.busy(p),"recovery unlocks action");
            // Current gameplay permits swapping weapons, but cancels the old
            // contact window so the abandoned swing cannot deal delayed damage.
            WeaponCombatSystem.request(p,false);
            h.assertTrue(PowerFormSystem.select(p,PowerForm.CLAWS,false)&&!WeaponCombatSystem.busy(p),"form switch cancels weapon-owned action");
            float after=mob.getHealth();advance(p,move.duration+5);
            h.assertTrue(mob.getHealth()==after,"cancelled form cannot leave delayed contacts");mob.discard();
        }
        h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=40)
    public static void heavyWhipCannotDamageThroughWallsAndModeExitCancels(GameTestHelper h){
        var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"weapon-wall"));
        p.setPos(h.absoluteVec(new Vec3(4,2,3)));p.setYRot(0);p.setOnGround(true);p.tickCount=100;
        var s=p.getData(ModAttachments.PLAYER_STATE);s.infect();s.setPrototypeMode(true);s.unlock(PowerForm.WHIPFIST);s.activate(PowerForm.WHIPFIST);
        for(int x=1;x<8;x++)for(int y=2;y<6;y++)h.setBlock(x,y,4,Blocks.STONE);
        var mob=h.spawn(EntityType.IRON_GOLEM,4,2,6);mob.setNoAi(true);float before=mob.getHealth();
        WeaponCombatSystem.request(p,true);
        for(int i=0;i<40;i++){p.tickCount++;WeaponCombatSystem.tick(new PlayerTickEvent.Post(p));}
        h.assertTrue(mob.getHealth()==before,"wall blocks a standalone whip longshot");
        WeaponCombatSystem.request(p,false);s.setPrototypeMode(false);p.tickCount++;WeaponCombatSystem.tick(new PlayerTickEvent.Post(p));
        h.assertTrue(!WeaponCombatSystem.busy(p)&&p.getData(ModAttachments.WEAPON_VISUAL).spec()==null,"exit clears transient combat state");
        mob.discard();h.succeed();
    }
}
