package com.codex.blacklight.client.camera;

import net.minecraft.client.Camera;

import com.codex.blacklight.client.ClientWallMotion;
import com.codex.blacklight.client.PrototypeAnimationController;
import com.codex.blacklight.combat.ConsumeVisual;
import com.codex.blacklight.mixin.client.PrototypeCameraAccess;
import com.codex.blacklight.mode.PrototypeModeSystem;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import com.codex.blacklight.client.ClientTargetLock;

/** Source follow primitives with a Minecraft state/collision bridge.
 * Observation is shared with movement; shake and active films remain visual only.
 */
public final class PrototypeChaseCamera {
    private static void setRotation(Camera camera,float yaw,float pitch,float roll){
        ((PrototypeCameraAccess)camera).blacklight$setRotation(yaw,pitch);
        if(roll==0)return;
        camera.rotation().rotateZ(roll*net.minecraft.util.Mth.DEG_TO_RAD);
        camera.getLookVector().set(0,0,1).rotate(camera.rotation());
        camera.getUpVector().set(0,1,0).rotate(camera.rotation());
        camera.getLeftVector().set(1,0,0).rotate(camera.rotation());
    }

    private static final SourceCameraMath.FollowFilter follow=new SourceCameraMath.FollowFilter();
    private static final SourceActionCamera action=new SourceActionCamera();
    private static final SourceCameraOrbit orbit=new SourceCameraOrbit();
    private static final SourceTargetFramer framer=new SourceTargetFramer();
    private static final SourceCameraFilm film=new SourceCameraFilm();
    private static SourceCameraFilm.Frame filmFrame;
    private static LocalPlayer previousPlayer;
    private static Object previousLevel;
    private static Vec3 previousEye;
    private static long previousNanos;
    private static double distance,elevation,lookYaw,lookPitch;
    private static boolean applied;
    private static double zoom=1;
    private static float collisionDistance;
    private static boolean wasOwned,inputSeeded,suspendedManual,externalSeeded;
    private static Vec3 lastOffset,handoffOffset,anchorOffset=Vec3.ZERO;
    private static float lastYaw,lastPitch;
    private static double handoffAge,handoffYawOffset,handoffPitchOffset;
    private static boolean handoffAngles,handoffPending;
    public static boolean applied(){return applied;}
    public static double zoom(){return zoom;}
    static SourceCameraFilm.Frame filmFrame(){return ownsView()?filmFrame:null;}
    public static float nearPlane(){var f=filmFrame();return f==null?.05F:(float)Mth.lerp(f.weight(),.05,f.near());}
    public static float farPlane(float ordinary){var f=filmFrame();return f==null?ordinary:(float)Mth.lerp(f.weight(),ordinary,Mth.clamp(f.far(),16,ordinary));}
    public static float controlYaw() {
        var mc=Minecraft.getInstance();
        if(CameraCompatibility.externalOwnsView())return CameraCompatibility.externalYaw(mc.gameRenderer.getMainCamera().getYRot());
        if(previousPlayer!=mc.player||!(wasOwned||inputSeeded))return mc.gameRenderer.getMainCamera().getYRot();
        return !orbit.manual()&&lastOffset!=null&&filmFrame==null?lastYaw:orbit.controlYaw();
    }
    /** Flight uses the same unshaken orbit pitch as mouse aiming, even between mouse events. */
    public static float controlPitch() {
        var mc=Minecraft.getInstance();
        if(CameraCompatibility.externalOwnsView())return CameraCompatibility.externalPitch(mc.gameRenderer.getMainCamera().getXRot());
        if(previousPlayer!=mc.player||!(wasOwned||inputSeeded))return mc.gameRenderer.getMainCamera().getXRot();
        return !orbit.manual()&&lastOffset!=null&&filmFrame==null?lastPitch:orbit.controlPitch();
    }
    public static boolean mouse(double x,double y) {
        if(CameraCompatibility.externalOwnsView()){reset();return false;}
        if(!ownsView()) {
            // Seed the external owner BEFORE its first mouse delta, not after it.
            var mc=Minecraft.getInstance();
            if(wasOwned&&previousPlayer==mc.player&&mc.player!=null&&mc.player.isAlive()
                    &&mc.options.getCameraType()==CameraType.THIRD_PERSON_BACK
                    &&(mc.player.isUsingItem()||mc.player.isScoping())
                    &&Double.isFinite(x)&&Double.isFinite(y)) {
                if(!externalSeeded)releaseControl();
                lastYaw=Mth.wrapDegrees(lastYaw+(float)(x*.15));
                lastPitch=Mth.clamp(lastPitch+(float)(y*.15),-80,80);
            }
            return false;
        }
        if(!Double.isFinite(x)||!Double.isFinite(y)||Math.abs(x)+Math.abs(y)<1e-8)return true;
        var mc=Minecraft.getInstance();
        // Seed immediately so an attack/tick between mouse input and render uses it.
        if(previousPlayer!=mc.player||previousLevel!=mc.level){reset();previousPlayer=mc.player;previousLevel=mc.level;}
        if(!wasOwned&&!inputSeeded){
            var camera=mc.gameRenderer.getMainCamera();orbit.reset(camera.getYRot(),camera.getXRot());inputSeeded=true;
        }
        if(!orbit.manual()||filmFrame!=null||handoffOffset!=null&&handoffAngles) {
            orbit.takeControl(lastOffset!=null?lastYaw:orbit.controlYaw(),lastOffset!=null?lastPitch:orbit.controlPitch());
            lookYaw=lookPitch=0;framer.reset();
            if(filmFrame!=null){beginHandoff(false);SourceCameraViewport.handoff();}
            handoffAngles=false;
        }
        film.cancelForInput(mc.player);filmFrame=null;
        orbit.input(x,y);
        // Vanilla sends view pitch for vertical attack aim; body facing and the
        // free orbit retain separate owners while actions are playing.
        if(mc.player!=null)mc.player.setXRot(orbit.controlPitch());
        return true;
    }
    public static void impact(com.codex.blacklight.network.PhysicalFeedbackPayload payload) {
        var mc=Minecraft.getInstance();
        if(ownsView())action.impact(payload,mc.player,mc.gameRenderer.getMainCamera().getPosition());
    }
    public static boolean ownsView() {
        var mc=Minecraft.getInstance();var p=mc.player;
        return !HydraCinematicCamera.active()&&!CameraCompatibility.externalOwnsView()&&p!=null&&!com.codex.blacklight.power.BiomassSystem.active(p)&&mc.level!=null&&mc.getCameraEntity()==p&&PrototypeModeSystem.isActive(p)
                &&p.isAlive()&&!p.isSpectator()&&!p.isSleeping()&&!p.isUsingItem()
                &&mc.options.getCameraType()==CameraType.THIRD_PERSON_BACK&&!mc.player.isScoping()
                &&ChaseCameraProfile.get("Ground")!=null&&SourceCameraEvents.available();
    }
    private PrototypeChaseCamera() {}

