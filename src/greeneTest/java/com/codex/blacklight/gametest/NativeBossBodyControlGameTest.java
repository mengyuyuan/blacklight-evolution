package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.entity.*;
import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.registry.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.*;

/** Ownership regressions: these fixtures use real server entities and acquisition
 * entry points. Dispatcher/native-skin visual checks remain a separate client run. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class NativeBossBodyControlGameTest {
    private static final EncounterKind[] KINDS={EncounterKind.ELIZABETH_GREENE,EncounterKind.GOLIATH};
    private static final ImpactProfile LAUNCH=new ImpactProfile(HitReaction.LAUNCH,ImpactProfile.UP,40,.6,.8,true);
    private static EncounterMob boss(GameTestHelper h,EncounterKind kind){
        var mob=EncounterEntities.CREATURES.get(kind).get().create(h.getLevel());
        // Explicit existing encounter fixture; fresh opening has its own regression.
        if(mob.kind()==EncounterKind.ELIZABETH_GREENE){var standing=mob.greeneBattle().save();standing.putBoolean("OpeningPending",false);mob.greeneBattle().load(standing);}

        mob.setPos(h.absoluteVec(new Vec3(8,2,8)));mob.setNoAi(true);mob.setNoGravity(true);mob.setPersistenceRequired();
        h.getLevel().addFreshEntity(mob);return mob;
    }
    @GameTest(template="blackwatch_arena",batch="native_boss_body",timeoutTicks=30)
    public static void genericLaunchCannotReplaceNativePoseOrBlockDamage(GameTestHelper h){
        for(var kind:KINDS){var mob=boss(h,kind);Vec3 velocity=new Vec3(.01,0,.02);mob.setDeltaMovement(velocity);
            h.assertTrue(NativeBossBodyControl.ownsPose(mob),kind+" starts native-owned");
            h.assertTrue(!ImpactControl.begin(mob,LAUNCH,37),kind+" rejects ordinary launch control");
            h.assertTrue(!ImpactControl.consumeOpening(mob,37),kind+" low-health opening is not a confirmed consume scene");
            h.assertTrue(!ImpactControl.stunned(mob)&&mob.getData(ModAttachments.HIT_REACTION).duration()==0,kind+" retains native AI clock");
            h.assertTrue(mob.getDeltaMovement().equals(velocity),kind+" control rejection cannot add a launch");
            float hp=mob.getHealth();
            h.assertTrue(mob.hurt(h.getLevel().damageSources().generic(),10),kind+" still accepts damage under native pose ownership");
            if(kind==EncounterKind.ELIZABETH_GREENE){var saved=new net.minecraft.nbt.CompoundTag();mob.saveWithoutId(saved);
                h.assertTrue(mob.getHealth()==hp,"guarded Greene retains core immunity under native pose ownership");
                for(int slot=0;slot<3;slot++)h.assertTrue(!mob.broken(slot)&&Math.abs(saved.getFloat("EncounterArmor"+slot)-(mob.greeneSegmentMaximum()-2))<.001,
                        "each attached limb receives its own two-point share against the current wave's armor");
            }else h.assertTrue(mob.getHealth()<hp,kind+" still takes own body damage");
            h.assertTrue(RagdollImpact.supportedBody(mob),kind+" remains eligible for its eventual corpse skin");mob.discard();
        }h.succeed();
    }
    @GameTest(template="blackwatch_arena",batch="native_boss_body",timeoutTicks=30)
    public static void staleImpactIsClearedButDeathRetainsItsHandoff(GameTestHelper h){
        for(var kind:KINDS){var mob=boss(h,kind);
            mob.setData(ModAttachments.HIT_REACTION,new HitReaction(h.getLevel().getGameTime(),40,HitReaction.DOWN,0,ImpactProfile.DOWN));
            h.assertTrue(!ImpactControl.stunned(mob)&&mob.getData(ModAttachments.HIT_REACTION).duration()==0,kind+" clears old generic down snapshot");
            mob.setHealth(0);
            h.assertTrue(!NativeBossBodyControl.ownsPose(mob)&&RagdollImpact.supportedBody(mob),kind+" death releases native ownership without disabling corpse capture");
            h.assertTrue(ImpactControl.begin(mob,LAUNCH,0)&&mob.getData(ModAttachments.HIT_REACTION).kind()==HitReaction.DEATH,kind+" death reaction remains reachable");mob.discard();
        }h.succeed();
    }
    @GameTest(template="blackwatch_arena",batch="native_boss_body",timeoutTicks=40)
    public static void actualConsumeAcquisitionOwnsBodyUntilCancelWithoutChangingOtherSpecies(GameTestHelper h){
        for(int x=0;x<16;x++)for(int z=0;z<16;z++){h.setBlock(x,1,z,Blocks.STONE);for(int y=2;y<16;y++)h.setBlock(x,y,z,Blocks.AIR);}
        for(var kind:KINDS){var mob=boss(h,kind);mob.setHealth(1);
            var player=FakePlayerFactory.get(h.getLevel(),new GameProfile(java.util.UUID.randomUUID(),"native-boss-grab"));
            player.setPos(ConsumeSequence.contact(mob).add(0,-player.getEyeHeight(),-2));player.setYRot(0);player.setXRot(0);player.setOnGround(true);player.tickCount=100;
            h.getLevel().addNewPlayer(player);
            try{var state=player.getData(ModAttachments.PLAYER_STATE);state.infect();state.setPrototypeMode(true);state.unlock(PowerForm.CLAWS);state.activate(PowerForm.CLAWS);player.setData(ModAttachments.PLAYER_STATE,state);
                ConsumeSystem.request(player);var visual=mob.getData(ModAttachments.CONSUME_VISUAL);
                h.assertTrue(visual.consumerId()==player.getId()&&visual.stage()==ConsumeVisual.MOUNT,kind+" real low-health request acquires mounted scene");
                h.assertTrue(!NativeBossBodyControl.ownsPose(mob),kind+" accepts the live dedicated consume owner");
                ConsumeSystem.cancel(player);
                h.assertTrue(NativeBossBodyControl.ownsPose(mob),kind+" cancellation restores native ownership");
            }finally{ConsumeSystem.cancel(player);player.discard();mob.discard();}
        }
        var pig=h.spawn(EntityType.PIG,new Vec3(3,2,3));pig.setNoAi(true);
        h.assertTrue(!NativeBossBodyControl.ownsPose(pig)&&ImpactControl.begin(pig,LAUNCH,0),"ordinary creature knockdown is unchanged");ImpactControl.cancel(pig);pig.discard();
        var flyer=boss(h,EncounterKind.FLYER);h.assertTrue(!NativeBossBodyControl.ownsPose(flyer),"flyer retains its separate flight/body controller");flyer.discard();h.succeed();
    }
}
