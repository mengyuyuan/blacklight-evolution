package com.codex.blacklight.combat;

import com.codex.blacklight.entity.EncounterMob;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import java.util.Comparator;
import java.util.UUID;

/** Server-owned attack clocks. The telegraph, fixed aim and closing sphere share one replicated center. */
public final class GoddessAttacks {
    public enum Move { REST, VACUOLE, BREATHER, NEEDLE_RAIN, WALL_SPEARS, BLOOD_SURF, CALL, STRONG_CALL, HAND, RUSH, STRUGGLE, INVERTED_ACID, TENDRIL_ERUPTION, SONIC_CRY, SPIKE_FIELD, INFECTION_WAVE, SEEKING_ORBS, TENDRIL_CRUSH, PACK_POUNCE }
    private static final Move[] ORDERS = {
            Move.VACUOLE, Move.HAND, Move.CALL, Move.HAND, Move.SONIC_CRY, Move.INVERTED_ACID,
            Move.SEEKING_ORBS, Move.BLOOD_SURF, Move.SPIKE_FIELD, Move.TENDRIL_CRUSH, Move.RUSH,
            Move.PACK_POUNCE, Move.WALL_SPEARS, Move.HAND, Move.INFECTION_WAVE, Move.STRONG_CALL,
            Move.TENDRIL_ERUPTION, Move.STRUGGLE, Move.NEEDLE_RAIN, Move.HAND
    };
    private static final Move[] ARMLESS = {
            Move.VACUOLE, Move.INVERTED_ACID, Move.CALL, Move.SONIC_CRY, Move.WALL_SPEARS,
            Move.PACK_POUNCE, Move.SPIKE_FIELD, Move.SEEKING_ORBS, Move.BLOOD_SURF, Move.STRUGGLE,
            Move.INFECTION_WAVE, Move.STRONG_CALL, Move.TENDRIL_ERUPTION, Move.NEEDLE_RAIN
    };
    public final GoddessTerrainCast terrain;
    public final GoddessCrownCast crown;
    public final GoddessCommandCast commands;
    public final GoddessHand hand;
    public final GoddessRush rush;
    public final GoddessStruggle struggle;
    final GoddessBrood brood;
    private final GoddessVolley volley;
    private final EncounterMob boss;
    private final GoddessSceneClock scene = new GoddessSceneClock();
    private Move move=Move.REST;
    private static final int GAZE_INHALE_TICKS = 12;
    private int elapsed, sequence, groups, order;
    private int lastVolley = -1;
    private Move lastAttack=Move.REST;
    static final int CALL_FIRST = 18, CALL_INTERVAL = 8;
    static final int NORMAL_CALL_END = 52, STRONG_CALL_END = 70;
    static final int RAIN_END = 44, SURF_END = 49, SPEARS_END = 80;
    private UUID target;
    private Vec3 flightHome;
    private Vec3 center=Vec3.ZERO;
    private final GoddessAim measuredAim = new GoddessAim();

    Vec3 lead(ServerPlayer player, double ticks) {
        Vec3 center = player.getBoundingBox().getCenter();
        measuredAim.observe(player.getUUID(), center, player.level().getGameTime());
        return measuredAim.lead(center, ticks);
    }
    private boolean caught,grounded,paid,bled;
    private float captureYaw;
    private Vec3 entry=Vec3.ZERO, impulse=Vec3.ZERO;
    private final java.util.List<UUID> debris=new java.util.ArrayList<>(4);
    public float captureYaw(){return boss.level().isClientSide?client().getFloat("CaptureYaw"):captureYaw;}
    private static double ease(double t){t=Math.max(0,Math.min(1,t));return t*t*(3-2*t);}
    GoddessAttacks(EncounterMob boss){this.boss=boss;terrain=new GoddessTerrainCast(boss);crown=new GoddessCrownCast(boss);commands=new GoddessCommandCast(boss);struggle=new GoddessStruggle(boss);hand=new GoddessHand(boss);rush=new GoddessRush(boss);brood=new GoddessBrood(boss);volley=new GoddessVolley(boss);}

