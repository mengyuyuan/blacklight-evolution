package com.codex.blacklight.military.aircraft;

import net.minecraft.world.phys.Vec3;

/** P1 TryOpenStrafeRun1020f9b0 and SimpleOpenStrafeRun102147c0/10214a00.
 * This is an independent opportunity corridor, not the cached tight-run path.
 */
final class HelicopterOpenStrafe {
    private static final double EPSILON_SQUARED=1.0e-10F,ANGLE=.26F;
    private HelicopterOpenStrafe(){}
    @FunctionalInterface interface Area {float height(Vec3 center,float width);}
    record Route(Vec3 start,Vec3 aim,Vec3 end){}

    /** Length fields count navigation cells. The MC adapter supplies one block
     * per cell and loaded terrain only; an unavailable sample rejects the plan. */
    static Route find(Vec3 craft,Vec3 target,boolean hydra,float cellSize,Area terrain){
        if(!HelicopterFlight.finite(craft)||!HelicopterFlight.finite(target)||terrain==null||!Float.isFinite(cellSize)||cellSize<=0)return null;
        float targetArea=terrain.height(target,20);
        if(!Float.isFinite(targetArea))return null;
        Vec3 aim=new Vec3(target.x,Math.max(target.y+10,targetArea),target.z);
        double maximum=aim.y+(hydra?27:17),minimum=aim.y+(hydra?15:5);
        double startY=craft.y;
        // MaxHeightToIgnoreStartLength=200. Authored MinLengthBeforeTarget=0,
        // so the rare correction changes only Y, not the initial horizontal point.
        if(Math.abs(startY-maximum)>200)startY=startY>maximum?maximum:minimum;
        Vec3 start=new Vec3(craft.x,startY,craft.z);
        float startArea=terrain.height(start,20);
        if(!Float.isFinite(startArea))return null;
        startY=Math.max(startY,startArea);
        if(startY>maximum)return null;
        start=new Vec3(start.x,Math.max(startY,minimum),start.z);
        Vec3 toward=target.subtract(start).multiply(1,0,1);
        double distance=toward.length();
        if(!Double.isFinite(distance)||distance*distance<EPSILON_SQUARED)return null; // MC degenerate-volume boundary.
        Vec3 step=toward.scale(cellSize/distance);
        double targetCells=Math.ceil(distance/cellSize);
        Vec3 sample=start;
        for(int i=0;i<40;i++){
            sample=sample.add(step);
            float pathArea=terrain.height(sample,20);
            if(!Float.isFinite(pathArea)||pathArea-target.y>30)return null;
            // Source indices are tested after advancing one cell: these are
            // strict comparisons, not >= or a world-distance20 threshold.
            if(i<=20||i<=targetCells+(hydra?6:4))continue;
            float endArea=terrain.height(sample,20);
            if(!Float.isFinite(endArea))return null;
            if(endArea-target.y>=30)continue;
            return new Route(start,aim,new Vec3(sample.x,Math.max(aim.y+15,endArea),sample.z));
        }
        return null;
    }

    static boolean aligned(Vec3 forward,Vec3 toward){
        if(!HelicopterFlight.finite(forward)||!HelicopterFlight.finite(toward))return false;
        double square=toward.x*toward.x+toward.z*toward.z;
        return Double.isFinite(square)&&square>=EPSILON_SQUARED
                &&(forward.x*toward.x+forward.z*toward.z)/Math.sqrt(square)>Math.cos(ANGLE);
    }

    static final class Run {
        enum Stage { PREPARE, RUN, ORIENT, DONE, ABORTED }
        private final Route route;
        private final Vec3 approach,reservationStart;
        private Stage stage;
        Run(Route route,Vec3 craft,Vec3 forward){
            this.route=route;
            if(route==null||!HelicopterFlight.finite(route.start())||!HelicopterFlight.finite(route.aim())||!HelicopterFlight.finite(route.end())
                    ||!HelicopterFlight.finite(craft)||!HelicopterFlight.finite(forward)){
                approach=reservationStart=Vec3.ZERO;stage=Stage.ABORTED;return;
            }
            boolean needsApproach=craft.distanceToSqr(route.start())>9; // ToleranceToStart3, inclusive.
            approach=reservationStart=needsApproach?route.start():craft;
            stage=needsApproach||!aligned(forward,route.aim().subtract(craft))?Stage.PREPARE:Stage.RUN;
        }
        Stage stage(){return stage;}
        Route route(){return route;}
        Vec3 reservationStart(){return reservationStart;}
        Vec3 destination(){return stage==Stage.PREPARE?approach:stage==Stage.RUN?route.end():null;}
        Vec3 facing(Vec3 currentTarget){return stage==Stage.ORIENT?currentTarget:route.aim();}
        void tick(Vec3 craft,Vec3 forward,Vec3 currentTarget,boolean movementFinished){
            if(stage==Stage.DONE||stage==Stage.ABORTED)return;
            if(!HelicopterFlight.finite(craft)||!HelicopterFlight.finite(forward)||!HelicopterFlight.finite(currentTarget)){stage=Stage.ABORTED;return;}
            switch(stage){
                case PREPARE -> {if(movementFinished&&aligned(forward,route.aim().subtract(craft)))stage=Stage.RUN;}
                case RUN -> {if(movementFinished)stage=Stage.ORIENT;} // Authored OrientAtEnd=true.
                case ORIENT -> {if(aligned(forward,currentTarget.subtract(craft)))stage=Stage.DONE;}
                default -> {}
            }
        }
    }
}