    private static void reset() {
        previousPlayer=null;previousLevel=null;previousEye=null;previousNanos=0;
        applied=false;lookYaw=lookPitch=0;zoom=1;action.reset();framer.reset();film.reset();filmFrame=null;collisionDistance=0;
        wasOwned=inputSeeded=suspendedManual=externalSeeded=false;lastOffset=handoffOffset=null;anchorOffset=Vec3.ZERO;
        SourceCameraViewport.reset();
    }

    private static void beginHandoff(boolean angles) {
        handoffOffset=lastOffset;handoffAge=0;handoffAngles=angles;handoffPending=true;
    }
    private static void releaseControl() {
        var p=Minecraft.getInstance().player;
        p.setYRot(lastYaw);p.yRotO=lastYaw;p.setXRot(lastPitch);p.xRotO=lastPitch;
        externalSeeded=true;
    }
    private static void finishFrame(net.minecraft.client.Camera camera,LocalPlayer player,Vec3 eye,double dt) {
        var access=(PrototypeCameraAccess)camera;
        if(handoffOffset!=null) {
            if(handoffPending){
                handoffYawOffset=Mth.wrapDegrees(lastYaw-camera.getYRot());
                handoffPitchOffset=lastPitch-camera.getXRot();handoffPending=false;
            }
            double t=Mth.clamp(handoffAge/.18,0,1),weight=1-t*t*(3-2*t);
            access.blacklight$setPosition(clearPosition(player,eye,
                    camera.getPosition().lerp(eye.add(handoffOffset),weight)));
            if(handoffAngles)setRotation(camera,camera.getYRot()+(float)(handoffYawOffset*weight),
                    (float)Mth.clamp(camera.getXRot()+handoffPitchOffset*weight,-80,80),0);
            handoffAge+=dt;
            if(t>=1)handoffOffset=null;
        }
        lastOffset=camera.getPosition().subtract(eye);lastYaw=camera.getYRot();lastPitch=camera.getXRot();
    }

