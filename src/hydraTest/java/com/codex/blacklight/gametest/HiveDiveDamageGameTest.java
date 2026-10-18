package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.power.CombatScale;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.registry.ModEntities;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.effect.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import java.util.UUID;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class HiveDiveDamageGameTest {
    @GameTest(template="wall_test",timeoutTicks=50)
    public static void rapidArmorPiercingHitsCannotDeleteHiveOrStaggerIt(GameTestHelper h){
        var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"HiveGunQA"));
        p.moveTo(h.absolutePos(new BlockPos(4,3,4)).getCenter());
        var state=p.getData(ModAttachments.PLAYER_STATE);state.infect();state.setPrototypeMode(true);
        var hive=h.spawn(ModEntities.BLACKLIGHT_HIVE.get(),5,2,5);hive.setNoAi(true);
        var arrow=EntityType.ARROW.create(h.getLevel());arrow.setOwner(p);
        // Reproduce two TaCZ damage parts and its invulnerableTime resets.
        // The second source has real armor-bypass tags, but remains a projectile.
        var normal=p.damageSources().arrow(arrow,p);
        var piercing=new DamageSource(BulletdiveDamage.source(p).typeHolder(),arrow,p){
            @Override public boolean is(net.minecraft.resources.ResourceKey<net.minecraft.world.damagesource.DamageType> key){
                return key!=BulletdiveDamage.TYPE&&super.is(key);
            }
        };
        h.assertTrue(piercing.is(DamageTypeTags.BYPASSES_ARMOR),"fixture must bypass armor");
        float before=hive.getHealth(),units=CombatScale.units(hive);
        for(int shot=0;shot<30;shot++){
            hive.invulnerableTime=0;hive.hurt(normal,100);
            hive.invulnerableTime=0;hive.hurt(piercing,100);
        }
        h.assertTrue(hive.isAlive()&&before-hive.getHealth()<=HiveDefense.MAX_CHIP*units+.001,"magazine/AP parts bypassed structural interval");
        ImpactControl.begin(hive,new ImpactProfile(HitReaction.LAUNCH,ImpactProfile.UP,40,1,1,true),0);
        h.assertTrue(!ImpactControl.stunned(hive)&&hive.getData(ModAttachments.HIT_REACTION).duration()==0,"building acquired a knockdown pose");
        hive.setDeltaMovement(Vec3.ZERO);hive.push(3,2,1);hive.knockback(3,1,0);
        h.assertTrue(hive.getDeltaMovement().equals(Vec3.ZERO),"building was pushed");
        hive.invulnerableTime=0;float afterBullets=hive.getHealth();
        hive.hurt(p.damageSources().playerAttack(p),12);
        h.assertTrue(Math.abs(afterBullets-hive.getHealth()-12*units)<.01,"prototype melee was reduced by gun protection");
        hive.invulnerableTime=0;float afterMelee=hive.getHealth();hive.hurt(BulletdiveDamage.source(p),12);
        h.assertTrue(Math.abs(afterMelee-hive.getHealth()-12*units)<.01,"prototype dive lost full structural damage");
        h.runAfterDelay(12,()->{
            hive.invulnerableTime=0;float health=hive.getHealth();hive.hurt(normal,20);
            h.assertTrue(hive.getHealth()<health,"conventional weapons became permanently immune");
            hive.discard();h.succeed();
        });
    }
    @GameTest(template="wall_test",timeoutTicks=40)
    public static void normalMeleeIsChipButAdministrativeKillStillWorks(GameTestHelper h){
        var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"HiveNormalQA"));
        var hive=h.spawn(ModEntities.BLACKLIGHT_HIVE.get(),5,2,5);hive.setNoAi(true);
        float before=hive.getHealth();hive.hurt(p.damageSources().playerAttack(p),10000);
        h.assertTrue(hive.isAlive()&&before-hive.getHealth()<=HiveDefense.MAX_CHIP*CombatScale.units(hive)+.001,"ordinary melee deletes hive");
        hive.hurt(h.getLevel().damageSources().genericKill(),10000);
        h.assertTrue(!hive.isAlive(),"administrative kill was blocked");h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=40)
    public static void diveBypassesArmorResistanceAndCooldownWithoutRemovingDeathHooks(GameTestHelper h){
        var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"DiveTrueQA"));
        var target=h.spawn(EntityType.COW,5,2,5);target.setNoAi(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);target.setHealth(1000);
        target.getAttribute(Attributes.ARMOR).setBaseValue(30);
        target.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(20);
        target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,600,4));
        var source=BulletdiveDamage.source(p);
        for(var tag:java.util.List.of(DamageTypeTags.BYPASSES_ARMOR,DamageTypeTags.BYPASSES_RESISTANCE,
                DamageTypeTags.BYPASSES_ENCHANTMENTS,DamageTypeTags.BYPASSES_SHIELD,DamageTypeTags.BYPASSES_COOLDOWN))
            h.assertTrue(source.is(tag),"missing true impact tag "+tag.location());
        h.assertTrue(!source.is(DamageTypeTags.BYPASSES_INVULNERABILITY),"impact must not defeat administrative invulnerability");
        target.invulnerableTime=20;float damage=BulletdivePhysics.impactDamage(22,300);
        h.assertTrue(target.hurt(source,damage),"real damage rejected");
        h.assertTrue(Math.abs(target.getHealth()-(1000-damage))<.01,"armor/resistance/cooldown reduced impact");
        target.hurt(source,BulletdivePhysics.impactDamage(22,60000));
        h.assertTrue(!target.isAlive()&&!target.isRemoved()&&target.getLastDamageSource().getEntity()==p,"lethal impact must run normal credited death, not remove entity");
        h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=40)
    public static void diveRespectsInvulnerabilityAndDoesNotApplyHunterDuelReduction(GameTestHelper h){
        var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"DiveBossQA"));
        var cow=h.spawn(EntityType.COW,3,2,3);cow.setInvulnerable(true);
        float health=cow.getHealth();cow.hurt(BulletdiveDamage.source(p),1000000);
        h.assertTrue(cow.getHealth()==health,"true damage bypassed target invulnerability");
        var hunter=h.spawn(ModEntities.BLACKLIGHT_HUNTER.get(),8,2,8);hunter.setNoAi(true);
        float before=hunter.getHealth();hunter.hurt(BulletdiveDamage.source(p),22);
        h.assertTrue(Math.abs(before-hunter.getHealth()-22*CombatScale.units(hunter))<.01,"hunter duel modifier reduced true damage");
        cow.discard();hunter.discard();h.succeed();
    }
}
