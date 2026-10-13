package com.codex.blacklight.client.renderer;

import com.codex.blacklight.combat.CombatTargeting;
import com.codex.blacklight.combat.DevastatorEffectTimeline;
import com.codex.blacklight.combat.DevastatorVisual;
import com.codex.blacklight.combat.WeaponMove;
import com.codex.blacklight.registry.ModAttachments;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3f;
import org.joml.Matrix4f;

/** Bounded, attached tissue geometry. The authoritative action supplies its effect window.
 * This renderer never selects attacks, spends resources, damages targets or changes the camera. */
@EventBusSubscriber(modid="blacklight", value=Dist.CLIENT)
public final class DevastatorEffectRenderer {
    public enum Style { TENDRIL, GRAVEYARD, PAIN, BULLETDIVE }
    public record Frame(Style style, long session, double elapsedSeconds,
                        double effectStartSeconds, double effectEndSeconds,
                        Vec3 origin, Vec3 direction, int targetId, double radius,
                        List<DevastatorVisual.Contact> contacts,double fallHeight) {
        public Frame {contacts=List.copyOf(contacts);}
        public Frame(Style style,long session,double elapsedSeconds,double effectStartSeconds,double effectEndSeconds,
                     Vec3 origin,Vec3 direction,int targetId,double radius){
            this(style,session,elapsedSeconds,effectStartSeconds,effectEndSeconds,origin,direction,targetId,radius,List.of(),0);
        }
        public Frame(Style style,long session,double elapsedSeconds,double effectStartSeconds,double effectEndSeconds,
                     Vec3 origin,Vec3 direction,int targetId,double radius,List<DevastatorVisual.Contact> contacts){
            this(style,session,elapsedSeconds,effectStartSeconds,effectEndSeconds,origin,direction,targetId,radius,contacts,0);
        }
        public boolean visible() {
            return style!=null && finite(origin) && finite(direction)
                    && Double.isFinite(elapsedSeconds) && Double.isFinite(effectStartSeconds)
                    && Double.isFinite(effectEndSeconds) && Double.isFinite(radius)
                    && effectEndSeconds>effectStartSeconds && elapsedSeconds>=effectStartSeconds
                    && elapsedSeconds<visualEnd() && radius>0;
        }
        public double visualEnd(){return DevastatorEffectTimeline.visibleEnd(style==Style.TENDRIL?1:style==Style.PAIN?2:style==Style.BULLETDIVE?3:0,effectStartSeconds,effectEndSeconds);}
        public double progress() {
            return Math.clamp((elapsedSeconds-effectStartSeconds)/(effectEndSeconds-effectStartSeconds),0,1);
        }
    }
    public record Statistics(long frame, int effects, int branches, int quads, int vertices) { }
    public static final int MAX_VERTICES_PER_FRAME=384_000;
    private static final int SEGMENTS=12, SIDES=6;
    private static final Map<Integer,Vec3[]> HANDS=new HashMap<>();
    private static final Map<Integer,BodyRoots> BODIES=new HashMap<>();
    record BodyRoots(Vec3 chest,Vec3[] chestSkin,Vec3[] torsoSkin,Vec3 rightShoulder,Vec3 leftShoulder,
                     Vec3 rightWrist,Vec3 leftWrist) { }
    private static long renderedFrame;
    private static int renderedEffects,renderedBranches,renderedQuads,renderedVertices;
    private DevastatorEffectRenderer() { }

    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void beforeFrame(RenderFrameEvent.Pre event) {
        renderedFrame++;renderedEffects=renderedBranches=renderedQuads=renderedVertices=0;
        BulletFlightVisualRenderer.beforeFrame(renderedFrame);
    }
    @SubscribeEvent
    public static void resetHands(RenderLevelStageEvent event) {
        if(event.getStage()==RenderLevelStageEvent.Stage.AFTER_SKY){HANDS.clear();BODIES.clear();BulletFlightVisualRenderer.resetBodies();}
    }
    /** Hook after a hand has its native third-person joint transform. No transform is modified. */
    public static void captureHand(AbstractClientPlayer player,PoseStack hand,boolean left) {
        if(StandalonePlayerRendering.nativeHandPass()||VisionPowerRenderer.drawingSilhouettes())return;
        // The supplied joint is Tool_R/L (the wrist), not the elbow. A .30m
        // offset moved the launch back up the forearm in the low source pose.
        var point=BlacklightBoneRendering.worldPoint(hand,new Vector3f(0,-.03F,0));
        if(finite(point))HANDS.computeIfAbsent(player.getId(),ignored->new Vec3[2])[left?1:0]=point;
    }
    /** Capture the same complete pose/frame that skins and clothing actually use.
     * First-person and sensory offscreen passes may not replace world anchors. */
    public static void captureBody(AbstractClientPlayer player,PoseStack skeletonFrame,Matrix4f[] poses){
        if(StandalonePlayerRendering.nativeHandPass()||VisionPowerRenderer.drawingSilhouettes())return;
        BODIES.put(player.getId(),bodyRoots(poses,skeletonFrame.last().pose(),
                Minecraft.getInstance().gameRenderer.getMainCamera().getPosition()));
        BulletFlightVisualRenderer.capture(player,skeletonFrame.last().pose(),poses,
                Minecraft.getInstance().gameRenderer.getMainCamera().getPosition());
    }
    public static Vec3 capturedChest(int entityId){var roots=BODIES.get(entityId);return roots==null?null:roots.chest;}
    static BodyRoots bodyRoots(Matrix4f[] poses,Matrix4f frame,Vec3 offset){
        return new BodyRoots(bonePoint(poses,frame,offset,"Chest",0,.16F,0),
                skinRoots(poses,frame,offset,"Chest",.16F,.23F),skinRoots(poses,frame,offset,"Torso",.13F,.18F),
                bonePoint(poses,frame,offset,"Arm_R",0,.04F,0),bonePoint(poses,frame,offset,"Arm_L",0,.04F,0),
                bonePoint(poses,frame,offset,"Tool_R",0,-.03F,0),bonePoint(poses,frame,offset,"Tool_L",0,-.03F,0));
    }
    private static Vec3[] skinRoots(Matrix4f[] poses,Matrix4f frame,Vec3 offset,String joint,float y,float side){
        return new Vec3[]{bonePoint(poses,frame,offset,joint,0,y,-.132F),bonePoint(poses,frame,offset,joint,0,y,.132F),
                bonePoint(poses,frame,offset,joint,side,y,0),bonePoint(poses,frame,offset,joint,-side,y,0)};
    }
    private static Vec3 bonePoint(Matrix4f[] poses,Matrix4f frame,Vec3 offset,String joint,float x,float y,float z){
        var bone=com.codex.blacklight.animation.BlacklightSkeleton.get().searchJointByName(joint);
        var point=poses[bone.getId()].transformPosition(new Vector3f(x,y,z));
        frame.transformPosition(point);
        return offset.add(point.x,point.y,point.z);
    }
    private static BodyRoots body(LivingEntity owner,float partial){
        var captured=BODIES.get(owner.getId());if(captured!=null)return captured;
        // The local actor has no world model pass in first person. Evaluate its
        // current source pose rather than using the standing collision-box height.
        var poses=com.codex.blacklight.animation.BlacklightSkeleton.get().getPoseMatrices(
                com.codex.blacklight.client.animation.BlacklightRenderPose.sample(owner,partial));
        var frame=new Matrix4f().rotationY((float)Math.toRadians(180-com.codex.blacklight.client.animation.BlacklightRenderPose.facing(owner,partial)))
                .scale(.9375F*owner.getScale());
        captured=bodyRoots(poses,frame,owner.getPosition(partial));BODIES.put(owner.getId(),captured);
        return captured;
    }
    static Vec3 outwardRoot(BodyRoots roots,Vec3 target,int branch){
        int family=branch%10;
        if(family==8)return roots.rightWrist;
        if(family==9)return roots.leftWrist;
        if(family==6||family==7)return roots.rightShoulder.distanceToSqr(target)<roots.leftShoulder.distanceToSqr(target)
                ?roots.rightShoulder:roots.leftShoulder;
        var surfaces=family<3?roots.chestSkin:roots.torsoSkin;
        Vec3 selected=surfaces[0];double distance=selected.distanceToSqr(target);
        for(int i=1;i<surfaces.length;i++)if(surfaces[i].distanceToSqr(target)<distance){selected=surfaces[i];distance=selected.distanceToSqr(target);}
        // Distribute roots over the chest/abdomen band while each exits toward
        // its destination. No shaft needs to cross the center of the body.
        if(family<6){
            int face=0;for(int i=0;i<surfaces.length;i++)if(surfaces[i]==selected){face=i;break;}
            double weight=.12+.10*(branch/10%3);
            return selected.lerp(family<3?roots.torsoSkin[face]:roots.chestSkin[face],weight);
        }
        return selected;
    }
    public static Statistics statistics() {
        return new Statistics(renderedFrame,renderedEffects,renderedBranches,renderedQuads,renderedVertices);
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)return;
        var mc=Minecraft.getInstance();if(mc.level==null)return;
        float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);
        for(var entity:mc.level.entitiesForRendering()) {
            if(!(entity instanceof Player player))continue;
            var visual=player.getData(ModAttachments.DEVASTATOR_VISUAL);
            if(visual.kind()<0||visual.phase()==0||visual.released()<0)continue;
            if(visual.kind()==3&&visual.phase()==2){
                var drawn=BulletFlightVisualRenderer.draw(event,player,MAX_VERTICES_PER_FRAME-renderedVertices);
                if(drawn.vertices()>0){renderedEffects++;renderedVertices+=drawn.vertices();renderedQuads+=drawn.quads();}
                continue;
            }
            var move=player.getData(ModAttachments.WEAPON_VISUAL).spec();
            boolean landing=visual.kind()==3||move==WeaponMove.DEV_AIR_GROUNDSPIKE;
            if(landing&&visual.phase()!=3&&!(visual.kind()==3&&visual.phase()==4)||visual.phase()==2&&(visual.kind()==0||visual.kind()==3))continue;
            Style style=switch(visual.kind()) {
                case 0 -> Style.GRAVEYARD;
                case 1 -> Style.TENDRIL;
                case 2 -> Style.PAIN;
                case 3 -> Style.BULLETDIVE;
                default -> null;
            };
            draw(event,player,new Frame(style,visual.session(),visual.age(player,partial),
                    visual.effectStart(),visual.effectEnd(),visual.origin(),visual.direction(),
                    visual.targetId(),visual.radius(),visual.contacts(),visual.fallHeight()));
        }
    }

    /** Call from the world effect pass for the current synchronized action only.
     * A cancelled/finished action supplies no Frame, so no deferred particle state can leak. */
    public static void draw(RenderLevelStageEvent event,LivingEntity owner,Frame frame) {
        var mc=Minecraft.getInstance();
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS
                ||mc.level==null||owner==null||owner.level()!=mc.level||!owner.isAlive()
                ||frame==null||!frame.visible()||renderedVertices>=MAX_VERTICES_PER_FRAME
                ||VisionPowerRenderer.drawingSilhouettes())return;
        float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);
        var camera=event.getCamera().getPosition();
        if(owner.getPosition(partial).distanceToSqr(camera)>96*96)return;
        var material=LiquidBiomassMaterial.mesh(ConsumedMobVisuals.WHITE);
        var buffers=mc.renderBuffers().bufferSource();var out=buffers.getBuffer(material);
        var stack=event.getPoseStack();stack.pushPose();stack.translate(-camera.x,-camera.y,-camera.z);
        int light=mc.getEntityRenderDispatcher().getPackedLightCoords(owner,partial);
        // Named return clocks govern each tissue family; body recovery has a
        // separate source lease and continues after this pass stops drawing.
        double elapsed=frame.elapsedSeconds()-frame.effectStartSeconds();
        double envelope=DevastatorEffectTimeline.spike(elapsed,0,.16,
                frame.effectEndSeconds()-frame.effectStartSeconds()-.2);
        int alpha=255;
        int verticesBefore=renderedVertices;
        try {
            switch(frame.style()) {
                case TENDRIL -> barrage(stack,out,owner,partial,frame,light,envelope,alpha);
                case GRAVEYARD -> graveyard(stack,out,owner,frame,light,envelope,alpha);
                case PAIN -> pain(stack,out,owner,partial,frame,light,envelope,alpha);
                case BULLETDIVE -> dive(stack,out,owner,partial,frame,light,envelope,alpha);
            }
            if(renderedVertices>verticesBefore)renderedEffects++;
        } finally {
            stack.popPose();buffers.endBatch(material);AuthoredDevastatorMesh.finish(buffers);
        }
    }

    private static void barrage(PoseStack stack,VertexConsumer ignored,LivingEntity owner,float partial,
                                Frame frame,int light,double unused,int alpha) {
        var body=body(owner,partial);Vec3 chest=body.chest;
        double radius=frame.radius(),elapsed=frame.elapsedSeconds()-(frame.effectStartSeconds()-.4);
        int targetCount=Math.min(64,frame.contacts().size());
        // Target order and absolute contact clocks come from the server's one
        // fixed selection. No render-frame query may invent damage recipients.
        // Random paths fill the remaining visual count and never hurt anything.
        for(int branch=0;branch<Math.max(60,targetCount);branch++) {
            double angle=branch*2.3999632297+noise(branch,frame.session())*.8;
            var contact=branch<targetCount?frame.contacts().get(branch):null;
            var entity=contact==null?null:owner.level().getEntity(contact.targetId());
            if(contact!=null&&(!(entity instanceof LivingEntity)||!Float.isFinite(contact.at())))continue;
            Vec3 target=entity instanceof LivingEntity living?
                    living.getBoundingBox().getCenter().add(living.getPosition(partial).subtract(living.position())):
                    chest.add(Math.cos(angle)*radius*(.30+.65*noise(branch+6,frame.session())),
                            (noise(branch+41,frame.session())-.35)*radius*.42,Math.sin(angle)*radius*(.30+.65*noise(branch+6,frame.session())));
            Vec3 root=outwardRoot(body,target,branch);
            double distance=root.distanceTo(target),delay=noise(branch+7,frame.session())*.16;
            double arrival=contact==null?Double.NaN:contact.at()-(frame.effectStartSeconds()-.4);
            var clock=contact==null?new Arrival(.4+delay,30*DevastatorEffectTimeline.WORLD_SCALE):
                    arrival(distance,arrival,.4,30*DevastatorEffectTimeline.WORLD_SCALE);
            double reverseAt=DevastatorEffectTimeline.TENDRIL_RETRACT_START+delay;
            if(contact!=null)reverseAt=Math.max(reverseAt,arrival+DevastatorEffectTimeline.PAIN_CONTACT_DWELL);
            double extent=DevastatorEffectTimeline.strand(elapsed,clock.launch(),reverseAt,distance,
                    clock.speed(),350*DevastatorEffectTimeline.WORLD_SCALE);
            if(extent<.01)continue;
            Vec3 end=root.lerp(target,extent);
            double localAge=Math.max(0,elapsed-clock.launch()),remaining=Math.max(0,reverseAt-elapsed);
            // Source widths are .3-.7 for this family, not the Pain family's 1-2.
            // Its .2/.5 amplitude ramps remain separate from travel/contact time.
            double ramp=smooth(Math.min(1,localAge/.2))*smooth(Math.min(1,(remaining+.12)/.5));
            double frequency=2+noise(branch+19,frame.session())*5;
            double wave=Math.sin(localAge*frequency+branch)*(.15+noise(branch+11,frame.session())*.35)
                    *DevastatorEffectTimeline.WORLD_SCALE*ramp;
            Vec3 direction=end.subtract(root).normalize();
            Vec3 lateral=direction.cross(new Vec3(0,1,0)).normalize();
            if(lateral.lengthSqr()<.1)lateral=new Vec3(Math.sin(angle),0,-Math.cos(angle));
            Vec3 control=root.lerp(end,.44+.08*noise(branch+17,frame.session())).add(lateral.scale(wave)).add(0,wave*.35,0);
            // Slight asynchronous pressure breathing leaves the outer fine
            // strands tensile, and a visible living flow at the attached roots.
            double width=(.3+noise(branch+9,frame.session())*.4)
                    *(1-.09*ramp+.09*ramp*Math.sin(localAge*frequency+branch*.73));
            width=Math.clamp(width,.3,.7);
            nativeStrand(stack,light,"tendril_segment",root,control,end,width,255,angle);
            if(contact!=null&&extent>.97){
                Vec3 tipDirection=end.subtract(control).normalize();
                nativeStrand(stack,light,"tendril_spike",end.subtract(tipDirection.scale(.40)),end.subtract(tipDirection.scale(.20)),end.add(tipDirection.scale(.22)),.9,255,angle);
            }
        }
    }
    private static void pain(PoseStack stack,VertexConsumer ignored,LivingEntity owner,float partial,
                             Frame frame,int light,double unused,int alpha) {
        var target=owner.level().getEntity(frame.targetId());
        var contact=frame.contacts().isEmpty()?null:frame.contacts().getFirst();
        var contactTarget=contact==null?null:owner.level().getEntity(contact.targetId());
        Vec3 eye=owner.getEyePosition(partial),direction=frame.direction().normalize();
        double distance=frame.radius();
        if(target instanceof LivingEntity living){
            Vec3 delta=living.getBoundingBox().getCenter().subtract(eye);double along=delta.dot(direction);
            if(along>0&&delta.subtract(direction.scale(along)).length()<1.25)distance=Math.min(distance,along);
        }
        Vec3 end=eye.add(direction.scale(distance));
        double elapsed=frame.elapsedSeconds()-frame.effectStartSeconds();
        Vec3 across=direction.cross(new Vec3(0,1,0)).normalize();if(across.lengthSqr()<.1)across=new Vec3(1,0,0);
        for(int branch=0;branch<10;branch++){
            Vec3 root=hand(owner,partial,(branch&1)!=0).add(across.scale((branch/2-2)*.10));
            double delay=(branch%3)*.666667;
            Vec3 destination=end;
            boolean fixedContact=branch==0&&contactTarget instanceof LivingEntity&&Float.isFinite(contact.at());
            // The direct lane may already have FX, but this first strand is the
            // controller's actual target. Do not invent its plan before sync.
            if(branch==0&&!fixedContact)continue;
            if(fixedContact)destination=CombatTargeting.multipart((LivingEntity)contactTarget)?
                    CombatTargeting.closestPoint(root,(LivingEntity)contactTarget):contactTarget.getBoundingBox().getCenter();
            double strandDistance=root.distanceTo(destination);
            var move=owner.getData(ModAttachments.WEAPON_VISUAL).spec();
            double sourceWindow=move!=null&&move.aerial()?1.666667:1.333333;
            double forward=Math.max((branch==0?35:30)*.8176232451,strandDistance/sourceWindow);
            // Each burst reaches its real target distance, then returns to its
            // wrist at the recovered native reverse velocity. The brief contact
            // dwell is a presentation adaptation, not a decoded native callback.
            if(fixedContact){
                var clock=arrival(strandDistance,contact.at()-frame.effectStartSeconds(),0,
                        35*DevastatorEffectTimeline.WORLD_SCALE);
                delay=clock.launch();forward=clock.speed();
            }
            double reverseAt=(fixedContact?contact.at()-frame.effectStartSeconds():delay+strandDistance/forward)
                    +DevastatorEffectTimeline.PAIN_CONTACT_DWELL;
            double extent=DevastatorEffectTimeline.strand(elapsed,delay,reverseAt,strandDistance,
                    forward,200*DevastatorEffectTimeline.WORLD_SCALE);
            if(extent<.01)continue;
            Vec3 tip=root.lerp(destination,extent);
            double wave=Math.sin(elapsed*(2+branch%4)+branch)*(.15+branch%3*.175)*.8176232451;
            Vec3 curve=root.lerp(tip,.5).add(across.scale(wave)).add(0,wave*.5,0);
            nativeStrand(stack,light,"tendril_segment",root,curve,tip,new double[]{1,1.5,2,1.6}[branch%4],255,0);
            Vec3 tipDirection=destination.subtract(root).normalize();
            if(extent>.97)nativeStrand(stack,light,"tendril_spike",tip.subtract(tipDirection.scale(.7)),tip.subtract(tipDirection.scale(.35)),tip.add(tipDirection.scale(.3)),2.8,255,0);
        }
    }
    private static void graveyard(PoseStack stack,VertexConsumer ignored,LivingEntity owner,Frame frame,
                                  int light,double unused,int alpha) {
        var level=owner.level();double radius=frame.radius(),elapsed=frame.elapsedSeconds()-frame.effectStartSeconds();
        int targetCount=Math.min(64,frame.contacts().size());
        for(int spike=0;spike<Math.max(54,targetCount);spike++) {
            double angle=spike*2.3999632297+frame.session()*.017;
            double fraction=spike<18?.05+.28*noise(spike+31,frame.session()):.28+.7*noise(spike+31,frame.session());
            var contact=spike<targetCount?frame.contacts().get(spike):null;
            var target=contact==null?null:level.getEntity(contact.targetId());
            if(contact!=null&&(!(target instanceof LivingEntity)||!Float.isFinite(contact.at())))continue;
            Vec3 base=target==null?frame.origin().add(Math.cos(angle)*radius*fraction,0,Math.sin(angle)*radius*fraction):target.position();
            var surface=level.clip(new ClipContext(base.add(0,3,0),base.add(0,-8,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,owner));
            if(surface.getType()!=HitResult.Type.BLOCK||surface.getDirection()!=net.minecraft.core.Direction.UP)continue;
            base=surface.getLocation();double delay=noise(spike+12,frame.session())*.25;
            double end=frame.effectEndSeconds()-frame.effectStartSeconds();
            double rise=.2+.3*noise(spike+13,frame.session());
            if(contact!=null){
                double due=Math.max(0,contact.at()-frame.effectStartSeconds());
                rise=Math.min(rise,due);delay=Math.max(0,due-rise);
            }
            double reverseAt=end-.2-.25+noise(spike+12,frame.session())*.25;
            double growth=rise>1e-8?DevastatorEffectTimeline.spike(elapsed,delay,rise,reverseAt):
                    elapsed>=delay?1-DevastatorEffectTimeline.smooth((elapsed-reverseAt)/.2):0;
            if(growth<.005)continue;
            String size=new String[]{"small","mid","large"}[spike%3];
            nativeSpike(stack,light,"groundspike_"+(spike<9?"cluster_":"")+size,base,(float)angle,growth,1,255);
        }
    }
    private static void dive(PoseStack stack,VertexConsumer ignored,LivingEntity owner,float partial,
                             Frame frame,int light,double envelope,int alpha) {
        int count=BulletImpactMist.draw(stack,frame,MAX_VERTICES_PER_FRAME-renderedVertices);
        renderedVertices+=count;renderedQuads+=count/4;
    }
    private static void nativeStrand(PoseStack stack,int light,String id,Vec3 root,Vec3 control,Vec3 tip,double width,int alpha,double twist){
        if(renderedVertices+AuthoredDevastatorMesh.vertices(id)>MAX_VERTICES_PER_FRAME)return;
        int count=AuthoredDevastatorMesh.strand(stack,Minecraft.getInstance().renderBuffers().bufferSource(),light,id,root,control,tip,width,alpha,twist);
        if(count>0){renderedBranches++;renderedVertices+=count;renderedQuads+=count/4;}
    }
    private record Arrival(double launch,double speed) { }
    /** Use source speed whenever there is enough preparation. Acceleration for
     * a short server contact window is presentation, never client hit authority. */
    private static Arrival arrival(double distance,double contact,double earliest,double sourceSpeed){
        double launch=Math.max(earliest,contact-distance/sourceSpeed);
        double available=Math.max(1e-8,contact-launch);
        return new Arrival(launch,Math.max(sourceSpeed,distance/available));
    }
    private static void nativeSpike(PoseStack stack,int light,String id,Vec3 base,float yaw,double growth,double size,int alpha){
        if(base.distanceToSqr(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition())>12*12)id+="_lod";
        if(renderedVertices+AuthoredDevastatorMesh.vertices(id)>MAX_VERTICES_PER_FRAME)return;
        int count=AuthoredDevastatorMesh.spike(stack,Minecraft.getInstance().renderBuffers().bufferSource(),light,id,base,yaw,growth,size,alpha);
        if(count>0){renderedBranches++;renderedVertices+=count;renderedQuads+=count/4;}
    }
    private static Vec3 hand(LivingEntity owner,float partial,boolean left) {
        Vec3[] hands=HANDS.get(owner.getId());if(hands!=null&&hands[left?1:0]!=null)return hands[left?1:0];
        if(!left) {
            Vec3 grip=WhipTetherRenderer.capturedGrip(owner.getId());if(grip!=null&&finite(grip))return grip;
        }
        var roots=body(owner,partial);return left?roots.leftWrist:roots.rightWrist;
    }
    private static boolean finite(Vec3 point) {
        return point!=null&&Double.isFinite(point.x)&&Double.isFinite(point.y)&&Double.isFinite(point.z);
    }
    private static double smooth(double t) {return t*t*(3-2*t);}
    private static double noise(int id,long session) {return .5+.5*Math.sin(id*12.9898+(session&1023)*.071);}
}