    private CompoundTag client(){return boss.goddessData().getCompound("Offense");}
    public Move move(){return boss.level().isClientSide?decode(client()):move;}
    public float age(float partial){
        float physical = boss.level().isClientSide ? scene.age(boss.tickCount,partial) : elapsed+partial;
        if(move()!=Move.VACUOLE)return physical;
        // Stretch just the first half of the look-up. Subsequent camera, grab, impact and
        // release frames keep their authored spacing, using the same positive server clock.
        return physical<2*GAZE_INHALE_TICKS ? physical*.5F : physical-GAZE_INHALE_TICKS;
    }
    void receiveTimeline(CompoundTag data, long observed) {
        Move move=decode(data);
        long start=data.getLong("Start");
        int age=data.getInt("Age");
        scene.synchronize(data.getInt("Sequence"),move.ordinal(),start,
                move==Move.VACUOLE?start+age:observed,boss.tickCount);
        // At low frame rates entity ticks can lag behind the spatial sounds and capture state.
        // Track the server's published cast age without ever replaying a completed beat.
        if(move==Move.VACUOLE)scene.catchUpTo(age,boss.tickCount);
        crown.load(data.getCompound("Crown"));commands.load(data.getCompound("Commands"));terrain.load(data.getCompound("Terrain"));
        struggle.receiveTimeline(data.getCompound("Struggle"),observed);
    }
    public Vec3 center(){return boss.level().isClientSide?readCenter(client()):center;}
    public boolean caught(){return boss.level().isClientSide?client().getBoolean("Caught"):caught;}
    public boolean grounded(){return boss.level().isClientSide?client().getBoolean("Ground"):grounded;}
    public boolean holds(ServerPlayer p){return p.getUUID().equals(target) && (move==Move.STRUGGLE?struggle.holds():move==Move.RUSH?rush.holding():caught&&!paid);}
    public float actorYaw(){return struggle.active()?struggle.facing():move()==Move.RUSH?rush.yaw()+180:captureYaw();}
    public int sequence(){return boss.level().isClientSide?client().getInt("Sequence"):sequence;}
    public boolean bled(){return boss.level().isClientSide?client().getBoolean("Bled"):bled;}
    public boolean ruptured(){return boss.level().isClientSide?client().getBoolean("Paid"):paid;}
    public boolean safe(ServerPlayer p){return move==Move.BREATHER || holds(p) || (move==Move.RUSH && p.getUUID().equals(target) && rush.released() && !rush.landed());}
    public UUID victim(){var t=boss.level().isClientSide?client():null;return t==null?target:t.hasUUID("Target")?t.getUUID("Target"):null;}
    private ServerPlayer player(ServerLevel level){return target==null?null:level.getEntity(target) instanceof ServerPlayer p&&p.isAlive()&&!p.isSpectator()&&!p.isCreative()?p:null;}
    private ServerPlayer nearest(ServerLevel level){
        return level.players().stream().filter(p->p.isAlive()&&!p.isSpectator()&&!p.isCreative()&&p.distanceToSqr(boss)<112*112)
                .min(Comparator.comparingDouble(p->p.distanceToSqr(boss))).orElse(null);
    }
    static boolean recoveryDue(int groups, float health, float maximum) {
        return health < maximum && (groups >= 4 || groups >= 2 && health <= maximum * .5F);
    }

    int previousVolley() { return lastVolley; }

    public boolean keepsProjectile(int cast) {
        return boss.goddess.active() && boss.goddess.action() == GoddessEncounter.Action.FLYING
                && (cast == sequence || lastVolley != -1 && cast == lastVolley);
    }

