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
    private static final ResourceLocation SKIN=ResourceLocation.fromNamespaceAndPath("blacklight","textures/entity/evolved_variant_body.png");
    private static final ResourceLocation WEAPON=ResourceLocation.fromNamespaceAndPath("blacklight","textures/entity/evolved_arms.png");
    private static final ResourceLocation WEAPON_GLOW=ResourceLocation.fromNamespaceAndPath("blacklight","textures/entity/evolved_arms_emissive.png");
    private static final ResourceLocation VARIANT_WEAPON=ResourceLocation.fromNamespaceAndPath("blacklight","textures/entity/evolved_variant_arms.png");
    private static final ResourceLocation VARIANT_GLOW=ResourceLocation.fromNamespaceAndPath("blacklight","textures/entity/evolved_variant_arms_emissive.png");
    private static final ResourceLocation PLAYER_WEAPON=ResourceLocation.fromNamespaceAndPath("blacklight","textures/entity/weapons_mc.png");
    private static final ResourceLocation PLAYER_WEAPON_GLOW=ResourceLocation.fromNamespaceAndPath("blacklight","textures/entity/weapons_mc_emissive.png");
    private static EvolvedBodyRenderer instance;
    private static final java.util.Map<EvolvedArchetype,EvolvedBodyRenderer> INSTANCES=new java.util.EnumMap<>(EvolvedArchetype.class);
    private final EvolvedArchetype type;
    private final PlayerModel<BlacklightInfectedHost> anatomy;
    private final Vertex[] vertices;private final Face[] faces;private final int[] drivers;
    private final int rightWrist=EvolvedMotions.index("Wrist_R"),leftWrist=EvolvedMotions.index("Wrist_L");
    private final Vector3f[] quad={new Vector3f(),new Vector3f(),new Vector3f(),new Vector3f()};
    private Vector3f[] posed;
    private final int[] tips={-1,-1};
    private static final class Transition {String clip;float time;float[] from,last;}
    private final java.util.Map<BlacklightInfectedHost,Transition> transitions=new java.util.WeakHashMap<>();
    private EvolvedBodyRenderer(EvolvedArchetype type){
        this.type=type;
        anatomy=new PlayerModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER),false);
        drivers=new int[]{EvolvedMotions.index("Spine_2"),EvolvedMotions.index("Head"),EvolvedMotions.index("Shoulder_R"),EvolvedMotions.index("Shoulder_L"),EvolvedMotions.index("Hip_R"),EvolvedMotions.index("Hip_L")};
        String mesh="models/evolved_variants/"+type.key()+".json";
        try(var input=Minecraft.getInstance().getResourceManager().open(ResourceLocation.fromNamespaceAndPath("blacklight",mesh));var reader=new java.io.InputStreamReader(input,java.nio.charset.StandardCharsets.UTF_8)){
            var root=JsonParser.parseReader(reader).getAsJsonObject();if(root.get("schema").getAsInt()!=2)throw new IllegalArgumentException("Evolved mesh schema");
            var vs=root.getAsJsonArray("vertices");vertices=new Vertex[vs.size()];
            for(int i=0;i<vertices.length;i++){var v=vs.get(i).getAsJsonArray();vertices[i]=new Vertex(new Vector3f(v.get(0).getAsFloat(),v.get(1).getAsFloat(),v.get(2).getAsFloat()),v.get(3).getAsInt(),v.get(4).getAsInt(),v.get(5).getAsFloat());}
            var fs=root.getAsJsonArray("faces");faces=new Face[fs.size()];for(int i=0;i<faces.length;i++){var f=fs.get(i).getAsJsonArray();int[] ids=new int[4];float[] uv=new float[8];for(int k=0;k<4;k++)ids[k]=f.get(k).getAsInt();for(int k=0;k<8;k++)uv[k]=f.get(6+k).getAsFloat();faces[i]=new Face(ids,f.get(4).getAsInt(),f.get(5).getAsInt(),uv,f.size()>14?f.get(14).getAsInt():-1);}
            for(var f:faces)if((f.kind==4||f.kind==6||f.kind==8)&&(f.role==2||f.role==3))for(int id:f.ids){int side=f.role-2;if(tips[side]<0||vertices[id].point.y>vertices[tips[side]].point.y)tips[side]=id;}
        }catch(Exception failure){throw new IllegalStateException("Cannot load P2 Evolved player binding",failure);}
    }
    static void reload(){instance=null;INSTANCES.clear();}
    static void render(BlacklightInfectedHost host,float partial,PoseStack stack,MultiBufferSource buffers,int light){
        instance=INSTANCES.computeIfAbsent(host.evolvedArchetype(),EvolvedBodyRenderer::new);
        var visual=host.getData(com.codex.blacklight.registry.ModAttachments.CONSUME_VISUAL);
        if(!visual.active()){instance.draw(host,partial,stack,buffers,light);return;}
        if(visual.consuming()&&visual.progress(host.level().getGameTime(),partial)>=.995F)return;
        var placement=com.codex.blacklight.client.ClientCarryPlacement.consumeOffset(host,partial);
        stack.pushPose();stack.translate(placement.x,placement.y,placement.z);
        try{
            var fracture=ConsumeFracture.visible(host,partial)?new ConsumeFracture(host,partial,stack,buffers,ConsumeAbsorptionFx.destination(host,partial,placement)):null;
            instance.draw(host,partial,stack,fracture==null?buffers:fracture,light);
            if(fracture!=null)fracture.finish();
        }finally{stack.popPose();}
    }
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
        var special=EvolvedSpecialMove.resolve(clip);
        var row=special==null?EvolvedMotions.sample(clip,time,loop):com.codex.blacklight.combat.EvolvedVariantPose.special(special,time);
        row=com.codex.blacklight.combat.EvolvedRestPose.apply(row,clip,host.tickCount+partial,host.getUUID().getLeastSignificantBits());
        row=com.codex.blacklight.combat.EvolvedVariantPose.stance(row,type,type==EvolvedArchetype.ARMORED&&host.evolvedShield()==0&&clip.equals("guard")?"idle":clip);
        float hitWeight=hit.weight(host.level().getGameTime(),partial);
        if(hitWeight>0&&!visual.active()&&host.cannibalTargetId()<0){
            String reaction=hit.kind()==com.codex.blacklight.combat.HitReaction.STAGGER?"hit":"heavy_hit";
            float at=hit.progress(host.level().getGameTime(),partial)*EvolvedMotions.clip(reaction).seconds()*20;
            row=EvolvedMotions.blend(row,EvolvedMotions.sample(reaction,at,false),hitWeight);
        }
        row=EvolvedMotions.blend(state.from,row,EvolvedForm.smooth((clock-state.time)/4));state.last=row;
        var receiver=visual.active()?com.codex.blacklight.client.animation.EvolvedConsumePose.skin(host,partial):null;
        var matrices=receiver==null?EvolvedMotions.skin(row,1):receiver;var joints=new RigidSkinning.Joint[matrices.length];for(int i=0;i<joints.length;i++)joints[i]=RigidSkinning.Joint.from(matrices[i]);
        if(posed==null){posed=new Vector3f[vertices.length];for(int i=0;i<posed.length;i++)posed[i]=new Vector3f();}
        var points=posed;var real=new Quaternionf();var dual=new Quaternionf();float foot=Float.NEGATIVE_INFINITY;
        for(int i=0;i<vertices.length;i++){var v=vertices[i];RigidSkinning.transform(joints[v.a],joints[v.b],v.weight,v.point,points[i],real,dual);if(v.point.y==24&&v.point.x>=-3.91F&&v.point.x<=3.91F&&Math.abs(v.point.z)<=2)foot=Math.max(foot,points[i].y);}
        float floor=!visual.active()&&host.onGround()&&Float.isFinite(foot)?24-foot:0,growth=host.isMetamorphosing()?EvolvedForm.humanBody(host.evolvedProgress(partial)):1,scale=1.12F*host.tissueBodyScale();
        stack.pushPose();float yaw=net.minecraft.util.Mth.rotLerp(partial,host.yBodyRotO,host.yBodyRot);stack.mulPose(Axis.YP.rotationDegrees(180-yaw));stack.scale(growth,growth,growth);stack.translate(0,1.501F*scale,0);stack.scale(-scale,-scale,scale);stack.translate(0,floor/16,0);
        var proxy=host.appearance();HumanoidModel<?> nativeModel=anatomy;
        if(proxy!=null&&Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(proxy) instanceof net.minecraft.client.renderer.entity.LivingEntityRenderer<?,?> renderer&&renderer.getModel() instanceof HumanoidModel<?> human)nativeModel=human;
        for(int role=0;role<6;role++){
            var identity=part(nativeModel,role);identity.resetPose();stack.pushPose();var m=new Matrix4f(matrices[drivers[role]]);m.m30(m.m30()/16).m31(m.m31()/16).m32(m.m32()/16);stack.mulPose(m);identity.translateAndRotate(stack);EvolvedDonorCapture.joint(identity,stack.last());stack.popPose();
        }
        boolean blade=type!=EvolvedArchetype.BLADE||EvolvedForm.blade(host.tissueInheritance(),host.nativeTissueCore(),TissueOrgans.seed(host.getUUID()),host.tissueAge()),grasp=EvolvedForm.grasp(host.tissueInheritance(),host.nativeTissueCore(),host.tissueAge());float deploy=host.evolvedDeployment(partial),graspDeploy=0;
        if(host.organActionCore()==TissueCore.HYDROSTAT){var c=TissueOrganCycle.cycle(TissueCore.HYDROSTAT);float t=host.organActionElapsed(partial);graspDeploy=EvolvedForm.smooth(t/6)*(1-EvolvedForm.smooth((t-c.retract())/(c.end()-c.retract())));}
        else if(host.cannibalTargetId()>=0&&!host.cannibalPrey())graspDeploy=EvolvedForm.smooth((host.level().getGameTime()+partial-host.cannibalStarted())/6);
        boolean runnerArms=type==EvolvedArchetype.BLADE||type==EvolvedArchetype.SKIRMISHER||type==EvolvedArchetype.ARMORED;
        VertexConsumer out=null;int material=-1,overlay=OverlayTexture.pack(0,host.hurtTime>0||host.deathTime>0);
        var rightAnchor=matrices[rightWrist].transformPosition(new Vector3f(-5,12,0));var leftAnchor=matrices[leftWrist].transformPosition(new Vector3f(5,12,0));var normal=new Vector3f();var edge=new Vector3f();
        float beat=TissueShading.heartbeat(host.level().getGameTime()+partial,(host.getUUID().getLeastSignificantBits()&1023)/1023F);int faceIndex=0;
        for(var f:faces){faceIndex++;
            if(f.kind>=4&&!blade||f.kind==0&&blade&&(f.role==2||f.role==3)&&(type==EvolvedArchetype.BLADE||type==EvolvedArchetype.SKIRMISHER||type==EvolvedArchetype.ARMORED&&f.role==2||(vertices[f.ids[0]].point.y+vertices[f.ids[1]].point.y+vertices[f.ids[2]].point.y+vertices[f.ids[3]].point.y)*.25F>6.5F))continue;
            if(f.kind==1&&!blade||f.kind==2&&(blade||grasp)||f.kind==3&&!grasp)continue;float amount=f.kind==0||f.kind>=4?1:f.kind==3?graspDeploy:deploy;if(f.kind==1&&f.role==3)amount*=1-graspDeploy;if(amount<=.001F)continue;
            var q=quad;var anchor=f.role==2?rightAnchor:leftAnchor;for(int k=0;k<4;k++){q[k].set(points[f.ids[k]]);if(f.kind>0)q[k].lerp(anchor,1-amount);}
            var n=normal.set(q[1]).sub(q[0]).cross(edge.set(q[2]).sub(q[0]));if(n.lengthSquared()<1e-10F)continue;n.normalize();int nextMaterial=f.kind==0?0:f.kind==5?2:f.kind==6?3:f.kind==7?4:f.kind==8?5:1;if(material!=nextMaterial){out=buffers.getBuffer(nextMaterial==2||nextMaterial==4?RenderType.entityTranslucentEmissive(nextMaterial==4?PLAYER_WEAPON_GLOW:runnerArms?WEAPON_GLOW:VARIANT_GLOW):RenderType.entityCutoutNoCull(nextMaterial==0?SKIN:nextMaterial==3?PLAYER_WEAPON:nextMaterial==5?ConsumedMobVisuals.WHITE:runnerArms?WEAPON:VARIANT_WEAPON));material=nextMaterial;}var identity=part(nativeModel,f.role);if(out instanceof ConsumeAnatomy.Consumer sink)sink.anatomy(f.role);EvolvedDonorCapture.mesh(identity,f.kind==0?"evolved_player":"evolved_weapon");
            boolean emissive=nextMaterial==2||nextMaterial==4;float jitter=((faceIndex*2654435761L)>>>16&255)/255F;for(int k=0;k<4;k++)out.addVertex(stack.last(),q[k].x/16,q[k].y/16,q[k].z/16).setColor(emissive?f.color:TissueShading.modulate(f.color,f.kind!=0,n.y,(k&1)==0?.35F:1,jitter,beat)).setUv(f.uv[k*2],f.uv[k*2+1]).setOverlay(overlay).setLight(nextMaterial==2||nextMaterial==4?LightTexture.FULL_BRIGHT:light).setNormal(stack.last(),n.x,n.y,n.z);
        }
        if(blade)for(int side=0;side<2;side++)if(tips[side]>=0){
            var inner=side==0?rightAnchor:leftAnchor;
            EvolvedCombatFx.capture(host,partial,stack,side,new Vector3f(inner).div(16),new Vector3f(points[tips[side]]).div(16));
        }
        EvolvedVariantFx.capture(host,partial,stack,new Vector3f(rightAnchor).div(16),new Vector3f(leftAnchor).div(16));
        stack.popPose();
    }
    static void cocoon(BlacklightInfectedHost host,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        if(!host.isMetamorphosing())return;float t=host.evolvedProgress(partial),cover=EvolvedForm.cover(t);if(cover<=.001F)return;
        float height=Math.max(2.4F,host.getBbHeight()*EvolvedForm.oldBody(t)),radius=Math.max(1,host.getBbWidth()*.6F*EvolvedForm.oldBody(t));
        var out=buffers.getBuffer(RenderType.entityCutoutNoCull(ConsumedMobVisuals.WHITE));
        // MC-style cocoon: stacked square layers stepped in whole pixels, not a
        // smooth octagonal egg. The middle layers stay widest; layers pulse in turn.
        float[] widths={.62F,.88F,1,.94F,.72F,.44F};int layers=widths.length;float pixel=1/16F;
        for(int layer=0;layer<layers;layer++){
            float pulse=1+.035F*(float)Math.sin(host.tickCount*.2F+layer*1.3F);
            float half=Math.max(pixel,Math.round(radius*widths[layer]*pulse*cover/pixel)*pixel);
            float y0=layer*height*cover/layers,y1=(layer+1)*height*cover/layers;
            int color=layer%2==0?0xff29252a:0xff3a2128,seam=0xff8a2230;
            cocoonBox(out,pose,-half,y0,-half,half,y1,half,color,light);
            // A thin blood seam between layers keeps the stacked read.
            if(layer>0)cocoonBox(out,pose,-half-pixel*.5F,y0-pixel,-half-pixel*.5F,half+pixel*.5F,y0+pixel,half+pixel*.5F,seam,light);
        }
    }
    private static void cocoonBox(VertexConsumer out,PoseStack pose,float x0,float y0,float z0,float x1,float y1,float z1,int color,int light){
        float[][][] faces={
            {{x0,y0,z0},{x1,y0,z0},{x1,y0,z1},{x0,y0,z1},{0,-1,0}},{{x0,y1,z1},{x1,y1,z1},{x1,y1,z0},{x0,y1,z0},{0,1,0}},
            {{x0,y0,z0},{x0,y1,z0},{x1,y1,z0},{x1,y0,z0},{0,0,-1}},{{x1,y0,z1},{x1,y1,z1},{x0,y1,z1},{x0,y0,z1},{0,0,1}},
            {{x0,y0,z1},{x0,y1,z1},{x0,y1,z0},{x0,y0,z0},{-1,0,0}},{{x1,y0,z0},{x1,y1,z0},{x1,y1,z1},{x1,y0,z1},{1,0,0}}};
        for(var f:faces){var n=f[4];for(int k=0;k<4;k++)out.addVertex(pose.last(),f[k][0],f[k][1],f[k][2]).setColor(color).setUv(.5F,.5F)
                .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose.last(),n[0],n[1],n[2]);}
    }
}
