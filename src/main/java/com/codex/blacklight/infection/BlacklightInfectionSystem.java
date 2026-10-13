package com.codex.blacklight.infection;

import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.registry.ModEffects;
import com.codex.blacklight.state.BlacklightState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.codex.blacklight.combat.ForgeDamageEvent;
import com.codex.blacklight.combat.ForgeEntityTickEvent;

import java.util.Comparator;

public final class BlacklightInfectionSystem {
    private static final double HUNT_RADIUS = 14.0D;

    private BlacklightInfectionSystem() {
    }

    public static boolean infect(LivingEntity target, Entity source) {
        if (!canInfect(target)) return false;
        boolean applied = target.addEffect(new MobEffectInstance(
                ModEffects.BLACKLIGHT_INFECTION.get(),
                MobEffectInstance.INFINITE_DURATION,
                0,
                false,
                false,
                false), source);
        // Vanilla dragons reject every status effect. Their saved onset marker drives the
        // same mutation timeline; immunity must not prevent the dedicated boss replacement.
        boolean dedicatedBoss=com.codex.blacklight.entity.InfectedBossProfile.forSpecies(
                net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString())!=null;
        if ((applied || dedicatedBoss) && ModAttachments.available(target)) {
            com.codex.blacklight.registry.ModAttachments.setData(target, ModAttachments.INFECTION_START, target.level().getGameTime());
            com.codex.blacklight.registry.ModAttachments.syncData(target, ModAttachments.INFECTION_START);
        }
        return applied || dedicatedBoss;
    }

    /** Eligibility is checked before random exposure/cooldown; players awaken voluntarily. */
    public static boolean canInfect(LivingEntity target) {
        return target != null && ModAttachments.available(target) && !target.level().isClientSide && target.isAlive()
                && target instanceof Mob && !(target instanceof com.codex.blacklight.entity.HiveTendril) && !isInfected(target);
    }

