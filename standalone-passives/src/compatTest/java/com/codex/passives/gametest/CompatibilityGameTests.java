
package com.codex.passives.gametest;
import com.codex.passives.*;
import com.mojang.authlib.GameProfile;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.*;
@GameTestHolder("adaptive_passives") @PrefixGameTestTemplate(false)
public final class CompatibilityGameTests {
    private static Object call(Object target,String name,Object... args) throws Exception {
        Class<?> type=target instanceof Class<?> c?c:target.getClass();
        for(var m:type.getMethods()) {
            if(!m.getName().equals(name)||m.getParameterCount()!=args.length) continue;
            boolean fits=true; Class<?>[] params=m.getParameterTypes();
            for(int i=0;i<params.length;i++) if(!params[i].isPrimitive()&&!params[i].isInstance(args[i])) fits=false;
            if(fits) return m.invoke(target instanceof Class<?>?null:target,args);
        }
        throw new NoSuchMethodException(name);
    }
    private static Class<?> clazz(String suffix) throws Exception { return Class.forName("com.codex.blacklight."+suffix); }
    private static Object data(ServerPlayer p,String name) throws Exception {
        Object key=clazz("registry.ModAttachments").getField(name).get(null);
        return call(p,"getData",key);
    }
    private static void data(ServerPlayer p,String name,Object value) throws Exception {
        Object key=clazz("registry.ModAttachments").getField(name).get(null); call(p,"setData",key,value);
    }
    private static ServerPlayer player(GameTestHelper h) {
        var p=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),new GameProfile(UUID.randomUUID(),"compat-qa"),ClientInformation.createDefault());
        p.connection=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"compat-net")).connection;
        p.setGameMode(GameType.SURVIVAL); p.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(2,2,2))); for(int i=0;i<65;i++) p.tick(); return p;
    }
    private static void near(GameTestHelper h,double got,double want,String why) { h.assertTrue(Math.abs(got-want)<.002,why+" got="+got+" want="+want); }
    private static void qualifyNative(ServerPlayer p) throws Exception {
        var state=data(p,"PLAYER_STATE");call(state,"infect");call(state,"setPrototypeMode",true);call(state,"addEvolutionPoints",10000);
        var skills=clazz("power.CombatSkill");
        call(state,"buyCombatSkill",skills.getField("ADAPTATION").get(null));call(state,"buyCombatSkill",skills.getField("TISSUE_REGENERATION").get(null));
        data(p,"EVOLUTION_MILESTONES",clazz("state.EvolutionMilestones").getConstructor(int.class).newInstance(clazz("state.EvolutionMilestones").getField("PERCENT").get(null)));
        data(p,"ADAPTIVE_PASSIVES",clazz("power.AdaptivePassiveState").getConstructor(int.class,int.class,int.class,long.class).newInstance(0,100,0,0L));
        call(clazz("power.CombatScale"),"attributes",p,true);p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);p.setHealth(1000);
    }
    @GameTest(template="empty",timeoutTicks=65)
    public static void potionAdaptationAndNativeRegenerationUsePostMitigationDamage(GameTestHelper h) throws Exception {
        var p=player(h);qualifyNative(p);PassiveApi.grant(p,1,1);var d=PassiveData.read(p);d.adaptation=100;d.save(p);
        p.invulnerableTime=0;p.hurt(p.damageSources().generic(),60);
        near(h,p.getHealth(),730,"one potion adaptation pass");near(h,PassiveData.read(p).adaptation,142,"potion adaptation layers");
        near(h,(int)call(data(p,"ADAPTIVE_PASSIVES"),"regeneration"),45,"native regeneration uses 270 actual, not 300 incoming HP");
        h.runAfterDelay(20,()->{try{call(clazz("power.AdaptivePassiveSystem"),"tick",new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(p));near(h,p.getHealth(),775,"native healing remains 45 actual HP");}
            catch(Exception e){throw new RuntimeException(e);}finally{p.discard();}h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=65)
    public static void nativeAdaptationAndPotionRegenerationUsePostMitigationDamage(GameTestHelper h) throws Exception {
        var p=player(h);qualifyNative(p);PassiveApi.grant(p,2,1);
        p.invulnerableTime=0;p.hurt(p.damageSources().generic(),60);
        near(h,p.getHealth(),730,"one native adaptation pass");near(h,(int)call(data(p,"ADAPTIVE_PASSIVES"),"adaptation"),142,"native adaptation layers");
        near(h,PassiveData.read(p).regeneration,45,"potion regeneration uses post-native actual damage");
        h.runAfterDelay(20,()->{try{PassiveSystem.tick(p);near(h,p.getHealth(),775,"potion healing also restores only 45 HP");near(h,PassiveData.read(p).regeneration,36,"potion actual-heal cost");}
            finally{p.discard();}h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=70)
    public static void sharedInstallNeverDoublesDamageOrHealing(GameTestHelper h) throws Exception {
        var p=player(h); var state=data(p,"PLAYER_STATE"); call(state,"infect"); call(state,"setPrototypeMode",true); call(state,"addEvolutionPoints",10000);
        var skills=clazz("power.CombatSkill");
        call(state,"buyCombatSkill",skills.getField("ADAPTATION").get(null)); call(state,"buyCombatSkill",skills.getField("TISSUE_REGENERATION").get(null));
        var nativeState=clazz("power.AdaptivePassiveState").getConstructor(int.class,int.class,int.class,long.class).newInstance(0,100,100,0L);
        data(p,"ADAPTIVE_PASSIVES",nativeState);
        call(clazz("power.CombatScale"),"attributes",p,true); p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200); p.setHealth(1000);
        PassiveApi.grant(p,1,1); PassiveApi.grant(p,2,1); var d=PassiveData.read(p); d.adaptation=100; d.regeneration=0; d.save(p);
        h.assertTrue(!(boolean)call(clazz("power.AdaptivePassiveSystem"),"active",p,1),"potion replaces native adaptation");
        p.invulnerableTime=0; p.hurt(p.damageSources().generic(),60); // Blacklight converts to 300 actual health units.
        near(h,p.getHealth(),730,"one mitigation pass"); near(h,PassiveData.read(p).adaptation,142,"one stack pass");
        near(h,PassiveData.read(p).regeneration,45,"actual damage only"); near(h,(int)call(data(p,"ADAPTIVE_PASSIVES"),"adaptation"),100,"native layers untouched");
        h.runAfterDelay(20,()->{ PassiveSystem.tick(p); near(h,p.getHealth(),775,"45 HP heals as 45, not 225");
            var after=PassiveData.read(p); after.bonusHealth=30; after.save(p); HealthGrowth.apply(p,30); near(h,p.getMaxHealth(),1030,"kill bonus not multiplied by five"); h.succeed(); });
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void legacyGrantMigratesWithoutUnlockingSkills(GameTestHelper h) throws Exception {
        var p=player(h); var state=data(p,"PLAYER_STATE"); int before=(int)call(state,"getCombatSkillMask");
        var old=clazz("power.AdaptivePassiveState").getConstructor(int.class,int.class,int.class,long.class).newInstance(3,90,10,0L);
        data(p,"ADAPTIVE_PASSIVES",old);
        call(clazz("power.AdaptivePassiveSystem"),"tick",new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(p));
        var d=PassiveData.read(p); near(h,d.adaptationTier,1,"legacy adaptation"); near(h,d.regenerationTier,1,"legacy regen"); near(h,d.adaptation,90,"legacy stacks");
        near(h,(int)call(data(p,"ADAPTIVE_PASSIVES"),"permanentMask"),0,"migration acknowledged once");
        near(h,(int)call(state,"getCombatSkillMask"),before,"potion never unlocks skill");
        PassiveApi.grant(p,1,2); PassiveApi.migrate(p,3,999,999); near(h,PassiveData.read(p).adaptation,90,"migration never overwrites independent progression"); h.succeed();
    }
}
