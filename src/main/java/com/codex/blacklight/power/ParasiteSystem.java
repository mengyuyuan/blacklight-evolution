package com.codex.blacklight.power;

import com.codex.blacklight.entity.BlacklightHive;
import com.codex.blacklight.entity.BlacklightHunter;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.TickEvent;

/** Supreme Parasite flow (feedback #22): suppression, Infected Hunter cure, and the apex rewards after the Supreme Hunter.
 * Stored as a death-persistent bit mask: nothing the player has learned is deleted while suppressed. */
@EventBusSubscriber(modid="blacklight")
public final class ParasiteSystem {
    public static final int SUPPRESSED=1,APEX=2;
    private ParasiteSystem(){}
    public static int bits(Player p){return ModAttachments.peekData(p,ModAttachments.PARASITE,0);}
    public static boolean suppressed(Player p){return p!=null&&(bits(p)&SUPPRESSED)!=0;}
    public static boolean apex(Player p){return p!=null&&(bits(p)&APEX)!=0;}
    private static void set(ServerPlayer p,int bits){ModAttachments.setData(p,ModAttachments.PARASITE,bits);ModAttachments.syncData(p,ModAttachments.PARASITE);}
    /** Only the starting claws and healing remain while suppressed. */
    public static boolean allows(Player p,PowerForm form){return !suppressed(p)||form==PowerForm.NONE||form==PowerForm.CLAWS;}
    public static boolean blocked(ServerPlayer p,PowerForm form){
        if(allows(p,form))return false;
        p.displayClientMessage(Component.translatable("message.blacklight.parasite_blocked"),true);return true;
    }