    public static boolean awaken(Player player) {
        if (player.level().isClientSide || player.isSpectator() || !player.isAlive()) return false;
        BlacklightState state = com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.PLAYER_STATE);
        if (state.isInfected()) return false;
        state.infect();
        com.codex.blacklight.registry.ModAttachments.setData(player, ModAttachments.PLAYER_STATE, state);
        com.codex.blacklight.registry.ModAttachments.syncData(player, ModAttachments.PLAYER_STATE);
        player.displayClientMessage(Component.translatable("message.blacklight.infected"), false);
        player.displayClientMessage(Component.translatable("message.blacklight.awakening_guide"), false);
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            com.codex.blacklight.power.GrowthSystem.award(serverPlayer, "awakening");
        }
        return true;
    }

    public static boolean isInfected(LivingEntity entity) {
        if (entity instanceof Player player) {
            return ModAttachments.peekData(player, ModAttachments.PLAYER_STATE).isInfected();
        }
        return entity instanceof com.codex.blacklight.entity.HiveTendril || entity instanceof com.codex.blacklight.entity.BlacklightInfectedHost || entity instanceof com.codex.blacklight.entity.BlacklightHunter || entity instanceof com.codex.blacklight.entity.BlacklightMutant || entity instanceof com.codex.blacklight.entity.BlacklightHive || entity.hasEffect(ModEffects.BLACKLIGHT_INFECTION.get())
                || (ModAttachments.peekData(entity, ModAttachments.INFECTION_START)>=0
                    && com.codex.blacklight.entity.InfectedBossProfile.forSpecies(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString())!=null);
    }

    public static void notifyBurstOwner(Entity owner, int exposedCount) {
        if (owner instanceof Player player) {
            player.displayClientMessage(Component.translatable(
                    "message.blacklight.vial_burst", exposedCount), true);
        }
    }

    @SubscribeEvent
    public static void onContactDamage(ForgeDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        if (event.getNewDamage() <= 0.0F || !canInfect(victim)) return;
        var damage = event.getSource();
        // Only physical bites/claws by infected creatures can spread infection.
        // Causing-entity alone also matches arrows, explosions and thorns, and
        // infected-player weapons must not start an outbreak on every attack.
        if (!(damage.getEntity() instanceof LivingEntity attacker) || attacker instanceof Player
                || !isInfected(attacker) || damage.getDirectEntity() != attacker
                || !(damage.is(DamageTypes.MOB_ATTACK) || damage.is(DamageTypes.MOB_ATTACK_NO_AGGRO))) return;
        long now = victim.level().getGameTime();
        var data = victim.getPersistentData();
        if (data.getLong(InfectionRules.NEXT_CONTACT_EXPOSURE) > now) return;
        // Failed rolls also consume this victim's exposure window. Multiple
        // attackers and save/reload cannot reroll every damage event.
        data.putLong(InfectionRules.NEXT_CONTACT_EXPOSURE, now + InfectionRules.CONTACT_COOLDOWN_TICKS);
        if (attacker.getRandom().nextFloat() < InfectionRules.CONTACT_CHANCE) infect(victim, attacker);
    }

    @SubscribeEvent
    public static void onInfectedMobTick(ForgeEntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Mob mob)
                || !(mob.level() instanceof ServerLevel serverLevel)
                || !ModAttachments.available(mob) || !mob.isAlive() || mob instanceof com.codex.blacklight.entity.HiveTendril || mob instanceof com.codex.blacklight.entity.BlacklightInfectedHost || mob instanceof com.codex.blacklight.entity.BlacklightHunter || mob instanceof com.codex.blacklight.entity.BlacklightMutant || mob instanceof com.codex.blacklight.entity.BlacklightHive || !isInfected(mob)) return;
        long onset=ModAttachments.peekData(mob, ModAttachments.INFECTION_START);
        // Migrate existing saved infections too, without removing infection or other effects.
        var infection=mob.getEffect(ModEffects.BLACKLIGHT_INFECTION.get());
        if(infection!=null&&infection.isVisible())mob.addEffect(new MobEffectInstance(
                infection.getEffect(),infection.getDuration(),infection.getAmplifier(),infection.isAmbient(),false,false));
        if(com.codex.blacklight.combat.ImpactControl.stunned(mob)
                || ModAttachments.peekData(mob, ModAttachments.CONSUME_VISUAL).active())return;
        if(onset>=0 && serverLevel.getGameTime()-onset<InfectionTimeline.DURATION) {
            mob.getNavigation().stop();
            return;
        }
        if (mob.getPersistentData().getLong("BlacklightConversionRetry") > serverLevel.getGameTime()) return;
        convertHost(mob, serverLevel);
    }

    private static void convertHost(Mob source, ServerLevel level) {
        var infected = com.codex.blacklight.registry.ModEntities.INFECTED_HOST.get().create(level);
        if (infected == null) return;
        infected.inherit(source);
        infected.copyPosition(source);
        infected.yBodyRot=source.yBodyRot;infected.yHeadRot=source.yHeadRot;
        infected.setDeltaMovement(source.getDeltaMovement());
        infected.setNoAi(source.isNoAi()); infected.setInvulnerable(source.isInvulnerable());
        infected.setCustomName(source.getCustomName()); infected.setCustomNameVisible(source.isCustomNameVisible());
        source.getTags().forEach(infected::addTag);
        // The source and its inventory survive if another mod vetoes replacement.
        if (!level.addFreshEntity(infected)) return;
        // Conversion is not a kill: return stored possessions even with doMobLoot=false.
        // Never clear them before the replacement joins, and retain the source if an
        // item-spawn veto prevents returning a slot. Earlier accepted drops stay dropped.
        if (!releaseStoredItems(source, level)) {
            infected.discard();
            source.getPersistentData().putLong("BlacklightConversionRetry", level.getGameTime() + 200);
            return;
        }
        for (var slot : net.minecraft.world.entity.EquipmentSlot.values()) {
            infected.setItemSlot(slot, source.getItemBySlot(slot).copyAndClear());
            infected.setDropChance(slot, 1.0F);
        }
        if (source.getVehicle() != null) infected.startRiding(source.getVehicle(), true);
        for (var passenger : java.util.List.copyOf(source.getPassengers())) passenger.startRiding(infected, true);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.SCULK_SOUL,
                infected.getX(),infected.getY()+infected.getBbHeight()*.5,infected.getZ(),
                12,infected.getBbWidth()*.35,infected.getBbHeight()*.25,infected.getBbWidth()*.35,.025);
        level.playSound(null,infected.blockPosition(),net.minecraft.sounds.SoundEvents.SCULK_CATALYST_BLOOM,
                net.minecraft.sounds.SoundSource.HOSTILE,.8F,.65F);
        source.discard();
    }

    /** Public vanilla inventory APIs; no detached visual NBT is used as item storage. */
    private static boolean releaseStoredItems(Mob source, ServerLevel level) {
        if (source instanceof net.minecraft.world.entity.animal.horse.AbstractHorse horse) {
            if (!releaseStoredSlot(source, level, horse.getSlot(400))) return false;
            if (!releaseStoredSlot(source, level, horse.getSlot(401))) return false;
            for (int index = 500; ; index++) {
                var slot=horse.getSlot(index);
                if(slot==net.minecraft.world.entity.SlotAccess.NULL)break;
                if (!releaseStoredSlot(source, level, slot)) return false;
            }
            // Remove the chest last: clearing slot 499 rebuilds/shrinks the inventory.
            if (horse instanceof net.minecraft.world.entity.animal.horse.AbstractChestedHorse chested
                    && chested.hasChest() && !releaseStoredSlot(source, level, horse.getSlot(499))) return false;
        } else {
            net.minecraft.world.Container inventory = source instanceof net.minecraft.world.entity.npc.InventoryCarrier carrier
                    ? carrier.getInventory() : source instanceof net.minecraft.world.Container container ? container : null;
            if (inventory != null) {
                for (int index = 0; index < inventory.getContainerSize(); index++) {
                    if (!releaseStoredSlot(source, level, net.minecraft.world.entity.SlotAccess.forContainer(inventory, index))) return false;
                }
            }
        }
        // These two vanilla species store only a saddle flag, not an inventory stack.
        // Keep this last, so accepted insertion is followed immediately by conversion.
        if ((source instanceof net.minecraft.world.entity.animal.Pig
                || source instanceof net.minecraft.world.entity.monster.Strider)
                && source instanceof net.minecraft.world.entity.Saddleable saddled && saddled.isSaddled()) {
            var drop = new net.minecraft.world.entity.item.ItemEntity(level, source.getX(), source.getY(), source.getZ(),
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SADDLE));
            drop.setDefaultPickUpDelay();
            if (!level.addFreshEntity(drop)) return false;
        }
        return true;
    }

    private static boolean releaseStoredSlot(Mob source, ServerLevel level, net.minecraft.world.entity.SlotAccess slot) {
        var stack = slot.get();
        if (stack.isEmpty()) return true;
        var stored = stack.copy();
        // Clear first so synchronous entity-join callbacks never see both copies.
        if (!slot.set(net.minecraft.world.item.ItemStack.EMPTY)) return false;
        var drop = new net.minecraft.world.entity.item.ItemEntity(level, source.getX(), source.getY(), source.getZ(), stored.copy());
        drop.setDefaultPickUpDelay();
        if (level.addFreshEntity(drop)) return true;
        slot.set(stored);
        return false;
    }

    private static boolean isValidTarget(Mob attacker, LivingEntity candidate) {
        if (candidate == null || candidate == attacker || !candidate.isAlive() || isInfected(candidate)) return false;
        return !(candidate instanceof Player player)
                || (!player.isCreative() && !player.isSpectator()
                    && (attacker.level().getServer() == null
                        || !attacker.level().getServer().isPvpAllowed()));
    }
}
