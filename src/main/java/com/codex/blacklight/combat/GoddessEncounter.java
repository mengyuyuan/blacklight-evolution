package com.codex.blacklight.combat;

import com.codex.blacklight.entity.EncounterMob;
import com.codex.blacklight.world.BossSiteData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/** The airborne outbreak is played on the original site owner, including all three executions. */
public final class GoddessEncounter {
    public enum Action { FLYING, STAGGER, FALLING, DOWN, GRIPPED, EXTRACTING, RISING }
    private final EncounterMob boss;
    public final GoddessAttacks attacks;
    private final GoddessVitals life = new GoddessVitals();
    private Action action = Action.FLYING;
    private UUID actor;
    private Vec3 grabFrom=Vec3.ZERO;
    public static EncounterMob paired(Player p){
        return p.level().getEntitiesOfClass(EncounterMob.class,p.getBoundingBox().inflate(76),b->b.goddess.active() &&
                b.goddess.action()==Action.EXTRACTING && b.goddess.isActor(p)).stream().findFirst().orElse(null);
    }
    private boolean isActor(Player p){
        if(!boss.level().isClientSide)return p.getUUID().equals(actor);
        var state=boss.goddessData();return state.hasUUID("Actor")&&state.getUUID("Actor").equals(p.getUUID());
    }
    private boolean initialized, committed;
    private int age, part, previousMask;
    private float facing, entryYaw, fallFrom;
    private boolean staggerLocked;
    static final int LOWER_TICKS = 20, OPEN_TICKS = 80, RECOVER_TICKS = 24;
    private static float fraction(float value) { return Math.max(0, Math.min(1, value)); }
    public float lowered(float partial) {
        float t = age(partial);
        return switch (action()) {
            case FLYING -> 0;
            case STAGGER -> Math.min(fraction(t / LOWER_TICKS), fraction((LOWER_TICKS + OPEN_TICKS + RECOVER_TICKS - t) / RECOVER_TICKS));
            case FALLING -> {
                float start = boss.level().isClientSide ? boss.goddessData().getFloat("FallFrom") : fallFrom;
                yield start + (1 - start) * fraction(t / 38F);
            }
            case RISING -> 1 - fraction(t / 50F);
            default -> 1;
        };
    }
    private double lift;
    private Vec3 approach;
    public Vec3 approach(){return approach;}
    float braceTilt(float partial) {
        if(action()==Action.STAGGER) return lowered(partial);
        if(action()!=Action.FALLING) return 0;
        float start=boss.level().isClientSide ? boss.goddessData().getFloat("FallFrom") : fallFrom;
        float t=fraction(age(partial)/38F);
        return start*(1-t*t*(3-2*t));
    }
    public double lift(float partial){
        double dy=boss.level().isClientSide?boss.goddessData().getDouble("LandingLift"):lift;
        return dy * lowered(partial);
    }
    private void site(int exposed){
        var site=GoddessLanding.find(boss,exposed,facing,false);
        if(site!=null){facing=site.yaw();lift=site.lift();approach=site.approach();}
        else {lift=0;approach=null;}
    }
    private record SurfaceSample(long tick,float partial,Vec3 origin,float yaw,int pose,int mask,Action action,
                                 java.util.List<HydraContact.Region> regions) {}
    private final SurfaceSample[] skin = new SurfaceSample[3];
    public java.util.List<HydraContact.Region> regions(Vec3 origin,float partial){
        int slot=partial==0?0:partial==1?1:2;
        var cached=skin[slot];
        long tick=boss.level().getGameTime();
        float yaw=facing(partial);
        int frame=(int)age(0),mask=removed();
        var action=action();
        if(cached!=null && cached.tick==tick && cached.partial==partial && cached.origin.equals(origin)
                && cached.yaw==yaw && cached.pose==frame && cached.mask==mask && cached.action==action)return cached.regions;
        var result=GoddessRig.regions(boss,origin,partial);
        skin[slot]=new SurfaceSample(tick,partial,origin,yaw,frame,mask,action,result);
        return result;
    }

