package com.codex.blacklight.military.aircraft;

import net.minecraft.world.phys.Vec3;

/** Source PrepareTightStrafeRun/TightStrafeRun action geometry and transitions.
 * HelicopterTactics owns their live bank, committed corridor and lifecycle;
 * native curved steering is adapted to swept Minecraft intermediate waypoints.
 * Movement completion is supplied by the adapter, not guessed from elapsed time.
 */
final class HelicopterStrafeManeuver {
    private HelicopterStrafeManeuver(){}
    private static final double PROJECTION_EPSILON=1.0e-10F;
    private static final float FACING_DOT=.97F,MAX_PATH_DISTANCE_SQUARED=40F;

    record Profile(float targetHeight,float heightTolerance){
        static Profile original(boolean hydra){return hydra?new Profile(25,15):new Profile(16,10);}
    }
    /** intermediate is formed before the extra 12-unit rise at start. */
    record Route(Vec3 start,Vec3 end,Vec3 aim,Vec3 intermediate,Vec3 targetAtPlan,Profile profile){
        boolean accepts(Vec3 target){
            if(!HelicopterFlight.finite(target))return false;
            // 1020e6d0/1020e1c0 compare a difference of squared distances,
            // not target displacement from its cached position.
            double delta=Math.abs(distanceSquared(start,end,target)-distanceSquared(start,end,targetAtPlan));
            return delta<=100&&Math.abs(target.y-(aim.y-profile.targetHeight()))<=profile.heightTolerance();
        }
    }

    static Route prepareRoute(HelicopterStrafePath.Path candidate,Vec3 targetAtPlan,float targetGround,boolean hydra){
        return route(candidate,targetAtPlan,targetGround,Float.NaN,hydra,false);
    }
    /** Authored source branches all have UseTargetIntermediate=false.
     * endAreaHeight is the source Distance=30 square terrain maximum, not an
     * arbitrary extra flight height. The caller must provide loaded terrain.
     */
    static Route tightRoute(HelicopterStrafePath.Path committed,Vec3 targetAtPlan,float targetGround,float endAreaHeight,boolean hydra){
        return route(committed,targetAtPlan,targetGround,endAreaHeight,hydra,true);
    }
    private static Route route(HelicopterStrafePath.Path path,Vec3 targetAtPlan,float ground,float endArea,boolean hydra,boolean tight){
        if(path==null||!HelicopterFlight.finite(path.start())||!HelicopterFlight.finite(path.end())
                ||!HelicopterFlight.finite(path.targetGround())||!HelicopterFlight.finite(targetAtPlan)
                ||!Float.isFinite(path.relativeRise())||!Float.isFinite(ground)||(tight&&!Float.isFinite(endArea)))return null;
        var profile=Profile.original(hydra);
        // Preserve the native comparison with the relative-rise output. Do not
        // silently replace it with ground+rise (1021424c and1021382a).
        double floor=Math.max(ground+profile.targetHeight(),path.relativeRise());
        Vec3 start=raised(path.start(),floor),end=raised(path.end(),tight?Math.max(floor,endArea):floor);
        Vec3 direction=end.subtract(start);
        double lengthSquared=direction.lengthSqr();
        if(!Double.isFinite(lengthSquared)||lengthSquared<=PROJECTION_EPSILON)return null; // MC malformed-input boundary.
        Vec3 aim=raised(path.targetGround(),floor);
        // 107badf0 normalizes at the source squared epsilon. Vec3.normalize()
        // would silently zero valid short directions below its 1e-4 length cutoff.
        Vec3 intermediate=start.add(direction.scale(15/Math.sqrt(lengthSquared)));
        if(tight)intermediate=new Vec3(intermediate.x,aim.y,intermediate.z);
        return new Route(start.add(0,12,0),end,aim,intermediate,targetAtPlan,profile);
    }
    private static Vec3 raised(Vec3 point,double floor){return new Vec3(point.x,Math.max(point.y,floor),point.z);}

