package com.codex.blacklight.gametest.vehicle;

import com.codex.blacklight.disguise.DisguiseIdentity;
import com.codex.blacklight.disguise.DisguiseSystem;
import com.codex.blacklight.entity.MilitarySoldier;
import com.codex.blacklight.firearms.FirearmItem;
import com.codex.blacklight.firearms.FirearmsRegistry;
import com.codex.blacklight.military.MilitaryLoadoutProfile;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.registry.ModEntities;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.level.GameType;
import net.minecraftforge.gametest.*;

/** Real goal ticks and actual gun stock, including protected actors outside the shot line. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class MilitaryLauncherSafetyGameTest {
    private static float healthFraction(LivingEntity entity){return entity.getHealth()/entity.getMaxHealth();}
    private static MilitarySoldier carrier(GameTestHelper h,MilitaryVehicleGameTest.Fixture f){
        var soldier=ModEntities.MILITARY_SOLDIER.get().create(f.level);
        soldier.setAutomaticUnitJoin(false);soldier.setPersistenceRequired();soldier.setPos(f.x+.5,81,.5);
        f.level.addFreshEntity(soldier);f.entities.add(soldier);
        if(!soldier.initializeLoadout(MilitaryLoadoutProfile.forDuty(MilitaryLoadoutProfile.Duty.ANTI_ARMOR),"qa-safety:"+f.x)){
            f.close();h.fail("real finite anti-armor loadout must issue before exercising its actual goal");
        }
        return soldier;
    }
    private static LivingEntity infected(MilitaryVehicleGameTest.Fixture f,int z){
        var target=ModEntities.INFECTED_HOST.get().create(f.level);target.setNoAi(true);target.setPersistenceRequired();
        target.setPos(f.x+.5,81,z);target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
        target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);target.setHealth(200);
        f.level.addFreshEntity(target);f.entities.add(target);return target;
    }
    private static ServerPlayer player(GameTestHelper h,MilitaryVehicleGameTest.Fixture f,GameType mode,int z){
        var player=MilitaryVehicleGameTest.mockPlayer(h);f.entities.add(player);player.setGameMode(mode);
        player.setPos(f.x+.5,81,z);player.setOnGround(true);return player;
    }
    private static int rockets(MilitarySoldier soldier){
        int result=soldier.reserveAmmo("rocket_round");
        for(var stack:java.util.List.of(soldier.getMainHandItem(),soldier.getOffhandItem()))
            if(stack.getItem()==FirearmsRegistry.weapon("rocket_launcher"))result+=FirearmItem.state(stack,0).magazine();
        return result;
    }
    private static int carbineRounds(MilitarySoldier soldier){
        int result=soldier.reserveAmmo("rifle_round");
        for(var stack:java.util.List.of(soldier.getMainHandItem(),soldier.getOffhandItem()))
            if(stack.getItem()==FirearmsRegistry.weapon("military_carbine"))result+=FirearmItem.state(stack,0).magazine();
        return result;
    }
    private static void rejectDirectPlayer(GameTestHelper h,int x,GameType mode){
        var f=new MilitaryVehicleGameTest.Fixture(h,x);var soldier=carrier(h,f);var player=player(h,f,mode,16);
        soldier.setTarget(player);float health=player.getHealth();
        h.runAfterDelay(120,()->{try(f){
            h.assertTrue(rockets(soldier)==3&&carbineRounds(soldier)==72,"actual NPC never debits either real weapon for a protected "+mode+" direct target");
            h.assertTrue(player.getHealth()==health,"actual protected direct target takes no projectile damage");
        }h.succeed();});
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6_loadout",timeoutTicks=180)
    public static void actualCreativeDirectTargetCannotConsumeEitherRealWeapon(GameTestHelper h){rejectDirectPlayer(h,276000,GameType.CREATIVE);}
    @GameTest(template="blackwatch_arena",batch="vehicle6_loadout",timeoutTicks=180)
    public static void actualSpectatorDirectTargetCannotConsumeEitherRealWeapon(GameTestHelper h){rejectDirectPlayer(h,277000,GameType.SPECTATOR);}

    @GameTest(template="blackwatch_arena",batch="vehicle6_loadout",timeoutTicks=180)
    public static void actualCreativePlayerInTheShotLinePreventsBothWeaponDebits(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,278000);var soldier=carrier(h,f);var blocker=player(h,f,GameType.CREATIVE,8);
        var target=infected(f,16);soldier.setTarget(target);float health=healthFraction(target),protectedHealth=blocker.getHealth();
        h.runAfterDelay(120,()->{try(f){
            h.assertTrue(rockets(soldier)==3&&carbineRounds(soldier)==72,"actual creative body in the line prevents launcher and carbine debits");
            h.assertTrue(Math.abs(healthFraction(target)-health)<1e-6&&blocker.getHealth()==protectedHealth,"actual protected obstruction admits no shot through to the legal target");
        }h.succeed();});
    }

    @GameTest(template="blackwatch_arena",batch="vehicle6_loadout",timeoutTicks=180)
    public static void actualSuccessfulMilitaryDisguisePreventsBothWeaponDebits(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,279000);var soldier=carrier(h,f);var player=player(h,f,GameType.SURVIVAL,16);
        var state=com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.PLAYER_STATE);state.infect();state.setPrototypeMode(true);com.codex.blacklight.registry.ModAttachments.setData(player, ModAttachments.PLAYER_STATE,state);
        DisguiseSystem.captured(player,DisguiseIdentity.capture(soldier));
        state=com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.PLAYER_STATE);state.setDisguised(true);com.codex.blacklight.registry.ModAttachments.setData(player, ModAttachments.PLAYER_STATE,state);
        if(!DisguiseSystem.fooled(soldier,player)){f.close();h.fail("fixture must establish an actual valid unexposed military identity");}
        soldier.setTarget(player);float health=player.getHealth();
        h.runAfterDelay(120,()->{try(f){
            h.assertTrue(DisguiseSystem.fooled(soldier,player),"the real identity remains successful during the firing window");
            h.assertTrue(rockets(soldier)==3&&carbineRounds(soldier)==72&&player.getHealth()==health,"actual NPC fires neither physical gun at the successfully disguised player");
        }h.succeed();});
    }

    @GameTest(template="blackwatch_arena",batch="vehicle6_loadout",timeoutTicks=220)
    public static void actualAllyBesideTheBlastAllowsRealCarbineButRefusesRocket(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,280000);var soldier=carrier(h,f);var target=infected(f,16);
        var ally=ModEntities.BLACKWATCH_SOLDIER.get().create(f.level);ally.setNoAi(true);ally.setAutomaticUnitJoin(false);
        ally.setPersistenceRequired();ally.setPos(f.x+3.5,81,16);f.level.addFreshEntity(ally);f.entities.add(ally);
        soldier.setTarget(target);float health=healthFraction(target),allyHealth=ally.getHealth();
        h.runAfterDelay(140,()->{try(f){
            h.assertTrue(rockets(soldier)==3,"actual nearby side ally outside the ray still prevents the unsafe blast");
            h.assertTrue(carbineRounds(soldier)<72&&healthFraction(target)<health,"actual NPC continues firing its real safe carbine at the legal target");
            h.assertTrue(ally.getHealth()==allyHealth,"actual side ally remains unharmed");
        }h.succeed();});
    }

    @GameTest(template="blackwatch_arena",batch="vehicle6_loadout",timeoutTicks=220)
    public static void actualNearEnemyInterceptCannotBypassMinimumRocketImpactDistance(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,281000);var soldier=carrier(h,f);var near=infected(f,6);var far=infected(f,16);
        // Keep this collision fixture stationary: real squad tactics otherwise flank by 2.5 blocks after a burst,
        // making a later distant rocket legitimate once the actual near obstruction has been cleared.
        soldier.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        var initial=soldier.position();var muzzle=soldier.getEyePosition().add(0,-.18,0);
        var initialImpact=near.getBoundingBox().inflate(.3).clip(muzzle,far.getBoundingBox().getCenter());
        if(initialImpact.isEmpty()||initialImpact.get().distanceTo(muzzle)>=10){f.close();h.fail("fixture must establish an actual living first collision inside ten blocks");}
        // Pin only target acquisition. The production firing/movement goal still runs normally.
        // Otherwise auto-acquiring the nearer infected would hide this first-impact regression.
        try{
            var field=Mob.class.getDeclaredField("targetSelector");field.setAccessible(true);
            ((GoalSelector)field.get(soldier)).removeAllGoals(goal->true);
        }catch(ReflectiveOperationException failure){f.close();throw new IllegalStateException("Cannot pin the actual far target for the first-impact fixture",failure);}
        soldier.setTarget(far);float nearHealth=healthFraction(near);String[] firstDebit={"none"};
        for(int tick=1;tick<=140;tick++){final int at=tick;h.runAtTickTime(at,()->{
            if(rockets(soldier)<3&&firstDebit[0].equals("none")){
                Object impact;
                try{var method=MilitarySoldier.class.getDeclaredMethod("explosiveImpact",LivingEntity.class,com.codex.blacklight.firearms.WeaponDefinition.class);method.setAccessible(true);impact=method.invoke(soldier,far,com.codex.blacklight.firearms.WeaponCatalog.ROCKET);}
                catch(ReflectiveOperationException error){impact=error.getClass().getSimpleName();}
                firstDebit[0]="tick="+at+" shooter="+soldier.position()+" near="+near.position()+" nearAlive="+near.isAlive()+" nearHealth="+near.getHealth()+" far="+far.position()+" predictedImpact="+impact;
            }
        });}
        h.runAfterDelay(140,()->{try(f){
            h.assertTrue(soldier.position().distanceTo(initial)<.05&&near.isAlive(),"fixture keeps the actual shooter and live obstruction in place: "+firstDebit[0]);
            h.assertTrue(rockets(soldier)==3,"first real living impact inside ten blocks prevents rocket debit despite the selected far target; "+firstDebit[0]);
            h.assertTrue(carbineRounds(soldier)<72&&healthFraction(near)<nearHealth,"production NPC goal still fires its actual carbine into the nearer legal obstruction");
        }h.succeed();});
    }
    private MilitaryLauncherSafetyGameTest(){}
}
