package com.codex.blacklight.client.renderer;

import com.codex.blacklight.combat.ConsumeGiverEffect;
import com.codex.blacklight.combat.ConsumeSkinGrowth;
import com.codex.blacklight.combat.ConsumeVisual;
import com.codex.blacklight.registry.ModAttachments;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/** The short hand-side fibres use the actual vanilla hand pass. Target positions use the world camera's projection. */
public final class FirstPersonConsumeTendrils {
    record Scene(ConsumeVisual visual,ConsumeVisual residueSource,float envelope,float residue,double age,float residueAge) {}
    private FirstPersonConsumeTendrils(){}
    static Scene active(AbstractClientPlayer actor,float partial){
        var mc=Minecraft.getInstance();
        if(actor!=mc.player||mc.getCameraEntity()!=actor||!mc.options.getCameraType().isFirstPerson()
                ||mc.gameRenderer.getMainCamera().isDetached()||actor.isSpectator()||!actor.isAlive()||actor.isInvisible()||actor.isScoping())return null;
        var v=actor.getData(ModAttachments.CONSUME_BODY_VISUAL);
        var retained=actor.getData(ModAttachments.CONSUME_RESIDUE_VISUAL);
        long now=actor.level().getGameTime();
        return scene(actor.getId(),v,retained,now,partial);
    }
    static Scene scene(int owner,ConsumeVisual v,ConsumeVisual retained,long now,float partial){
        boolean active=v.consuming()&&v.consumerId()==owner;
        boolean settled=retained.consuming()&&retained.consumerId()==owner;
        if(!active&&!settled)return null;
        float age=active?v.elapsed(now,partial)/20F:0F;
        float retainedAge=settled?retained.elapsed(now,partial)/20F:0F;
        float envelope=active?ConsumeGiverEffect.exterior(v,age):0F;
        float mass=active?ConsumeGiverEffect.residue(v,age):0F;
        float retainedMass=settled?ConsumeGiverEffect.residue(retained,retainedAge):0F;
        boolean useRetained=retainedMass>mass;
        var residueSource=useRetained?retained:v;
        float residue=Math.max(mass,retainedMass);
        if(Math.max(envelope,residue)<=.004F)return null;
        float residueAge=ConsumeGiverEffect.residueAge(residueSource,useRetained?retainedAge:age);
        return new Scene(v,residueSource,envelope,residue,active?age-ConsumeGiverEffect.exteriorStart(v):0,residueAge);
    }
    private record Target(Vec3 eye,Vec3 tip,int victim){}
    private static Target target(AbstractClientPlayer owner,Scene scene,boolean left,float partial,Matrix4f inverse){
        var camera=Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        for(var mob:owner.level().getEntitiesOfClass(LivingEntity.class,owner.getBoundingBox().inflate(8),e->e!=owner&&e.isAlive()&&!e.isInvisible())){
            var v=mob.getData(ModAttachments.CONSUME_VISUAL);
            if(!v.consuming()||v.started()!=scene.visual.started()||v.consumerId()!=owner.getId())continue;
            if(ConsumeFracture.phase(mob,partial).returnFlow()>=.97F)return null;
            Vec3[] points=ChestConsumeTendrils.receiverPoints(mob,partial);
            Target best=null;double score=-Double.MAX_VALUE;
            var lateral=points[2].subtract(points[4]);
            var leftShoulder=AdaptiveRagdoll.consumeLimbJoint(mob,partial,4,points[1].add(lateral.scale(.92)),false);
            var rightShoulder=AdaptiveRagdoll.consumeLimbJoint(mob,partial,2,points[1].subtract(lateral.scale(.92)),false);
            for(var tip:new Vec3[]{leftShoulder,rightShoulder,points[2],points[4],points[0],points[1]}){
                var clip=com.codex.blacklight.client.TargetLockHud.handTarget(tip);
                if(clip==null||clip.w<=.11F||!Float.isFinite(clip.x+clip.y+clip.w))continue;
                double x=clip.x/clip.w,y=clip.y/clip.w;
                if(reticle(x,y)<1.35)continue;
                var obstruction=owner.level().clip(new ClipContext(camera,tip,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,owner));
                if(obstruction.getType()!=HitResult.Type.MISS&&camera.distanceTo(obstruction.getLocation())+.055<camera.distanceTo(tip))continue;
                double extent=Math.max(Math.abs(x),Math.abs(y));
                double rank=(left?-x:x)*.9-y*.48-.05*camera.distanceTo(tip)-3*Math.max(0,extent-.94);
                // An offscreen victim can still pull the fibre out of view below the hand. Terminate
                // just beyond the screen in its real direction, leaving the rest to the world-space render.
                if(extent>1.20){double fraction=1.20/extent;x*=fraction;y*=fraction;}
                if(rank>score){score=rank;best=new Target(unproject(inverse,x,y,clip.w),tip,mob.getId());}
            }
            return best;
        }
        return null;
    }
    static double reticle(double x,double y){return Math.hypot(x/.105,y/.155);}
    private static Vec3 unproject(Matrix4f inverse,double x,double y,double depth){
        var ray=inverse.transformProject(new Vector3f((float)x,(float)y,0));
        return new Vec3(ray).scale(-depth/ray.z);
    }
    private static Vec3 ndc(Matrix4f projection,Vec3 eye){
        var v=projection.transform(new Vector4f((float)eye.x,(float)eye.y,(float)eye.z,1));
        return new Vec3(v.x/v.w,v.y/v.w,0);
    }
    static Vec3[] path(Vec3 root,Vec3 tip,Matrix4f projection,Matrix4f inverse,int side,int strand,double age,double envelope,boolean linked){
        if(root.z>=-.07||tip.z>=-.07)return new Vec3[0];
        var r=ndc(projection,root);var end=ndc(projection,tip);
        if(!Double.isFinite(r.x+r.y+end.x+end.y))return new Vec3[0];
        // Enter below the sight line when the receiver crosses to the opposite side of the screen.
        var elbow=new Vec3(side*(.54+.10*strand),Math.min(-.30,Math.max(-.67,r.y+.12-.075*strand)),0);
        if(!linked)elbow=new Vec3(side*(.40+.11*strand),-.55-.09*strand,0);
        Vec3[] path=new Vec3[13];
        for(int i=0;i<path.length;i++){
            double t=i/(double)(path.length-1);
            double u=linked?t*Math.min(1,envelope*1.42):t;
            double curve=Math.sin(Math.PI*u);
            Vec3 p=r.lerp(elbow,u).lerp(elbow.lerp(end,u),u);
            double x=p.x+side*curve*(.025*Math.sin(age*6+u*7+strand));
            double y=p.y+curve*(.018*Math.cos(age*6.4+u*8+strand*2));
            if(i>0 && (i<path.length-1||linked&&u<.999) && Math.abs(x)<.128 && y>-.22 && y<.20){
                double floor=-.21*Math.sqrt(Math.max(0,1-x*x/(.13*.13)));
                y=Math.min(y,floor);
            }
            double depth=1/((1-u)/-root.z+u/-tip.z);
            path[i]=unproject(inverse,x,y,depth);
        }
        path[0]=root;
        if(!linked||envelope>=1/1.42)path[path.length-1]=tip;
        return path;
    }
    static double gap(Vec3 a,Vec3 b){
        double x=a.x/.105,y=a.y/.155,dx=(b.x-a.x)/.105,dy=(b.y-a.y)/.155;
        double length=dx*dx+dy*dy;
        double t=length<1e-12?0:Math.max(0,Math.min(1,-(x*dx+y*dy)/length));
        return Math.hypot(x+t*dx,y+t*dy);
    }
    static void draw(AbstractClientPlayer owner,PoseStack stack,MultiBufferSource buffers,int light,float partial,
                     boolean left,Vec3 wristLocal,Vec3 forearmLocal,Vec3 settleWrist,Vec3 settleForearm,Scene scene){
        if(scene==null)return;
        var modelView=new Matrix4f(RenderSystem.getModelViewMatrix());
        var handFrame=new Matrix4f(modelView).mul(stack.last().pose());
        var wrist=new Vec3(handFrame.transformPosition(wristLocal.toVector3f()));
        var forearm=new Vec3(handFrame.transformPosition(forearmLocal.toVector3f()));
        var projection=new Matrix4f(RenderSystem.getProjectionMatrix());var inverse=new Matrix4f(projection).invert();
        if(!Float.isFinite(inverse.m00()+handFrame.m00())||wrist.z>=-.07&&scene.residue<=.004F)return;
        var target=scene.envelope>.004F?target(owner,scene,left,partial,inverse):null;
        var pose=new PoseStack();
        pose.mulPose(modelView.invert());
        var out=buffers.getBuffer(RenderType.entityCutoutNoCull(ConsumeTendrilSegmentMesh.TEXTURE));
        int side=left?-1:1;
        for(int strand=0;scene.envelope>.004F&&strand<2;strand++){
            boolean linked=strand==0&&target!=null;
            Vec3 root=wrist.lerp(forearm,strand==0?.12:.29);
            Vec3 tip=linked?target.eye:forearm.lerp(wrist,strand==0?.11:.04);
            Vec3[] points=path(root,tip,projection,inverse,side,strand,scene.age,scene.envelope,linked);
            if(points.length==0)continue;
            double[] radii=new double[points.length];
            for(int j=0;j<radii.length;j++){
                double t=j/(double)(radii.length-1);
                radii[j]=Math.min(.046,-points[j].z*(strand==0?.023:.014))*scene.envelope
                        *(.61+.39*Math.sin(t*Math.PI))*(linked?1-.52*t:1);
            }
            emit(owner,scene,pose,out,light,projection,points,radii,target,linked,left,scene.envelope,linked?"bridge":"arm");
        }
        if(scene.residue>.004F){
            wrist=new Vec3(handFrame.transformPosition(settleWrist.toVector3f()));
            forearm=new Vec3(handFrame.transformPosition(settleForearm.toVector3f()));
            var axis=wrist.subtract(forearm).normalize();
            var centre=forearm.lerp(wrist,.47);
            var normal=centre.scale(-1).normalize();
            normal=normal.subtract(axis.scale(normal.dot(axis))).normalize();
            if(normal.lengthSqr()<.5)normal=new Vec3(0,1,0).subtract(axis.scale(axis.y)).normalize();
            var across=axis.cross(normal).normalize();
            var growth=armGrowth(left,scene.residueAge);
            for(int strand=0;strand<3;strand++){
                var fold=residue(forearm,wrist,normal,across,scene.residue,growth,strand);
                emit(owner,scene,pose,out,light,projection,fold.points(),fold.radii(),null,false,left,scene.residue,"knot");
            }
        }
    }
    record ArmGrowth(boolean left,ConsumeSkinGrowth.Weights weights,double pull) {}
    record Fold(Vec3[] points,double[] radii) {}
    static ArmGrowth armGrowth(boolean left,float age){
        var weights=ConsumeSkinPath.giverWeights(age);
        double pull=0;
        if(age>ConsumeGiverEffect.REVERSE_SECONDS){
            // As the giver coverage leaves the back of the hand, draw the fold up the posed forearm.
            var peak=ConsumeSkinPath.giverWeights(ConsumeGiverEffect.REVERSE_SECONDS);
            double atPeak=ConsumeSkinPath.armCoverage(peak,left,.84);
            if(atPeak>0){
                double loss=1-Math.min(1,ConsumeSkinPath.armCoverage(weights,left,.84)/atPeak);
                pull=loss*loss*(3-2*loss);
            }
        }
        return new ArmGrowth(left,weights,pull);
    }
    static Fold residue(Vec3 forearm,Vec3 wrist,Vec3 outward,Vec3 across,double mass,ArmGrowth growth,int strand){
        double length=forearm.distanceTo(wrist);
        double span=Math.min(.30,length);
        Vec3[] points=new Vec3[9];
        double[] radii=new double[points.length];
        for(int i=0;i<points.length;i++){
            double t=i/(double)(points.length-1),arch=Math.sin(Math.PI*t);
            double u=(t-.5)*(.94-.12*strand)+(strand-1)*.10;
            double along=Math.min(1,Math.max(0,.47+u*span/Math.max(.00001,length)));
            // Cuff-to-hand mesh samples span the elbow side of the forearm through the knuckles.
            double shoulderToFingers=.44+.48*along;
            double localMass=mass*ConsumeSkinPath.armCoverage(growth.weights(),growth.left(),shoulderToFingers);
            double width=(.21*Math.sin(t*Math.PI*(1.5+strand*.33)+strand*2)+(strand-1)*.12)*localMass;
            double sink=Math.max(0,1-localMass/.15);
            double height=.035*localMass+span*(localMass*(-.05+arch*(.30+.045*strand))-.125*sink*sink);
            // Keep the moving base on the segment between the two actual posed surface anchors.
            var base=forearm.lerp(wrist,along*(1-.67*growth.pull()));
            points[i]=base.add(across.scale(span*width)).add(outward.scale(height));
            radii[i]=Math.min(.048,length*.23)*(.36+.64*arch)*localMass;
        }
        return new Fold(points,radii);
    }
    private static void emit(AbstractClientPlayer owner,Scene scene,PoseStack pose,com.mojang.blaze3d.vertex.VertexConsumer out,int light,
                             Matrix4f projection,Vec3[] points,double[] radii,Target target,boolean linked,boolean left,
                             float strength,String kind){
        // A swing can put the actual palm over the crosshair. Clip only overlapping spans, including their thickness.
        int begin=0;
        double minGap=Double.POSITIVE_INFINITY;
        for(int j=1;j<=points.length;j++){
            double clearance=j==points.length?0:gap(ndc(projection,points[j-1]),ndc(projection,points[j]));
            double padding=j==points.length?0:Math.max(radii[j-1],radii[j])/Math.max(.001,-Math.max(points[j-1].z,points[j].z))*Math.max(projection.m00()/.105,projection.m11()/.155);
            if(j<points.length&&radii[j-1]+radii[j]>1e-6&&points[j-1].z<-.07&&points[j].z<-.07&&clearance>1+padding){minGap=Math.min(minGap,clearance);continue;}
            if(j-begin>=2){
                var span=java.util.Arrays.copyOfRange(points,begin,j);
                ConsumeTendrilSegmentMesh.draw(pose,out,light,span,java.util.Arrays.copyOfRange(radii,begin,j),new Vec3(0,1,0),0xffffffff);
                if(observer!=null)observer.accept(new Sample(owner.getId(),target==null?-1:target.victim,kind.equals("knot")?scene.residueSource.started():scene.visual.started(),
                        linked,left,strength,span.length,ndc(projection,span[0]),ndc(projection,span[span.length-1]),minGap,kind));
            }
            begin=j;minGap=Double.POSITIVE_INFINITY;
        }
    }
    public record Sample(int owner,int target,long start,boolean linked,boolean left,float strength,int points,Vec3 projectedRoot,Vec3 projectedTip,double nearestReticle,String kind){}
    public static java.util.function.Consumer<Sample> observer;
}
