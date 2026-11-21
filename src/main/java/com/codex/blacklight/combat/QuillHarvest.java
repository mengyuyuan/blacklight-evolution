package com.codex.blacklight.combat;

import com.codex.blacklight.entity.QuillOrgan;
import com.codex.blacklight.entity.BackspineQuill;
import com.codex.blacklight.power.*;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.state.BlacklightState;
import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.server.level.*;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import com.codex.blacklight.combat.ForgeDamageEvent;
import com.codex.blacklight.combat.ForgeEntityTickEvent;
import static com.codex.blacklight.config.GameplayBalanceConfig.*;

@EventBusSubscriber(modid="blacklight")
public final class QuillHarvest {
    private static final String MARK = "BlacklightQuillParasite";
    private static final String VOLLEY = "BlacklightNextQuillSortie";
    public static final ResourceKey<DamageType> DRAIN = ResourceKey.create(Registries.DAMAGE_TYPE,
            new ResourceLocation("blacklight", "quill_drain"));
    private static final Map<ServerPlayer, Long> EMPTY_HINT = new WeakHashMap<>();
    private QuillHarvest() {}
    @SubscribeEvent public static void serverStarted(net.minecraftforge.event.server.ServerStartedEvent event) {
        updateQuillDefaults();
    }

    public static BlacklightState state(ServerPlayer p) { return ModAttachments.getData(p,ModAttachments.PLAYER_STATE); }
    public static void sync(ServerPlayer p) { ModAttachments.syncData(p,ModAttachments.PLAYER_STATE); }
    public static boolean active(ServerPlayer p) {
        return p != null && p.isAlive() && !p.isSpectator() && state(p).isInfected() && state(p).isPrototypeMode();
    }
    public static boolean payShot(ServerPlayer p, boolean heavy, boolean charged, boolean combo) {
        int cost = !heavy ? QUILL_LIGHT.get() : charged ? QUILL_CHARGED.get() : combo ? QUILL_COMBO.get() : QUILL_QUICK.get();
        if (state(p).spendBiomass(cost)) {
            if (cost > 0) sync(p);
            return true;
        }
        long now = p.level().getGameTime();
        if (now >= EMPTY_HINT.getOrDefault(p, 0L)) {
            p.displayClientMessage(Component.translatable("message.blacklight.quill_empty"), true);
            EMPTY_HINT.put(p, now + 20);
        }
        return false;
    }

    public static void plant(ServerPlayer p, LivingEntity target, Vec3 entry, int socket) {
        if (!(target instanceof Mob mob) || protectedPet(mob) || !CombatTargeting.damageable(p,mob)) return;
        attach(p,mob,entry,socket);
    }

    /** Capture eligibility before hurt(), while pet, grapple and boss protections can still be checked. */
    public static boolean canHarvestImpact(ServerPlayer p,LivingEntity target){
        return target instanceof Mob mob && !(mob instanceof com.codex.blacklight.world.GreeneHost) && active(p) && CombatTargeting.damageable(p,mob) && !protectedPet(mob)
                && ConsumeSystem.eligible(mob) && AssimilationSystem.ready(mob)
                && (!ConsumeSequence.boss(mob) || ConsumeSystem.healthReady(p,mob));
    }

    public static void impact(ServerPlayer p,LivingEntity target,Vec3 entry,int socket,boolean harvestable){
        if(target.isAlive()){
            plant(p,target,entry,socket);
        }else if(harvestable && target instanceof Mob mob
                && ((com.codex.blacklight.mixin.LivingDeathAccess)mob).blacklight$deathAccepted()){
            var carrier=attach(p,mob,entry,socket);
            if(carrier!=null && carrier.collect(p,mob,AssimilationSystem.quote(p,mob)))carrier.harvest(true);
        }
    }

