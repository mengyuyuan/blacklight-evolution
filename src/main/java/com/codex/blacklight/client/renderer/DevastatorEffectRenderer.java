package com.codex.blacklight.client.renderer;

import com.codex.blacklight.combat.CombatTargeting;
import com.codex.blacklight.combat.WeaponMove;
import com.codex.blacklight.registry.ModAttachments;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.Map;
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

/** Bounded, attached tissue geometry. The authoritative action supplies its effect window.
 * This renderer never selects attacks, spends resources, damages targets or changes the camera. */
@EventBusSubscriber(modid="blacklight", value=Dist.CLIENT)
public final class DevastatorEffectRenderer {
    public enum Style { TENDRIL, GRAVEYARD, PAIN, BULLETDIVE }
    public record Frame(Style style, long session, double elapsedSeconds,
                        double effectStartSeconds, double effectEndSeconds,
                        Vec3 origin, Vec3 direction, int targetId, double radius) {
        public boolean visible() {
            return style!=null && finite(origin) && finite(direction)
                    && Double.isFinite(elapsedSeconds) && Double.isFinite(effectStartSeconds)
                    && Double.isFinite(effectEndSeconds) && Double.isFinite(radius)
                    && effectEndSeconds>effectStartSeconds && elapsedSeconds>=effectStartSeconds
                    && elapsedSeconds<effectEndSeconds && radius>0;
        }
        public double progress() {
            return Math.clamp((elapsedSeconds-effectStartSeconds)/(effectEndSeconds-effectStartSeconds),0,1);
        }
    }
    public record Statistics(long frame, int effects, int branches, int quads, int vertices) { }
    public static final int MAX_VERTICES_PER_FRAME=384_000;
    private static final int SEGMENTS=12, SIDES=6;
    private static final Map<Integer,Vec3[]> HANDS=new HashMap<>();
    private static long renderedFrame;
    private static int renderedEffects,renderedBranches,renderedQuads,renderedVertices;
    private DevastatorEffectRenderer() { }

    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void beforeFrame(RenderFrameEvent.Pre event) {
        renderedFrame++;renderedEffects=renderedBranches=renderedQuads=renderedVertices=0;
    }
    @SubscribeEvent
    public static void resetHands(RenderLevelStageEvent event) {
        if(event.getStage()==RenderLevelStageEvent.Stage.AFTER_SKY)HANDS.clear();
    }
    /** Hook after a hand has its native third-person joint transform. No transform is modified. */
    public static void captureHand(AbstractClientPlayer player,PoseStack hand,boolean left) {
        if(StandalonePlayerRendering.nativeHandPass())return;
        var point=BlacklightBoneRendering.worldPoint(hand,new Vector3f(0,.30F,.02F));
        if(finite(point))HANDS.computeIfAbsent(player.getId(),ignored->new Vec3[2])[left?1:0]=point;
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
            var move=player.getData(ModAttachments.WEAPON_VISUAL).spec();
            boolean landing=visual.kind()==3||move==WeaponMove.DEV_AIR_GROUNDSPIKE;
            if(landing&&visual.phase()!=3||visual.phase()==2&&(visual.kind()==0||visual.kind()==3))continue;
            Style style=switch(visual.kind()) {
                case 0 -> Style.GRAVEYARD;
                case 1 -> Style.TENDRIL;
                case 2 -> Style.PAIN;
                case 3 -> Style.BULLETDIVE;
                default -> null;
            };
            draw(event,player,new Frame(style,visual.session(),visual.age(player,partial),
                    visual.effectStart(),visual.effectEnd(),visual.origin(),visual.direction(),
                    visual.targetId(),visual.radius()));
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
        double progress=frame.progress();
        // Source windows govern existence; this is the MC geometry's bounded growth/retraction.
        double growth=smooth(Math.min(1,progress/.24));
        double retract=smooth(Math.clamp((progress-.76)/.24,0,1));
        double envelope=growth*(1-retract);
        int alpha=(int)Math.round(255*Math.min(1,envelope*2));
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
        Vec3 chest=owner.getPosition(partial).add(0,owner.getBbHeight()*.58,0);
        double radius=frame.radius(),elapsed=frame.elapsedSeconds()-frame.effectStartSeconds();
        var targets=owner.level().getEntitiesOfClass(LivingEntity.class,owner.getBoundingBox().inflate(radius),
                t->t!=owner&&t.isAlive()&&!t.isSpectator()&&(owner instanceof Player p?CombatTargeting.damageable(p,t):true));
        targets.sort(java.util.Comparator.comparingDouble(owner::distanceToSqr));
        // True target strands have different launch sites and travel times; the
        // remaining source count fills irregular random paths around the actor.
        for(int branch=0;branch<60;branch++) {
            double angle=branch*2.3999632297+noise(branch,frame.session())*.8;
            Vec3 root=branch%4==0?hand(owner,partial,(branch&4)!=0):chest.add(Math.cos(angle)*.25,(noise(branch+5,frame.session())-.5)*.55,Math.sin(angle)*.25);
            Vec3 target=branch<Math.min(50,targets.size())?targets.get(branch).getBoundingBox().getCenter():
                    chest.add(Math.cos(angle)*radius*(.30+.65*noise(branch+6,frame.session())),
                            (noise(branch+41,frame.session())-.35)*radius*.42,Math.sin(angle)*radius*(.30+.65*noise(branch+6,frame.session())));
            double distance=root.distanceTo(target),delay=noise(branch+7,frame.session())*.16;
            double grown=Math.clamp((elapsed-delay)*30*.8176232451/Math.max(.01,distance),0,1);
            double remaining=frame.effectEndSeconds()-frame.elapsedSeconds();
            double retract=Math.clamp(remaining*350*.8176232451/Math.max(.01,distance),0,1);
            double extent=Math.min(grown,retract);if(extent<.01)continue;
            Vec3 end=root.lerp(target,extent);
            double wave=Math.sin(elapsed*(2+noise(branch+19,frame.session())*5)+branch)*(.15+noise(branch+11,frame.session())*.35)*.8176232451;
            Vec3 control=root.lerp(end,.46).add(Math.sin(angle)*wave,.5*wave,-Math.cos(angle)*wave);
            nativeStrand(stack,light,"tendril_segment",root,control,end,.7+noise(branch+9,frame.session())*1.9,255,angle);
            if(branch<targets.size()&&extent>.97){
                Vec3 direction=end.subtract(control).normalize();
                nativeStrand(stack,light,"tendril_spike",end.subtract(direction.scale(.40)),end.subtract(direction.scale(.20)),end.add(direction.scale(.22)),2.2,255,angle);
            }
        }
    }
    private static void pain(PoseStack stack,VertexConsumer ignored,LivingEntity owner,float partial,
                             Frame frame,int light,double unused,int alpha) {
        var target=owner.level().getEntity(frame.targetId());
        Vec3 eye=owner.getEyePosition(partial),direction=frame.direction().normalize();
        double distance=frame.radius();
        if(target instanceof LivingEntity living&&living.isAlive()){
            Vec3 delta=living.getBoundingBox().getCenter().subtract(eye);double along=delta.dot(direction);
            if(along>0&&delta.subtract(direction.scale(along)).length()<1.25)distance=Math.min(distance,along);
        }
        Vec3 end=eye.add(direction.scale(distance));
        double elapsed=frame.elapsedSeconds()-frame.effectStartSeconds();
        Vec3 across=direction.cross(new Vec3(0,1,0)).normalize();if(across.lengthSqr()<.1)across=new Vec3(1,0,0);
        for(int branch=0;branch<10;branch++){
            Vec3 root=hand(owner,partial,(branch&1)!=0).add(across.scale((branch/2-2)*.10));
            double delay=(branch%3)*.666667;
            var move=owner.getData(ModAttachments.WEAPON_VISUAL).spec();
            double sourceWindow=move!=null&&move.aerial()?1.666667:1.333333;
            double forward=Math.max((branch==0?35:30)*.8176232451,distance/sourceWindow);
            double grown=Math.clamp((elapsed-delay)*forward/Math.max(.01,distance),0,1);
            double retract=Math.clamp((frame.effectEndSeconds()-frame.elapsedSeconds())*200*.8176232451/Math.max(.01,distance),0,1);
            double extent=Math.min(grown,retract);if(extent<.01)continue;
            Vec3 tip=root.lerp(end,extent);
            double wave=Math.sin(elapsed*(2+branch%4)+branch)*(.15+branch%3*.175)*.8176232451;
            Vec3 curve=root.lerp(tip,.5).add(across.scale(wave)).add(0,wave*.5,0);
            nativeStrand(stack,light,"tendril_segment",root,curve,tip,new double[]{1,1.5,2,1.6}[branch%4],255,0);
            if(extent>.97)nativeStrand(stack,light,"tendril_spike",tip.subtract(direction.scale(.7)),tip.subtract(direction.scale(.35)),tip.add(direction.scale(.3)),2.8,255,0);
        }
    }
    private static void graveyard(PoseStack stack,VertexConsumer ignored,LivingEntity owner,Frame frame,
                                  int light,double unused,int alpha) {
        var level=owner.level();double radius=frame.radius(),elapsed=frame.elapsedSeconds()-frame.effectStartSeconds();
        var targets=level.getEntitiesOfClass(LivingEntity.class,owner.getBoundingBox().inflate(radius),t->t!=owner&&t.isAlive());
        targets.sort(java.util.Comparator.comparingDouble(owner::distanceToSqr));
        for(int spike=0;spike<54;spike++) {
            double angle=spike*2.3999632297+frame.session()*.017;
            double fraction=spike<18?.05+.28*noise(spike+31,frame.session()):.28+.7*noise(spike+31,frame.session());
            Vec3 base=spike<Math.min(40,targets.size())?targets.get(spike).position():frame.origin().add(Math.cos(angle)*radius*fraction,0,Math.sin(angle)*radius*fraction);
            var surface=level.clip(new ClipContext(base.add(0,3,0),base.add(0,-8,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,owner));
            if(surface.getType()!=HitResult.Type.BLOCK||surface.getDirection()!=net.minecraft.core.Direction.UP)continue;
            base=surface.getLocation();double delay=noise(spike+12,frame.session())*.25;
            double growth=smooth(Math.clamp((elapsed-delay)/(.2+.3*noise(spike+13,frame.session())),0,1));
            double descend=smooth(Math.clamp((frame.effectEndSeconds()-frame.elapsedSeconds()-delay)/.2,0,1));
            growth=Math.min(growth,descend);if(growth<.005)continue;
            String size=new String[]{"small","mid","large"}[spike%3];
            nativeSpike(stack,light,"groundspike_"+(spike<9?"cluster_":"")+size,base,(float)angle,growth,1,255);
        }
    }
    private static void dive(PoseStack stack,VertexConsumer ignored,LivingEntity owner,float partial,
                             Frame frame,int light,double envelope,int alpha) {
        // Landing energy also controls this impulse's scale. Terrain sends real
        // block fragments; sculpted clusters provide a short ground-level tissue burst.
        for(int branch=0;branch<6;branch++){
            double angle=branch*Math.PI/3+frame.session()*.01;
            Vec3 base=frame.origin().add(Math.cos(angle)*frame.radius()*.20,0,Math.sin(angle)*frame.radius()*.20);
            nativeSpike(stack,light,"groundspike_cluster_small",base,(float)angle,envelope,.28+frame.radius()*.04,alpha);
        }
    }
    private static void nativeStrand(PoseStack stack,int light,String id,Vec3 root,Vec3 control,Vec3 tip,double width,int alpha,double twist){
        if(renderedVertices+AuthoredDevastatorMesh.vertices(id)>MAX_VERTICES_PER_FRAME)return;
        int count=AuthoredDevastatorMesh.strand(stack,Minecraft.getInstance().renderBuffers().bufferSource(),light,id,root,control,tip,width,alpha,twist);
        if(count>0){renderedBranches++;renderedVertices+=count;renderedQuads+=count/4;}
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
        double yaw=Math.toRadians(owner.yBodyRot);Vec3 side=new Vec3(Math.cos(yaw),0,Math.sin(yaw));
        return owner.getPosition(partial).add(side.scale(left?.38:-.38)).add(0,owner.getBbHeight()*.58,0);
    }
    private static boolean finite(Vec3 point) {
        return point!=null&&Double.isFinite(point.x)&&Double.isFinite(point.y)&&Double.isFinite(point.z);
    }
    private static double smooth(double t) {return t*t*(3-2*t);}
    private static double noise(int id,long session) {return .5+.5*Math.sin(id*12.9898+(session&1023)*.071);}
}
