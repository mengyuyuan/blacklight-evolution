package com.codex.blacklight.gametest;

import com.codex.blacklight.entity.BlacklightInfectedHost;
import com.codex.blacklight.registry.*;
import com.codex.blacklight.combat.CrossModCombat;
import com.codex.blacklight.config.CrossModCombatConfig;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.eventbus.api.*;

/** Exercises cultivation PLAYER -> converted cultivation host, with real weapons/spells. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class CultivationIncomingGameTest {
    private static final Map<String,Object> RESULTS=new LinkedHashMap<>();
    private static void row(String key,Map<String,Object> value)throws Exception{
        RESULTS.put(key,value);java.nio.file.Files.writeString(java.nio.file.Path.of("cultivation-incoming.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(RESULTS));
        System.out.println("CULTIVATION_INCOMING "+key+" "+value);
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    private static ServerPlayer player(GameTestHelper h)throws Exception{
        h.getLevel().getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL,true);
        var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"CultIncomingQA"));
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);p.setNoGravity(true);p.setPos(h.absolutePos(new BlockPos(4,30,5)).getCenter());
        h.getLevel().addNewPlayer(p);
        var cap=Class.forName("com.xiaoxiang.cultivation.cultivation.CultivationCapability");var data=((Optional<?>)cap.getMethod("get",net.minecraft.world.entity.player.Player.class).invoke(null,p)).orElseThrow();
        data.getClass().getMethod("setIdentityId",String.class).invoke(data,"mortal");
        var realm=Class.forName("com.xiaoxiang.cultivation.cultivation.realm.Realm");data.getClass().getMethod("setRealm",realm).invoke(data,Enum.valueOf((Class)realm,"TRUE_IMMORTAL"));
        data.getClass().getMethod("setAttack",int.class).invoke(data,100);
        var item=BuiltInRegistries.ITEM.get(new ResourceLocation("xiaoxiang_cultivation","xuan_iron_sword_low"));
        h.assertTrue(item!=net.minecraft.world.item.Items.AIR,"real cultivation sword is installed");p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(item));
        h.assertTrue(!ModAttachments.getData(p,ModAttachments.PLAYER_STATE).isInfected(),"attacker is a cultivation-only player");return p;
    }
    private static BlacklightInfectedHost host(GameTestHelper h,double hp,long age)throws Exception{
        var type=BuiltInRegistries.ENTITY_TYPE.get(new ResourceLocation("xiaoxiang_cultivation","wandering_cultivator"));
        var source=(Mob)type.create(h.getLevel());source.getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp);source.setHealth(source.getMaxHealth());
        var host=ModEntities.INFECTED_HOST.get().create(h.getLevel());host.inherit(source);host.setNoAi(true);host.setNoGravity(true);host.setPos(h.absolutePos(new BlockPos(9,30,5)).getCenter());h.getLevel().addFreshEntity(host);
        if(age>0)host.advanceTissue(age);host.setHealth(host.getMaxHealth());source.discard();return host;
    }
    private static void cleanup(ServerPlayer p,BlacklightInfectedHost h){h.discard();p.serverLevel().removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);}
    private static final class Trace {
        private final LivingEntity target;final List<String> events=new ArrayList<>();Trace(LivingEntity target){this.target=target;}
        @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)public void attack(LivingAttackEvent e){if(e.getEntity()==target)events.add("attack="+e.getAmount()+" canceled="+e.isCanceled()+" external="+CrossModCombat.externalAttack(e.getSource()));}
        @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)public void hurt(LivingHurtEvent e){if(e.getEntity()==target)events.add("hurt="+e.getAmount()+" canceled="+e.isCanceled());}
        @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)public void damage(LivingDamageEvent e){if(e.getEntity()==target)events.add("damage="+e.getAmount()+" canceled="+e.isCanceled());}
    }
    @GameTest(template="wall_test",timeoutTicks=60,batch="cult_incoming_melee")
    public static void cultivationPlayerSwordActuallyDamagesConvertedCultivator(GameTestHelper h)throws Exception{
        var p=player(h);var target=host(h,10000,0);var trace=new Trace(target);net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(trace);
        try{
            var cooldown=LivingEntity.class.getDeclaredField("attackStrengthTicker");cooldown.setAccessible(true);cooldown.setInt(p,1000);
            p.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(20);float before=target.getHealth();p.attack(target);float loss=before-target.getHealth();
            row("realSword",Map.of("hostMaxHp",target.getMaxHealth(),"firstHitLoss",loss,"events",trace.events));
            h.assertTrue(loss>30,"real player sword attack must avoid the old 75-percent suppression: "+trace.events);
        }finally{net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(trace);cleanup(p,target);}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=60,batch="cult_incoming_spell")
    public static void realFlyingSwordCollisionDamagesConvertedCultivator(GameTestHelper h)throws Exception{
        var p=player(h);var target=host(h,10000,0);var trace=new Trace(target);net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(trace);Entity sword=null;
        try{
            var type=Class.forName("com.xiaoxiang.cultivation.entity.SwordProjectileEntity");var origin=p.position().add(0,target.getBbHeight()*.5,0);var aim=target.getBoundingBox().getCenter();
            try{sword=(Entity)type.getConstructor(Level.class,LivingEntity.class,Vec3.class,Vec3.class,boolean.class,boolean.class).newInstance(h.getLevel(),p,origin,aim,false,true);}
            catch(NoSuchMethodException older){sword=(Entity)type.getConstructor(Level.class,LivingEntity.class,Vec3.class,Vec3.class,boolean.class).newInstance(h.getLevel(),p,origin,aim,false);}
            type.getMethod("setDirectHitDamage",float.class).invoke(sword,100F);h.getLevel().addFreshEntity(sword);
            float before=target.getHealth();for(int i=0;i<8&&!sword.isRemoved();i++)sword.tick();float loss=before-target.getHealth();
            row("realFlyingSword",Map.of("hostMaxHp",target.getMaxHealth(),"firstSpellLoss",loss,"removed",sword.isRemoved(),"events",trace.events));
            h.assertTrue(loss>25,"real moving cultivation projectile avoids the old 75-percent suppression: "+trace.events);
        }finally{if(sword!=null)sword.discard();net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(trace);cleanup(p,target);}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=70,batch="cult_incoming_window")
    public static void externalBudgetReopensInsteadOfMakingTheHostPermanentlyImmune(GameTestHelper h)throws Exception{
        var p=player(h);var target=host(h,10000,0);var trace=new Trace(target);net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(trace);
        var losses=new ArrayList<Float>();float maximum=target.getMaxHealth();
        boolean reduce=CrossModCombatConfig.REDUCE_PLAYER_ATTACKS.get(),common=CrossModCombatConfig.PROTECT_COMMON.get();CrossModCombatConfig.REDUCE_PLAYER_ATTACKS.set(true);CrossModCombatConfig.PROTECT_COMMON.set(true);
        try{for(int i=0;i<6;i++){target.invulnerableTime=0;float before=target.getHealth();target.hurt(p.damageSources().playerAttack(p),100000);losses.add(before-target.getHealth());}
            row("windowInitial",Map.of("maxHp",maximum,"losses",losses,"events",trace.events));
            h.assertTrue(losses.get(0)>0&&losses.get(5)==0,"first external hit works; a full window rejects the remaining burst");
            h.assertTrue(maximum-target.getHealth()<=maximum*CrossModCombatConfig.WINDOW_FRACTION.get()+.1,"burst obeys the configured maximum");
        }catch(Throwable failure){net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(trace);cleanup(p,target);CrossModCombatConfig.REDUCE_PLAYER_ATTACKS.set(reduce);CrossModCombatConfig.PROTECT_COMMON.set(common);throw failure;}
        h.runAfterDelay(CrossModCombatConfig.WINDOW_TICKS.get()+2,()->{
            try{target.invulnerableTime=0;float before=target.getHealth();target.hurt(p.damageSources().playerAttack(p),100000);float loss=before-target.getHealth();row("windowReopened",Map.of("loss",loss,"events",trace.events));h.assertTrue(loss>0,"external damage must resume after the window");h.succeed();}
            catch(Exception e){throw new RuntimeException(e);}finally{net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(trace);cleanup(p,target);CrossModCombatConfig.REDUCE_PLAYER_ATTACKS.set(reduce);CrossModCombatConfig.PROTECT_COMMON.set(common);}
        });
    }
    @GameTest(template="wall_test",timeoutTicks=60,batch="cult_incoming_mature")
    public static void realCultivationWeaponStillDamagesAMatureConvertedCultivator(GameTestHelper h)throws Exception{
        var p=player(h);var target=host(h,10000,72000);var trace=new Trace(target);net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(trace);
        try{float before=target.getHealth();target.hurt(p.damageSources().playerAttack(p),100);float loss=before-target.getHealth();
            row("matureCultivator",Map.of("maxHp",target.getMaxHealth(),"age",target.tissueAge(),"loss",loss,"events",trace.events));
            h.assertTrue(loss>0,"maturity must not make legal cultivation damage zero: "+trace.events);
        }finally{net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(trace);cleanup(p,target);}h.succeed();
    }
}
