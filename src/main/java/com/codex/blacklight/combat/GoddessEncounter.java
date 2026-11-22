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
    private float facing;
    private double lift;
    private Vec3 approach;
    public Vec3 approach(){return approach;}
    public double lift(float partial){
        double dy=boss.level().isClientSide?boss.goddessData().getDouble("LandingLift"):lift;
        float t=age(partial);
        return dy*switch(action()){
            case FLYING -> 0F;
            case STAGGER,FALLING -> net.minecraft.util.Mth.clamp(t/(action()==Action.STAGGER?20F:38F),0,1);
            case RISING -> Math.max(0,1-t/50);
            default -> 1F;
        };
    }
    private void site(int exposed){
        var site=GoddessLanding.find(boss,exposed,facing);
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
        return action() == Action.FLYING ? net.minecraft.util.Mth.rotLerp(partial,boss.yBodyRotO,boss.yBodyRot)
                : boss.level().isClientSide ? boss.goddessData().getFloat("Facing") : facing;
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
    public void stagger() {
        if (action != Action.FLYING || life.waiting()) return;
        facing = boss.yBodyRot;
        attacks.cancel();
        site(life.removed()==3?GoddessVitals.HEAD:(life.removed()&1)==0?1:2);
        change(Action.STAGGER);
    }
    private void fall() {
        attacks.cancel();
        boss.cancelAttack();
        facing = boss.yBodyRot;
        previousMask = life.removed();
        part = (previousMask & 3) == 3 ? GoddessVitals.HEAD :
                (previousMask & GoddessVitals.LEFT_ARM) == 0 ? GoddessVitals.LEFT_ARM : GoddessVitals.RIGHT_ARM;
        site(part);
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
        } else if (action == Action.STAGGER && age >= 100) change(Action.FLYING);
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
        facing=t.getFloat("Facing");lift=t.getDouble("LandingLift");
        approach=t.contains("FloorX")?new Vec3(t.getDouble("FloorX"),t.getDouble("FloorY"),t.getDouble("FloorZ")):null;
        committed=t.getBoolean("Committed");
        actor=t.hasUUID("Actor") ? t.getUUID("Actor") : null;
        grabFrom=new Vec3(t.getDouble("GripX"),t.getDouble("GripY"),t.getDouble("GripZ"));
        life.restore(t.getInt("Removed"), t.getBoolean("Waiting"), t.getFloat("Remainder"));
        publish();
    }
}