    private static String select(LocalPlayer player) {
        var recovery=com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.CONSUME_RECOVERY);
        if(com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.GRAB_STAGE)!=ConsumeVisual.NONE
                ||recovery.active()&&recovery.phaseProgress(player.level().getGameTime(),0)<1
                &&com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.WEAPON_VISUAL).spec()==null)return "Grapple";
        if(com.codex.blacklight.client.ClientTargetLock.hasValidLock())return "Target";
        if(player.hurtTime>0)return "Unstable";
        var wall=ClientWallMotion.modeFor(player);
        switch(wall) {
            case CLING,CLIMB: return "Climb";
            case RUN: return "WallRun";
            case VAULT: return "WallRun_Parkour";
            case MANTLE,TOP: return "Jump_Roof";
            case PARKOUR: return "Ground_Parkour";
            case LEAP: return "Jump_Leap";
            default: break;
        }
        if(player.isInWater()||player.isInLava()||player.isPassenger())return "Ground";
        // The locomotion phase already debounces the one-frame air gaps on steps.
        return switch(PrototypeAnimationController.phaseFor(player)) {
            case JUMP_LAUNCH,RISE,FALL -> player.getDeltaMovement().horizontalDistance()>.15 ? "Jump_Leap":"Jump_Vertical";
            case RUN -> "Ground_Running";
            default -> "Ground";
        };
    }

    public static void apply(net.minecraft.client.Camera camera,float partial) {
        var mc=Minecraft.getInstance();var player=mc.player;
        if(CameraCompatibility.externalOwnsView()){reset();return;}
        if(player==null||mc.level==null||camera!=mc.gameRenderer.getMainCamera()||camera.getEntity()!=player
                ||!PrototypeModeSystem.isActive(player)||!player.isAlive()||player.isSpectator()||player.isSleeping()
                ||mc.options.getCameraType()!=CameraType.THIRD_PERSON_BACK) {
            reset();return;
        }
        if(com.codex.blacklight.power.BiomassSystem.active(player)){reset();return;}
        String state=select(player);var profile=ChaseCameraProfile.get(state);
        if(profile==null){reset();return;}
        Vec3 eye=player.getEyePosition(partial);
        long now=System.nanoTime();
        double frameTime=previousNanos==0?0:Math.max(0,(now-previousNanos)*1e-9);
        double expected=player.getDeltaMovement().length()*20*Math.min(frameTime,1);
        double resetDistance=Math.max(8,expected*2+2);
        boolean discontinuity=player!=previousPlayer||mc.level!=previousLevel||previousEye==null||previousEye.distanceToSqr(eye)>resetDistance*resetDistance;
        double dt=mc.isPaused()?0:Math.min(.25,frameTime);
        if(discontinuity&&!(inputSeeded&&previousEye==null)) {
            reset();distance=profile.distance();elevation=profile.elevation();
            orbit.reset(camera.getYRot(),camera.getXRot());
            collisionDistance=(float)(distance*player.getScale());
            follow.reset(eye,profile.filterWidth());previousPlayer=player;previousLevel=mc.level;dt=0;
        }
        previousNanos=now;previousEye=eye;previousPlayer=player;previousLevel=mc.level;
        if(!ownsView()) {
            if(wasOwned) {
                // Transfer visible facing to vanilla item use before returning control.
                suspendedManual=orbit.manual();
                if(!externalSeeded){
                    releaseControl();
                    setRotation(camera,lastYaw,lastPitch,0);
                }
                beginHandoff(true);SourceCameraViewport.handoff();
                action.reset();framer.reset();film.reset();filmFrame=null;zoom=1;
            }
            wasOwned=inputSeeded=applied=false;
            finishFrame(camera,player,eye,dt);return;
        }
        if(!wasOwned) {
            if(!inputSeeded)orbit.reset(camera.getYRot(),camera.getXRot());
            if(suspendedManual&&!inputSeeded)orbit.takeControl(camera.getYRot(),camera.getXRot());
            distance=profile.distance();elevation=profile.elevation();collisionDistance=(float)(distance*player.getScale());
            follow.reset(eye,profile.filterWidth());anchorOffset=Vec3.ZERO;
            // A mouse delta already seeded this frame must remain visible immediately.
            beginHandoff(!inputSeeded);SourceCameraViewport.handoff();
        }
        wasOwned=applied=true;inputSeeded=externalSeeded=false;
        var actionValues=action.update(player,partial,dt,camera.getPosition());zoom=actionValues.zoom();
        if(!com.codex.blacklight.client.ClientTargetLock.hasValidLock()&&!state.equals("Grapple")) {
            // Native hints are independent flags: AutoTarget can coexist with Jumping.
            // Slide and Float select the unstable handler (native mode 4).
            if(action.hint("Slide")||action.hint("Float"))state="Unstable";
            else if(action.hint("Climbing"))state="Climb";
            else if(action.hint("Jumping")||action.hint("Falling"))state="Jump_Leap";
            profile=ChaseCameraProfile.get(state);
        }
        // Preserve the source filter at low speed, with a Minecraft-space lag budget.
        double lagLimit=.45*player.getScale();
        double speed=player.getDeltaMovement().length()*20;
        double width=Math.min(profile.filterWidth(),2*lagLimit/Math.max(1,speed));
        Vec3 wantedOffset=follow.update(eye,width,profile.changeRate(),dt).subtract(eye);
        if(wantedOffset.length()>lagLimit)wantedOffset=wantedOffset.normalize().scale(lagLimit);
        anchorOffset=anchorOffset.lerp(wantedOffset,1-Math.exp(-dt/.08));
        if(anchorOffset.length()>lagLimit)anchorOffset=anchorOffset.normalize().scale(lagLimit);
        Vec3 anchor=eye.add(anchorOffset);
        var anchorHit=mc.level.clip(new ClipContext(eye,anchor,ClipContext.Block.VISUAL,ClipContext.Fluid.NONE,player));
        if(anchorHit.getType()!=HitResult.Type.MISS) {
            double safe=Math.max(0,eye.distanceTo(anchorHit.getLocation())-.05);
            anchorOffset=anchorOffset.lengthSqr()<1e-8?Vec3.ZERO:anchorOffset.normalize().scale(safe);
            anchor=eye.add(anchorOffset);
        }
        distance=SourceCameraMath.approach(distance,profile.distance(),profile.distanceRate(),dt,profile.slowingDistance());
        elevation=SourceCameraMath.approach(elevation,profile.elevation(),profile.elevationRate(),dt,profile.elevationSlow());
        boolean targeting=com.codex.blacklight.client.ClientTargetLock.hasValidLock();
        var target=targeting?ClientTargetLock.target():null;
        if(!targeting&&action.hint("AutoTarget")) {
            var accepted=player.level().getEntity(com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.WEAPON_VISUAL).targetId());
            if(accepted instanceof net.minecraft.world.entity.LivingEntity living&&living.distanceToSqr(player)<4096
                    &&player.hasLineOfSight(living))target=living;
        }
        if(target!=null&&(!target.isAlive()||target.level()!=player.level()))target=null;
        var pose=orbit.update(player,state,anchor,distance,elevation,target,dt);
        float yaw=pose.yaw(),pitch=pose.pitch();
        boolean manual=orbit.manual();
        // Source look-ahead uses normalized movement and camera axes (0x10342c0b).
        Vec3 velocity=player.getDeltaMovement().scale(20),direction=velocity.normalize();
        Float travelFacing=PrototypeAnimationController.movementFacing(player,partial);
        var weapon=com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.WEAPON_VISUAL);
        float bodyFacing=weapon.spec()!=null?weapon.yaw():travelFacing!=null?travelFacing:Mth.rotLerp(partial,player.yBodyRotO,player.yBodyRot);
        Vec3 facing=Vec3.directionFromRotation(0,bodyFacing);
        Vec3 right=Vec3.directionFromRotation(0,yaw+90);
        double side=facing.dot(right),up=direction.dot(Vec3.directionFromRotation(pitch-90,yaw));
        double yawTarget=velocity.length()>1&&facing.dot(direction)>0
                ?Math.copySign(profile.yaw()*fraction(Math.abs(side),profile.yawMin(),profile.yawMax()),side):0;
        double pitchTarget=up>0?profile.pitchUp()*fraction(up,profile.pitchUpMin(),profile.pitchUpMax())
                :-profile.pitchDown()*fraction(-up,profile.pitchDownMin(),profile.pitchDownMax());
        lookYaw=manual?0:SourceCameraMath.approach(lookYaw,targeting?0:yawTarget,profile.yaw()==0?30:profile.yawRate(),dt,profile.yaw()==0?2:profile.yawSlow());
        lookPitch=manual?0:SourceCameraMath.approach(lookPitch,targeting?0:pitchTarget,profile.pitchRate()==0?30:profile.pitchRate(),dt,profile.pitchRate()==0?2:profile.pitchSlow());
        var access=(PrototypeCameraAccess)camera;
        // The movement/input API sees the same base orbit as the rendered camera.
        // Do not feed action shake or cinematic offsets into attack direction.
        setRotation(camera,yaw,pitch,0);
        access.blacklight$setPosition(anchor);
        float desired=(float)(distance*player.getScale()/actionValues.dolly());
        orbit.position(anchor.subtract(Vec3.directionFromRotation(pitch,yaw).scale(desired)));
        float clear=(float)access.blacklight$getMaxZoom(desired);
        // Collision retracts immediately, including on fast turns; never smooth through walls.
        float safe=clear<desired?Math.max(0,clear-.05F):clear;
        // Retract immediately; ease out only into space confirmed clear this frame.
        collisionDistance=Math.min(safe,(float)SourceCameraMath.approach(collisionDistance,safe,10,dt,.5));
        access.blacklight$move(-collisionDistance,0,0);
        Vec3 desiredLook=Vec3.directionFromRotation(pitch-(float)lookPitch,yaw+(float)lookYaw);
        desiredLook=framer.aim(camera.getPosition(),desiredLook,player,manual?null:target,partial,dt);
        if(!manual&&actionValues.reAim()>0) {
            Vec3 offset=actorVector(actionValues.offset(),action.facing()).scale(player.getScale());
            Vec3 aim=player.getPosition(partial).add(offset).subtract(camera.getPosition()).normalize();
            if(aim.lengthSqr()>.0001)desiredLook=desiredLook.lerp(aim,actionValues.reAim()).normalize();
        }
        filmFrame=film.update(player,partial,dt);
        double roll=0;
        if(filmFrame!=null) {
            var f=filmFrame;Vec3 position=camera.getPosition().lerp(f.position(),f.weight());
            access.blacklight$setPosition(position);
            desiredLook=desiredLook.lerp(f.look(),f.weight()).normalize();
            Vec3 flatRight=desiredLook.cross(new Vec3(0,1,0)).normalize();
            Vec3 naturalUp=flatRight.cross(desiredLook).normalize();
            roll=Math.toDegrees(Math.atan2(f.up().dot(flatRight),f.up().dot(naturalUp)))*f.weight();
        }
        Vec3 shake=action.rotation();
        float finalYaw=(float)Math.toDegrees(Math.atan2(-desiredLook.x,desiredLook.z));
        float finalPitch=(float)-Math.toDegrees(Math.atan2(desiredLook.y,desiredLook.horizontalDistance()));
        setRotation(camera,finalYaw,finalPitch,(float)roll);
        access.blacklight$setPosition(clearPosition(player,eye,camera.getPosition()));
        finishFrame(camera,player,eye,dt);
        // Handoff and observation use the unshaken pose, never accumulated recoil.
        finalYaw=camera.getYRot();finalPitch=camera.getXRot();
        setRotation(camera,finalYaw+(float)shake.y,Mth.clamp(finalPitch+(float)shake.x,-89,89),(float)(shake.z+roll));
    }

    private static Vec3 clearPosition(LocalPlayer p,Vec3 origin,Vec3 wanted) {
        Vec3 delta=wanted.subtract(origin);double fraction=1;
        // Eight corners protect the near plane as well as the central camera ray.
        double radius=Math.max(.12,nearPlane());
        for(int x=-1;x<=1;x+=2)for(int y=-1;y<=1;y+=2)for(int z=-1;z<=1;z+=2) {
            Vec3 start=origin.add(x*radius,y*radius,z*radius);
            var hit=p.level().clip(new ClipContext(start,start.add(delta),ClipContext.Block.VISUAL,ClipContext.Fluid.NONE,p));
            if(hit.getType()!=HitResult.Type.MISS&&delta.lengthSqr()>.0001)
                fraction=Math.min(fraction,Math.max(0,(hit.getLocation().distanceTo(start)-.05)/delta.length()));
        }
        return origin.add(delta.scale(fraction));
    }

    static Vec3 actorVector(Vec3 source,float facing) {
        // Source uses X right, Y up, -Z forward. Minecraft yaw zero looks toward +Z.
        Vec3 forward=Vec3.directionFromRotation(0,facing),right=Vec3.directionFromRotation(0,facing+90);
        return right.scale(source.x).add(0,source.y,0).add(forward.scale(-source.z));
    }

    private static double fraction(double speed,double min,double max) {
        if(speed<=min)return 0;
        return max<=min?1:Mth.clamp((speed-min)/(max-min),0,1);
    }
}
