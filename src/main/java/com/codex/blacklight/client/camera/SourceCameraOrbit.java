package com.codex.blacklight.client.camera;

import com.codex.blacklight.movement.WallPoseState;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Spherical chase state. Source handlers: ground 1033ac30, jump 1033b6b0,
 * climb 1033c4b0, wall 1033cd40, unstable 1033dc50, grapple 1033ddf0,
 * target 1033fed0. Entity bounds and wall contacts come from Minecraft.
 * Mouse deltas retain MC sensitivity; the source's stick acceleration is not
 * applied a second time to already scaled mouse input.
 */
final class SourceCameraOrbit {
    record Pose(float yaw,float pitch) {}
    private double yaw,pitch,inputYaw,inputPitch,age,launchY,smallTurnAge,lastTurn;
    private String mode="";
    private boolean manual;
    private Vec3 ideal;

    void reset(float yaw,float pitch) {
        this.yaw=yaw;this.pitch=pitch;inputYaw=inputPitch=age=smallTurnAge=lastTurn=0;
        mode="";manual=false;ideal=null;
    }
    void input(double dx,double dy) {
        if(Double.isFinite(dx)&&Double.isFinite(dy)){inputYaw+=dx*.15;inputPitch+=dy*.15;}
    }
    boolean manual(){return manual||Math.abs(inputYaw)+Math.abs(inputPitch)>1e-8;}
    float controlYaw(){return (float)Mth.wrapDegrees(yaw+inputYaw);}
    float controlPitch(){return (float)Mth.clamp(pitch+inputPitch,-80,80);}
    void takeControl(float yaw,float pitch) {
        this.yaw=yaw;this.pitch=Mth.clamp(pitch,-80,80);manual=true;
    }
    void position(Vec3 position){ideal=position;}
    Pose update(LocalPlayer player,String state,Vec3 anchor,double distance,double elevation,
                net.minecraft.world.entity.Entity target,double dt) {
        String next=state.startsWith("Ground")?"Ground":state.startsWith("Jump")?"Jump":
                state.startsWith("WallRun")?"Wall":state;
        if(!next.equals(mode)) {
            // Terrain and hurt-state transitions are frequent in Minecraft. Keep
            // the user's orbit across them instead of repeatedly granting automatic
            // follow control. Locking an enemy does not revoke mouse ownership.
            mode=next;age=smallTurnAge=lastTurn=0;launchY=player.getY();
        }
        age+=dt;
        boolean input=Math.abs(inputYaw)+Math.abs(inputPitch)>1e-8;
        // Native PC timeout is 1e9 seconds (10343540). Retain manual ownership
        // across MC traversal/reaction boundaries as well, avoiding re-entry snaps.
        if(input)manual=true;
        Vec3 velocity=player.getDeltaMovement().scale(20);
        double speed=velocity.horizontalDistance();
        double wantedYaw=yaw,wantedPitch=pitch;
        if(!manual&&ideal!=null) {
            Vec3 toward=anchor.subtract(ideal);
            if(toward.lengthSqr()>.01) {
                wantedYaw=yaw(toward);wantedPitch=pitch(toward);
            }
        }
        switch(mode) {
            case "Ground" -> {
                if(!manual) {
                    // A fast backwards step can cross the previous camera point.
                    // Never turn that geometric crossing into a 180-degree snap.
                    double delta=Mth.wrapDegrees(wantedYaw-yaw);
                    if(Math.abs(delta)<90)yaw=SourceCameraMath.angle(yaw,wantedYaw,120,dt,12);
                    if(speed>=1)pitch=SourceCameraMath.approach(pitch,elevation,60,dt,2);
                }
            }
            case "Jump" -> {
                if(!manual) {
                    boolean gliding=com.codex.blacklight.registry.ModAttachments.getData(player, com.codex.blacklight.registry.ModAttachments.GLIDE_UNTIL)>player.level().getGameTime()&&!player.onGround();
                    double weight=Mth.clamp(age/1.5,0,1)*Mth.clamp((speed-1.5)/(8-1.5),0,1);
                    if(gliding)weight=1;
                    if(age>.05&&speed>1.5)wantedYaw=yaw(velocity);
                    double delta=Mth.wrapDegrees(wantedYaw-yaw);
                    if(Math.abs(delta)<35) {
                        if(delta*lastTurn<0)smallTurnAge=0;
                        smallTurnAge+=dt;weight*=Mth.clamp(smallTurnAge/.6,0,1);
                    }else smallTurnAge=0;
                    lastTurn=delta;
                    boolean vertical=state.equals("Jump_Vertical");
                    yaw=SourceCameraMath.angle(yaw,wantedYaw,(gliding?75:vertical?150:45)*weight,dt,vertical?25:35);
                    // Source fall-height range: 15 -> 60 degrees over 50 units.
                    double maximum=Mth.lerp(Mth.clamp((launchY-player.getY())/50,0,1),15,60);
                    wantedPitch=Mth.clamp(vertical?elevation:wantedPitch,-60,maximum);
                    pitch=SourceCameraMath.approach(pitch,wantedPitch,gliding?60:vertical?150:75,dt,35);
                }
            }
            case "Climb", "Wall" -> {
                var surface=WallPoseState.get(player);
                if(surface!=null) {
                    Vec3 normal=new Vec3(surface.normalX(),0,surface.normalZ());
                    // Camera's outward ray stays on the exposed side of the wall.
                    double wallYaw=yaw(normal.scale(-1));
                    if(!manual) {
                        wantedYaw=wallYaw+Mth.clamp(Mth.wrapDegrees(wantedYaw-wallYaw),-60,60);
                        yaw=SourceCameraMath.angle(yaw,wantedYaw,mode.equals("Climb")?60:120,dt,5);
                        wantedPitch=Mth.clamp(wantedPitch,-75,75);
                        pitch=SourceCameraMath.approach(pitch,wantedPitch,mode.equals("Climb")?60:120,dt,5);
                    }
                    // Manual observation keeps the same sensitivity at wall corners.
                    // Camera collision handles safety without rotating the user's view.
                }
            }
            case "Target" -> {
                if(target!=null) {
                    // Consume mouse input once here, before target framing. Previously
                    // this branch discarded it, making a valid lock impossible to orbit.
                    if(input){
                        yaw=Mth.wrapDegrees(yaw+inputYaw);
                        pitch=Mth.clamp(pitch+inputPitch,-80,80);
                        wantedYaw=yaw;wantedPitch=pitch;
                    }
                    Vec3 center=target instanceof LivingEntity living&&com.codex.blacklight.combat.CombatTargeting.multipart(living)
                            ?com.codex.blacklight.client.ClientTargetLock.focusPoint(target)
                            :target.position().add(0,target.getBbHeight()*.5,0);
                    Vec3 delta=center.subtract(anchor);
                    if(delta.horizontalDistanceSqr()>.01) {
                        // Constrain the orbit to the actor/target viewing cone,
                        // not to the target's health or a new focus candidate.
                        double targetYaw=yaw(delta);
                        double halfFov=SourceCameraViewport.horizontalHalfFov();
                        double separation=delta.horizontalDistance();
                        double slack=Math.toDegrees(Math.asin(Mth.clamp(distance*Math.sin(Math.toRadians(halfFov))/separation,-1,1)))+halfFov;
                        // Automatic framing stays within the source cone. A mouse-
                        // chosen orbit remains manual until leaving this mode; do not
                        // immediately pull it back to the side after the next frame.
                        if(!manual){
                            wantedYaw=targetYaw+Mth.clamp(Mth.wrapDegrees(wantedYaw-targetYaw),-slack,slack);
                            yaw=SourceCameraMath.angle(yaw,wantedYaw,300,dt,10);
                        }
                    }
                    if(!manual)pitch=SourceCameraMath.approach(pitch,Mth.clamp(wantedPitch,-60,80),300,dt,10);
                    // Input is consumed only here; target cycling has its own action.
                    inputYaw=inputPitch=0;
                }
                // If a target disappears between tick and render, fall through to
                // ordinary mouse application instead of holding a dead camera lock.
            }
            case "Grapple" -> {
                if(!manual) {
                    yaw=SourceCameraMath.angle(yaw,wantedYaw,20,dt,10);
                    pitch=SourceCameraMath.approach(pitch,wantedPitch,20,dt,10);
                }
            }
            case "Unstable" -> { /* Source retains the orbit during a reaction. */ }
            default -> { }
        }
        yaw=Mth.wrapDegrees(yaw+inputYaw);pitch+=inputPitch;
        // One observation range across landing, wall exit and lock transitions.
        pitch=Mth.clamp(pitch,-80,80);
        inputYaw=inputPitch=0;
        return new Pose((float)yaw,(float)pitch);
    }
    static float yaw(Vec3 direction){return (float)Math.toDegrees(Math.atan2(-direction.x,direction.z));}
    static float pitch(Vec3 direction){return (float)-Math.toDegrees(Math.atan2(direction.y,direction.horizontalDistance()));}
}
