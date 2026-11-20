package com.codex.blacklight.client.renderer;

import com.codex.blacklight.animation.BlacklightSkeleton;
import com.codex.blacklight.entity.QuillOrgan;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.WeakHashMap;

/** Add the receiving shoulder over the current walk or attack without authoring a second leg track. */
public final class QuillReception {
    private static final WeakHashMap<AbstractClientPlayer,Envelope> PLAYERS=new WeakHashMap<>();
    private static final class Envelope {
        int at=Integer.MIN_VALUE;
        float left,right,oldLeft,oldRight;
        float hitLeft,hitRight,oldHitLeft,oldHitRight;
        int trackUntil=-1,frameAt=-1;
        Matrix4f chestWorld;
    }
    private QuillReception(){}
    private static Envelope sample(AbstractClientPlayer p){
        var e=PLAYERS.computeIfAbsent(p,k->new Envelope());
        if(e.at==p.tickCount)return e;
        e.oldLeft=e.left;e.oldRight=e.right;e.left*=.75F;e.right*=.75F;e.at=p.tickCount;
        e.oldHitLeft=e.hitLeft;e.oldHitRight=e.hitRight;e.hitLeft*=.57F;e.hitRight*=.57F;
        for(var q:p.level().getEntitiesOfClass(QuillOrgan.class,p.getBoundingBox().inflate(4),
                q->q.actor()==p.getId() && (q.phase()==QuillOrgan.RETURN || q.phase()==QuillOrgan.LIQUID
                    || q.phase()==QuillOrgan.ORBIT && q.age()<3))){
            float proximity=(float)Mth.clamp(1-q.position().distanceTo(p.position().add(0,p.getBbHeight()*.79,0))/4,0,1);
            float beat=q.phase()==QuillOrgan.LIQUID?.75F:q.phase()==QuillOrgan.ORBIT?.4F:1;
            if(q.arrivesRight())e.right=Math.max(e.right,proximity*beat);else e.left=Math.max(e.left,proximity*beat);
            if(q.phase()==QuillOrgan.LIQUID){
                double age=q.age();
                float impact=(float)(Math.exp(-Math.pow((age-3)/1.25,2))+.54*Math.exp(-Math.pow((age-7)/1.9,2)));
                if(q.arrivesRight())e.hitRight=Math.max(e.hitRight,impact);else e.hitLeft=Math.max(e.hitLeft,impact);
            }
        }
        return e;
    }
    public static float weight(AbstractClientPlayer p,float partial,boolean right){
        var e=sample(p);return right?Mth.lerp(partial,e.oldRight,e.right):Mth.lerp(partial,e.oldLeft,e.left);
    }
    public static void pose(AbstractClientPlayer p,float partial,Matrix4f[] matrices){
        float r=weight(p,partial,true),l=weight(p,partial,false);
        if(l+r<.01F)return;
        var sk=BlacklightSkeleton.get();
        bend(matrices,sk.chest,-.09F*Math.max(r,l),(r-l)*.15F,(l-r)*.07F);
        if(r>.01F)bend(matrices,sk.armR,-.12F*r,0,-.16F*r);
        if(l>.01F)bend(matrices,sk.armL,-.12F*l,0,.16F*l);
        var e=sample(p);
        float hr=Mth.lerp(partial,e.oldHitRight,e.hitRight),hl=Mth.lerp(partial,e.oldHitLeft,e.hitLeft);
        float beat=Math.max(hr,hl);
        if(beat>.008F){
            bend(matrices,sk.chest,.22F*beat,(hl-hr)*.31F,(hr-hl)*.16F);
            bend(matrices,sk.head,-.14F*beat,(hr-hl)*.16F,0);
            bend(matrices,sk.armR,-.33F*hr,.05F*hr,-.18F*hr);
            bend(matrices,sk.armL,-.33F*hl,-.05F*hl,.18F*hl);
        }
    }
    public static void captureBack(AbstractClientPlayer p,com.mojang.blaze3d.vertex.PoseStack stack,Matrix4f[] bones){
        var e=PLAYERS.get(p);
        if(e==null || e.trackUntil<p.tickCount || StandalonePlayerRendering.nativeHandPass())return;
        var eye=net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        e.chestWorld=new Matrix4f().translation((float)eye.x,(float)eye.y,(float)eye.z)
                .mul(stack.last().pose()).mul(com.codex.blacklight.animation.ScapulaMount.chest(bones));
        e.frameAt=p.tickCount;
    }
    /** Open shoulder, shoulder blade and upper spine follow the same posed chest as the rendered skin. */
    public static net.minecraft.world.phys.Vec3 backPoint(AbstractClientPlayer p,float partial,boolean right,double u){
        var e=PLAYERS.computeIfAbsent(p,k->new Envelope());
        e.trackUntil=p.tickCount+3;
        double side=right?1:-1,arc=Math.sin(Math.PI*u);
        var bind=new Vector3f((float)(side*(.35-.30*u)),(float)(1.52-.31*u+.055*arc),(float)(.18+.075*arc-.02*u));
        var feet=p.getPosition(partial);
        if(e.chestWorld!=null && p.tickCount-e.frameAt<=1){
            var v=e.chestWorld.transformPosition(new Vector3f(bind));
            var point=new net.minecraft.world.phys.Vec3(v.x,v.y,v.z);
            if(point.distanceToSqr(feet)<5)return point;
        }
        return feet.add(new net.minecraft.world.phys.Vec3(-bind.x,bind.y,-bind.z)
                .yRot((float)Math.toRadians(-Mth.rotLerp(partial,p.yBodyRotO,p.yBodyRot))));
    }
    private static void bend(Matrix4f[] pose,BlacklightSkeleton.Bone root,float pitch,float yaw,float roll){
        var p=pose[root.getId()].getTranslation(new Vector3f());
        var delta=new Matrix4f().translation(p).rotateY(yaw).rotateX(pitch).rotateZ(roll).translate(-p.x,-p.y,-p.z);
        branch(pose,root,delta);
    }
    private static void branch(Matrix4f[] pose,BlacklightSkeleton.Bone bone,Matrix4f delta){
        pose[bone.getId()]=new Matrix4f(delta).mul(pose[bone.getId()]);
        for(var child:bone.getSubJoints())branch(pose,child,delta);
    }
}
