package com.codex.blacklight.client;

import com.codex.blacklight.power.*;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.state.BlacklightState;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Explicitly invoked disposable-server checks. Real server ticks advance grace and interval;
 * no world clock is changed and no synthetic damage/healing event replaces Player.hurt/heal.
 * Root should await the returned future before installing its next player fixture.
 */
@EventBusSubscriber(modid="blacklight", value=Dist.CLIENT)
public final class StandaloneCriticalMassChecks {
    private static final Path QA=Path.of("D:/Blacklight-Standalone/run-native-qa").toAbsolutePath().normalize();
    private static final Map<ServerPlayer,Run> RUNS=new IdentityHashMap<>();
    private StandaloneCriticalMassChecks() { }

    public static CompletableFuture<JsonObject> run(ServerPlayer p) {
        if(!p.server.isSameThread())throw new IllegalStateException("Critical Mass checks require the server thread");
        if(!p.server.getServerDirectory().toAbsolutePath().normalize().equals(QA)
                ||!p.server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize()
                    .equals(QA.resolve("saves/Blacklight Standalone QA v4")))
            throw new IllegalStateException("Critical Mass checks refuse a non-disposable game directory/save");
        if(RUNS.containsKey(p))return RUNS.get(p).completion;
        if(!p.isAlive())throw new IllegalStateException("Critical Mass checks require a living QA player");
        var run=new Run(p);RUNS.put(p,run);
        try { run.begin(); } catch(Throwable error) { run.finish(error); }
        return run.completion;
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void tick(PlayerTickEvent.Post event) {
        if(!(event.getEntity() instanceof ServerPlayer p))return;
        var run=RUNS.get(p);if(run==null)return;
        try { run.advance(); } catch(Throwable error) { run.finish(error); }
    }

    private static final class Run {
        final ServerPlayer p;
        final CompletableFuture<JsonObject> completion=new CompletableFuture<>();
        final JsonObject report=new JsonObject();
        final JsonArray checks=new JsonArray();
        final BlacklightState originalState;
        final CriticalMassState originalMass;
        final AdrenalineState originalAdrenaline;
        final GameType originalMode;
        final int originalUtility, originalInvulnerableTime;
        final float originalHealth, originalAbsorption;
        final double originalAbsorptionBase;
        final boolean originalInvulnerable;
        long began, surgeStarted, firstRelease;
        int stage;

        Run(ServerPlayer p) {
            this.p=p;originalState=p.getData(ModAttachments.PLAYER_STATE);
            originalMass=p.getData(ModAttachments.CRITICAL_MASS);originalAdrenaline=p.getData(ModAttachments.ADRENALINE);
            originalMode=p.gameMode.getGameModeForPlayer();originalUtility=p.getData(ModAttachments.UTILITY_POWERS);
            originalHealth=p.getHealth();originalAbsorption=p.getAbsorptionAmount();
            originalAbsorptionBase=p.getAttribute(Attributes.MAX_ABSORPTION).getBaseValue();
            originalInvulnerable=p.isInvulnerable();originalInvulnerableTime=p.invulnerableTime;
        }
        long now(){return p.level().getGameTime();}
        CriticalMassState mass(){return p.getData(ModAttachments.CRITICAL_MASS);}
        AdrenalineState adrenaline(){return p.getData(ModAttachments.ADRENALINE);}
        void begin() {
            began=now();
            report.addProperty("source","real Player.heal/hurt, registered CriticalMassSystem handlers, live server tick boundaries; lifecycle event dispatch without disconnecting the QA client");
            var state=new BlacklightState();state.infect();state.setPrototypeMode(true);state.activate(PowerForm.NONE);
            state.addEvolutionPoints(10_000);state.buyCombatSkill(CombatSkill.CRITICAL_MASS);state.buyCombatSkill(CombatSkill.ADRENALINE_SURGE);
            p.setData(ModAttachments.PLAYER_STATE,state);p.syncData(ModAttachments.PLAYER_STATE);
            p.setData(ModAttachments.CRITICAL_MASS,CriticalMassState.EMPTY);p.setData(ModAttachments.ADRENALINE,AdrenalineState.EMPTY);
            p.setData(ModAttachments.UTILITY_POWERS,0);p.syncData(ModAttachments.UTILITY_POWERS);
            p.setGameMode(GameType.SURVIVAL);GrowthSystem.updateHealth(p);
            p.setAbsorptionAmount(0);p.setInvulnerable(true);
            float maximum=p.getMaxHealth(), units=CombatScale.units(p);

            p.setHealth(maximum*.5F);
            CriticalMassSystem.healFromConsume(p,maximum*.1F/units);
            require(p.getHealth()>maximum*.5F&&p.getHealth()<maximum,"consume did not heal a wounded player");
            require(mass().reserve()==0,"wounded consume granted reserve without overflow");
            record("wounded_consume_heals_without_reserve");

            p.setHealth(maximum);p.heal(maximum/units);
            require(mass().reserve()==0,"ordinary healing filled Critical Mass");
            record("ordinary_full_health_heal_does_not_fill_reserve");

            CriticalMassSystem.healFromConsume(p,maximum*.1F/units);
            near(mass().reserve(),.22F,"consume overflow did not apply source .55 gain");
            record("consume_overflow_applies_source_gain");

            float before=mass().reserve();
            Consumer<LivingHealEvent> cancel=e->{if(e.getEntity()==p)e.setCanceled(true);};
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,false,LivingHealEvent.class,cancel);
            try { CriticalMassSystem.healFromConsume(p,maximum/units); }
            finally { NeoForge.EVENT_BUS.unregister(cancel); }
            near(mass().reserve(),before,"canceled healing created reserve");
            record("canceled_consume_heal_does_not_create_reserve");

            p.setData(ModAttachments.CRITICAL_MASS,new CriticalMassState(1,true));
            p.setHealth(maximum*.75F);float health=p.getHealth();
            hurt(.2F);
            near(p.getHealth(),health,"reserve protection also reduced health");
            near(mass().reserve(),1-.2F*units/(maximum*.25F),"reserve did not absorb the final converted damage");
            record("reserve_absorbs_before_health");

            p.setData(ModAttachments.CRITICAL_MASS,new CriticalMassState(.1F,true));p.setHealth(maximum*.75F);
            health=p.getHealth();hurt(maximum*.05F/units);
            require(mass().reserve()==0,"overflow damage retained depleted reserve");
            near(p.getHealth(),health-maximum*.025F,"reserve overflow removed more than the health remainder");
            record("reserve_overflow_only_reduces_health_by_remainder");

            p.setData(ModAttachments.CRITICAL_MASS,new CriticalMassState(1,true));
            p.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(Math.max(originalAbsorptionBase,2/units));
            p.setAbsorptionAmount(2);
            float golden=p.getAbsorptionAmount();health=p.getHealth();hurt(.2F);
            require(golden>0,"golden absorption fixture has no actual absorption");
            near(p.getHealth(),health,"reserve and golden absorption reduced health");
            near(p.getAbsorptionAmount(),golden,"reserve protection stole golden absorption");
            p.setAbsorptionAmount(0);record("reserve_protection_preserves_golden_absorption");

            p.setData(ModAttachments.CRITICAL_MASS,new CriticalMassState(1,true));
            Consumer<LivingDamageEvent.Pre> zero=e->{if(e.getEntity()==p)e.setNewDamage(0);};
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,LivingDamageEvent.Pre.class,zero);
            try { hurt(.2F); } finally { NeoForge.EVENT_BUS.unregister(zero); }
            near(mass().reserve(),1,"late zero-damage decision consumed reserve");
            record("late_pre_zero_damage_does_not_consume_reserve");

            p.setData(ModAttachments.CRITICAL_MASS,CriticalMassState.EMPTY);
            triggerSurge();surgeStarted=now();
            require(p.isAlive()&&adrenaline().active(now()),"lethal damage did not trigger Adrenaline");
            require(adrenaline().undamageableUntil()-now()==36,"source 1.8-second grace is not 36 ticks");
            require(adrenaline().until()-now()==100,"adapted release opportunity is not 100 ticks");
            health=p.getHealth();hurt(.1F);
            near(p.getHealth(),health,"Adrenaline did not reject incoming damage");
            record("lethal_damage_triggers_separate_grace_and_release_lease");
            stage=1;
        }
        void advance() {
            long now=now();
            require(now-began<650,"live tick acceptance exceeded its bounded deadline");
            if(stage==1&&now>=surgeStarted+36) {
                require(!adrenaline().undamageable(now),"grace did not expire at tick 36");
                require(CriticalMassSystem.ready(p),"release opportunity incorrectly expired with grace");
                float health=p.getHealth();hurt(.1F);require(p.getHealth()<health,"expired grace continued blocking damage");
                record("live_tick_36_damage_grace_expires");
                require(CriticalMassSystem.spend(p),"free Adrenaline release was rejected");firstRelease=now;
                require(mass().lastAdrenalineDevastatorAt()==now,"free release timestamp was not recorded");
                require(!CriticalMassSystem.spend(p),"one Adrenaline opportunity allowed two free releases");
                record("free_release_is_single_use");

                p.setData(ModAttachments.CRITICAL_MASS,new CriticalMassState(1,false,firstRelease));
                require(CriticalMassSystem.spend(p),"Adrenaline interval blocked an ordinary Critical Mass release");
                require(mass().reserve()==0&&mass().lastAdrenalineDevastatorAt()==firstRelease,"reserve release altered the free-release interval");
                record("adrenaline_interval_does_not_block_reserve_release");

                p.setData(ModAttachments.ADRENALINE,new AdrenalineState(now+100,now+36,true));
                NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(p));
                require(adrenaline().equals(AdrenalineState.EMPTY),"logout event retained the transient lease");
                require(mass().lastAdrenalineDevastatorAt()==firstRelease,"logout event erased release history");
                p.setData(ModAttachments.ADRENALINE,new AdrenalineState(now+100,now+36,true));
                NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerChangedDimensionEvent(p,p.level().dimension(),p.level().dimension()));
                require(adrenaline().equals(AdrenalineState.EMPTY),"dimension event retained the transient lease");
                var encoded=CriticalMassState.CODEC.encodeStart(JsonOps.INSTANCE,mass()).getOrThrow();
                p.setData(ModAttachments.CRITICAL_MASS,CriticalMassState.CODEC.parse(JsonOps.INSTANCE,encoded).getOrThrow());
                require(mass().lastAdrenalineDevastatorAt()==firstRelease,"save/load erased free-release history");
                p.setHealth(p.getMaxHealth()*.6F);
                record("lifecycle_and_codec_keep_interval_but_clear_lease");
                stage=2;
            }
            if(stage==2&&now>=firstRelease+100) {
                require(mass().adrenalineArmed(),"health recovery did not rearm Adrenaline");
                triggerSurge();
                require(p.isAlive()&&adrenaline().undamageable(now),"free-release interval prevented Adrenaline survival");
                require(mass().lastAdrenalineDevastatorAt()==firstRelease,"second lethal trigger erased release history");
                require(!CriticalMassSystem.ready(p)&&!CriticalMassSystem.spend(p),"free release ignored its interval");
                p.setHealth(p.getMaxHealth()*.6F);
                record("interval_does_not_prevent_survival_trigger");
                stage=3;
            }
            if(stage==3&&now>=firstRelease+449) {
                require(now==firstRelease+449,"tick-449 boundary was skipped");
                triggerSurge();
                require(!CriticalMassSystem.ready(p)&&!CriticalMassSystem.spend(p),"free release was allowed at tick 449");
                require(mass().lastAdrenalineDevastatorAt()==firstRelease,"rejected release changed saved interval");
                record("live_tick_449_free_release_remains_blocked");
                stage=4;
            }
            if(stage==4&&now>=firstRelease+450) {
                require(now==firstRelease+450,"tick-450 boundary was skipped");
                require(CriticalMassSystem.ready(p)&&CriticalMassSystem.spend(p),"free release remained blocked at tick 450");
                require(mass().lastAdrenalineDevastatorAt()==now,"second free release did not update saved interval");
                require(!adrenaline().chargeAvailable(),"second free release did not spend its one-shot permission");
                record("live_tick_450_free_release_available");
                finish(null);
            }
        }
        void triggerSurge() {
            p.setHealth(Math.max(1,p.getMaxHealth()*.6F));
            hurt(Math.max(100,p.getMaxHealth()*10/CombatScale.units(p)));
        }
        void hurt(float amount) {
            p.invulnerableTime=0;p.setInvulnerable(false);
            try { p.hurt(p.damageSources().generic(),amount); }
            finally { p.setInvulnerable(true); }
        }
        void record(String name) {
            var entry=new JsonObject();entry.addProperty("name",name);entry.addProperty("tick",now());
            entry.addProperty("health",p.getHealth());entry.addProperty("reserve",mass().reserve());
            entry.addProperty("lastAdrenalineDevastatorAt",mass().lastAdrenalineDevastatorAt());
            entry.addProperty("adrenalineUntil",adrenaline().until());entry.addProperty("undamageableUntil",adrenaline().undamageableUntil());
            entry.addProperty("chargeAvailable",adrenaline().chargeAvailable());entry.addProperty("passed",true);checks.add(entry);
        }
        void finish(Throwable error) {
            RUNS.remove(p);report.addProperty("passed",error==null);report.addProperty("elapsedTicks",now()-began);
            report.addProperty("sourceGraceTicks",36);report.addProperty("sourceFreeReleaseIntervalTicks",450);
            report.addProperty("adaptedReleaseLeaseTicks",100);report.add("checks",checks);
            if(error!=null)report.addProperty("error",error.toString());
            try {
                p.setData(ModAttachments.PLAYER_STATE,originalState);p.syncData(ModAttachments.PLAYER_STATE);
                p.setData(ModAttachments.CRITICAL_MASS,originalMass);p.syncData(ModAttachments.CRITICAL_MASS);
                p.setData(ModAttachments.ADRENALINE,originalAdrenaline);p.syncData(ModAttachments.ADRENALINE);
                p.setData(ModAttachments.UTILITY_POWERS,originalUtility);p.syncData(ModAttachments.UTILITY_POWERS);
                p.setGameMode(originalMode);GrowthSystem.updateHealth(p);
                p.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(originalAbsorptionBase);
                p.setHealth(Math.max(1,Math.min(p.getMaxHealth(),originalHealth)));p.setAbsorptionAmount(originalAbsorption);
                p.setInvulnerable(originalInvulnerable);p.invulnerableTime=originalInvulnerableTime;
            } catch(Throwable restoreError) { report.addProperty("passed",false);report.addProperty("restoreError",restoreError.toString()); }
            completion.complete(report);
        }
    }
    private static void require(boolean condition,String message) { if(!condition)throw new IllegalStateException(message); }
    private static void near(float actual,float expected,String message) { require(Float.isFinite(actual)&&Math.abs(actual-expected)<.002F,message+": "+actual+" != "+expected); }
}