    /** 103bbbe0 is intentionally asymmetric: before start and inside the
     * segment it measures XZ only; at/beyond end it includes vertical distance.
     * Replacing it with a conventional 3D segment distance changes abort gates.
     */
    static double distanceSquared(Vec3 start,Vec3 end,Vec3 point){
        double dx=end.x-start.x,dz=end.z-start.z,px=point.x-start.x,pz=point.z-start.z;
        double projection=px*dx+pz*dz;
        if(projection<=PROJECTION_EPSILON)return px*px+pz*pz;
        double lengthSquared=dx*dx+dz*dz;
        if(projection>=lengthSquared)return point.distanceToSqr(end);
        return px*px+pz*pz-projection*projection/lengthSquared;
    }
    /** 103bab40: horizontal forward dot must be strictly greater than .97. */
    static boolean aligned(Vec3 forward,Vec3 toAim){
        if(!HelicopterFlight.finite(forward)||!HelicopterFlight.finite(toAim))return false;
        double a=forward.horizontalDistanceSqr(),b=toAim.horizontalDistanceSqr();
        return a>=PROJECTION_EPSILON&&b>=PROJECTION_EPSILON
                &&(forward.x*toAim.x+forward.z*toAim.z)/Math.sqrt(a*b)>FACING_DOT;
    }
    /** Native movement status query0x26/4 returns unfinished only for2 or3.
     * A Minecraft adapter still needs to distinguish a blocked route from an
     * arrival; mapping every stopped aircraft to success would be unsafe.
     */
    static boolean sourceMovementFinished(int status){return status!=2&&status!=3;}
    static float speed(double sample){
        if(!Double.isFinite(sample)||sample<0||sample>=1)throw new IllegalArgumentException("sample outside [0,1)");
        return .8F+(1F-.8F)*(float)sample;
    }

    static final class Prepare {
        enum Stage { APPROACH, ALIGN, WAIT, READY, ABORTED }
        private final Route route;
        private final float speed;
        private Stage stage;
        private float elapsed;
        Prepare(Route route,Vec3 target,double sample){
            this.route=route;speed=HelicopterStrafeManeuver.speed(sample);
            stage=route!=null&&route.accepts(target)?Stage.APPROACH:Stage.ABORTED;
        }
        Route route(){return route;}
        Stage stage(){return stage;}
        float speed(){return speed;}
        float elapsed(){return elapsed;}
        void abort(){if(stage!=Stage.READY)stage=Stage.ABORTED;}
        /** Native10214460: validate on approach completion, on stopped+aligned,
         * then every wait tick. Entering WAIT does not consume that tick's dt.
         */
        void tick(float seconds,Vec3 target,boolean movementFinished,boolean facingAligned){
            if(stage==Stage.READY||stage==Stage.ABORTED)return;
            if(!Float.isFinite(seconds)||seconds<0||!HelicopterFlight.finite(target)){abort();return;}
            switch(stage){
                case APPROACH -> {if(movementFinished)stage=route.accepts(target)?Stage.ALIGN:Stage.ABORTED;}
                case ALIGN -> {
                    if(facingAligned&&movementFinished){stage=route.accepts(target)?Stage.WAIT:Stage.ABORTED;elapsed=0;}
                }
                case WAIT -> {
                    elapsed+=seconds;
                    if(!route.accepts(target))stage=Stage.ABORTED;
                    else if(elapsed>.5F)stage=Stage.READY;
                }
                default -> {}
            }
        }
    }

    static final class Tight {
        enum Stage { OUTBOUND, TURN, RETURN, DONE, ABORTED }
        private final Route route;
        private final boolean goBack;
        private final float speed;
        private Stage stage;
        /** Native begin10213620 starts directly in outbound state3 after the
         * preceding prepare action; it does not repeat prepare or its wait.
         */
        Tight(Route committedRoute,Vec3 target,double sample,boolean goBack){
            route=committedRoute;this.goBack=goBack;speed=HelicopterStrafeManeuver.speed(sample);
            stage=route!=null&&route.accepts(target)?Stage.OUTBOUND:Stage.ABORTED;
        }
        Route route(){return route;}
        Stage stage(){return stage;}
        float speed(){return speed;}
        boolean performingRun(){return stage==Stage.OUTBOUND||stage==Stage.RETURN;}
        Vec3 destination(){return stage==Stage.OUTBOUND?route.end():stage==Stage.RETURN?route.start():null;}
        void abort(){if(stage!=Stage.DONE)stage=Stage.ABORTED;}
        void tick(Vec3 position,Vec3 target,boolean movementFinished,boolean facingAligned){
            if(stage==Stage.DONE||stage==Stage.ABORTED)return;
            if(!HelicopterFlight.finite(position)||!HelicopterFlight.finite(target)){abort();return;}
            switch(stage){
                case OUTBOUND -> {
                    if(movementFinished)stage=goBack&&route.accepts(target)?Stage.TURN:Stage.DONE;
                    // Native10213ef8 compares the squared helper result to40,
                    // not40*40; the return leg has no matching per-tick gate.
                    else if(distanceSquared(route.start(),route.end(),position)>MAX_PATH_DISTANCE_SQUARED)stage=Stage.ABORTED;
                }
                case TURN -> {
                    // Unlike prepare, native turnaround only requires facing;
                    // it does not call the movement completion query again.
                    if(facingAligned)stage=route.accepts(target)?Stage.RETURN:Stage.ABORTED;
                }
                case RETURN -> {if(movementFinished)stage=Stage.DONE;}
                default -> {}
            }
        }
    }
}