    public GoddessEncounter(EncounterMob boss) { this.boss = boss; this.attacks = new GoddessAttacks(boss); }
    public boolean active() { return boss.greeneOutbreak() && boss.greeneBattle().armourWave() > 1; }
    public int removed() { return boss.level().isClientSide ? boss.goddessData().getInt("Removed") : life.removed(); }
    public int part() { return boss.level().isClientSide ? boss.goddessData().getInt("Part") : part; }
    public int poseMask() {
        return action() == Action.EXTRACTING || action() == Action.RISING
                ? (boss.level().isClientSide ? boss.goddessData().getInt("Before") : previousMask) : removed();
    }
    public Action action() {
        if (!boss.level().isClientSide) return action;
        return readAction(boss.goddessData());
    }
    private static Action readAction(CompoundTag tag) {
        int index = tag.getInt("Action");
        return index >= 0 && index < Action.values().length ? Action.values()[index] : Action.FLYING;
    }
    public float age(float partial) {
        return boss.level().isClientSide ? Math.max(0, boss.level().getGameTime() - boss.goddessData().getLong("Start") + partial) : age + partial;
    }
    public float facing(float partial) {
        if (action() == Action.FLYING) return net.minecraft.util.Mth.rotLerp(partial,boss.yBodyRotO,boss.yBodyRot);
        var data = boss.goddessData();
        float start = boss.level().isClientSide ? data.getFloat("EntryYaw") : entryYaw;
        float end = boss.level().isClientSide ? data.getFloat("Facing") : facing;
        return net.minecraft.util.Mth.rotLerp(lowered(partial), start, end);
    }
    public boolean terminal() { return life.finished(); }
    public boolean zeroOnBar() { return (removed() & 3) == 3 && part() == GoddessVitals.HEAD && action()!=Action.FLYING && action()!=Action.STAGGER && action()!=Action.RISING; }
    public boolean damageable() { return action() == Action.FLYING || action() == Action.STAGGER; }

