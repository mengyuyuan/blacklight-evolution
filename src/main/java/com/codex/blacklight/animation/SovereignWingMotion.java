package com.codex.blacklight.animation;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Articulation in model space: +Y up, +Z behind the actor. No entity/camera motion. */
public final class SovereignWingMotion {
    public record Angles(float x, float y, float z) {}
    public record Wing(Angles root, float strokeDegrees, Angles elbow, Angles wrist, Angles[] fingers) {}
    public record Pose(Wing left, Wing right) {
        public Angles bone(String name) {
            Wing w=name.startsWith("left")?left:right;
            if(name.endsWith("_root"))return w.root;
            if(name.endsWith("_elbow"))return w.elbow;
            if(name.endsWith("_wrist"))return w.wrist;
            if(name.contains("_finger_"))return w.fingers[name.charAt(name.length()-1)-'1'];
            return ZERO;
        }
    }
    private static final Angles ZERO=new Angles(0,0,0);
    /** Sweep back and quiet the stroke progressively. Fixed mount bones are not touched. */
    public static Pose streamline(Pose pose,float amount){
        amount=clamp(amount,0,1);
        return new Pose(streamline(pose.left,1,amount),streamline(pose.right,-1,amount));
    }
    private static Wing streamline(Wing w,float side,float amount){
        return new Wing(new Angles(w.root.x,w.root.y-side*18*amount,w.root.z),
                w.strokeDegrees*(1-.6F*amount)-side*10*amount,w.elbow,w.wrist,w.fingers);
    }
    private static Angles mix(Angles a,Angles b,float t){return new Angles(a.x+(b.x-a.x)*t,a.y+(b.y-a.y)*t,a.z+(b.z-a.z)*t);}
    private static Wing mix(Wing a,Wing b,float t){
        var fingers=new Angles[4];for(int i=0;i<4;i++)fingers[i]=mix(a.fingers[i],b.fingers[i],t);
        return new Wing(mix(a.root,b.root,t),a.strokeDegrees+(b.strokeDegrees-a.strokeDegrees)*t,mix(a.elbow,b.elbow,t),mix(a.wrist,b.wrist,t),fingers);
    }
    public static Pose interpolate(Pose a,Pose b,float t){return new Pose(mix(a.left,b.left,t),mix(a.right,b.right,t));}
    public static Vector3f strokeAxis(Quaternionf chestRotation) {
        return new Quaternionf(chestRotation).normalize().conjugate().transform(new Vector3f(0,0,1));
    }
    public static Quaternionf rootRotation(Wing wing,Vector3f axis) {
        var a=wing.root;
        return new Quaternionf().rotationAxis((float)Math.toRadians(wing.strokeDegrees),axis)
                .rotateY((float)Math.toRadians(a.y)).rotateZ((float)Math.toRadians(a.z))
                .rotateX((float)Math.toRadians(a.x));
    }
    /** Reference rig: proximal arch and distal blade each have a real bend joint. */
    public static Quaternionf articulation(String name,Pose pose,Vector3f axis,float air,Quaternionf folded) {
        Wing w=name.startsWith("left")?pose.left:pose.right;
        Quaternionf flight;
        String part=name.contains("_")?name.split("_")[1]:"";
        if(part.equals("root"))flight=rootRotation(w,axis);
        else {
            Angles a=ZERO;
            if(part.equals("rib")||part.equals("arch")) {
                float scale=part.equals("rib")?.35F:.65F;
                a=new Angles(w.elbow.x*scale,w.elbow.y*scale,w.elbow.z*scale);
            } else if(part.equals("finger")||part.equals("tip")) {
                int i=name.charAt(name.length()-1)-'1';var f=w.fingers[i];
                a=new Angles((w.wrist.x+f.x)*.5F,(w.wrist.y+f.y)*.5F,(w.wrist.z+f.z)*.5F);
            }
            flight=new Quaternionf().rotationY((float)Math.toRadians(a.y)).rotateZ((float)Math.toRadians(a.z)).rotateX((float)Math.toRadians(a.x));
        }
        float delay=switch(part){case "arch"->.08F;case "finger"->.04F;case "tip"->.14F;default->0;};
        float amount=smooth((1-clamp(air,0,1)-delay)/(1-delay));
        return flight.slerp(folded,amount).normalize();
    }
    private static float clamp(float v,float lo,float hi){return Float.isFinite(v)?com.codex.blacklight.forge.WingMath.clamp(v,lo,hi):0;}
    private static float smooth(double x){float v=(float)com.codex.blacklight.forge.WingMath.clamp(x,0,1);return v*v*(3-2*v);}
    private static double cycle(double phase){return phase-Math.floor(phase);}
    /** Sampled authored bat stroke; no synthetic sine-wave flapping. */
    public static float stroke(double phase) {
        return BatWingAnimation.stroke(phase);
    }
    /** Same cycle length: a loaded 36% downstroke, then a relaxed 64% recovery. */
    public static double loadedPhase(double phase,float force){
        double p=cycle(phase);
        double loaded=p<.36?.5*smooth(p/.36):.5+.5*smooth((p-.36)/.64);
        return p+(loaded-p)*clamp(force,0,1);
    }
    public static Pose sample(double phase,float deployment,float air,float speed,float forward,float lateral,float vertical,float turn,float brake) {
        return sample(phase,deployment,air,speed,forward,lateral,vertical,turn,brake,0);
    }
    public static Pose sample(double phase,float deployment,float air,float speed,float forward,float lateral,float vertical,float turn,float brake,float thrust) {
        if(!Double.isFinite(phase))phase=0;
        deployment=clamp(deployment,0,1);air=clamp(air,0,1);speed=clamp(speed,0,1);
        forward=clamp(forward,-1,1);lateral=clamp(lateral,-1,1);vertical=clamp(vertical,-1,1);
        turn=clamp(turn,-1,1);brake=clamp(brake,0,1);
        // Positive steering means the model's +X side leads; the opposite wing pushes harder.
        float steering=clamp(lateral*.65F+turn*.6F,-1,1);
        thrust=clamp(thrust,0,1);
        return new Pose(wing(1,phase,deployment,air,speed,forward,vertical,steering,brake,thrust),
                wing(-1,phase,deployment,air,speed,forward,vertical,steering,brake,thrust));
    }
    private static Wing wing(float side,double phase,float deploy,float air,float speed,float forward,float vertical,float steering,float brake,float thrust) {
        phase=loadedPhase(phase,thrust);
        float beat=stroke(phase);
        float amplitude=Math.min(43,28+Math.max(0,vertical)*7-speed*5-Math.max(0,-vertical)*6+thrust*14)*(1-side*steering*.18F);
        // Retarget the source arm's secondary swing into the long wing's aft sweep.
        float sourceSweep=(BatWingAnimation.angle("Arm1.L",2,phase)+18.11F)*.28F;
        float sweep=12+Math.max(0,forward)*12+sourceSweep+side*steering*5-brake*7+thrust*7;
        float fold=70+(sweep-70)*air*deploy;
        // The rest fan opens outboard of the head rather than converging over the neck.
        // Decreasing mirrored Z rotates the outboard spars DOWN, not up. Forward flight
        // pitches the stroke aft; backward/braking reverses its plane to push forward.
        var root=new Angles(air*(-forward*(10+thrust*6)+vertical*8+brake*22),-side*fold,
                side*air*(side*steering*3));
        // Forearm flexion draws the wrist inward. The hand folds in the opposite
        // direction, instead of swinging disconnected fingers about an elbow.
        float flex=(BatWingAnimation.angle("Arm4.L",0,phase)+29)*.32F;
        // Let the forearm lead the return stroke and the long blades follow it.
        // A permanent 70-degree wrist opening flattened this downward-pointing
        // concept into a horizontal paddle, regardless of the bat joint curves.
        var elbow=new Angles(air*(-forward*2),-side*air*(flex*.7F+brake*5),side*air*(-8+flex*.5F));
        var wrist=new Angles(air*BatWingAnimation.angle("Arm2.L",0,phase)*.55F,
                -side*air*(8+flex*1.2F+brake*8),side*(45*(1-air)+air*(54+8*(1-beat)+BatWingAnimation.angle("Arm4.L",2,phase)*.45F)));
        var fingers=new Angles[4];
        for(int i=0;i<4;i++) {
            double delayed=phase-.025-i*(.012+thrust*.008);
            String source=i==0?"Arm2.L":i==1?"Arm3.L":"Arm4.L";
            fingers[i]=new Angles(air*(BatWingAnimation.angle(source,0,delayed)*.2F-forward*2),
                    side*air*(BatWingAnimation.angle(source,1,delayed)*.35F-brake*3),
                    side*air*BatWingAnimation.angle(source,2,delayed)*.22F);
        }
        // The long, up-curved concept spars already rise above the shoulder.
        // Retarget their neutral elevation downward, preserving the source stroke
        // range instead of lifting the proximal arch across the character's head.
        return new Wing(root,side*air*(-6+amplitude*(beat-1)),elbow,wrist,fingers);
    }
    /** Integrates frequency instead of multiplying absolute time by a changing speed. */
    public static final class Controller {
        private double lastTime=Double.NaN,phase;
        private float speed,forward,lateral,vertical,turn,brake,thrust;
        public Pose update(double time,float deployment,float air,float targetSpeed,float targetForward,
                           float targetLateral,float targetVertical,float targetTurn,float targetBrake) {
            return update(time,deployment,air,targetSpeed,targetForward,targetLateral,targetVertical,targetTurn,targetBrake,0);
        }
        public Pose update(double time,float deployment,float air,float targetSpeed,float targetForward,
                           float targetLateral,float targetVertical,float targetTurn,float targetBrake,float targetThrust) {
            if(!Double.isFinite(time))time=Double.isNaN(lastTime)?0:lastTime;
            // Entity clocks can restart on a world change; keep the pose, rebase time.
            if(time<lastTime-.5)lastTime=time;
            double dt=Double.isNaN(lastTime)?0:com.codex.blacklight.forge.WingMath.clamp(time-lastTime,0,.1);
            if(Double.isNaN(lastTime)||time>lastTime)lastTime=time;
            float a=1-(float)Math.exp(-dt*5);
            speed+=(clamp(targetSpeed,0,1)-speed)*a;
            forward+=(clamp(targetForward,-1,1)-forward)*a;
            lateral+=(clamp(targetLateral,-1,1)-lateral)*a;
            vertical+=(clamp(targetVertical,-1,1)-vertical)*a;
            turn+=(clamp(targetTurn,-1,1)-turn)*a;
            brake+=(clamp(targetBrake,0,1)-brake)*a;
            thrust+=(clamp(targetThrust,0,1)-thrust)*a;
            // Force changes the stroke envelope, not the flapping frequency.
            if(air>.001)phase=cycle(phase+dt*(.82+Math.max(0,vertical)*.08));
            return sample(phase,deployment,air,speed,forward,lateral,vertical,turn,brake,thrust);
        }
    }
    private SovereignWingMotion(){}
}