    private void send(){boss.goddess.publish();}
    private void enter(Move next,ServerPlayer p){
        if(move==Move.SEEKING_ORBS || move==Move.TENDRIL_CRUSH || move==Move.PACK_POUNCE)commands.clear();
        if(move==Move.VACUOLE && caught && !paid && boss.level() instanceof ServerLevel level)GoddessHeldMovement.release(player(level),boss);
        debris.clear();
        if(move==Move.BREATHER || next==Move.BREATHER)brood.clearMeals();
        if (next == Move.BREATHER || next == Move.STRUGGLE || next == Move.RUSH || next == Move.VACUOLE) lastVolley = -1;
        move=next;elapsed=0;caught=false;grounded=false;paid=false;bled=false;
        target=p==null?null:p.getUUID();
        if(p!=null){center=p.getBoundingBox().getCenter();captureYaw=p.yBodyRot;}
        sequence++;send();
    }
    public void cancel(){
        if(struggle.active() && boss.level() instanceof ServerLevel level)struggle.cancel(player(level));
        hand.cancel();
        abandonMove();
    }
    private void abandonMove(){
        lastVolley = -1;
        if((caught || move==Move.RUSH) && boss.level() instanceof ServerLevel level){
            var p=player(level);if(p!=null)p.fallDistance=0;
        }
        enter(Move.REST,null);
    }
    // The entity root follows the cave floor. Its articulated mesh supplies the actual floating height.
    private void approach(ServerLevel level,ServerPlayer player){
        if(flightHome==null)flightHome=boss.position();
        Vec3 offset=player.position().subtract(boss.position()).multiply(1,0,1);
        double distance=offset.length();
        if(distance<16 || distance>112 || (boss.tickCount&1)!=0)return;
        Vec3 forward=offset.normalize();
        Vec3 probe=boss.position().add(0,6,0);
        boolean obscured=!clear(probe,player.getEyePosition());
        if(distance<32 && !obscured)return;
        boolean clockwise=(boss.getId()&1)==0;
        float side=clockwise?1:-1;
        float steer=obscured?.88F*side:.12F*side;
        for(float angle:new float[]{steer,-steer,1.25F*side,-1.25F*side}){
            Vec3 step=forward.yRot(angle).scale(obscured?.48:.58);
            Vec3 end=boss.position().add(step);
            if(end.subtract(flightHome).horizontalDistance()>64 || !level.hasChunkAt(BlockPos.containing(end)))continue;
            var floor=level.clip(new ClipContext(end.add(0,1.6,0),end.add(0,-2.6,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,boss));
            if(floor.getType()!=HitResult.Type.BLOCK || floor.getDirection()!=net.minecraft.core.Direction.UP)continue;
            if(!clear(probe,probe.add(step.scale(8))) || !clear(probe.add(0,12,0),probe.add(0,12,0).add(step.scale(6))))continue;
            double climb=Math.max(-.5,Math.min(.5,floor.getLocation().y-boss.getY()));
            Vec3 motion=step.add(0,climb,0);
            var box=boss.getBoundingBox();
            if(!level.getWorldBorder().isWithinBounds(box.move(motion)))continue;
            // Move against live terrain so excavation and newly placed columns affect the pursuit.
            Vec3 permitted=net.minecraft.world.entity.Entity.collideBoundingBox(boss,motion,box,level,java.util.List.of());
            if(permitted.horizontalDistanceSqr()<step.horizontalDistanceSqr()*.64)continue;
            boss.move(net.minecraft.world.entity.MoverType.SELF,permitted);
            return;
        }
    }
    public void tick(){
        if(!(boss.level() instanceof ServerLevel level))return;
        elapsed++;
        if(flightHome==null)flightHome=boss.position();
        // Stop the body for every committed throw, capture and rest meal; keep advancing while conducting at range.
        if(move==Move.HAND || move==Move.CALL || move==Move.STRONG_CALL
                || move==Move.NEEDLE_RAIN && elapsed<8 || move==Move.WALL_SPEARS && elapsed<16){
            var tracked=player(level);
            if(tracked!=null)approach(level,tracked);
        }
        if(move==Move.REST){
            if (elapsed < (order == 0 ? 10 : 2)) return;
            var p=nearest(level);
            if(p==null)return;
            boolean recover = groups >= 2 && level.players().stream().anyMatch(other -> other.isAlive()
                    && !other.isSpectator() && !other.isCreative() && other.distanceToSqr(boss) <= 100 * 100
                    && recoveryDue(groups, other.getHealth(), other.getMaxHealth()));
            if(recover){enter(Move.BREATHER,p);}
            else {
                Move[] choices=(boss.goddess.removed()&3)==3?ARMLESS:ORDERS;
                int slot=Math.floorMod(order++,choices.length);
                Move selected=choices[slot];
                if(selected==Move.TENDRIL_CRUSH && (!hand.readyForGrip() || !commands.canCrush(level,p)))selected=Move.HAND;
                if(selected==Move.PACK_POUNCE && !commands.canPounce(level,p))selected=fallback();
                if((selected==Move.CALL || selected==Move.STRONG_CALL) && !brood.ready(selected==Move.STRONG_CALL))selected=fallback();
                if(selected==Move.STRUGGLE){
                    if(!struggle.begin(p))selected=fallback();
                }else if(selected==Move.RUSH && hand.readyForGrip() && !rush.close(p) && struggle.begin(p)){
                    selected=Move.STRUGGLE;
                }
                else if(selected==Move.RUSH){
                    if(!rush.begin(p))selected=Move.WALL_SPEARS;
                }else if(selected==Move.HAND && slot==3 && rush.close(p) && rush.begin(p,true)){
                    selected=Move.RUSH;
                }
                if (selected == Move.HAND && !hand.begin(level,p)) {
                    selected = fallback();
                }
                enter(selected,p);
            }
            if(move==Move.VACUOLE)SpiderFoley.play(boss,eyes(),SpiderFoley.Cue.INHALE,3.8F);
            else if(GoddessCastPose.active(move))SpiderFoley.play(boss,eyes(),SpiderFoley.Cue.LOAD,3.6F);
            else SpiderFoley.play(boss,eyes(),move==Move.BREATHER?SpiderFoley.Cue.BRACE:SpiderFoley.Cue.LOAD,1.25F);
            return;
        }
        if(move==Move.BREATHER){
            if(brood.rest(level,elapsed)){
                groups=0;
                enter(Move.REST,null);
            }
            return;
        }
        var p=player(level);
        if(p==null && struggle.active() && struggle.step()==GoddessStruggle.Step.OPEN && struggle.age(0)<15)return;
        if(p==null){if(struggle.active()){struggle.cancel(null);hand.cancel();}if(move==Move.HAND)hand.cancel();abandonMove();return;}
        measuredAim.observe(p.getUUID(), p.getBoundingBox().getCenter(), level.getGameTime());
        telegraphStrain();
        switch(move){
            case STRUGGLE -> {
                int result=struggle.tick(p,elapsed);
                if(result!=0){
                    if(!struggle.hair())hand.cancel();
                    if(result==2){boss.goddess.finishedAttack();groups=0;enter(Move.BREATHER,p);}
                    else finish();
                }
            }
            case INVERTED_ACID, TENDRIL_ERUPTION -> {var active=move;if(crown.tick(level,p,active,elapsed) && move==active)finish();}
            case SONIC_CRY, SPIKE_FIELD, INFECTION_WAVE -> {var active=move;if(terrain.tick(level,p,active,elapsed) && move==active)finish();}
            case SEEKING_ORBS, TENDRIL_CRUSH, PACK_POUNCE -> {var active=move;if(commands.tick(level,p,active,elapsed) && move==active)finish();}
            case VACUOLE -> bubble(level,p);
            case HAND -> {if(hand.tick(level,p,volley) && move==Move.HAND)finish();}
            case RUSH -> {if(rush.tick(level,p,elapsed))finish();}
            case CALL, STRONG_CALL -> {
                boolean strong=move==Move.STRONG_CALL;
                if(brood.orderBroken(level,sequence)){finish();return;}
                if(elapsed==8)brood.signal(level,p,strong);
                if(elapsed>=CALL_FIRST && elapsed<CALL_FIRST+(strong?6:4)*CALL_INTERVAL && (elapsed-CALL_FIRST)%CALL_INTERVAL==0){
                    if(brood.reinforce(level,p,strong,(elapsed-CALL_FIRST)/CALL_INTERVAL,sequence) && strong && !paid){
                        paid=true;send();boss.goddess.paid(4500);
                        if(move!=Move.STRONG_CALL)return;
                    }
                }
                if(elapsed==CALL_FIRST+CALL_INTERVAL && !brood.issued(sequence)){
                    enter(Move.NEEDLE_RAIN,p);
                    return;
                }
                if(brood.issued(sequence)){
                    if(elapsed==CALL_FIRST+CALL_INTERVAL+1)com.codex.blacklight.entity.GoddessEmplacement.deploy(boss,p,strong || (sequence&1)==0);
                    if(strong && elapsed==CALL_FIRST+3*CALL_INTERVAL+1)com.codex.blacklight.entity.GoddessEmplacement.deploy(boss,p,false);
                }
                if(elapsed>=(strong?STRONG_CALL_END:NORMAL_CALL_END)){
                    if(brood.issued(sequence))finish();
                    else enter(Move.NEEDLE_RAIN,p);
                }
            }
            case NEEDLE_RAIN, WALL_SPEARS, BLOOD_SURF -> {
                if (move == Move.NEEDLE_RAIN && elapsed >= 8 && elapsed <= 32 && (elapsed - 8) % 6 == 0) {
                    // Each row commits four ticks before release. Both the crown and the needles use this mark.
                    var crown = boss.hurtRegions(1).get(11);
                    if (!crown.boxes().isEmpty()) {
                        double range = crown.bounds().getCenter().distanceTo(p.getBoundingBox().getCenter());
                        center = lead(p, 4 + GoddessAim.flightTicks(range, GoddessVolley.NEEDLE_SPEED));
                        boss.getLookControl().setLookAt(center.x, center.y, center.z, 18, 12);
                        send();
                    }
                } else if (move != Move.NEEDLE_RAIN && (elapsed <= 10 || move == Move.BLOOD_SURF && elapsed >= 21 && elapsed <= 28)) {
                    center = move == Move.BLOOD_SURF ? lead(p, elapsed <= 10 ? 12 - elapsed + 10 : 38 - elapsed + 12)
                            : p.getBoundingBox().getCenter();
                    boss.getLookControl().setLookAt(center.x, center.y, center.z, 18, 12);
                    if(elapsed%3==0 || elapsed==10 || elapsed==28)send();
                }
                if(move==Move.NEEDLE_RAIN && elapsed>=12&&elapsed<=36&&(elapsed-12)%6==0)volley.rain(level,center,(elapsed-12)/6);
                if(move==Move.WALL_SPEARS && elapsed==28 && !volley.spears(level,center)){
                    enter(Move.NEEDLE_RAIN,p);
                    return;
                }
                if(move==Move.BLOOD_SURF && (elapsed==12||elapsed==38) && !volley.waves(level,center,elapsed==38)){
                    enter(Move.NEEDLE_RAIN,p);
                    return;
                }
                // A countered spear fan has nothing left to recall. Do not wait through the empty recall.
                if(move==Move.WALL_SPEARS && elapsed>=44 && elapsed%5==0
                        && level.getEntitiesOfClass(com.codex.blacklight.entity.EvolvedQuill.class,boss.getBoundingBox().inflate(84),
                            bolt->bolt.bloodSpear() && bolt.getOwner()==boss && bolt.getPersistentData().getInt("GoddessSequence")==sequence).isEmpty()){
                    finish();
                    return;
                }
                if(elapsed>=(move==Move.WALL_SPEARS?SPEARS_END:move==Move.BLOOD_SURF?SURF_END:RAIN_END))finish();
            }
            default -> { }
        }
    }
    public static boolean mayEvolve(ServerLevel level,com.codex.blacklight.entity.BlacklightInfectedHost host){return GoddessBrood.canEvolve(level,host);}
    public static void releaseBrood(net.minecraft.world.entity.Mob mob){GoddessBrood.release(mob);}
    int strainingSlot(){
        int wrist=boss.goddess.supportingWrist();
        return switch(move){
            case RUSH -> elapsed>=12 && elapsed<=26 || rush.grip()<0 && elapsed>=43 && elapsed<60 ? (rush.arm()==1?5:9) : -1;
            case HAND -> elapsed>=5 && elapsed<=34 ? wrist : -1;
            // A fully charged rock needs about 20 ticks to leave the hand and reach her face from the cave floor.
            case VACUOLE -> elapsed>=1 && elapsed<GAZE_INHALE_TICKS+28 ? 0 : -1;
            case SEEKING_ORBS -> elapsed>=15 && elapsed<34 ? ((boss.goddess.removed() & GoddessVitals.RIGHT_ARM)==0 ? 9 : wrist>=0 ? wrist : 11) : -1;
            case TENDRIL_ERUPTION -> elapsed>=53 && elapsed<76 ? 1 : -1;
            case NEEDLE_RAIN -> elapsed>=4 && elapsed<=35 ? 11 : -1;
            case BLOOD_SURF -> elapsed<=12 || elapsed>=22 && elapsed<=38 ? (wrist>=0?wrist:1) : -1;
            case CALL, STRONG_CALL, WALL_SPEARS -> elapsed>=12 && elapsed<=32 ? (wrist>=0?wrist:1) : -1;
            default -> -1;
        };
    }
    private void telegraphStrain(){
        int slot=strainingSlot();
        if(slot<0 || elapsed%4!=0 && !(move==Move.VACUOLE && elapsed==1))return;
        var region=boss.hurtRegions(0).get(slot);
        if(region.boxes().isEmpty())return;
        var joint=region.bounds().getCenter();
        var edge=region.boxes().get((elapsed/4)%region.boxes().size()).nearest(center);
        SpiderFoley.strands(boss,edge,joint.subtract(edge),4);
        if(move==Move.SEEKING_ORBS || move==Move.TENDRIL_ERUPTION){
            SpiderFoley.strands(boss,joint,edge.subtract(joint),7);
            return;
        }
        if(boss.level() instanceof ServerLevel server){
            var visible=edge.add(center.subtract(edge).normalize().scale(.2));
            // A player on the floor can be more than 32 blocks from the face of this 46-block boss.
            for(var observer:server.players())if(observer.distanceToSqr(boss)<=112*112)
                server.sendParticles(observer,net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,true,
                        visible.x,visible.y,visible.z,6,.36,.32,.36,.018);
        }
    }
    private Move fallback(){
        Move[] volleys={Move.WALL_SPEARS,Move.BLOOD_SURF,Move.NEEDLE_RAIN,Move.VACUOLE};
        int index=Math.floorMod(order,volleys.length);
        return volleys[index]!=lastAttack?volleys[index]:volleys[(index+1)%volleys.length];
    }
    private void finish(){
        lastAttack=move;
        boss.goddess.finishedAttack();
        lastVolley = sequence;
        groups=Math.min(4,groups+(move==Move.STRONG_CALL && paid ? 2 : 1));
        enter(Move.REST,null);
    }
    private Vec3 eyes(){return GoddessCastPose.face(boss,1);}
    private boolean clear(Vec3 from,Vec3 to){
        return boss.level().clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,boss)).getType()!=HitResult.Type.BLOCK;
    }
    public void moveInsideBubble(net.minecraft.world.entity.player.Player player){
        double time=age(0)-28;
        double strike=ease(time/4)*(1-ease((time-4)/10));
        double close=ease((time-5)/18);
        var data=boss.level().isClientSide?client():null;
        var initial=data==null?entry:new Vec3(data.getDouble("EntryX"),data.getDouble("EntryY"),data.getDouble("EntryZ"));
        var direction=data==null?impulse:new Vec3(data.getDouble("PushX"),0,data.getDouble("PushZ"));
        var origin=center();
        boolean ground=grounded();
        var feet=origin.add(initial.multiply(1-ease(time/15),0,1-ease(time/15)))
                .add(direction.scale((ground?.34:.65)*strike-.14*close));
        double y=ground?origin.y+initial.y:origin.y+initial.y+.18*strike-.13*close;
        var push=new Vec3(feet.x,y,feet.z).subtract(player.position());
        if(push.lengthSqr()>.45*.45)push=push.normalize().scale(.45);
        player.move(net.minecraft.world.entity.MoverType.SELF,push);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance=0;
    }
    private void bubble(ServerLevel level,ServerPlayer p){
        int phase=elapsed-GAZE_INHALE_TICKS;
        if(phase<=20){
            if(!clear(eyes(),p.getEyePosition())){abandonMove();return;}
            center=p.getBoundingBox().getCenter();
            boss.getLookControl().setLookAt(p,18,12);
            if(phase%3==0 || phase==20)send();
            if(phase==20){
                SpiderFoley.play(boss,eyes(),SpiderFoley.Cue.STRAIN,3.8F);
                SpiderFoley.play(boss,center,SpiderFoley.Cue.LOAD,1.15F);
            }
        } else if(phase==28){
            Vec3 nearest=new Vec3(Math.max(p.getBoundingBox().minX,Math.min(center.x,p.getBoundingBox().maxX)),
                    Math.max(p.getBoundingBox().minY,Math.min(center.y,p.getBoundingBox().maxY)),
                    Math.max(p.getBoundingBox().minZ,Math.min(center.z,p.getBoundingBox().maxZ)));
            if(nearest.distanceToSqr(center)<=9 && clear(center,p.getBoundingBox().getCenter()) && clear(eyes(),center)){
                caught=true;
                var bounds=p.getBoundingBox();
                var soles=new AABB(bounds.minX+.025,bounds.minY-.065,bounds.minZ+.025,bounds.maxX-.025,bounds.minY+.002,bounds.maxZ-.025);
                grounded=p.getDeltaMovement().y<.09 && level.getBlockCollisions(p,soles).iterator().hasNext();
                captureYaw=p.yBodyRot;
                entry=p.position().subtract(center);
                var travel=p.getDeltaMovement().multiply(1,0,1);
                var lateral=new Vec3(Math.cos(Math.toRadians(captureYaw)),0,Math.sin(Math.toRadians(captureYaw)));
                impulse=travel.lengthSqr()>.01?travel.normalize():lateral;
                if(grounded)drawGround(level,p);
                SpiderFoley.play(boss,center,SpiderFoley.Cue.CATCH,1.3F);
            }
            send();
        }
        if(caught && phase>=28 && phase<52){
            moveInsideBubble(p);
            GoddessHeldMovement.commit(p,boss);
            if(grounded)pullDebris(level);
        }
        if(phase==34 && caught)sound(level,center,com.codex.blacklight.registry.ModInfectedSounds.Pressure.STRAIN,.75F);
        if(phase==52 && caught && !paid){
            GoddessHeldMovement.release(p,boss);
            paid=true;
            float before=p.getHealth();
            p.hurt(boss.damageSources().mobAttack(boss),GoddessImpact.VACUOLE.incoming(p));
            p.fallDistance=0;
            bled=p.getHealth()<before;
            sound(level,center,com.codex.blacklight.registry.ModInfectedSounds.Pressure.BURST,1.6F);
            if(grounded)burstDust(level);
            send();
        }
        if(phase==55 && caught && paid){
            var wall=reflector(level);
            if(wall!=null)sound(level,wall,com.codex.blacklight.registry.ModInfectedSounds.Pressure.ECHO,1.0F);
        }
        if(phase>=(caught?62:34))finish();
    }
    private void drawGround(ServerLevel level,ServerPlayer p){
        if(!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)
                || !net.neoforged.neoforge.event.EventHooks.canEntityGrief(level,boss))return;
        for(int i=0;i<4;i++){
            double angle=Math.toRadians(i*90+boss.yBodyRot);
            Vec3 edge=p.position().add(Math.cos(angle)*2.35,0,Math.sin(angle)*2.35);
            var hit=level.clip(new ClipContext(edge.add(0,1.25,0),edge.add(0,-1.75,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,boss));
            if(hit.getType()!=HitResult.Type.BLOCK)continue;
            BlockPos at=hit.getBlockPos();
            if(!BlockThrowSystem.selectable(p,at)|| !net.neoforged.neoforge.common.CommonHooks.canEntityDestroy(level,at,boss))continue;
            var state=level.getBlockState(at);
            var fragment=FallingBlockEntity.fall(level,at,state);
            Vec3 inward=center.subtract(fragment.position()).multiply(1,0,1).normalize();
            fragment.setDeltaMovement(inward.scale(.13).add(0,.36,0));
            fragment.hurtMarked=true;debris.add(fragment.getUUID());
            SpiderFoley.play(boss,fragment.position(),SpiderFoley.Cue.SCRAPE,.7F);
        }
    }
    private void sound(ServerLevel level,Vec3 point,com.codex.blacklight.registry.ModInfectedSounds.Pressure cue,float gain){
        if(!boss.isSilent())level.playSound(null,point.x,point.y,point.z,com.codex.blacklight.registry.ModInfectedSounds.pressure(cue),
                net.minecraft.sounds.SoundSource.HOSTILE,gain,1F);
    }
    private Vec3 reflector(ServerLevel level){
        Vec3 best=null,near=null;
        for(var d:new Vec3[]{new Vec3(1,.12,0),new Vec3(-1,.12,0),new Vec3(0,.15,1),new Vec3(0,.15,-1),new Vec3(0,1,0),new Vec3(0,-1,0)}){
            var hit=level.clip(new ClipContext(center,center.add(d.scale(72)),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,boss));
            if(hit.getType()!=HitResult.Type.BLOCK)continue;
            Vec3 at=hit.getLocation();
            if(center.distanceToSqr(at)<9){near=at;continue;}
            if(best==null||center.distanceToSqr(at)<center.distanceToSqr(best))best=at;
        }
        return best==null?near:best;
    }
    private void burstDust(ServerLevel level){
        for(int i=0;i<12;i++){
            double angle=(i+.25)*Math.PI/6;
            var outward=new Vec3(Math.cos(angle),0,Math.sin(angle));
            var start=center.add(outward.scale(.6+.17*(i%3)));
            var hit=level.clip(new ClipContext(start.add(0,.4,0),start.add(0,-1.8,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,boss));
            if(hit.getType()!=HitResult.Type.BLOCK)continue;
            var surface=level.getBlockState(hit.getBlockPos());
            var chips=new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,surface);
            var at=hit.getLocation();
            level.sendParticles(chips,at.x,at.y+.045,at.z,0,outward.x,.33+.09*(i%3),outward.z,.19);
        }
    }
    private void pullDebris(ServerLevel level){
        for(var id:debris){
            if(!(level.getEntity(id) instanceof FallingBlockEntity block) || block.isRemoved())continue;
            var radial=center.subtract(block.position()).multiply(1,0,1);
            double distance=radial.length();
            if(distance<.01)continue;
            // Pieces pile up outside the victim's boots and drop under ordinary block physics.
            double speed=Math.max(0,Math.min(.165,(distance-.85)*.16));
            var desired=radial.scale(speed/distance);
            var v=block.getDeltaMovement();
            block.setDeltaMovement(v.x*.55+desired.x*.45,v.y,v.z*.55+desired.z*.45);
            block.hurtMarked=true;
        }
    }
    public CompoundTag save(){
        var t=new CompoundTag();t.put("Crown",crown.save());t.put("Commands",commands.save());t.put("Terrain",terrain.save());t.put("Hand",hand.save());t.put("Rush",rush.save());t.put("Struggle",struggle.save());t.putString("Move",move.name());t.putString("LastAttack",lastAttack.name());t.putInt("Age",elapsed);if(move==Move.VACUOLE)t.putBoolean("GazeInhale",true);t.putLong("Start",boss.level().getGameTime()-elapsed);
        brood.saveCall(t);t.putInt("Sequence",sequence);t.putInt("Groups",groups);t.putInt("LastVolley",lastVolley);t.putInt("Order",order);t.put("RecoveryMeals",brood.saveMeals());
        t.putBoolean("Caught",caught);t.putBoolean("Ground",grounded);t.putBoolean("Paid",paid);t.putBoolean("Bled",bled);
        t.putDouble("X",center.x);t.putDouble("Y",center.y);t.putDouble("Z",center.z);
        t.putFloat("CaptureYaw",captureYaw);
        t.putDouble("EntryX",entry.x);t.putDouble("EntryY",entry.y);t.putDouble("EntryZ",entry.z);
        t.putDouble("PushX",impulse.x);t.putDouble("PushZ",impulse.z);
        for(int i=0;i<debris.size();i++)t.putUUID("Stone"+i,debris.get(i));
        if(flightHome!=null){t.putDouble("HomeX",flightHome.x);t.putDouble("HomeY",flightHome.y);t.putDouble("HomeZ",flightHome.z);}
        if(target!=null)t.putUUID("Target",target);
        return t;
    }
    private static Move decode(CompoundTag t){try{return Move.valueOf(t.getString("Move"));}catch(IllegalArgumentException e){return Move.REST;}}
    private static Vec3 readCenter(CompoundTag t){return new Vec3(t.getDouble("X"),t.getDouble("Y"),t.getDouble("Z"));}
    public void load(CompoundTag t){
        flightHome=t.contains("HomeX")?new Vec3(t.getDouble("HomeX"),t.getDouble("HomeY"),t.getDouble("HomeZ")):null;
        crown.load(t.getCompound("Crown"));commands.load(t.getCompound("Commands"));terrain.load(t.getCompound("Terrain"));brood.loadCall(t);struggle.load(t.getCompound("Struggle"));hand.load(t.getCompound("Hand"));rush.load(t.getCompound("Rush"));captureYaw=t.getFloat("CaptureYaw");
        entry=new Vec3(t.getDouble("EntryX"),t.contains("EntryY")?t.getDouble("EntryY"):-.9,t.getDouble("EntryZ"));
        impulse=new Vec3(t.getDouble("PushX"),0,t.getDouble("PushZ"));
        debris.clear();for(int i=0;i<4;i++)if(t.hasUUID("Stone"+i))debris.add(t.getUUID("Stone"+i));
        try{lastAttack=Move.valueOf(t.getString("LastAttack"));}catch(IllegalArgumentException ignored){lastAttack=Move.REST;}
        move=decode(t);elapsed=Math.max(0,t.getInt("Age"));
        if(move==Move.VACUOLE && elapsed>0 && !t.getBoolean("GazeInhale"))elapsed+=GAZE_INHALE_TICKS;
        sequence=t.getInt("Sequence");lastVolley=t.contains("LastVolley")?t.getInt("LastVolley"):-1;groups=t.getInt("Groups");order=t.getInt("Order");brood.loadMeals(t.getList("RecoveryMeals",10));
        caught=t.getBoolean("Caught");grounded=t.getBoolean("Ground");paid=t.getBoolean("Paid");bled=t.getBoolean("Bled");center=readCenter(t);target=t.hasUUID("Target")?t.getUUID("Target"):null;
        if(move==Move.STRUGGLE){move=Move.REST;target=null;elapsed=0;sequence++;hand.cancel();}
    }
}
