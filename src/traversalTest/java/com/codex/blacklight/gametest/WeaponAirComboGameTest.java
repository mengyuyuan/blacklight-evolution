package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.registry.ModAttachments;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import java.util.UUID;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class WeaponAirComboGameTest {
    private static ServerPlayer player(GameTestHelper h,PowerForm form,boolean air){
        var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"air-combo-qa"));
        p.setPos(h.absoluteVec(new Vec3(4,air?7:2,4)));p.setYRot(0);p.setOnGround(!air);p.tickCount=100;
        var state=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE);state.infect();state.setPrototypeMode(true);state.unlock(form);state.activate(form);
        com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.MOVEMENT_STATE).reset();return p;
    }
    private static void tick(ServerPlayer p,int count){for(int i=0;i<count;i++){p.tickCount++;WeaponCombatSystem.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,p));}}
    private static WeaponMove move(ServerPlayer p){return com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.WEAPON_VISUAL).spec();}
    private static void close(ServerPlayer p){WeaponCombatSystem.cancelHeldInput(p);WeaponCombatSystem.cancel(p);}

    @GameTest(template="wall_test",timeoutTicks=40)
    public static void allWeaponsCompleteFourAirHitsWithoutGroundAttacks(GameTestHelper h){
        for(var form:PowerForm.values())if(WeaponMove.supports(form)){
            var p=player(h,form,true);WeaponCombatSystem.request(p,false);
            for(var expected:new WeaponMove[]{WeaponMove.AIR_AUTO1,WeaponMove.AIR_AUTO2,WeaponMove.AIR_AUTO3,WeaponMove.AIR_AUTO4}){
                h.assertTrue(move(p)==expected,form+" air stage "+expected);
                tick(p,expected.comboBufferTick());
                if(expected!=WeaponMove.AIR_AUTO4)WeaponCombatSystem.request(p,false);
                tick(p,(expected==WeaponMove.AIR_AUTO4?expected.duration:expected.comboLinkTick())-expected.comboBufferTick());
            }
            h.assertTrue(!WeaponCombatSystem.busy(p),"fourth hit recovers");
            WeaponCombatSystem.request(p,false);h.assertTrue(!WeaponCombatSystem.busy(p),"cannot restart infinite air chain");
            p.setOnGround(true);tick(p,1);p.setOnGround(false);WeaponCombatSystem.request(p,false);
            h.assertTrue(move(p)==WeaponMove.AIR_AUTO1,"landing restores air chain budget");close(p);
        }h.succeed();
    }

    @GameTest(template="wall_test",timeoutTicks=40)
    public static void queuedAirSpecialSelectsCurrentWeapon(GameTestHelper h){
        for(var form:PowerForm.values())if(WeaponMove.supports(form)){
            var p=player(h,form,true);WeaponCombatSystem.request(p,false);var first=move(p);
            tick(p,first.comboBufferTick());WeaponCombatSystem.request(p,true);tick(p,first.comboLinkTick()-first.comboBufferTick());
            h.assertTrue(move(p)==WeaponMove.choose(form,WeaponMove.Input.SPECIAL,true,false,false),"air special follows weapon: "+form);close(p);
        }h.succeed();
    }

    @GameTest(template="wall_test",timeoutTicks=40)
    public static void queuedAttackRechecksLandingAndTakeoff(GameTestHelper h){
        var p=player(h,PowerForm.BLADE,false);WeaponCombatSystem.request(p,false);var first=move(p);
        tick(p,first.comboBufferTick());WeaponCombatSystem.request(p,false);p.setOnGround(false);
        tick(p,first.comboLinkTick()-first.comboBufferTick());h.assertTrue(move(p)==WeaponMove.AIR_AUTO1,"takeoff switches buffered ground attack to air opener");
        first=move(p);tick(p,first.comboBufferTick());WeaponCombatSystem.request(p,false);p.setOnGround(true);
        tick(p,first.comboLinkTick()-first.comboBufferTick());h.assertTrue(move(p)==WeaponMove.BLADE_CUT,"landing switches buffered air attack to ground opener");close(p);h.succeed();
    }

    @GameTest(template="wall_test",timeoutTicks=40)
    public static void jumpBufferedDuringAttackStartsAirCombo(GameTestHelper h){
        MovingCombatGameTest.supportFloor(h,1);
        var p=player(h,PowerForm.CLAWS,false);WeaponCombatSystem.request(p,false);var first=move(p);
        tick(p,first.comboBufferTick());WeaponCombatSystem.request(p,false);
        WeaponCombatSystem.acceptInput(p,false,false,false,true);
        tick(p,first.end-first.comboBufferTick());
        h.assertTrue(!p.onGround()&&p.getDeltaMovement().y>0,"buffered jump leaves ground after contact window");
        h.assertTrue(move(p)==WeaponMove.AIR_AUTO1,"buffered follow-up starts aerial chain");close(p);h.succeed();
    }

    @GameTest(template="wall_test",timeoutTicks=40)
    public static void landingAttacksWaitForCollisionAndRecoverOnce(GameTestHelper h){
        for(var form:new PowerForm[]{PowerForm.BLADE,PowerForm.HAMMERFISTS}){
            var p=player(h,form,true);WeaponCombatSystem.request(p,true);var chosen=move(p);
            tick(p,100);h.assertTrue(WeaponCombatSystem.busy(p),"descent cannot expire before ground contact");
            p.setPos(h.absoluteVec(new Vec3(4,2,4)));p.setOnGround(true);tick(p,1);
            var visual=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.WEAPON_VISUAL);
            h.assertTrue(visual.clipOffset()>0&&move(p)==chosen,"landing seeks impact pose instead of restarting windup");
            tick(p,chosen.duration+2);h.assertTrue(!WeaponCombatSystem.busy(p),"landing completes recovery");close(p);
        }h.succeed();
    }

    @GameTest(template="wall_test",timeoutTicks=40)
    public static void chordsReplaceStartupWithoutReplayingWhileHeld(GameTestHelper h){
        for(int kind=0;kind<3;kind++){
            var p=player(h,PowerForm.MUSCLE_MASS,false);
            WeaponCombatSystem.acceptInput(p,kind!=2,kind==2,false,false);
            WeaponCombatSystem.acceptInput(p,kind!=2,kind!=1,kind!=0,false);
            var expected=new WeaponMove[]{WeaponMove.COMBO_GROUND_POUND,WeaponMove.COMBO_PAIN,WeaponMove.COMBO_CLAP}[kind];
            h.assertTrue(move(p)==expected,"second button replaces startup with one chord");
            WeaponCombatSystem.acceptInput(p,false,false,false,false);
            for(int i=0;i<expected.duration+60;i++)tick(p,1);
            h.assertTrue(!WeaponCombatSystem.busy(p),"released chord recovers exactly once");close(p);
        }h.succeed();
    }

    @GameTest(template="wall_test",timeoutTicks=40)
    public static void flyingKickMovesAndWeaponChangeCancelsSharedPose(GameTestHelper h){
        var p=player(h,PowerForm.CLAWS,true);WeaponCombatSystem.request(p,true);var kick=move(p);
        tick(p,kick.start);h.assertTrue(p.getDeltaMovement().z>.4,"flying kick has forward impulse");
        var state=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE);state.unlock(PowerForm.BLADE);state.activate(PowerForm.BLADE);tick(p,1);
        h.assertTrue(!WeaponCombatSystem.busy(p),"changing weapon cancels old shared pose");close(p);h.succeed();
    }

    @GameTest(template="wall_test",timeoutTicks=40)
    public static void nativeProfileCoversEveryReachableWeaponMove(GameTestHelper h){
        if(System.getProperty("blacklight.localWeaponMotions")==null){h.succeed();return;}
        for(var m:WeaponMove.values()){
            if(m==WeaponMove.BLADE_CLEAVE)continue; // Retired pre-native prototype, kept for wire IDs only.
            var d=LocalWeaponMotion.find(m.id);h.assertTrue(d.isPresent(),"native profile missing "+m.id);
            h.assertTrue(m.end<m.duration&&m.start<m.end,"valid native strike and recovery "+m.id);
            d.orElseThrow().charge().ifPresent(c->h.assertTrue(LocalVisualMotion.find(c.animation()).isPresent(),"native hold asset resolves "+m.id));
        }h.succeed();
    }
}