    private static QuillOrgan attach(ServerPlayer p,Mob mob,Vec3 entry,int socket){
        CompoundTag mark = mob.getPersistentData().getCompound(MARK);
        if (mark.hasUUID("owner") && !p.getUUID().equals(mark.getUUID("owner"))) return null;
        mark.putUUID("owner", p.getUUID());
        mark.putInt("layers", Math.min(QUILL_LAYERS.get(), mark.getInt("layers")+1));
        if (!mark.contains("x")) {
            mark.putInt("socket",Math.max(0,Math.min(5,socket)));
            Vec3 relative = entry.subtract(mob.position()).yRot((float)Math.toRadians(mob.yBodyRot));
            mark.putDouble("x",relative.x); mark.putDouble("y",relative.y); mark.putDouble("z",relative.z);
        }
        mark.putDouble("attack", attack(p));
        mob.getPersistentData().put(MARK,mark);
        return organ(p.serverLevel(),mob,mark);
    }
    public static Vec3 anchor(Mob mob, CompoundTag mark) {
        return mob.position().add(new Vec3(mark.getDouble("x"),mark.getDouble("y"),mark.getDouble("z"))
                .yRot((float)Math.toRadians(-mob.yBodyRot)));
    }
    public static CompoundTag mark(Mob mob) { return mob.getPersistentData().getCompound(MARK); }
    public static boolean protectedPet(Entity entity) {
        return entity instanceof OwnableEntity pet && pet.getOwnerUUID()!=null
                || entity instanceof TamableAnimal tame && tame.isTame()
                || entity instanceof AbstractHorse horse && horse.isTamed();
    }
    private static QuillOrgan organ(ServerLevel level, Mob mob, CompoundTag mark) {
        if (mark.hasUUID("organ") && level.getEntity(mark.getUUID("organ")) instanceof QuillOrgan q && !q.isRemoved()) return q;
        var q = new QuillOrgan(level, mob, mark.getUUID("owner"), anchor(mob,mark));
        if (!level.addFreshEntity(q)) return null;
        mark.putUUID("organ",q.getUUID());
        return q;
    }
    private static double attack(net.minecraft.world.entity.player.Player p) {
        return EvolutionAttribute.attackMultiplier(InfiniteEvolutionSystem.effectiveRank(p,EvolutionAttribute.STRENGTH));
    }
    public static double drainFraction(int layers, double percent, double growth, double attack) {
        return layers * (percent / 100.0) * (1 + growth * Math.max(0,attack-1));
    }
    public static double drainPercent(net.minecraft.world.entity.player.Player player,int layers) {
        return 100*configuredDrain(layers,attack(player));
    }
    private static double configuredDrain(int layers,double attack) {
        return drainFraction(Math.min(layers,value(QUILL_LAYERS)),value(QUILL_DRAIN),value(QUILL_GROWTH),attack);
    }
    public static int harvestRange(BlacklightState state) { return state.hasCombatSkill(CombatSkill.QUILL_REACH) ? QUILL_RANGE_UP.get() : QUILL_RANGE.get(); }
    static double range(ServerPlayer p) { return harvestRange(state(p)); }
    public static boolean available(ServerPlayer p, Mob mob) {
        return active(p) && p.level()==mob.level() && mob.distanceToSqr(p)<=range(p)*range(p)
                && eligibleHost(p,mob);
    }
    private static boolean eligibleHost(ServerPlayer p, Mob mob) {
        return !(mob instanceof com.codex.blacklight.world.GreeneHost) && active(p) && p.level()==mob.level() && !protectedPet(mob) && ConsumeSystem.eligible(mob) && CombatTargeting.damageable(p,mob)
                && ConsumeSystem.healthReady(p,mob);
    }
    public static void recall(ServerPlayer p) {
        double radius = range(p);
        var selected=new ArrayList<QuillOrgan>();
        for (Mob mob : p.serverLevel().getEntitiesOfClass(Mob.class,p.getBoundingBox().inflate(radius))) {
            var mark = mark(mob);
            if (!mark.hasUUID("owner") || !p.getUUID().equals(mark.getUUID("owner")) || !available(p,mob)) continue;
            var q = organ(p.serverLevel(),mob,mark);
            if(q!=null && q.phase()==QuillOrgan.ROOT) selected.add(q);
        }
        selected.sort(Comparator.comparingDouble(q->q.distanceToSqr(p)));
        if(selected.isEmpty()) return;
        Vec3 first=selected.get(0).position().subtract(p.position()).yRot((float)Math.toRadians(p.yBodyRot));
        boolean right=first.x<0;
        BackspineCombat.prepareReception(p,right);
        for(int i=0;i<selected.size();i++){
            var q=selected.get(i);
            q.routeShoulder(i%2==0?right:!right,i==0);
            q.harvest(false);
        }
    }
    @SubscribeEvent public static void tick(ForgeEntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Mob mob) || !(mob.level() instanceof ServerLevel level)
                || !mob.isAlive() || mob.tickCount % 20 != 0 || !mob.getPersistentData().contains(MARK)) return;
        if(protectedPet(mob)){
            mob.getPersistentData().remove(MARK);
            return;
        }
        var mark = mark(mob);
        if (!mark.hasUUID("owner") || mark.getInt("layers")<1) return;
        var q = organ(level,mob,mark);
        if(q==null || q.phase()!=QuillOrgan.ROOT) return;
        var owner = level.getServer().getPlayerList().getPlayer(mark.getUUID("owner"));
        if(owner!=null) mark.putDouble("attack",attack(owner));
        boolean harvest = owner!=null && available(owner,mob) && state(owner).hasCombatSkill(CombatSkill.QUILL_HARVEST);
        if (owner!=null && owner.level()==level && !CombatTargeting.damageable(owner,mob)) return;
        if (ConsumeLastChance.awaitingFinisher(mob)) {
            if (harvest && !mark.getBoolean("harvestRejected")) q.harvest(false);
            return;
        }
        mark.remove("harvestRejected");
        double amount = mob.getMaxHealth()*configuredDrain(mark.getInt("layers"),mark.getDouble("attack"));
        if(amount<=0) return;
        var type=level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DRAIN);
        var source=new DamageSource(type, null, owner!=null&&owner.level()==level?owner:null);
        var reward=harvest ? AssimilationSystem.quote(owner,mob) : null;
        float damage=(float)Math.min(Float.MAX_VALUE,amount/CombatScale.units(mob));
        // 1.20 has no damage-type tag for knockback. A parasite's pulse adds no external impulse.
        Vec3 before=mob.getDeltaMovement();
        boolean hit=harvest && mob.getMaxHealth()<ConsumeSystem.DIRECT_GRAB_MAX_HEALTH ? ConsumeLastChance.execute(mob,()->mob.hurt(source,damage)) : mob.hurt(source,damage);
        if(hit && mob.isAlive())mob.setDeltaMovement(before);
        if(hit && harvest && ConsumeLastChance.awaitingFinisher(mob)) q.harvest(false);
        if(hit && harvest && !mob.isAlive() && ((com.codex.blacklight.mixin.LivingDeathAccess)mob).blacklight$deathAccepted()
                && q.collect(owner,mob,reward)) q.harvest(true);
    }
    public static boolean finish(ServerPlayer p, Mob mob, QuillOrgan q) {
        if (!eligibleHost(p,mob)) return false;
        if (mob instanceof com.codex.blacklight.entity.BlacklightHunter hunter && !hunter.claim(p)) return false;
        if (mob instanceof com.codex.blacklight.entity.BlacklightSupremeHunter supreme && !supreme.claim(p)) return false;
        var reward=AssimilationSystem.quote(p,mob);
        if(!ConsumeSystem.defeatForConsume(p,mob)){
            // A death veto leaves the marked host available for E without replaying extraction every second.
            if(ConsumeLastChance.awaitingFinisher(mob))mark(mob).putBoolean("harvestRejected",true);
            return false;
        }
        return q.collect(p,mob,reward);
    }
    static TargetSelection attackTarget(ServerPlayer p, double range, boolean orbit) {
        return attackTarget(p,range,orbit,-1);
    }
    static TargetSelection attackTarget(ServerPlayer p, double range, boolean orbit,int preferredId) {
        var lock = CombatTargeting.lockedSelection(p);
        if (lock!=null) return !protectedPet(lock.target()) && (!orbit || !ConsumeLastChance.awaitingFinisher(lock.target())) && lock.visibleWithinRange(p,range) ? lock : null;

        var candidates = p.serverLevel().getEntitiesOfClass(Mob.class,p.getBoundingBox().inflate(range),
                mob -> !protectedPet(mob) && (!orbit || !ConsumeLastChance.awaitingFinisher(mob)) && (mob instanceof Enemy || mob.getTarget()==p || !orbit && mob instanceof Animal)
                        && CombatTargeting.selectable(p,mob));

        if (candidates.isEmpty()) return null;
        Set<UUID> incoming = new HashSet<>();
        var search = p.getBoundingBox().inflate(range + QUILL_ORBIT_RADIUS.get());
        for (var shot : p.serverLevel().getEntitiesOfClass(BackspineQuill.class,search,
                shot -> shot.aimTarget()!=null && shot.getOwner()==p)) incoming.add(shot.aimTarget());
        for (var shard : p.serverLevel().getEntitiesOfClass(QuillOrgan.class,search,
                shard -> shard.strikeTarget()!=null && p.getUUID().equals(shard.owner()))) incoming.add(shard.strikeTarget());

        TargetSelection selected = null;
        int bestPriority = Integer.MAX_VALUE;
        float lowest = Float.POSITIVE_INFINITY;
        int tied = 0;
        for (var mob : candidates) {
            int priority = 0;
            // Within each coverage group, prioritize enemies over passive wildlife.
            if (mob.getPersistentData().contains(MARK) && mark(mob).hasUUID("owner")) priority = 4;
            else if (incoming.contains(mob.getUUID())) priority = 2;
            if (!(mob instanceof Enemy) && mob.getTarget()!=p) priority++;
            float hp = mob.getHealth();
            if (priority>bestPriority || (priority==bestPriority && hp>lowest)) continue;
            var visible = TargetSelection.choices(p,mob).stream().filter(part -> part.visibleWithinRange(p,range)).findFirst().orElse(null);
            if (visible==null) continue;
            if (priority<bestPriority || hp<lowest) {
                selected=visible;
                bestPriority=priority;
                lowest=hp;
                tied=1;
            } else if (mob.getId()==preferredId || (selected.target().getId()!=preferredId && p.getRandom().nextInt(++tied)==0)) {
                selected=visible;
            }
        }
        return selected;
    }
    public static TargetSelection sortie(ServerPlayer p) {
        if (!active(p) || state(p).getActivePower()!=PowerForm.BACKSPINES) return null;
        long now=p.level().getGameTime();
        if (now<p.getPersistentData().getLong(VOLLEY)) return null;
        var target=attackTarget(p,QUILL_AUTO_RANGE.get(),true);
        p.getPersistentData().putLong(VOLLEY,now+(target==null?4:QUILL_AUTO_INTERVAL.get()));
        return target;
    }
    @SubscribeEvent public static void wounded(ForgeDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || event.getNewDamage()<=0 || !active(p) || p.getHealth()>=p.getMaxHealth()) return;
        var shards=p.serverLevel().getEntitiesOfClass(QuillOrgan.class,p.getBoundingBox().inflate(Math.max(5,QUILL_ORBIT_RADIUS.get()+1.5)),
                q->q.phase()==QuillOrgan.ORBIT && p.getUUID().equals(q.owner()));
        if (!shards.isEmpty()) shards.get(p.getRandom().nextInt(shards.size())).liquefy();
    }
}
