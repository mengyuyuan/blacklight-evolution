package com.codex.blacklight.gametest;

import com.codex.blacklight.compat.*;
import com.codex.blacklight.combat.*;
import com.codex.blacklight.registry.*;
import com.codex.blacklight.config.*;
import com.codex.blacklight.power.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class NativeMechanismGameTest {
    private static final ResourceLocation OWNED=ResourceLocation.fromNamespaceAndPath("blacklight","qa_owned");
    private static ServerPlayer player(GameTestHelper h){
        return new net.neoforged.neoforge.common.util.FakePlayer(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"MechanismQA"));
    }
    private static Mob mob(GameTestHelper h){
        var mob=EntityType.COW.create(h.getLevel());mob.setNoAi(true);mob.setPos(h.absolutePos(new BlockPos(4,20,4)).getCenter());h.getLevel().addFreshEntity(mob);return mob;
    }
    private static java.util.UUID id(String name){return java.util.UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    @GameTest(template="wall_test",batch="native_attribute_clamp")
    public static void ownedBaselinePreservesForeignOperationsAndClamp(GameTestHelper h){
        var attribute=new AttributeInstance(net.minecraft.core.Holder.direct(new RangedAttribute("qa",60,0,100)),a->{});
        attribute.addTransientModifier(new AttributeModifier(ResourceLocation.fromNamespaceAndPath("qa","foreign_add"),20,AttributeModifier.Operation.ADD_VALUE));
        attribute.addTransientModifier(new AttributeModifier(ResourceLocation.fromNamespaceAndPath("qa","foreign_base"),.25,AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        attribute.addTransientModifier(new AttributeModifier(ResourceLocation.fromNamespaceAndPath("qa","foreign_total"),.5,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        double nativeValue=attribute.getValue();
        attribute.addTransientModifier(new AttributeModifier(OWNED,3,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        h.assertTrue(nativeValue==100&&attribute.getValue()==100,"fixture crosses the real attribute cap");
        h.assertTrue(attribute.getValue()/4==25,"old inverse-final-value method is demonstrably wrong");
        for(int i=0;i<100;i++)h.assertTrue(NativeAttributeBaseline.without(attribute,OWNED)==nativeValue,"foreign baseline stays exact under repeated reads");
        h.assertTrue(attribute.getModifiers().size()==4&&attribute.getModifier(ResourceLocation.fromNamespaceAndPath("qa","foreign_add")).amount()==20,"source attribute and foreign modifiers are not edited");
        attribute.removeModifier(OWNED);
        h.assertTrue(attribute.getValue()==nativeValue,"cure returns the actual native value");h.succeed();
    }
    private static final class ForeignDamage {
        final LivingEntity target;final boolean reserve;int resource=30;
        ForeignDamage(LivingEntity target,boolean reserve){this.target=target;this.reserve=reserve;}
        @SubscribeEvent public void attack(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event){
            if(event.getEntity()!=target)return;
            if(reserve)resource-=4;else target.setHealth(target.getHealth()-3);
            event.setCanceled(true);
        }
    }
    @GameTest(template="wall_test",batch="native_actual_loss")
    public static void externalFalseReturnWithActualLossIsStillANativeHit(GameTestHelper h){
        var target=mob(h);var p=player(h);var listener=new ForeignDamage(target,false);
        boolean diag=CrossModCombatConfig.NATIVE_DIAGNOSTICS.get();CrossModCombatConfig.NATIVE_DIAGNOSTICS.set(true);
        NeoForge.EVENT_BUS.register(listener);
        try{
            float before=target.getHealth();boolean result=target.hurt(DevastatorDamage.source(p),8);
            h.assertTrue(result&&before-target.getHealth()==3,"native feedback follows actual foreign HP loss");
            var trace=NativeHitObservation.last(target).orElseThrow();
            h.assertTrue(!trace.returned()&&trace.result()==NativeHitObservation.Result.HEALTH,"receipt distinguishes returned false from real damage");
            target.invulnerableTime=0;before=target.getHealth();
            boolean ordinary=target.hurt(p.damageSources().playerAttack(p),8);
            h.assertTrue(!ordinary&&before-target.getHealth()==3,"unrelated ordinary attack keeps the foreign API result");
            h.succeed();
        }finally{NeoForge.EVENT_BUS.unregister(listener);CrossModCombatConfig.NATIVE_DIAGNOSTICS.set(diag);target.discard();p.discard();}
    }
    @GameTest(template="wall_test",batch="native_shield_progress")
    public static void finiteReserveProgressDoesNotBecomeForcedHealthDamage(GameTestHelper h){
        var target=mob(h);var p=player(h);var listener=new ForeignDamage(target,true);
        var key=ResourceLocation.fromNamespaceAndPath("blacklight","qa_reserve_"+target.getId());
        NativeCombatAccess.register(new DamageMechanismAdapter(){
            public ResourceLocation id(){return key;}
            public boolean supports(LivingEntity entity){return entity==target;}
            public OptionalDouble reserve(LivingEntity entity){return OptionalDouble.of(listener.resource);}
        });
        boolean diag=CrossModCombatConfig.NATIVE_DIAGNOSTICS.get();CrossModCombatConfig.NATIVE_DIAGNOSTICS.set(true);NeoForge.EVENT_BUS.register(listener);
        try{
            float hp=target.getHealth();boolean accepted=target.hurt(DevastatorDamage.source(p),8);
            var trace=NativeHitObservation.last(target).orElseThrow();
            h.assertTrue(!accepted&&target.getHealth()==hp&&listener.resource==26,"blocked shield hit consumes reserve without fake HP damage");
            h.assertTrue(trace.result()==NativeHitObservation.Result.RESERVE,"shield progress is not diagnosed as invulnerability");h.succeed();
        }finally{NeoForge.EVENT_BUS.unregister(listener);CrossModCombatConfig.NATIVE_DIAGNOSTICS.set(diag);target.discard();p.discard();}
    }
    @GameTest(template="wall_test",batch="native_boundary")
    public static void nullAndRemovedPassiveContextsDoNotCreateState(GameTestHelper h){
        h.assertTrue(!ModAttachments.available(null),"missing presentation entity is not live state");
        h.assertTrue(ModAttachments.peekData(null,ModAttachments.CONSUME_VISUAL,ConsumeVisual.EMPTY)==ConsumeVisual.EMPTY,"missing actor has read-only empty visual");
        h.assertTrue(!BiomassSystem.begin(null,false),"late action with no player is rejected before state access");
        var target=mob(h);var p=player(h);float hp=target.getHealth();
        h.assertTrue(NativeHitObservation.begin(target,null,3)==null&&!NativeCombatAccess.bypassNpcShield(target,null),"missing external damage context is rejected by our mechanism boundary");
        h.assertTrue(!target.hurt(DevastatorDamage.source(p),Float.NaN)&&target.getHealth()==hp,"invalid damage cannot poison health");
        target.discard();h.assertTrue(!CombatTargeting.damageable(p,target),"removed foreign target cannot be acquired");
        h.assertTrue(!CombatTargeting.damageableFrom(p,null,null),"missing delayed target/origin rejected");p.discard();h.succeed();
    }
}
