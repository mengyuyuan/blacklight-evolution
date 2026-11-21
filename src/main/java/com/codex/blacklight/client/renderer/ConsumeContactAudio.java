package com.codex.blacklight.client.renderer;

import com.codex.blacklight.combat.ConsumeVisual;
import com.codex.blacklight.network.ConsumeContactSoundPayload;
import com.codex.blacklight.registry.ModWeaponSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Use the rendered contact position when available; remote and off-camera impacts use the server position. */
public final class ConsumeContactAudio {
    private record Cue(String kind,Vec3 point,int owner,float gain,float pitch,
                       ConsumeContactMix.Strike strike,int region,String surface){}
    private static final ConsumeContactMix<Cue> mix=new ConsumeContactMix<>();
    private static ClientLevel world;
    private ConsumeContactAudio(){}

    private static ClientLevel level(){
        ClientLevel next=Minecraft.getInstance().level;
        if(next!=world){world=next;mix.clear();}
        return next;
    }
    public static void confirmed(LivingEntity victim,ConsumeVisual scene,String kind,int ordinal,Vec3 point,int region){
        var current=level();
        if(current==null||victim.level()!=current||Minecraft.getInstance().isPaused()||!scene.consuming()||ordinal<0)return;
        var key=new ConsumeContactMix.Strike(victim.getId(),scene.consumerId(),scene.started(),ordinal);
        String surface=kind;
        kind=com.codex.blacklight.combat.ConsumeImpactKind.forContact(scene,ordinal);
        if(ModWeaponSounds.event("consume."+kind)!=null)
            mix.local(key,new Cue(kind,point,scene.consumerId(),1F,1F,key,region,surface),current.getGameTime(),cue->play(current,cue));
    }
    public static void receive(ConsumeContactSoundPayload data){
        var current=level();if(current==null)return;
        var key=new ConsumeContactMix.Strike(data.victim(),data.owner(),data.started(),data.ordinal());
        var cue=new Cue(data.kind(),data.point(),data.owner(),data.gain(),data.pitch(),key,0,"");
        if(data.ordinal()==ConsumeContactSoundPayload.PHASE) {
            play(current,cue);
            return;
        }
        if(data.ordinal()==ConsumeContactSoundPayload.LANDING){
            mix.local(key,cue,current.getGameTime(),c->play(current,c));
            return;
        }
        mix.server(key,cue,current.getGameTime());
    }
    private static void play(ClientLevel level,Cue cue){
        var sound=ModWeaponSounds.event("consume."+cue.kind());
        var strike=cue.strike();
        if(strike.ordinal()==ConsumeContactSoundPayload.LANDING){
            if(!ConsumeStrikeEffects.landing(level,cue.point(),strike.victim(),strike.started()))return;
        } else if(strike.ordinal()>=0 && level.getEntity(strike.victim()) instanceof LivingEntity victim){
            var scene=victim.getData(com.codex.blacklight.registry.ModAttachments.CONSUME_VISUAL);
            if(scene.consuming() && scene.started()==strike.started() && scene.consumerId()==strike.owner()){
                ConsumeBodyImpact.contact(victim,scene,strike.ordinal(),cue.point());
                boolean head=cue.region()==1 && cue.surface().equals("crush");
                ConsumeStrikeEffects.hit(level,victim,scene,strike.ordinal(),cue.kind(),cue.point(),head,
                        cue.region()!=0 && cue.region()!=1 && cue.surface().equals("dent"));
                var hard=com.codex.blacklight.combat.PlayerHitEffects.hardContactSound(victim,cue.point());
                if(hard!=null)sound=hard;
            }
        }
        if(sound==null)return;
        ConsumeFoleyPlayback.play(level,sound,listenerPoint(cue.owner(),cue.point()),cue.point(),cue.gain(),cue.pitch());
    }
    static Vec3 listenerPoint(int owner,Vec3 point){
        var mc=Minecraft.getInstance();
        if(mc.player!=null && owner==mc.player.getId() && mc.getCameraEntity()==mc.player) {
            var ear=mc.gameRenderer.getMainCamera().getPosition();
            var offset=point.subtract(ear);
            // Keep left/right placement when the execution camera dollies away from the performer's body.
            if(offset.lengthSqr()>6.25)point=ear.add(offset.normalize().scale(2.5));
        }
        return point;
    }
    public static void tick(){
        var current=level();if(current==null||Minecraft.getInstance().isPaused())return;
        mix.tick(current.getGameTime(),cue->play(current,cue));
    }
}
