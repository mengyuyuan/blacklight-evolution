package com.codex.blacklight.client.renderer;
import com.codex.blacklight.entity.BlacklightInfectedHost;
import com.codex.blacklight.infection.*;
import com.codex.blacklight.combat.EvolvedMotions;
import com.codex.blacklight.animation.RigidSkinning;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Standard player cuboids skinned by P2 Evolved's full 67-joint hierarchy. */
final class EvolvedBodyRenderer {
    private record Vertex(Vector3f point,int a,int b,float weight){}
    private record Face(int[] ids,int role,int kind,float[] uv,int color){}
    private static final ResourceLocation SKIN=ResourceLocation.fromNamespaceAndPath("blacklight","textures/entity/evolved_runner_mc.png");
    private static final ResourceLocation WEAPON=ResourceLocation.fromNamespaceAndPath("blacklight","textures/entity/evolved_arms.png");
    private static final ResourceLocation WEAPON_GLOW=ResourceLocation.fromNamespaceAndPath("blacklight","textures/entity/evolved_arms_emissive.png");
    private static EvolvedBodyRenderer instance;
    private final PlayerModel<BlacklightInfectedHost> anatomy;
    private final Vertex[] vertices;private final Face[] faces;private final int[] drivers;
    private final int rightWrist=EvolvedMotions.index("Wrist_R"),leftWrist=EvolvedMotions.index("Wrist_L");
    private final Vector3f[] quad={new Vector3f(),new Vector3f(),new Vector3f(),new Vector3f()};
    private Vector3f[] posed;
    private final int[] tips={-1,-1};
    private static final class Transition {String clip;float time;float[] from,last;}
    private final java.util.Map<BlacklightInfectedHost,Transition> transitions=new java.util.WeakHashMap<>();
    private EvolvedBodyRenderer(){
        anatomy=new PlayerModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER),false);
        drivers=new int[]{EvolvedMotions.index("Spine_2"),EvolvedMotions.index("Head"),EvolvedMotions.index("Shoulder_R"),EvolvedMotions.index("Shoulder_L"),EvolvedMotions.index("Hip_R"),EvolvedMotions.index("Hip_L")};
        try(var input=Minecraft.getInstance().getResourceManager().open(ResourceLocation.fromNamespaceAndPath("blacklight","models/evolved_player_rig.json"));var reader=new java.io.InputStreamReader(input,java.nio.charset.StandardCharsets.UTF_8)){
            var root=JsonParser.parseReader(reader).getAsJsonObject();if(root.get("schema").getAsInt()!=2)throw new IllegalArgumentException("Evolved mesh schema");
            var vs=root.getAsJsonArray("vertices");vertices=new Vertex[vs.size()];
            for(int i=0;i<vertices.length;i++){var v=vs.get(i).getAsJsonArray();vertices[i]=new Vertex(new Vector3f(v.get(0).getAsFloat(),v.get(1).getAsFloat(),v.get(2).getAsFloat()),v.get(3).getAsInt(),v.get(4).getAsInt(),v.get(5).getAsFloat());}
            var fs=root.getAsJsonArray("faces");faces=new Face[fs.size()];for(int i=0;i<faces.length;i++){var f=fs.get(i).getAsJsonArray();int[] ids=new int[4];float[] uv=new float[8];for(int k=0;k<4;k++)ids[k]=f.get(k).getAsInt();for(int k=0;k<8;k++)uv[k]=f.get(6+k).getAsFloat();faces[i]=new Face(ids,f.get(4).getAsInt(),f.get(5).getAsInt(),uv,f.size()>14?f.get(14).getAsInt():-1);}
            for(var f:faces)if(f.kind==4&&(f.role==2||f.role==3))for(int id:f.ids){int side=f.role-2;if(tips[side]<0||vertices[id].point.y>vertices[tips[side]].point.y)tips[side]=id;}
        }catch(Exception failure){throw new IllegalStateException("Cannot load P2 Evolved player binding",failure);}
    }
    static void reload(){instance=null;}
    static void render(BlacklightInfectedHost host,float partial,PoseStack stack,MultiBufferSource buffers,int light){if(instance==null)instance=new EvolvedBodyRenderer();instance.draw(host,partial,stack,buffers,light);}
    private static ModelPart part(HumanoidModel<?> m,int role){return switch(role){case 1->m.head;case 2->m.rightArm;case 3->m.leftArm;case 4->m.rightLeg;case 5->m.leftLeg;default->m.body;};}
    private void draw(BlacklightInfectedHost host,float partial,PoseStack stack,MultiBufferSource buffers,int light){
        String clip=host.evolvedAction();float time=host.evolvedActionElapsed(partial);boolean loop=false;
        var visual=host.getData(com.codex.blacklight.registry.ModAttachments.CONSUME_VISUAL);
        var hit=host.getData(com.codex.blacklight.registry.ModAttachments.HIT_REACTION);
        if(visual.active()){clip="victim";time=visual.progress(host.level().getGameTime(),partial)*EvolvedMotions.clip(clip).seconds()*20;}
        else if(host.cannibalTargetId()>=0){clip=host.cannibalPrey()?"victim":"consume";time=Math.clamp((host.level().getGameTime()+partial-host.cannibalStarted())/host.cannibalDuration(),0,1)*EvolvedMotions.clip(clip).seconds()*20;}
        else if(host.organActionCore()!=null){clip="consume";time=host.organActionElapsed(partial)/TissueOrganCycle.cycle(host.organActionCore()).end()*EvolvedMotions.clip(clip).seconds()*20;}
        else if(clip.isEmpty()){
            float speed=host.walkAnimation.speed(partial);clip=host.evolvedEngaged()?"guard":"idle";loop=true;
            if(speed>.025F){
                double yaw=Math.toRadians(host.yBodyRot),dx=host.getX()-host.xo,dz=host.getZ()-host.zo;
                double forward=-Math.sin(yaw)*dx+Math.cos(yaw)*dz,left=Math.cos(yaw)*dx+Math.sin(yaw)*dz;
                clip=Math.abs(left)>Math.abs(forward)*1.2?(left>0?"left":"right"):forward<-.015?"back":host.isSprinting()?"sprint":"run";
                if(host.evolvedEngaged()&&!host.isSprinting())clip=switch(clip){case "left"->"guard_left";case "right"->"guard_right";case "back"->"guard_back";default->"guard_forward";};
            }
            time=speed>.025F?host.walkAnimation.position(partial)*EvolvedMotions.clip(clip).seconds()*20/(clip.equals("sprint")?8.5F:6.2F):host.tickCount+partial;
            if(speed<=.025F&&!host.evolvedEngaged()){
                int phase=Math.floorMod(host.level().getGameTime()+host.getUUID().getLeastSignificantBits(),400);
                String gesture=((host.getUUID().getMostSignificantBits()^host.level().getGameTime()/400)&1)==0?"idle_shrug":"idle_neck";
                if(phase<EvolvedMotions.clip(gesture).seconds()*20){clip=gesture;time=phase+partial;loop=false;}
            }
        }
        float clock=host.tickCount+partial;var state=transitions.computeIfAbsent(host,k->new Transition());
        if(!clip.equals(state.clip)){state.clip=clip;state.from=state.last;state.time=clock;}
        var row=EvolvedMotions.sample(clip,time,loop);
        row=com.codex.blacklight.combat.EvolvedRestPose.apply(row,clip,host.tickCount+partial,host.getUUID().getLeastSignificantBits());
        float hitWeight=hit.weight(host.level().getGameTime(),partial);
        if(hitWeight>0&&!visual.active()&&host.cannibalTargetId()<0){
            String reaction=hit.kind()==com.codex.blacklight.combat.HitReaction.STAGGER?"hit":"heavy_hit";
            float at=hit.progress(host.level().getGameTime(),partial)*EvolvedMotions.clip(reaction).seconds()*20;
            row=EvolvedMotions.blend(row,EvolvedMotions.sample(reaction,at,false),hitWeight);
        }
        row=EvolvedMotions.blend(state.from,row,EvolvedForm.smooth((clock-state.time)/4));state.last=row;
        var matrices=EvolvedMotions.skin(row,1);var joints=new RigidSkinning.Joint[matrices.length];for(int i=0;i<joints.length;i++)joints[i]=RigidSkinning.Joint.from(matrices[i]);
        if(posed==null){posed=new Vector3f[vertices.length];for(int i=0;i<posed.length;i++)posed[i]=new Vector3f();}
        var points=posed;var real=new Quaternionf();var dual=new Quaternionf();float foot=Float.NEGATIVE_INFINITY;
        for(int i=0;i<vertices.length;i++){var v=vertices[i];RigidSkinning.transform(joints[v.a],joints[v.b],v.weight,v.point,points[i],real,dual);if(v.point.y==24&&v.point.x>=-3.91F&&v.point.x<=3.91F&&Math.abs(v.point.z)<=2)foot=Math.max(foot,points[i].y);}
        float floor=host.onGround()&&Float.isFinite(foot)?24-foot:0,growth=host.isMetamorphosing()?EvolvedForm.humanBody(host.evolvedProgress(partial)):1,scale=1.12F*host.tissueBodyScale();
        stack.pushPose();float yaw=net.minecraft.util.Mth.rotLerp(partial,host.yBodyRotO,host.yBodyRot);stack.mulPose(Axis.YP.rotationDegrees(180-yaw));stack.scale(growth,growth,growth);stack.translate(0,1.501F*scale,0);stack.scale(-scale,-scale,scale);stack.translate(0,floor/16,0);
        var proxy=host.appearance();HumanoidModel<?> nativeModel=anatomy;
        if(proxy!=null&&Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(proxy) instanceof net.minecraft.client.renderer.entity.LivingEntityRenderer<?,?> renderer&&renderer.getModel() instanceof HumanoidModel<?> human)nativeModel=human;
        for(int role=0;role<6;role++){
            var identity=part(nativeModel,role);identity.resetPose();stack.pushPose();var m=new Matrix4f(matrices[drivers[role]]);m.m30(m.m30()/16).m31(m.m31()/16).m32(m.m32()/16);stack.mulPose(m);identity.translateAndRotate(stack);EvolvedDonorCapture.joint(identity,stack.last());stack.popPose();
        }
        boolean blade=EvolvedForm.blade(host.tissueInheritance(),host.nativeTissueCore(),TissueOrgans.seed(host.getUUID()),host.tissueAge()),grasp=EvolvedForm.grasp(host.tissueInheritance(),host.nativeTissueCore(),host.tissueAge());float deploy=host.evolvedDeployment(partial),graspDeploy=0;
        if(host.organActionCore()==TissueCore.HYDROSTAT){var c=TissueOrganCycle.cycle(TissueCore.HYDROSTAT);float t=host.organActionElapsed(partial);graspDeploy=EvolvedForm.smooth(t/6)*(1-EvolvedForm.smooth((t-c.retract())/(c.end()-c.retract())));}
        else if(host.cannibalTargetId()>=0&&!host.cannibalPrey())graspDeploy=EvolvedForm.smooth((host.level().getGameTime()+partial-host.cannibalStarted())/6);
        VertexConsumer out=null;int material=-1,overlay=OverlayTexture.pack(0,host.hurtTime>0||host.deathTime>0);
        var rightAnchor=matrices[rightWrist].transformPosition(new Vector3f(-5,12,0));var leftAnchor=matrices[leftWrist].transformPosition(new Vector3f(5,12,0));var normal=new Vector3f();var edge=new Vector3f();
        for(var f:faces){
            if(f.kind>=4&&!blade||f.kind==0&&blade&&(f.role==2||f.role==3))continue;
            if(f.kind==1&&!blade||f.kind==2&&(blade||grasp)||f.kind==3&&!grasp)continue;float amount=f.kind==0||f.kind>=4?1:f.kind==3?graspDeploy:deploy;if(f.kind==1&&f.role==3)amount*=1-graspDeploy;if(amount<=.001F)continue;
            var q=quad;var anchor=f.role==2?rightAnchor:leftAnchor;for(int k=0;k<4;k++){q[k].set(points[f.ids[k]]);if(f.kind>0)q[k].lerp(anchor,1-amount);}
            var n=normal.set(q[1]).sub(q[0]).cross(edge.set(q[2]).sub(q[0]));if(n.lengthSquared()<1e-10F)continue;n.normalize();int nextMaterial=f.kind==0?0:f.kind==5?2:1;if(material!=nextMaterial){out=buffers.getBuffer(nextMaterial==2?RenderType.entityTranslucentEmissive(WEAPON_GLOW):RenderType.entityCutoutNoCull(nextMaterial==0?SKIN:WEAPON));material=nextMaterial;}var identity=part(nativeModel,f.role);if(out instanceof ConsumeAnatomy.Consumer sink)sink.anatomy(identity);EvolvedDonorCapture.mesh(identity,f.kind==0?"evolved_player":"evolved_weapon");
            for(int k=0;k<4;k++)out.addVertex(stack.last(),q[k].x/16,q[k].y/16,q[k].z/16).setColor(f.color).setUv(f.uv[k*2],f.uv[k*2+1]).setOverlay(overlay).setLight(nextMaterial==2?LightTexture.FULL_BRIGHT:light).setNormal(stack.last(),n.x,n.y,n.z);
        }
        if(blade)for(int side=0;side<2;side++)if(tips[side]>=0){
            var inner=side==0?rightAnchor:leftAnchor;
            EvolvedCombatFx.capture(host,partial,stack,side,new Vector3f(inner).div(16),new Vector3f(points[tips[side]]).div(16));
        }
        stack.popPose();
    }
    static void cocoon(BlacklightInfectedHost host,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        if(!host.isMetamorphosing())return;float t=host.evolvedProgress(partial),cover=EvolvedForm.cover(t);if(cover<=.001F)return;
        float height=Math.max(2.4F,host.getBbHeight()*EvolvedForm.oldBody(t)),radius=Math.max(1,host.getBbWidth()*.6F*EvolvedForm.oldBody(t));
        var out=buffers.getBuffer(RenderType.entityCutoutNoCull(ConsumedMobVisuals.WHITE));
        for(int band=0;band<5;band++)for(int side=0;side<8;side++){
            var points=new Vector3f[4];for(int k=0;k<4;k++){int row=band+(k>=2?1:0),s=side+(k==1||k==2?1:0);float v=row/5F,a=s*(float)Math.PI/4;
                float wide=(.5F+.5F*(float)Math.sin(Math.PI*v))*(1+.025F*(float)Math.sin(host.tickCount*.2F+side));points[k]=new Vector3f((float)Math.cos(a)*radius*wide*cover,v*height*cover,(float)Math.sin(a)*radius*wide*cover);}
            var normal=new Vector3f(points[1]).sub(points[0]).cross(new Vector3f(points[2]).sub(points[0])).normalize();int color=(side+band)%4==0?0xff67282d:0xff29252a;
            for(var p:points)out.addVertex(pose.last(),p.x,p.y,p.z).setColor(color).setUv(.5F,.5F).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose.last(),normal.x,normal.y,normal.z);
        }
    }
}