    public void born() {
        initialized = true;
        life.restore(0,false,0);
        boss.spider.synchronizeShellHealth();
        boss.setHealth(boss.getMaxHealth());
        change(Action.FLYING);
    }
    private void migrate() {
        float fraction = boss.getHealth()/boss.getMaxHealth();
        boss.spider.synchronizeShellHealth();
        initialized = true;
        float hp = life.healthAfterHit(boss.getMaxHealth(), fraction * boss.getMaxHealth());
        boss.setHealth(hp);
        if (life.waiting()) fall();
        else change(Action.FLYING);
    }
    public float healthWrite(float proposed) {
        return life.healthAfterHit(boss.getHealth(),proposed);
    }
    public void paid(float amount) {
        if (!damageable() || amount <= 0) return;
        boss.setHealth(healthWrite(boss.getHealth()-amount));
        afterDamage();
    }
    public void afterDamage() {
        if (life.waiting() && damageable()) fall();
    }
    private int nextPart() {
        if ((removed() & GoddessVitals.LEFT_ARM) == 0) return GoddessVitals.LEFT_ARM;
        return (removed() & GoddessVitals.RIGHT_ARM) == 0 ? GoddessVitals.RIGHT_ARM : GoddessVitals.HEAD;
    }
    int supportingWrist() {
        return switch (nextPart()) {
            case GoddessVitals.LEFT_ARM -> 5;
            case GoddessVitals.RIGHT_ARM -> 9;
            default -> -1;
        };
    }
    public boolean groundWeakSpot(net.minecraft.world.damagesource.DamageSource source, HydraHurtContact.Contact hit) {
        if (action != Action.STAGGER || age < LOWER_TICKS || age >= LOWER_TICKS + OPEN_TICKS || hit == null
                || !(source.getDirectEntity() instanceof ServerPlayer player)) return false;
        if (!player.onGround() && (player.getDeltaMovement().y > .01
                || !player.level().getBlockCollisions(player,player.getBoundingBox().move(0,-.04,0)).iterator().hasNext())) return false;
        int slot = GoddessRig.PARTS.indexOf(hit.bone());
        if (slot < 0 || (slot != 0 && slot != 1 && slot != supportingWrist())) return false;
        double reach = player.entityInteractionRange() + 0.5;
        return player.getEyePosition().distanceToSqr(hit.point()) <= reach * reach
                && HydraHurtContact.clear(boss, player.getEyePosition(), hit.point());
    }
    public void rockImpact(com.codex.blacklight.entity.ThrownBlock rock, HydraHurtContact.Contact hit) {
        if (hit == null || !(rock.getOwner() instanceof ServerPlayer)) return;
        int slot = GoddessRig.PARTS.indexOf(hit.bone());
        if (slot == 0 && (rock.twoHanded() || rock.chargedImpact())
                || slot >= 0 && slot == supportingWrist() && rock.chargedImpact()) stagger(hit);
    }
    public void aerialImpact(HydraHurtContact.Contact hit) {
        if (hit != null && attacks.strainingSlot() == GoddessRig.PARTS.indexOf(hit.bone()) && attacks.strainingSlot() >= 0) stagger(hit);
    }
    void finishedAttack() { staggerLocked = false; }
    private void stagger(HydraHurtContact.Contact hit) {
        if (action != Action.FLYING || life.waiting() || staggerLocked) return;
        var landing = GoddessLanding.find(boss, nextPart(), boss.yBodyRot,true);
        if (landing == null) return;
        entryYaw = boss.yBodyRot;
        facing = landing.yaw();
        lift = landing.lift();
        approach = landing.approach();
        staggerLocked = true;
        attacks.cancel();
        change(Action.STAGGER);
        SpiderFoley.play(boss, hit.point(), SpiderFoley.Cue.STRAIN, 1.6F);
    }
    private void fall() {
        boolean continuing = action == Action.STAGGER;
        fallFrom = continuing ? lowered(0) : 0;
        attacks.cancel();
        boss.cancelAttack();
        previousMask = life.removed();
        part = nextPart();
        if (!continuing) {
            entryYaw = facing = boss.yBodyRot;
            site(part);
        }
        change(Action.FALLING);
        boss.playSound(SoundEvents.RAVAGER_ROAR, 2F,.65F);
    }
    private void change(Action next) {
        action = next;
        age = 0;
        publish();
    }
    /** Validate both presses against the same exposed part and player, including after a reconnect. */
    public static boolean input(ServerPlayer player,boolean confirm) {
        var nearby = player.level().getEntitiesOfClass(EncounterMob.class, player.getBoundingBox().inflate(70),
                mob -> mob.goddess.active() && (mob.goddess.action == Action.DOWN || mob.goddess.action == Action.GRIPPED));
        for (var mob : nearby) {
            var fight = mob.goddess;
            if (fight.action == Action.GRIPPED && player.getUUID().equals(fight.actor)) {
                if (!confirm || !fight.nearPart(player)) fight.release();
                else {
                    fight.committed = false;
                    fight.grabFrom=player.position();
                    fight.change(Action.EXTRACTING);
                }
                return true;
            }
            if (!confirm && fight.action == Action.DOWN && fight.nearPart(player)) {
                fight.actor = player.getUUID();
                BossSiteData.participant(mob,player);
                fight.change(Action.GRIPPED);
                player.displayClientMessage(Component.translatable("message.blacklight.goddess_confirm",Component.keybind("key.use")),true);
                return true;
            }
        }
        return false;
    }
    private boolean nearPart(Player player) {
        return !player.isSpectator() && player.isAlive() && GoddessRig.reachable(boss,player,part);
    }
    private void release() {
        actor=null;
        change(Action.DOWN);
    }
    public void tick() {
        if (!initialized) { migrate(); return; }
        age++;
        if (action == Action.FLYING) attacks.tick();
        var player = actor == null ? null : boss.level().getPlayerByUUID(actor);
        if (action == Action.FALLING && age == 38) {
            change(Action.DOWN);
            boss.playSound(SoundEvents.GENERIC_EXPLODE.value(),2,.65F);
        } else if (action == Action.GRIPPED && (player == null || !nearPart(player) || age >= 100)) release();
        else if (action == Action.EXTRACTING) tickExecution(player);
        else if (action == Action.RISING && age >= 50) {
            if (life.waiting()) fall(); else change(Action.FLYING);
        } else if (action == Action.STAGGER) {
            if (age == LOWER_TICKS && approach != null) SpiderFoley.ground(boss,approach,1.8F,SpiderFoley.Cue.BRACE);
            if (age >= LOWER_TICKS + OPEN_TICKS + RECOVER_TICKS) change(Action.FLYING);
        }
        if (action == Action.DOWN && age % 20 == 1) {
            for (var p : boss.level().players()) if (nearPart(p))
                p.displayClientMessage(Component.translatable("message.blacklight.goddess_grab",Component.keybind("key.blacklight.grab")),true);
        }
    }
    private void tickExecution(Player player) {
        if (!committed && (player == null || !player.isAlive() || player.isSpectator())) { release(); return; }
        if(player instanceof ServerPlayer p && p.isAlive()){
            float mix=Math.min(1,age/12F);mix=mix*mix*(3-2*mix);
            Vec3 next=grabFrom.lerp(GoddessActor.point(boss,0,0),mix);
            if(!boss.level().getBlockCollisions(p,p.getBoundingBox().move(next.subtract(p.position()))).iterator().hasNext()){
                p.teleportTo(next.x,next.y,next.z);
            }
            p.setDeltaMovement(Vec3.ZERO);p.fallDistance=0;
            p.setYRot(GoddessActor.yaw(boss,0));p.yBodyRot=p.getYRot();p.yHeadRot=p.getYRot();
        }
        if (!committed && age >= 56) {
            previousMask = life.removed();
            float hp = life.absorb(part,boss.getHealth());
            committed = true;
            boss.setHealth(Math.max(1,hp));
            boss.playSound(SoundEvents.GENERIC_EXPLODE.value(),1.2F,.75F);
            publish();
        }
        if (age < 139) return;
        if (life.finished()) {
            var source = player == null ? boss.damageSources().generic() : boss.damageSources().playerAttack(player);
            boss.setHealth(0);
            boss.die(source);
        } else {
            actor=null;
            committed=false;
            if (life.waiting()) fall(); else change(Action.RISING);
        }
    }
    void publish() {
        var tag = save();
        tag.putLong("Start",boss.level().getGameTime()-age);
        boss.goddessData(tag);
    }
    public CompoundTag save() {
        var t=new CompoundTag();
        t.put("Offense",attacks.save());
        t.putBoolean("Initialized",initialized);
        t.putInt("Action",action.ordinal());
        t.putInt("Age",age);
        t.putInt("Part",part);
        t.putInt("Removed",life.removed());
        t.putInt("Before",previousMask);
        t.putFloat("Remainder",life.remainder());
        t.putBoolean("Waiting",life.waiting());
        t.putBoolean("Committed",committed);
        t.putBoolean("StaggerLocked",staggerLocked);
        t.putFloat("EntryYaw",entryYaw);
        t.putFloat("FallFrom",fallFrom);
        t.putFloat("Facing",facing);t.putDouble("LandingLift",lift);
        if(approach!=null){t.putDouble("FloorX",approach.x);t.putDouble("FloorY",approach.y);t.putDouble("FloorZ",approach.z);}
        if (actor!=null){t.putUUID("Actor",actor);t.putDouble("GripX",grabFrom.x);t.putDouble("GripY",grabFrom.y);t.putDouble("GripZ",grabFrom.z);}
        return t;
    }
    public void load(CompoundTag t) {
        attacks.load(t.getCompound("Offense"));
        initialized=t.getBoolean("Initialized");
        action=readAction(t);
        age=Math.max(0,t.getInt("Age"));
        part=t.getInt("Part");
        previousMask=t.getInt("Before") & 7;
        staggerLocked=t.getBoolean("StaggerLocked") || action==Action.STAGGER;
        entryYaw=t.contains("EntryYaw") ? t.getFloat("EntryYaw") : t.getFloat("Facing");
        fallFrom=fraction(t.getFloat("FallFrom"));
        facing=t.getFloat("Facing");lift=t.getDouble("LandingLift");
        approach=t.contains("FloorX")?new Vec3(t.getDouble("FloorX"),t.getDouble("FloorY"),t.getDouble("FloorZ")):null;
        committed=t.getBoolean("Committed");
        actor=t.hasUUID("Actor") ? t.getUUID("Actor") : null;
        grabFrom=new Vec3(t.getDouble("GripX"),t.getDouble("GripY"),t.getDouble("GripZ"));
        life.restore(t.getInt("Removed"), t.getBoolean("Waiting"), t.getFloat("Remainder"));
        publish();
    }
}
