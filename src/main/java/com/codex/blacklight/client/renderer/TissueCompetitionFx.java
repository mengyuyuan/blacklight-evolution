package com.codex.blacklight.client.renderer;

import com.codex.blacklight.entity.BlacklightInfectedHost;
import com.codex.blacklight.infection.NativeInfection;
import com.codex.blacklight.infection.NativeTissue;
import com.codex.blacklight.infection.TissueCompetition;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/** A short attached bridge carries the actual posed surface into its consumer.
 * Mixed native/converted pairs are validated by reciprocal, transient scene IDs. */
final class TissueCompetitionFx {
    private record Scene(int target,long start,boolean prey,int duration){}
    private TissueCompetitionFx(){}
    private static Scene scene(Mob mob){
        if(mob instanceof BlacklightInfectedHost h)return new Scene(h.cannibalTargetId(),h.cannibalStarted(),h.cannibalPrey(),h.cannibalDuration());
        if(!NativeInfection.active(mob))return new Scene(-1,-1,false,TissueCompetition.END_TICKS);
        var s=NativeTissue.snapshot(mob);
        return new Scene(s.contains("CannibalTarget")?s.getInt("CannibalTarget"):-1,
                s.contains("CannibalStarted")?s.getLong("CannibalStarted"):-1,s.getBoolean("CannibalPrey"),Math.max(1,s.getInt("CannibalDuration")));
    }
    private static Mob peer(Mob mob){
        var s=scene(mob);
        if(s.start()<0||s.target()<0||!mob.isAlive()||!(mob.level().getEntity(s.target()) instanceof Mob peer)||!peer.isAlive())return null;
        var other=scene(peer);
        return other.target()==mob.getId()&&other.start()==s.start()&&other.prey()!=s.prey()?peer:null;
    }
    static boolean active(Mob mob){return peer(mob)!=null;}
    static long started(Mob mob){return scene(mob).start();}
    static boolean prey(Mob mob){return scene(mob).prey();}
    static float collapse(Mob mob,float partial){
        var s=scene(mob);float elapsed=mob.level().getGameTime()-s.start()+partial;
        float t=net.minecraft.util.Mth.clamp((elapsed-TissueCompetition.COVER_TICKS)/Math.max(1,s.duration()-TissueCompetition.COVER_TICKS),0,1);
        return t*t*(3-2*t);
    }
    static void apply(Mob mob,float partial,PoseStack stack){
        var peer=peer(mob);if(peer==null||!prey(mob))return;
        float t=collapse(mob,partial);float scale=1-.92F*t;
        Vec3 delta=peer.getPosition(partial).add(0,peer.getBbHeight()*.45,0).subtract(mob.getPosition(partial).add(0,mob.getBbHeight()*.5,0));
        stack.translate(delta.x*t,mob.getBbHeight()*.5+delta.y*t,delta.z*t);
        stack.scale(scale,scale,scale);stack.translate(0,-mob.getBbHeight()*.5,0);
    }
    static void render(Mob mob,float partial,PoseStack stack,MultiBufferSource buffers,int light){
        var target=peer(mob);if(target==null||prey(mob)||mob.isInvisible())return;
        Vec3 start=new Vec3(0,mob.getBbHeight()*.5,0);
        Vec3 end=target.getPosition(partial).subtract(mob.getPosition(partial)).add(0,target.getBbHeight()*.5,0);
        float age=mob.level().getGameTime()-started(mob)+partial;
        float cover=net.minecraft.util.Mth.clamp(age/TissueCompetition.COVER_TICKS,0,1),flow=collapse(target,partial);
        var out=buffers.getBuffer(RenderType.entityCutoutNoCull(ConsumedMobVisuals.WHITE));
        for(int strand=0;strand<3;strand++){
            Vec3 previous=start;double phase=strand*Math.PI*2/3;
            for(int i=1;i<=12;i++){
                double t=i/12D;double wrap=Math.sin(t*Math.PI)*.16*cover;
                Vec3 next=start.lerp(end,t).add(Math.cos(phase+t*5+age*.04)*wrap,Math.sin(phase+t*5+age*.04)*wrap,Math.sin(phase+t*5)*wrap);
                ConsumedMobVisuals.tube(stack,out,light,previous,next,(.035+.035*Math.sin(t*Math.PI))*cover*(1-.4*flow),0xff25171e);
                previous=next;
            }
        }
    }
}