    public static boolean inject(ServerPlayer user,LivingEntity target){
        var level=user.serverLevel();
        if(target instanceof BlacklightHive hive){
            if(!hive.isAlive())return false;
            hive.parasiteBurst(user);
            level.playSound(null,hive.blockPosition(),SoundEvents.WARDEN_SONIC_BOOM,SoundSource.HOSTILE,3F,.5F);
            return true;
        }
        if(target instanceof BlacklightHunter hunter){
            if(!hunter.isAlive()||hunter.parasite()){user.displayClientMessage(Component.translatable("message.blacklight.syringe_no_effect"),true);return false;}
            hunter.infectParasite();
            user.displayClientMessage(Component.translatable("message.blacklight.hunter_infected"),false);
            level.playSound(null,hunter.blockPosition(),SoundEvents.SCULK_SHRIEKER_SHRIEK,SoundSource.HOSTILE,1.5F,.6F);
            return true;
        }
        if(target instanceof ServerPlayer victim){
            if(!ModAttachments.getData(victim,ModAttachments.PLAYER_STATE).isInfected()||suppressed(victim)){
                user.displayClientMessage(Component.translatable("message.blacklight.syringe_no_effect"),true);return false;}
            suppress(victim);
            if(victim!=user)user.displayClientMessage(Component.translatable("message.blacklight.syringe_other",victim.getDisplayName()),false);
            return true;
        }
        return false;
    }
    public static void suppress(ServerPlayer p){
        set(p,bits(p)|SUPPRESSED);
        enforce(p);
        p.displayClientMessage(Component.translatable("message.blacklight.parasite_suppressed"),false);
        p.level().playSound(null,p.blockPosition(),SoundEvents.ELDER_GUARDIAN_CURSE,SoundSource.PLAYERS,1F,.7F);
    }
    /** Consuming an Infected Hunter: lift the suppression and take the three apex forms without EP. */
    public static void consumedInfectedHunter(ServerPlayer p){
        set(p,bits(p)&~SUPPRESSED);
        p.removeEffect(MobEffects.WITHER);
        var state=ModAttachments.getData(p,ModAttachments.PLAYER_STATE);
        state.unlock(PowerForm.BLADE);state.unlock(PowerForm.ARMOR);state.unlock(PowerForm.SOVEREIGN_WINGS);
        ModAttachments.setData(p,ModAttachments.PLAYER_STATE,state);ModAttachments.syncData(p,ModAttachments.PLAYER_STATE);
        GrowthSystem.award(p,"biological_experiment");
        p.displayClientMessage(Component.translatable("message.blacklight.parasite_cured"),false);
    }
    public static void grantApex(ServerPlayer p){
        if(apex(p))return;
        set(p,bits(p)|APEX);
        p.displayClientMessage(Component.translatable("message.blacklight.apex_granted"),false);
    }
    private static void enforce(ServerPlayer p){
        var state=ModAttachments.getData(p,ModAttachments.PLAYER_STATE);
        if(state.isPrototypeMode()&&!allows(p,state.getActivePower())){
            com.codex.blacklight.combat.WeaponCombatSystem.prepareFormSwitch(p);
            state.activate(PowerForm.CLAWS);ModAttachments.setData(p,ModAttachments.PLAYER_STATE,state);ModAttachments.syncData(p,ModAttachments.PLAYER_STATE);
        }
        int utility=ModAttachments.getData(p,ModAttachments.UTILITY_POWERS);
        if(utility!=0){
            for(var form:new PowerForm[]{PowerForm.SHIELD,PowerForm.ARMOR,PowerForm.THERMAL_VISION,PowerForm.INFECTED_VISION,PowerForm.SOVEREIGN_WINGS})
                if((ModAttachments.getData(p,ModAttachments.UTILITY_POWERS)&form.bit())!=0)UtilityPowerSystem.toggle(p,form);
        }
        if(BiomassSystem.active(p))BiomassSystem.cancel(p);
        var wither=p.getEffect(MobEffects.WITHER);
        if(wither==null||!wither.isInfiniteDuration())p.addEffect(new MobEffectInstance(MobEffects.WITHER,MobEffectInstance.INFINITE_DURATION,0,false,true,true));
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!(event.player instanceof ServerPlayer p)||!p.isAlive())return;
        int bits=bits(p);if(bits==0)return;
        if((bits&SUPPRESSED)!=0&&p.tickCount%10==0)enforce(p);
        // Supreme Hunter reward: close to Regeneration V (vanilla V heals 1 every 3 ticks).
        if((bits&APEX)!=0&&p.tickCount%3==0&&p.getHealth()<p.getMaxHealth())p.heal(1F/Math.max(1F,CombatScale.units(p)));
    }
    @SubscribeEvent(priority=EventPriority.LOW)
    public static void incoming(LivingHurtEvent event){
        if(!(event.getEntity() instanceof ServerPlayer p)||event.isCanceled()||event.getAmount()<=0)return;
        var source=event.getSource();
        // Healing is never suppressed, so the parasite's wither can wear the body down but never kill.
        if(suppressed(p)&&source.is(DamageTypes.WITHER)&&event.getAmount()*CombatScale.units(p)>=p.getHealth()-1){event.setCanceled(true);return;}
        if(apex(p)&&!ModAttachments.getData(p,ModAttachments.PLAYER_STATE).isPrototypeMode()
                &&!source.is(DamageTypeTags.BYPASSES_INVULNERABILITY))event.setAmount(event.getAmount()*.2F);
    }
    /** Inject before any mob or mod interaction hook can swallow the click. */
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void interact(net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract event){
        var stack=event.getItemStack();
        if(!(stack.getItem() instanceof com.codex.blacklight.item.ParasiteSyringeItem))return;
        var target=event.getTarget();
        if(!(target instanceof BlacklightHunter||target instanceof BlacklightHive||target instanceof Player))return;
        event.setCanceled(true);event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        if(!(event.getEntity() instanceof ServerPlayer user)||user.getCooldowns().isOnCooldown(stack.getItem()))return;
        if(inject(user,(LivingEntity)target))com.codex.blacklight.item.ParasiteSyringeItem.spend(user,stack);
    }
    /** Prototype-mode super armor after defeating the Supreme Hunter. */
    public static boolean superArmor(Player p){return apex(p)&&PrototypeMetabolism.active(p);}
    @SubscribeEvent public static void knockback(LivingKnockBackEvent event){
        if(event.getEntity() instanceof Player p&&superArmor(p))event.setCanceled(true);
    }
}
