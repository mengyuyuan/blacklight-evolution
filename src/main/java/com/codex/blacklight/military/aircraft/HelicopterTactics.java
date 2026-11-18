package com.codex.blacklight.military.aircraft;

import com.codex.blacklight.entity.BlackwatchHelicopter;
import java.util.List;
import java.util.function.DoubleSupplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Authored engage_apache bank and tight/open-run lifecycles, through Minecraft flight.
 * Source branch clocks/geometry are distinct from the swept navigation adapter.
 * Native curved steering is adapted to swept waypoints, not reproduced exactly.
 */
final class HelicopterTactics {
    private final BlackwatchHelicopter craft;
    private final HelicopterCircleStrafe circle;
    private final HelicopterHellfireAttack hellfire;
    private final DoubleSupplier bankDraw;
    private HelicopterTacticBank.Tactic tactic;
    private HelicopterStrafeManeuver.Prepare prepare;
    private HelicopterStrafeManeuver.Tight tight;
    private HelicopterOpenStrafe.Run open;
    private Vec3 destination,terrainAt;
    private float terrain25=Float.NaN,terrain60=Float.NaN;
    private long terrainTick=Long.MIN_VALUE;
    private int age,retry,openRetry;
    private boolean intermediatePassed;

    HelicopterTactics(BlackwatchHelicopter craft,HelicopterCircleStrafe circle,HelicopterHellfireAttack hellfire){
        this(craft,circle,hellfire,()->craft.level().random.nextDouble());
    }
    HelicopterTactics(BlackwatchHelicopter craft,HelicopterCircleStrafe circle,HelicopterHellfireAttack hellfire,DoubleSupplier bankDraw){
        this.craft=craft;this.circle=circle;this.hellfire=hellfire;this.bankDraw=java.util.Objects.requireNonNull(bankDraw);
    }
    String name(){return open!=null?"OPEN_STRAFE":tactic==null?"NONE":tactic.name();}
    HelicopterGunEnvelope.Profile gunEnvelope(){
        // Actual Spawn BranchRefs, not names: circle74252/simple93312 ->127;
        // prepare75648/Blackwatch tight76988/open81552 ->151.
        if(open!=null||tactic==HelicopterTacticBank.Tactic.STRAFE)return HelicopterGunEnvelope.Profile.STRAFE_151;
        if(tactic==HelicopterTacticBank.Tactic.CIRCLE||tactic==HelicopterTacticBank.Tactic.SIMPLE)return HelicopterGunEnvelope.Profile.ORDINARY_127;
        return HelicopterGunEnvelope.Profile.NONE;
    }
    String stage(){return open!=null?"OPEN_"+open.stage().name():tight!=null?tight.stage().name():prepare!=null?prepare.stage().name():name();}
    Vec3 destination(){return destination;}
    void reset(){
        if(craft.level() instanceof ServerLevel level)HelicopterAirspace.forLevel(level).forget(craft);
        clear();terrainAt=null;terrainTick=Long.MIN_VALUE;terrain25=terrain60=Float.NaN;retry=0;
    }
    /** Local hover timing can resume; world-owned path reservations/leases must
     * be reacquired, never restored from an entity save. */
    void restore(CompoundTag saved){
        reset();
        if(hellfire.hovering()){tactic=HelicopterTacticBank.Tactic.HOVER;return;}
        if(saved.getInt("Version")!=1||!saved.contains("Age",3))return;
        int restoredAge=saved.getInt("Age");if(restoredAge<0||restoredAge>=200)return;
        String name=saved.getString("Tactic");
        if(name.equals("CIRCLE")&&circle.initialized())tactic=HelicopterTacticBank.Tactic.CIRCLE;
        else if(name.equals("SIMPLE"))tactic=HelicopterTacticBank.Tactic.SIMPLE;
        if(tactic!=null)age=restoredAge;
    }
    CompoundTag save(){
        var saved=new CompoundTag();saved.putInt("Version",1);saved.putString("Tactic",name());saved.putInt("Age",age);return saved;
    }
    private void clear(){
        tactic=null;prepare=null;tight=null;open=null;destination=null;age=0;openRetry=0;intermediatePassed=false;
        craft.navigation().reset();
    }
    private void finish(HelicopterAirspace space){space.releaseVolume(craft);clear();}
    private void terrain(ServerLevel level,HelicopterAirspace space,Vec3 target){
        long now=level.getGameTime();
        // Bounded MC heightmap adapter: cache a loaded-terrain sample for at most
        // one second, invalidating immediately after a one-block target move.
        if(terrainAt!=null&&terrainAt.distanceToSqr(target)<1&&now-terrainTick<20)return;
        terrainAt=target;terrainTick=now;terrain25=space.areaHeight(target,25);terrain60=space.areaHeight(target,60);
    }
    private boolean canStrafe(HelicopterAirspace space,Vec3 target){
        if(target.y>=300||!Float.isFinite(terrain25)||terrain25-target.y<=5)return false;
        var candidate=space.query(craft,target,.5F,2,5,40,false);
        // Source conditions run in this order; Request=true is a mutation on
        // each bank pass, even when the later weighted draw chooses hover.
        return candidate!=null&&candidate.valid()&&space.requestToken(craft);
    }
    private boolean select(ServerLevel level,HelicopterAirspace space,Vec3 target,boolean hydra,boolean hoverEligible){
        var selected=HelicopterTacticBank.choose(List.of(
                branch(HelicopterTacticBank.Tactic.CIRCLE,()->target.y<300&&Float.isFinite(terrain60)&&terrain60-target.y<22
                        &&HelicopterCircleStrafe.firingBandAvailable(target.y,terrain60,hydra)),
                branch(HelicopterTacticBank.Tactic.STRAFE,()->canStrafe(space,target)),
                branch(HelicopterTacticBank.Tactic.SIMPLE,()->true),
                branch(HelicopterTacticBank.Tactic.HOVER,()->hoverEligible&&hellfire.ready())),bankDraw);
        if(selected==null)return false;
        tactic=selected;age=0;craft.navigation().reset();
        switch(selected){
            case CIRCLE -> {circle.reset();circle.begin(level.random.nextDouble());}
            case HOVER -> {
                if(!hellfire.begin(20+level.random.nextInt(21),20+level.random.nextInt(21))){finish(space);return false;}
            }
            case STRAFE -> {
                var candidate=space.candidate(craft);
                var route=candidate!=null&&candidate.valid()?HelicopterStrafeManeuver.prepareRoute(candidate.path(),candidate.targetAtPlan(),space.groundAt(target),hydra):null;
                prepare=new HelicopterStrafeManeuver.Prepare(route,target,level.random.nextDouble());
                if(prepare.stage()==HelicopterStrafeManeuver.Prepare.Stage.ABORTED){finish(space);return false;}
                // Native prepare begin commits the candidate (message0x32/2).
                // Later refreshes of ReserveVolume must not replace this copy.
                space.commit(craft);space.reserveSource(craft,target,true,5,null);
            }
            case SIMPLE -> space.reserveSource(craft,target,false,10,null);
        }
        craft.beginStrafe();return true;
    }
    private static HelicopterTacticBank.Branch<HelicopterTacticBank.Tactic> branch(HelicopterTacticBank.Tactic tactic,java.util.function.BooleanSupplier condition){
        return new HelicopterTacticBank.Branch<>(tactic,tactic.weight,condition);
    }
    /** Returns permission for the chin-gun thread; final impact/crew checks stay
     * in HelicopterCombat.fire. Every path issues at most one movement per tick. */
    boolean tick(ServerLevel level,Vec3 target,Vec3 aim,boolean seen,boolean hydra,boolean targetHelicopter,boolean targetTank,boolean hoverEligible){
        var space=HelicopterAirspace.forLevel(level);
        if(!seen||!HelicopterFlight.finite(target)||!HelicopterFlight.finite(aim)){
            // MC observation policy: no new route or target update behind walls.
            finish(space);hellfire.abort();craft.combatVector(Vec3.ZERO,aim);return false;
        }
        terrain(level,space,target);
        if(tactic==HelicopterTacticBank.Tactic.HOVER&&!hellfire.hovering())finish(space);
        if((tactic==HelicopterTacticBank.Tactic.CIRCLE||tactic==HelicopterTacticBank.Tactic.SIMPLE&&open==null)&&age>=200)finish(space);
        if(tactic==HelicopterTacticBank.Tactic.CIRCLE&&Float.isFinite(terrain25)&&terrain25-target.y>=10)finish(space);
        if(tactic==HelicopterTacticBank.Tactic.CIRCLE&&!HelicopterCircleStrafe.firingBandAvailable(target.y,terrain60,hydra))finish(space);
        if(tactic==null){
            if(retry>0){retry--;craft.combatVector(Vec3.ZERO,aim);return false;}
            if(!select(level,space,target,hydra,hoverEligible)){retry=10;craft.combatVector(Vec3.ZERO,aim);return false;}
        }
        age++;
        return switch(tactic){
            case HOVER -> {craft.combatVector(Vec3.ZERO,aim);yield false;}
            case CIRCLE -> {
                var motion=circle.firingMotion(craft.position(),target,terrain60,hydra,forward());
                craft.combatVector(motion==null?Vec3.ZERO:motion.request(),aim);
                yield motion!=null;
            }
            case STRAFE -> strafe(level,space,target,aim,hydra);
            case SIMPLE -> simple(level,space,target,aim,hydra,targetHelicopter,targetTank);
        };
    }
    private Vec3 forward(){return HelicopterArmament.direction(craft.getYRot(),0);}
    private boolean strafe(ServerLevel level,HelicopterAirspace space,Vec3 target,Vec3 aim,boolean hydra){
        if(!space.refreshToken(craft)){
            finish(space);craft.combatVector(Vec3.ZERO,aim);return false;
        }
        space.reserveSource(craft,target,true,5,null);
        if(tight==null){
            var route=prepare.route();destination=route.start();
            // Flight must face the same committed point as the completion test.
            // A still-valid lateral target move otherwise stalls ALIGN forever.
            boolean arrived=craft.combatMove(destination,route.aim(),prepare.speed());
            if(craft.navigation().status()==HelicopterNavigation.Status.FAILED){finish(space);retry=10;return false;}
            prepare.tick(.05F,target,arrived,HelicopterStrafeManeuver.aligned(forward(),route.aim().subtract(craft.position())));
            if(prepare.stage()==HelicopterStrafeManeuver.Prepare.Stage.ABORTED){finish(space);return false;}
            if(prepare.stage()==HelicopterStrafeManeuver.Prepare.Stage.READY){
                var committed=space.committed(craft);
                var run=committed!=null&&committed.valid()?HelicopterStrafeManeuver.tightRoute(committed.path(),committed.targetAtPlan(),space.groundAt(target),space.areaHeight(committed.path().end(),30),hydra):null;
                tight=new HelicopterStrafeManeuver.Tight(run,target,level.random.nextDouble(),true);
                if(tight.stage()==HelicopterStrafeManeuver.Tight.Stage.ABORTED){finish(space);return false;}
                intermediatePassed=false;craft.navigation().reset();
            }
            return true;
        }
        var previous=tight.stage();boolean arrived=false;
        if(tight.performingRun()){
            var route=tight.route();var end=tight.destination();
            // MC navigation uses the authored intermediate as a swept waypoint.
            // This is not a claim to reproduce native curved-flight interpolation.
            if(!intermediatePassed&&craft.position().distanceToSqr(route.intermediate())<=1)intermediatePassed=true;
            destination=intermediatePassed?end:route.intermediate();
            boolean atWaypoint=craft.combatMove(destination,aim,tight.speed());
            arrived=intermediatePassed&&atWaypoint;
        }else{destination=craft.position();craft.combatVector(Vec3.ZERO,tight.route().aim());}
        if(craft.navigation().status()==HelicopterNavigation.Status.FAILED){finish(space);retry=10;return false;}
        tight.tick(craft.position(),target,arrived,HelicopterStrafeManeuver.aligned(forward(),tight.route().aim().subtract(craft.position())));
        if(tight.stage()!=previous){craft.navigation().reset();intermediatePassed=false;}
        if(tight.stage()==HelicopterStrafeManeuver.Tight.Stage.DONE||tight.stage()==HelicopterStrafeManeuver.Tight.Stage.ABORTED){finish(space);return false;}
        // The original blackwatchVariety!=0 branch uses the chin gun, including
        // preparation. The existing yaw/muzzle/LOS gates decide actual firing.
        return true;
    }
    private boolean simple(ServerLevel level,HelicopterAirspace space,Vec3 target,Vec3 aim,boolean hydra,boolean targetHelicopter,boolean targetTank){
        if(open!=null)return open(space,target,aim);
        if(openRetry>0)openRetry--;
        if(openRetry<=0&&target.y<300&&craft.position().subtract(target).horizontalDistance()<=300&&space.tokenAvailable(craft)){
            // ConditionalExecute71248 checks availability without taking a
            // token. Only TryOpen's successful IfFound branch requests it.
            openRetry=10; // Bounded MC terrain sampling; native tries every update.
            var candidate=HelicopterOpenStrafe.find(craft.position(),target,hydra,1,space::areaHeight);
            if(candidate!=null&&space.requestToken(craft)){
                var run=new HelicopterOpenStrafe.Run(candidate,craft.position(),forward());
                if(run.stage()!=HelicopterOpenStrafe.Run.Stage.ABORTED&&space.reserveOpen(craft,run)){
                    open=run;destination=null;intermediatePassed=false;craft.navigation().reset();
                    return open(space,target,aim);
                }
            }
        }
        boolean inPosition=inPosition(space,target,targetHelicopter,targetTank)&&space.pointAvailable(craft.position(),7,craft);
        if(inPosition){destination=null;craft.combatVector(Vec3.ZERO,aim);}
        else{
            double range=destination==null?0:destination.subtract(target).horizontalDistance();
            if(destination!=null&&(range<10||range>30||!space.pointAvailable(destination,9,craft))){destination=null;craft.navigation().reset();}
            if(destination==null&&retry<=0){
                // Authored close-range reaction89196/89308 uses firstValid,
                // unlike the target-class-specific initial lowestValid search.
                destination=craft.position().subtract(target).horizontalDistance()<5
                        ?HelicopterCombatDestination.reposition(craft,target,space)
                        :HelicopterCombatDestination.find(craft,target,hydra,targetHelicopter,space);
                retry=10;
            }
            if(retry>0)retry--;
            if(destination==null)craft.combatVector(Vec3.ZERO,aim);
            else{
                boolean arrived=craft.combatMove(destination,aim,1);
                if(arrived||craft.navigation().status()==HelicopterNavigation.Status.FAILED){destination=null;craft.navigation().reset();}
            }
        }
        space.reserveSource(craft,target,false,10,destination);
        return true;
    }
    private boolean open(HelicopterAirspace space,Vec3 target,Vec3 aim){
        if(!space.refreshToken(craft)){finish(space);craft.combatVector(Vec3.ZERO,aim);return false;}
        var previous=open.stage();boolean arrived=false;
        Vec3 facing=open.facing(target);
        if(previous==HelicopterOpenStrafe.Run.Stage.PREPARE){
            destination=open.destination();arrived=craft.combatMove(destination,facing,1);
        }else if(previous==HelicopterOpenStrafe.Run.Stage.RUN){
            // Native navigation receives the raised target as its curved-path
            // intermediate. MC uses it as a swept waypoint before the fixed end.
            if(!intermediatePassed&&craft.position().distanceToSqr(open.route().aim())<=1)intermediatePassed=true;
            destination=intermediatePassed?open.route().end():open.route().aim();
            boolean atWaypoint=craft.combatMove(destination,facing,1);arrived=intermediatePassed&&atWaypoint;
        }else{destination=craft.position();craft.combatVector(Vec3.ZERO,facing);}
        if(craft.navigation().status()==HelicopterNavigation.Status.FAILED){finish(space);retry=10;return false;}
        open.tick(craft.position(),forward(),target,arrived);
        if(open.stage()!=previous){craft.navigation().reset();intermediatePassed=false;}
        if(open.stage()==HelicopterOpenStrafe.Run.Stage.DONE||open.stage()==HelicopterOpenStrafe.Run.Stage.ABORTED){finish(space);return false;}
        // Blackwatch uses the nonzero-variety chin-gun branch. Existing gun
        // aiming, range, muzzle, cover and crew checks retain firing authority.
        return true;
    }
    private boolean inPosition(HelicopterAirspace space,Vec3 target,boolean helicopter,boolean tank){
        Vec3 delta=target.subtract(craft.position());double ground=space.groundAt(craft.position());
        double pitch=Math.toDegrees(Math.atan2(Math.abs(delta.y),delta.horizontalDistance()));
        return Double.isFinite(ground)&&delta.horizontalDistance()<= (helicopter?50:45)&&Math.abs(delta.y)<=30
                &&(tank||delta.y<0)&&pitch<=(helicopter?40:30)&&craft.getY()-ground>=8;
    }
}
