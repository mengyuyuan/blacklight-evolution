package com.codex.blacklight.client.renderer;

import com.codex.blacklight.client.ShaderPackCompat;
import com.codex.blacklight.client.camera.CameraCompatibility;
import com.codex.blacklight.client.camera.HydraCinematicCamera;
import com.codex.blacklight.combat.ConsumeVisual;
import com.codex.blacklight.combat.ConsumeGroundSurface;
import com.codex.blacklight.combat.ConsumeWound;
import com.codex.blacklight.combat.FleshStrike;
import com.codex.blacklight.combat.PlayerHitEffects;
import com.codex.blacklight.config.PassiveHudConfig;
import com.codex.blacklight.infection.InfectedAttackStyle;
import com.codex.blacklight.registry.ModWeaponSounds;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;

/** One world-space emitter per accepted contact. Landing dust belongs to the touched block. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class ConsumeStrikeEffects {
    private record Burst(Vec3 point, Vec3 axis, SprayEmitterPath path, FleshStrike strike, float size,
                         boolean rupture, boolean metal, long seed, double born) {}
    private record Ground(Vec3 point, int color, float size, long seed, double born, int victim, long scene) {}
    private static final List<Burst> bursts=new ArrayList<>();
    private static final List<Ground> landings=new ArrayList<>();
    private static ClientLevel level;
    private static int kickOwner=-1;
    private static double kickTime, kickPitch, kickRoll;

    private ConsumeStrikeEffects() {}
    private static void world(ClientLevel current) {
        if(current!=level){level=current;bursts.clear();landings.clear();kickOwner=-1;}
    }
    private static double now(ClientLevel level) {
        return (double)level.getGameTime()+Minecraft.getInstance().getFrameTime();
    }
    private static long seed(int victim,long started,int ordinal) {
        return started*1000003L+victim*179L+ordinal*31L;
    }
    public static boolean landing(ClientLevel current,Vec3 at,int victim,long started) {
        world(current);
        return ground(at,ConsumeGroundSurface.Load.BODY,victim,started,seed(victim,started,-2));
    }
    private static boolean ground(Vec3 at,ConsumeGroundSurface.Load load,int victim,long scene,long seed) {
        double time=now(level);
        for(var other:landings){
            if(other.victim==victim&&other.scene==scene&&time-other.born>=0&&time-other.born<1.35
                    &&other.point.distanceToSqr(at)<.66)return false;
        }
        var surface=ConsumeGroundSurface.sample(level,at,load);
        if(surface==null)return false;
        at=surface.contact();
        var source=surface.tiles().get(0);var pos=source.pos();var state=source.state();
        TerrainRipple.consume(level,surface,load,seed,time);
        float size=load==ConsumeGroundSurface.Load.BODY?.88F:load==ConsumeGroundSurface.Load.MAUL?1.44F:1.22F;
        int color=state.getMapColor(level,pos).col;
        if(color==0)color=0x8b8176;
        if(landings.size()>=32)landings.remove(0);
        landings.add(new Ground(at,color,size,seed,time,victim,scene));
        var debris=new BlockParticleOption(ParticleTypes.BLOCK,state);
        var random=net.minecraft.util.RandomSource.create(seed);
        for(int i=0;i<(load==ConsumeGroundSurface.Load.BODY?6:load==ConsumeGroundSurface.Load.MAUL?15:11);i++){
            double angle=random.nextDouble()*Math.PI*2, speed=(.035+.09*random.nextDouble())*size;
            level.addParticle(debris,at.x,at.y+.045,at.z,Math.cos(angle)*speed,.04+random.nextDouble()*.09,Math.sin(angle)*speed);
        }
        return true;
    }
    static void hit(ClientLevel current,LivingEntity victim,ConsumeVisual scene,int ordinal,String cue,
                    Vec3 point,boolean head,boolean intercepted) {
        world(current);
        var owner=current.getEntity(scene.consumerId());
        if(!(owner instanceof LivingEntity actor) || victim.isInvisible())return;
        var wound=ConsumeWound.forCue(cue);
        var front=Vec3.directionFromRotation(0,scene.facing());
        var side=new Vec3(-front.z,0,front.x);
        double hand=(ordinal%2==0?1:-1);
        Vec3 axis=switch(cue) {
            case "blade_split","claw_shear","mount_blade","mount_claw","beat_hook","throat_rip" -> side.scale(hand).add(front.scale(.36)).add(0,.24,0);
            case "hook_rend","lash_recoil","arm_wrench","heart_grab","mount_cable","hunter_rend" -> front.scale(-.82).add(side.scale(.52)).add(0,.39,0);
            case "knee_bump","mount_heave" -> front.scale(.48).add(0,1,0);
            case "heel_stamp","head_drop","hammer_press","beat_crush","mount_maul" -> side.scale(hand*.65).add(front.scale(.32)).add(0,.36,0);
            default -> front.add(side.scale(.34*hand)).add(0,.19,0);
        };
        axis=axis.normalize();
        boolean headBurst=head && (wound==ConsumeWound.HEAD_CRUSH || wound==ConsumeWound.FINISHING_CRUSH);
        boolean metal=PlayerHitEffects.metalAt(victim,point);
        boolean flesh=PlayerHitEffects.fleshBody(victim);
        long random=seed(victim.getId(),scene.started(),ordinal);
        boolean quietGrip=wound==ConsumeWound.COMPRESSION || cue.equals("neck_snap");
        if((metal || flesh) && !quietGrip){
            FleshStrike strike=switch(wound) {
                case CUT -> cue.contains("claw")?FleshStrike.CLAW:FleshStrike.EDGE;
                case LACERATION -> FleshStrike.LASH;
                case HEAD_CRUSH,FINISHING_CRUSH -> headBurst?FleshStrike.HAMMER:FleshStrike.FIST;
                case PUNCTURE -> FleshStrike.QUILL;
                default -> FleshStrike.FIST;
            };
            float size=intercepted?.58F:headBurst?1.48F:wound==ConsumeWound.JAB?.63F:
                    wound==ConsumeWound.LACERATION?1.24F:.95F;
            if(bursts.size()>=64)bursts.remove(0);
            bursts.add(new Burst(point,axis,new SprayEmitterPath(point,axis,false),strike,size,headBurst,metal,random,now(current)));
        }
        // Only a blow that also reaches a supporting surface disturbs it.
        if(cue.equals("heel_stamp") || cue.equals("beat_crush") || cue.equals("head_drop") || cue.equals("hammer_press")){
            boolean maul=cue.equals("head_drop")||cue.equals("hammer_press");
            // Follow the body thickness to its contact patch, without projecting an upright or airborne hit onto the floor.
            double reach=Math.min(.45,Math.max(.21,victim.getBbWidth()*.52+.06));
            var floor=current.clip(new ClipContext(point.add(0,.045,0),point.add(0,-reach,0),
                    ClipContext.Block.COLLIDER,ClipContext.Fluid.ANY,victim));
            if(floor.getType()==HitResult.Type.BLOCK && floor.getDirection()==Direction.UP){
                var load=maul?ConsumeGroundSurface.Load.MAUL:ConsumeGroundSurface.Load.STAMP;
                var at=floor.getLocation();
                if(ground(at,load,victim.getId(),scene.started(),random)){
                    var sound=ModWeaponSounds.event("consume.body_land");
                    if(sound!=null)ConsumeFoleyPlayback.play(current,sound,ConsumeContactAudio.listenerPoint(actor.getId(),at),at,
                            maul?1.28F:1.12F,maul?.79F:.89F);
                }
            }
        }
        var mc=Minecraft.getInstance();
        if(mc.player==actor && mc.getCameraEntity()==actor){
            kickOwner=actor.getId();kickTime=now(current);
            kickPitch=quietGrip?.32:headBurst?2.55:cue.equals("knee_bump")?-1.58:1.12;
            kickRoll=hand*(wound==ConsumeWound.CUT||wound==ConsumeWound.LACERATION?1.55:quietGrip?.18:.68);
        }
    }
    @SubscribeEvent public static void camera(ViewportEvent.ComputeCameraAngles event){
        var mc=Minecraft.getInstance();
        if(mc.level!=level || level==null || mc.player==null || mc.player.getId()!=kickOwner ||
                event.getCamera().getEntity()!=mc.player || !mc.options.getCameraType().isFirstPerson() ||
                CameraCompatibility.externalOwnsView() || HydraCinematicCamera.active() || !PassiveHudConfig.CINEMATIC_COMBAT.get())return;
        double age=(double)level.getGameTime()+event.getPartialTick()-kickTime;
        if(age<0 || age>=4)return;
        double envelope=Math.sin(Math.min(1,age/.8)*Math.PI*.5)*Math.exp(-age*.78)*mc.options.damageTiltStrength().get();
        event.setPitch(event.getPitch()+(float)(kickPitch*envelope));
        event.setRoll(event.getRoll()+(float)(kickRoll*envelope));
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=ShaderPackCompat.effectStage())return;
        world(Minecraft.getInstance().level);
        if(level==null || (bursts.isEmpty()&&landings.isEmpty()))return;
        double time=(double)level.getGameTime()+event.getPartialTick();
        bursts.removeIf(b->time-b.born()>14);
        landings.removeIf(g->time-g.born()>20);
        var camera=event.getCamera().getPosition();
        var pose=event.getPoseStack();pose.pushPose();pose.translate(-camera.x,-camera.y,-camera.z);
        var buffers=ShaderPackCompat.buffers();var type=ImpactSprayMaterial.type();var out=buffers.getBuffer(type);
        for(var b:bursts){
            double age=time-b.born();
            if(age<0 || camera.distanceToSqr(b.point())>40*40)continue;
            int light=LevelRenderer.getLightColor(level,BlockPos.containing(b.point()));
            if(b.metal())ImpactSpray.sparks(pose,out,b.point(),b.axis(),camera,age,b.size(),b.seed(),light);
            else {
                ImpactSpray.flesh(pose,out,b.path(),camera,b.rupture()?InfectedAttackStyle.BURST:InfectedAttackStyle.TEAR,
                        b.strike(),false,false,age,b.size(),b.seed(),false,light);
                if(b.rupture())ImpactSpray.flesh(pose,out,b.point().add(0,-.075,0),b.axis().scale(-.6).add(0,.62,0),camera,
                        InfectedAttackStyle.PIERCE,age-.76,b.size()*.72F,b.seed()+17,true,light);
            }
        }
        for(var g:landings){
            if(camera.distanceToSqr(g.point())<=40*40)ImpactSpray.groundDust(pose,out,camera,g.point(),time-g.born(),
                    g.color(),g.size(),g.seed(),LevelRenderer.getLightColor(level,BlockPos.containing(g.point().add(0,.06,0))));
        }
        buffers.endBatch(type);pose.popPose();
    }
}
