package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.power.PowerForm;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class ActionFeedbackGameTest {
    @GameTest(template="wall_test", batch="action_feedback_material", timeoutTicks=60)
    public static void oneArmoredChestDoesNotTurnTheWholeBodyIntoMetal(GameTestHelper h) {
        var husk=h.spawn(EntityType.HUSK,3,8,3);
        husk.setItemSlot(EquipmentSlot.CHEST,new ItemStack(Items.IRON_CHESTPLATE));
        h.assertTrue(PlayerHitEffects.metalAt(husk,husk.position().add(0,husk.getBbHeight()*.62,0)),"chest must spark");
        h.assertTrue(!PlayerHitEffects.metalAt(husk,husk.position().add(0,husk.getBbHeight()*.90,0)),"bare head must bleed");
        h.assertTrue(PlayerHitEffects.fleshBody(husk),"uncovered flesh");
        h.assertTrue(!PlayerHitEffects.fleshBody(h.spawn(EntityType.IRON_GOLEM,5,8,3)),"iron must stay dry");
        h.assertTrue(!PlayerHitEffects.fleshBody(h.spawn(EntityType.SKELETON,6,8,3)),"bone chips");
        h.assertTrue(!PlayerHitEffects.fleshBody(h.spawn(EntityType.SNOW_GOLEM,7,8,3)),"packed snow chips");
        h.succeed();
    }

    /** Replay each accepted-contact route. Collision and input are exercised separately in the moving and blocked fixtures. */
    @GameTest(template="wall_test", batch="action_feedback_catalog", timeoutTicks=120)
    public static void everyRegisteredMoveFinishesItsAcceptedContactOnAnActualReceiver(GameTestHelper h) {
        var actors=new java.util.EnumMap<PowerForm,net.minecraft.server.level.ServerPlayer>(PowerForm.class);
        for (var move : WeaponMove.values()) {
            var form=move.form;
            var actor=actors.computeIfAbsent(form,key->MovingCombatGameTest.player(h,key));
            var target=h.spawn(EntityType.HUSK,3,24,5);
            target.setNoAi(true);
            target.setPos(actor.position().add(0,0,1.45));
            float hp=target.getHealth();
            boolean accepted=target.hurt(actor.damageSources().playerAttack(actor),1.0F);
            h.assertTrue(accepted && target.getHealth()<hp,move.id+" fixture must accept a real hit");
            if (move.id.startsWith("dev_") || move==WeaponMove.BULLETDIVE_DROP) {
                CombatImpactEffects.devastator(actor,target,move,actor.position());
            } else {
                var from=actor.position();
                var toward=target.position().subtract(from).normalize();
                WeaponImpact.apply(actor,target,move,toward,true,move==move.uncharged()?0F:1F);
            }
            Vec3 velocity=target.getDeltaMovement();
            h.assertTrue(Double.isFinite(velocity.lengthSqr()),move.id+" produced a non-finite receiver motion");
            org.slf4j.LoggerFactory.getLogger(ActionFeedbackGameTest.class).info("ACTION_FEEDBACK {} {} {} {} {} {} {}",
                    move.id,form.id(),move.start,move.end,ImpactTier.of(move),FleshStrike.of(form,move),velocity.length());
            target.discard();
        }
        h.succeed();
    }
}
