package com.codex.blacklight.client.renderer;

import com.codex.blacklight.entity.BlacklightInfectedHost;
import com.codex.blacklight.infection.TissueGrowth;
import com.codex.blacklight.infection.TissueOrgans;
import com.codex.blacklight.infection.TissueSurfaceLayout;
import com.codex.blacklight.mixin.client.ModelPartChildrenAccessor;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.*;
import org.joml.Vector3f;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Tissue rooted in actual cube surfaces, then transformed by the host's posed skeleton.
 * A stable UUID/geometry seed replaces frame-random placements and bounding-box pillars. */
final class SurfaceTissue {
    private static final net.minecraft.resources.ResourceLocation MATERIAL=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("blacklight","textures/entity/tissue_fibres.png");
    private static final Map<BlacklightInfectedHost,TentacleGait> HOST_GAITS=new WeakHashMap<>();
    private record Part(ModelPart part, PoseStack.Pose pose, int light, int overlay, int color) {}
    private final Map<ModelPart, Part> pending = new IdentityHashMap<>();
    private final long seed;
    private final float growth;
    private final BlacklightInfectedHost host;
    private final float partial;
    private final org.joml.Matrix4f rootPose;
    private final Map<ModelPart,Integer> anatomy;
    private final ModelPart primaryBody;
    private final boolean anatomical;
    private final AnatomicalTissue growthModel;
    private final AuthoredInfectionParts.Binding approved;
    SurfaceTissue(BlacklightInfectedHost host,EntityModel<?> model,PoseStack.Pose root,float partial,AnatomicalTissue growthModel,AuthoredInfectionParts.Binding approved) {
        this.growthModel=growthModel;this.approved=approved;this.anatomical=growthModel!=null||approved.available();
        this.host=host;this.partial=partial;rootPose=new org.joml.Matrix4f(root.pose());
        anatomy=ConsumeAnatomy.model(model);
        ModelPart selected=null;float volume=0;
        for(var entry:anatomy.entrySet())if(entry.getValue()==ConsumeAnatomy.BODY)
            for(var cube:((ModelPartChildrenAccessor)(Object)entry.getKey()).blacklight$cubes()) {
                float size=(cube.maxX-cube.minX)*(cube.maxY-cube.minY)*(cube.maxZ-cube.minZ);
                if(size>volume){volume=size;selected=entry.getKey();}
            }
        primaryBody=selected;
        seed = host.getUUID().getMostSignificantBits() ^ host.getUUID().getLeastSignificantBits();
        growth = TissueGrowth.progress(host.tissueAge());
    }
    boolean replacesBody(ModelPart part) { return !anatomical&&part==primaryBody&&growth>.66F; }
    /** A local compile transform, not a write to the shared species model or its child joints. */
    PoseStack.Pose mutatedPose(ModelPart part,PoseStack.Pose pose) {
        if(anatomical)return pose;
        var cubes=((ModelPartChildrenAccessor)(Object)part).blacklight$cubes();
        if(cubes.isEmpty())return pose;
        int role=anatomy.getOrDefault(part,ConsumeAnatomy.BODY);
        var mutation=TissueOrgans.mutation(seed,host.tissueAge(),role);
        if(!mutation.active())return pose;
        var cube=cubes.getFirst();
        float x=(cube.minX+cube.maxX)*.5F,y=(cube.minY+cube.maxY)*.5F,z=(cube.minZ+cube.maxZ)*.5F;
        boolean arm=role==ConsumeAnatomy.RIGHT_ARM||role==ConsumeAnatomy.LEFT_ARM;
        if(arm)y=cube.minY;else if(role==ConsumeAnatomy.HEAD)y=cube.maxY;
        var copy=new PoseStack();copy.last().pose().set(pose.pose());copy.last().normal().set(pose.normal());
        copy.translate(x/16,y/16,z/16);
        // A quadruped's foreleg keeps its foot planted while its muscle mass thickens.
        copy.scale(mutation.x(),arm&&!host.humanoid()?1:mutation.y(),mutation.z());
        copy.translate(-x/16,-y/16,-z/16);return copy.last();
    }
    void capture(ModelPart part, PoseStack.Pose pose, int light, int overlay, int color) {
        if (pending.size() >= 32 || pending.containsKey(part)) return;
        if (((ModelPartChildrenAccessor)(Object)part).blacklight$cubes().isEmpty()) return;
        var copy = new PoseStack();
        copy.last().pose().set(pose.pose()); copy.last().normal().set(pose.normal());
        pending.put(part, new Part(part, copy.last(), light, overlay, color));
    }
    void flush(MultiBufferSource buffers) {
        if (pending.isEmpty()) return;
        var out = buffers.getBuffer(RenderType.entityCutoutNoCull(MATERIAL));
        if(anatomical){renderOrgans(out);pending.clear();return;}
        int budget=768;
        Part trunk=largest(ConsumeAnatomy.BODY);
        for (var p : pending.values()) {
            if(p==trunk)continue; // The trunk grows as one connected volume, never as intersecting sausages.
            if(out instanceof ConsumeAnatomy.Consumer consumer)consumer.anatomy(p.part);
            int role=anatomy.getOrDefault(p.part,ConsumeAnatomy.BODY);
            int count = 0;
            for (var cube : ((ModelPartChildrenAccessor)(Object)p.part).blacklight$cubes()) {
                if (++count > 2) break;
                float[] low = {cube.minX, cube.minY, cube.minZ};
                float[] high = {cube.maxX, cube.maxY, cube.maxZ};
                if (high[0]-low[0]<.5F || high[1]-low[1]<.5F || high[2]-low[2]<.5F) continue;
                var rest=p.part.getInitialPose();
                long key = TissueGrowth.mix(seed ^ Float.floatToIntBits(rest.x) ^ ((long)Float.floatToIntBits(rest.z)<<32) ^ Float.floatToIntBits(rest.y));
                for (int k=0;k<3;k++) key = TissueGrowth.mix(key ^ Float.floatToIntBits(low[k]) ^ ((long)Float.floatToIntBits(high[k])<<32));
                float delay=role==ConsumeAnatomy.BODY?0:.10F+.12F*TissueGrowth.sample(key,81);
                float local=Math.clamp((growth-delay)/(1-delay),0,1);
                for(var cell:TissueSurfaceLayout.cells(key)) {
                    if(budget--<=0)break;
                    float visible=TissueSurfaceLayout.reveal(local,cell.rank());
                    if(visible<=0)continue;
                    int axis=cell.face()/2,a=(axis+1)%3,b=(axis+2)%3;
                    float sign=cell.face()%2==0?-1:1;
                    // Keep the actual muzzle, eyes and authored facial wound readable.
                    if(role==ConsumeAnatomy.HEAD&&axis==2&&sign<0)continue;
                    float[] center=new float[3];
                    center[axis]=(sign<0?low[axis]:high[axis])+sign*.045F;
                    center[a]=low[a]+(high[a]-low[a])*(cell.u()*.5F+.25F);
                    center[b]=low[b]+(high[b]-low[b])*(cell.v()*.5F+.25F);
                    float size=(float)Math.sqrt(visible);
                    float width=((high[a]-low[a])*.25F+.012F)*size;
                    float length=((high[b]-low[b])*.25F+.012F)*size;
                    float depth=(.06F+.20F*local)*visible;
                    // Neighbouring patches merge into a continuous shell over every face.
                    int shell=role==ConsumeAnatomy.BODY?0x41333a:0x50353a;
                    plate(out,p,center,axis,a,b,sign,width,length,depth,shell);
                    float[] seam=center.clone();seam[axis]+=sign*.025F;
                    if(cell.rank()%3==0)wedge(out,p,seam,axis,a,b,sign,Math.min(.14F,width*.14F),length*.92F,depth+.02F,0x6a343d,0x412a31);
                }
            }
        }
        renderOrgans(out);
        pending.clear();
    }
    private Part largest(int role) {
        Part result=null;float size=0;
        for(var p:pending.values())if(anatomy.getOrDefault(p.part,ConsumeAnatomy.BODY)==role) {
            var cube=largestCube(p);if(cube==null)continue;
            float volume=(cube.maxX-cube.minX)*(cube.maxY-cube.minY)*(cube.maxZ-cube.minZ);
            if(volume>size){size=volume;result=p;}
        }
        return result;
    }
    private static ModelPart.Cube largestCube(Part part) {
        ModelPart.Cube result=null;float size=0;
        for(var cube:((ModelPartChildrenAccessor)(Object)part.part).blacklight$cubes()) {
            float volume=(cube.maxX-cube.minX)*(cube.maxY-cube.minY)*(cube.maxZ-cube.minZ);
            if(volume>size){size=volume;result=cube;}
        }
        return result;
    }
    /** Host-space frame: mass follows the shoulder/rib cage instead of radiating from a spike socket. */
    private record Frame(Vector3f center,Vector3f right,Vector3f up,Vector3f forward,float width,float height,float length) {
        Vector3f point(float side,float rise,float front) {
            return new Vector3f(center).fma(side*width,right).fma(rise*height,up).fma(front*length,forward);
        }
    }
    private static Frame frame(Part part,ModelPart.Cube cube) {
        boolean horizontal=Math.abs(Math.sin(part.part.getInitialPose().xRot))>.5;
        return new Frame(new Vector3f((cube.minX+cube.maxX)*.5F,(cube.minY+cube.maxY)*.5F,(cube.minZ+cube.maxZ)*.5F),
                new Vector3f(1,0,0),horizontal?new Vector3f(0,0,1):new Vector3f(0,-1,0),
                horizontal?new Vector3f(0,-1,0):new Vector3f(0,0,-1),
                (cube.maxX-cube.minX)*.5F,(horizontal?cube.maxZ-cube.minZ:cube.maxY-cube.minY)*.5F,
                (horizontal?cube.maxY-cube.minY:cube.maxZ-cube.minZ)*.5F);
    }
    private void renderOrgans(VertexConsumer out) {
        Part body=largest(ConsumeAnatomy.BODY);if(body==null)return;
        var cube=largestCube(body);if(cube==null)return;
        Frame f=frame(body,cube);
        float side=TissueOrgans.dominantArm(seed)==ConsumeAnatomy.LEFT_ARM?1:-1;
        float bodyScale=host.tissueBodyScale();
        float scale=TissueOrgans.bodyFactor(host.getBbWidth()/bodyScale,host.getBbHeight()/bodyScale)*16/1.12F;
        if(anatomical&&(growthModel==null||growthModel.procedural()))renderSupportingTentacles(out,body,f,bodyScale);
        if(!anatomical)renderBodyEnvelope(out,body,f,side);
        for(int slot=0;slot<TissueOrgans.count(seed);slot++) {
            var organ=TissueOrgans.organ(seed,host.tissueAge(),slot);float g=organ.growth();if(g<=.001F)continue;
            if(anatomical&&organ.kind()!=TissueOrgans.Kind.TENDRIL)continue;
            if(out instanceof ConsumeAnatomy.Consumer consumer)consumer.anatomy(body.part);
            switch(organ.kind()) {
                case CARAPACE, MUSCLE -> { /* Differentiated regions of the same connected body envelope. */ }
                case TENDRIL -> {
                    float extension=TissueOrgans.extension(host.tissueStrikeElapsed(partial));
                    float elapsed=host.tissueStrikeElapsed(partial);
                    boolean grapple=host.tissueGripReleaseAt()>=0&&elapsed>=TissueOrgans.HIT&&elapsed<TissueOrgans.DURATION;
                    float released=host.level().getGameTime()+partial-host.tissueGripReleaseAt();
                    if(grapple)extension=released<=0?1:com.codex.blacklight.infection.TissueAnatomy.smooth(1-released/5F);
                    if(extension<=0)continue; // The muscle folds inside its shoulder gland at rest.
                    int segments=grapple?17:9;
                    Vector3f[] path=new Vector3f[segments];float[] w=new float[segments],d=new float[segments];
                    Vector3f endpoint=null;
                    float coilRadius=scale*.18F,coilHeight=scale*.18F;
                    if(extension>0) {
                        var relative=host.tissueStrikePoint().subtract(0,host.tissueStanceOffset(),0).scale(1/(1.12*bodyScale));
                        if((elapsed<=TissueOrgans.HIT||grapple&&released<=0)&&host.level().getEntity(host.tissueStrikeTargetId()) instanceof net.minecraft.world.entity.LivingEntity target) {
                            relative=target.getBoundingBox().getCenter().subtract(host.getPosition(partial)).subtract(0,host.tissueStanceOffset(),0).scale(1/(1.12*bodyScale));
                            coilRadius=target.getBbWidth()*.57F*16/(1.12F*bodyScale);coilHeight=target.getBbHeight()*.42F*16/(1.12F*bodyScale);
                        }
                        // A target teleport must not turn a missed strike into an unbounded visible spear.
                        if(relative.length()<host.getBbWidth()+host.tissueOrganReach()*2+2&&Math.abs(body.pose.pose().determinant())>1e-8F)
                            endpoint=new org.joml.Matrix4f(body.pose.pose()).invert().mul(rootPose)
                                    .transformPosition(new Vector3f((float)relative.x,(float)relative.y,(float)relative.z)).mul(16);
                    }
                    float muscle=TissueOrgans.organ(seed,host.tissueAge(),1).growth();
                    Vector3f root=host.humanoid()?f.point(side*(1.015F+.56F*muscle),.5F,0)
                            :f.point(side*(1.015F+.56F*muscle),0,.5F);
                    Vector3f generatedRoot=growthModel!=null?growthModel.tendrilRoot(body.part):null;
                    if(generatedRoot!=null)root=generatedRoot;
                    else if(approved.available())root=approved.developRoot(body.part,f.point(side*.93F,host.humanoid()?.35F:0,host.humanoid()?0:.35F));
                    else if(growthModel!=null&&!growthModel.procedural()){
                        float stage=com.codex.blacklight.infection.TissueAnatomy.local(seed,growth,0);
                        float x=com.codex.blacklight.infection.TissueAnatomy.coordinate(6.08F,10.15F,13.45F,stage);
                        root=f.point(side*x/f.width,0,.42F);
                    }
                    for(int i=0;i<segments;i++) {
                        float t=i/(float)(segments-1);
                        path[i]=new Vector3f(root).fma(-side*t*2,f.right);
                        if(endpoint!=null) {
                            Vector3f strike=new Vector3f(root).lerp(endpoint,t).fma(scale*.14F*(float)Math.sin(Math.PI*t),f.up);
                            if(grapple) {
                                if(i<8)strike.set(root).lerp(new Vector3f(endpoint).fma(coilRadius,f.right).fma(-coilHeight*.5F,f.up),i/8F)
                                        .fma(scale*.12F*(float)Math.sin(Math.PI*i/8F),f.up);
                                else {
                                    float turn=(i-8)/8F,coil=turn*(float)Math.PI*2.2F;
                                    strike.set(endpoint).fma((float)Math.cos(coil)*coilRadius,f.right)
                                            .fma((turn-.5F)*coilHeight,f.up).fma((float)Math.sin(coil)*coilRadius,f.forward);
                                }
                            }
                            path[i].lerp(strike,extension);
                        }
                        float taper=i==segments-1?.025F:(.35F+.65F*(float)Math.sin(Math.PI*(t*.76F+.20F)))*(1-t*(grapple?.55F:.82F));
                        w[i]=organ.radius()*scale*.66F*taper;d[i]=w[i]*.76F;
                    }
                    tissueTube(out,body,path,w,d,f.right,TissueOrgans.Kind.TENDRIL);
                }
                case BLADE -> {
                    if(!host.humanoid())continue;
                    Part limb=largest(TissueOrgans.dominantArm(seed));if(limb==null)continue;
                    var limbCube=largestCube(limb);if(limbCube==null)continue;
                    if(out instanceof ConsumeAnatomy.Consumer consumer)consumer.anatomy(limb.part);
                    // A single broad hooked forearm plate, aligned with the limb rather than projecting off the back.
                    float x=(limbCube.minX+limbCube.maxX)*.5F;
                    float z=limbCube.minZ;
                    Vector3f[] path=new Vector3f[6];float[] w=new float[6],d=new float[6];
                    for(int i=0;i<6;i++) {
                        float t=i/5F;
                        path[i]=new Vector3f(x+side*(float)Math.sin(t*Math.PI)*g,
                                net.minecraft.util.Mth.lerp(t,limbCube.minY,limbCube.maxY+2*g),z-(.5F+2*(float)Math.sin(t*Math.PI))*g);
                        w[i]=(limbCube.maxX-limbCube.minX)*(.34F+.20F*(float)Math.sin(t*Math.PI))*g*(i==5?.05F:1);
                        d[i]=w[i]*.38F;
                    }
                    tissueTube(out,limb,path,w,d,new Vector3f(1,0,0),TissueOrgans.Kind.BLADE);
                }
            }
        }
    }
    /** Reuses the abandoned slime's planted-tip solver on real host surfaces.
     * Contact tests run only on stepping/reset, never force-loading chunks. */
    private void renderSupportingTentacles(VertexConsumer out,Part body,Frame frame,float bodyScale) {
        float amount=host.tissueTentacles();if(amount<.005F)return;
        int count=com.codex.blacklight.infection.TissueEvolution.tentacleCount(seed);
        var gait=HOST_GAITS.computeIfAbsent(host,k->new TentacleGait(count));
        float yaw=net.minecraft.util.Mth.rotLerp(partial,host.yBodyRotO,host.yBodyRot);
        Vec3 world=host.getPosition(partial);double reach=host.getBbWidth()*.5+bodyScale*.85;
        gait.update(host.level().getGameTime()+partial,world,yaw,host.onGround(),reach,point->{
            if(!host.level().hasChunkAt(net.minecraft.core.BlockPos.containing(point)))return point;
            var hit=host.level().clip(new net.minecraft.world.level.ClipContext(point.add(0,bodyScale*.8,0),point.add(0,-bodyScale*1.4,0),
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,host));
            return hit.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK?hit.getLocation().add(0,.025,0):point;
        });
        if(Math.abs(body.pose.pose().determinant())<1e-8F)return;
        var inverse=new org.joml.Matrix4f(body.pose.pose()).invert().mul(rootPose);
        if(out instanceof ConsumeAnatomy.Consumer consumer)consumer.anatomy(body.part);
        for(int arm=0;arm<count;arm++) {
            double angle=arm*Math.PI*2/count;
            Vec3 relative=gait.tip(arm).subtract(world).subtract(0,host.tissueStanceOffset(),0).scale(1/(1.12*bodyScale));
            Vector3f tip=inverse.transformPosition(new Vector3f((float)relative.x,(float)relative.y,(float)relative.z),new Vector3f()).mul(16);
            // Root identity follows the body's rest directions, never the
            // currently planted tip; turning must bend a limb, not slide its socket.
            double homeAngle=angle+.12*Math.sin(arm*2.4);
            Vec3 homeDirection=new Vec3(Math.cos(homeAngle),0,Math.sin(homeAngle)).yRot((float)Math.toRadians(-yaw));
            Vector3f heading=inverse.transformDirection(new Vector3f((float)homeDirection.x,0,(float)homeDirection.z),new Vector3f());
            float lateral=heading.dot(frame.right),forward=heading.dot(frame.forward),span=(float)Math.hypot(lateral,forward);
            Vector3f root=growthModel!=null?growthModel.tentacleRoot(body.part,lateral/Math.max(1,span),forward/Math.max(1,span))
                    :approved.developRoot(body.part,frame.point(.86F*lateral/Math.max(1,span),0,.60F*forward/Math.max(1,span)));
            if(root==null)continue;
            tip=new Vector3f(root).lerp(tip,amount);
            Vector3f[] path=new Vector3f[7];float[] widths=new float[7],depths=new float[7];
            float radius=Math.clamp(Math.min(frame.width,frame.height)*.30F,1F,2.8F)*amount;
            Vector3f spanVector=new Vector3f(tip).sub(root),outward=new Vector3f(spanVector).fma(-spanVector.dot(frame.up),frame.up);
            if(outward.lengthSquared()<1e-5F)outward.set(frame.right);else outward.normalize();
            Vector3f control1=new Vector3f(root).fma(spanVector.length()*.40F,outward).fma(1.5F*amount,frame.up);
            Vector3f control2=new Vector3f(tip).fma(-spanVector.length()*.12F,outward).fma(3F*amount,frame.up);
            for(int i=0;i<path.length;i++) {
                float t=i/(float)(path.length-1);
                float a=1-t;
                path[i]=new Vector3f(root).mul(a*a*a).fma(3*a*a*t,control1).fma(3*a*t*t,control2).fma(t*t*t,tip);
                widths[i]=radius*(1-t*.70F);depths[i]=widths[i]*(i==path.length-1?.45F:.85F);
            }
            tissueTube(out,body,path,widths,depths,frame.right,TissueOrgans.Kind.TENDRIL);
        }
    }
    private void renderBodyEnvelope(VertexConsumer out,Part body,Frame f,float dominant) {
        float muscle=TissueOrgans.organ(seed,host.tissueAge(),1).growth();
        float secondary=TissueOrgans.organ(seed,host.tissueAge(),3).growth();
        float plate=TissueOrgans.organ(seed,host.tissueAge(),2).growth();
        if(muscle<=.001F)return;
        boolean upright=host.humanoid();
        float longSize=upright?f.height:f.length,crossSize=upright?f.length:f.height;
        Vector3f along=upright?f.up:f.forward;
        Vector3f vertical=upright?new Vector3f(f.forward).negate():f.up;
        float[] shoulder={0,.08F,.18F,.40F,.65F,.95F,1,.70F,.05F};
        float[] hump={0,.08F,.24F,.58F,.95F,1,.80F,.34F,0};
        float rounding=Math.clamp((growth-.64F)/.36F,0,1);
        // A multiple of eight includes the exact original box corners during the early growth blend.
        int rings=9,sides=16;
        Vector3f[][] shell=new Vector3f[rings][sides];
        for(int ring=0;ring<rings;ring++)for(int side=0;side<sides;side++) {
            double angle=side*Math.PI*2/sides;
            float x=(float)Math.cos(angle),y=(float)Math.sin(angle),edge=Math.max(Math.abs(x),Math.abs(y));
            // Grow out of the original box before rounding its corners; hidden original skin cannot poke through.
            float roundedX=Math.copySign((float)Math.pow(Math.abs(x),.68),x);
            float roundedY=Math.copySign((float)Math.pow(Math.abs(y),.68),y);
            x=net.minecraft.util.Mth.lerp(rounding,x/edge,roundedX);
            y=net.minecraft.util.Mth.lerp(rounding,y/edge,roundedY);
            float asym=x*dominant>0?1:.52F;
            float regional=x*dominant>0?muscle:secondary;
            float width=f.width*(1.015F+shoulder[ring]*(.29F+.27F*asym)*regional);
            float height=crossSize*(1.015F+(y>0?.58F*hump[ring]*plate:.15F*shoulder[ring]*muscle));
            float fibre=(side==0||side==sides/2)?.035F*(float)Math.sin(ring*2.5F)*muscle:0;
            shell[ring][side]=new Vector3f(f.center).fma(x*(width+f.width*fibre),f.right).fma(y*height,vertical)
                    .fma((ring/4F-1)*longSize*1.015F,along);
        }
        int outlet=dominant>0?0:sides/2;
        for(int ring=0;ring<rings-1;ring++)for(int side=0;side<sides;side++) {
            int next=(side+1)%sides;
            float start=.13F+Math.abs((ring+.5F)/(rings-1)-.66F)*.20F+Math.min(side,sides-side)*.01F;
            float visible=Math.clamp((growth-start)/.25F,0,1);
            if(visible<=0)continue;
            // Large dark dorsal plates frame warmer, exposed flank muscle; no equal-size repeated armour tiles.
            double angle=(side+.5)*Math.PI*2/sides;
            double dorsal=Math.sin(angle),flank=Math.abs(Math.cos(angle));
            int rgb=dorsal>.7?0x50414a:dorsal>.35?0x5d3845:flank>.75?0x874650:0x633a43;
            if(flank>.75&&ring%3==0)rgb=0x713943;
            Vector3f[] quad={shell[ring][side],shell[ring+1][side],shell[ring+1][next],shell[ring][next]};
            Vector3f midpoint=new Vector3f();for(var v:quad)midpoint.add(v);midpoint.mul(.25F);
            Vector3f[] grown=new Vector3f[4];for(int i=0;i<4;i++)grown[i]=new Vector3f(midpoint).lerp(quad[i],visible);
            if(growth>.67F&&(ring==5||ring==6)&&(side==outlet||next==outlet))continue;
            triangle(out,body,grown[0],grown[2],grown[1],rgb);triangle(out,body,grown[0],grown[3],grown[2],rgb);
        }
        if(growth>.67F) {
            // A single connected slit, not four separate square cavities.
            int left=(outlet+sides-1)%sides,right=(outlet+1)%sides;
            Vector3f[] border={shell[5][left],shell[5][right],shell[6][right],shell[7][right],shell[7][left],shell[6][left]};
            Vector3f center=new Vector3f();for(var v:border)center.add(v);center.div(border.length);
            Vector3f[] inner=new Vector3f[border.length];
            for(int i=0;i<inner.length;i++)inner[i]=new Vector3f(center).lerp(border[i],.48F).fma(-dominant*f.width*.28F,f.right);
            Vector3f floor=new Vector3f(center).fma(-dominant*f.width*.32F,f.right);
            for(int i=0;i<border.length;i++) {
                int j=(i+1)%border.length;
                surfaceTriangle(out,body,border[i],border[j],inner[j],0,dominant,0x74434e);
                surfaceTriangle(out,body,border[i],inner[j],inner[i],0,dominant,0x5c303b);
                surfaceTriangle(out,body,inner[i],inner[j],floor,0,dominant,0x35252c);
            }
        }
        for(int end:new int[]{0,rings-1}) {
            Vector3f center=new Vector3f(f.center).fma((end==0?-1:1)*longSize*1.015F,along);
            for(int s=0;s<sides;s++)if(growth>.65F) {
                if(end==0)triangle(out,body,center,shell[end][(s+1)%sides],shell[end][s],0x69404a);
                else triangle(out,body,center,shell[end][s],shell[end][(s+1)%sides],0x69404a);
            }
        }
    }
    /** Continuous connected flesh, with a stable cross-section so the surface cannot twist into a ribbon. */
    private static void tissueTube(VertexConsumer out,Part p,Vector3f[] path,float[] widths,float[] depths,Vector3f across,TissueOrgans.Kind kind) {
        int sides=8;Vector3f[][] skin=new Vector3f[path.length][sides];
        Vector3f previous=null;
        for(int ring=0;ring<path.length;ring++) {
            Vector3f tangent=new Vector3f(path[Math.min(path.length-1,ring+1)]).sub(path[Math.max(0,ring-1)]);
            if(tangent.lengthSquared()<1e-8F)tangent.set(0,1,0);else tangent.normalize();
            Vector3f a=new Vector3f(across).fma(-across.dot(tangent),tangent);
            if(a.lengthSquared()<1e-5F)a=new Vector3f(Math.abs(tangent.y)<.85F?new Vector3f(0,1,0):new Vector3f(0,0,1)).cross(tangent);
            a.normalize();if(previous!=null&&a.dot(previous)<0)a.negate();previous=new Vector3f(a);
            Vector3f b=new Vector3f(tangent).cross(a).normalize();
            for(int s=0;s<sides;s++) {
                double angle=s*Math.PI*2/sides;
                skin[ring][s]=new Vector3f(path[ring]).fma((float)Math.cos(angle)*widths[ring],a).fma((float)Math.sin(angle)*depths[ring],b);
            }
        }
        int[] flesh={0x563137,0x79424a,0x62343c,0x442b32,0x37292e,0x502d35,0x85505a,0x60363e};
        int[] shell={0x333238,0x494047,0x5d323b,0x342e35,0x24282e,0x40383e,0x75404b,0x42383d};
        int[] colors=kind==TissueOrgans.Kind.MUSCLE?flesh:shell;
        for(int r=0;r<path.length-1;r++)for(int s=0;s<sides;s++) {
            int next=(s+1)%sides,rgb=colors[s];
            if(kind==TissueOrgans.Kind.TENDRIL&&r>path.length-4)rgb=s%3==0?0x4d4147:0x2c2b31;
            triangle(out,p,skin[r][s],skin[r+1][next],skin[r+1][s],rgb);
            triangle(out,p,skin[r][s],skin[r][next],skin[r+1][next],rgb);
        }
        for(int s=0;s<sides;s++) {
            triangle(out,p,path[0],skin[0][(s+1)%sides],skin[0][s],colors[s]);
            int end=path.length-1;triangle(out,p,path[end],skin[end][s],skin[end][(s+1)%sides],colors[s]);
        }
    }
    private static void plate(VertexConsumer out,Part p,float[] center,int axis,int a,int b,float sign,
                              float width,float length,float depth,int shell) {
        Vector3f[] corners=new Vector3f[4];
        for(int i=0;i<4;i++) {
            float[] v=center.clone();v[a]+=(i==0||i==3?-width:width);v[b]+=(i<2?-length:length);
            corners[i]=new Vector3f(v[0],v[1],v[2]);
        }
        float[] top=center.clone();top[axis]+=sign*depth;
        var crown=new Vector3f(top[0],top[1],top[2]);
        for(int i=0;i<4;i++)surfaceTriangle(out,p,corners[i],corners[(i+1)%4],crown,axis,sign,shell);
    }
    private static void wedge(VertexConsumer out,Part p,float[] center,int axis,int a,int b,float sign,
                              float width,float length,float height,int shell,int root) {
        Vector3f[][] rings=new Vector3f[5][3];
        for(int r=0;r<5;r++) {
            float t=r/4F, taper=(float)Math.sin(Math.PI*t)*.85F+.15F;
            for(int j=0;j<3;j++) {
                float[] v=center.clone();v[b]+=(t*2-1)*length;
                v[a]+=(j-1)*width*taper;
                v[axis]+=sign*(j==1?height*taper:.018F);
                rings[r][j]=new Vector3f(v[0],v[1],v[2]);
            }
        }
        for(int r=0;r<4;r++)for(int j=0;j<2;j++) {
            surfaceTriangle(out,p,rings[r][j],rings[r+1][j],rings[r+1][j+1],axis,sign,j==0?shell:root);
            surfaceTriangle(out,p,rings[r][j],rings[r+1][j+1],rings[r][j+1],axis,sign,j==0?shell:root);
        }
        triangle(out,p,rings[0][0],rings[0][1],rings[0][2],root);
        triangle(out,p,rings[4][2],rings[4][1],rings[4][0],root);
    }
    private static void surfaceTriangle(VertexConsumer out,Part p,Vector3f a,Vector3f b,Vector3f c,int axis,float sign,int rgb) {
        if(new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a)).get(axis)*sign<0)triangle(out,p,a,c,b,rgb);
        else triangle(out,p,a,b,c,rgb);
    }
    private static void triangle(VertexConsumer out,Part p,Vector3f a,Vector3f b,Vector3f c,int rgb) {
        Vector3f normal=new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a));
        if(normal.lengthSquared()<1e-8F)return;
        normal.normalize();
        int axis=Math.abs(normal.x)>Math.abs(normal.y)?0:1;if(Math.abs(normal.z)>Math.abs(normal.get(axis)))axis=2;
        int u=(axis+1)%3,w=(axis+2)%3;
        for(var v:new Vector3f[]{a,b,c,c})
            out.addVertex(p.pose,v.x/16,v.y/16,v.z/16).setColor((p.color&0xff000000)|rgb)
                    .setUv(v.get(u)/16,v.get(w)/16).setOverlay(p.overlay).setLight(p.light)
                    .setNormal(p.pose,normal.x,normal.y,normal.z);
    }
}
