package com.codex.blacklight.registry;
import com.codex.blacklight.power.PowerForm;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import java.util.*;
import java.util.function.Supplier;

/** Authored mono cues, broadcast once from the server at the actual contact. */
public final class ModWeaponSounds {
    private static final DeferredRegister<SoundEvent> SOUNDS=DeferredRegister.create(Registries.SOUND_EVENT,"blacklight");
    private static final Map<String,Supplier<SoundEvent>> EVENTS=new HashMap<>();
    static {
        for(String form:List.of("none","claws","blade","hammerfists","whipfist","muscle_mass"))
            for(String cue:List.of("swing","hit","heavy","ground","morph")){
                String id="weapon."+form+"."+cue;
                EVENTS.put(id,SOUNDS.register(id,()->SoundEvent.createVariableRangeEvent(new ResourceLocation("blacklight",id))));
            }
    }
    static {
        for(String form:List.of("armor","shield"))
            for(String cue:List.of("deploy","retract","hit","heavy","deflect","break")){
                if(form.equals("armor")&&cue.equals("break"))continue;
                String id="defense."+form+"."+cue;
                EVENTS.put(id,SOUNDS.register(id,()->SoundEvent.createVariableRangeEvent(new ResourceLocation("blacklight",id))));
            }
    }
    private static final Map<ServerPlayer,Long> DEFENSE_CONTACTS=new WeakHashMap<>();
    /** Shared gate prevents the projectile feedback and damage hooks sounding twice. */
    public static void defense(ServerPlayer player,PowerForm form,String cue,Vec3 at,float strength){
        if(form!=PowerForm.ARMOR&&form!=PowerForm.SHIELD)return;
        var sound=EVENTS.get("defense."+form.id()+"."+cue);if(sound==null)return;
        boolean contact=cue.equals("hit")||cue.equals("heavy")||cue.equals("deflect");
        if(contact){
            long now=player.level().getGameTime();Long previous=DEFENSE_CONTACTS.get(player);
            if(previous!=null&&now>=previous&&now-previous<3)return;
            DEFENSE_CONTACTS.put(player,now);
        }
        float force=Float.isFinite(strength)?Math.max(0,Math.min(1,strength)):0;
        float volume=contact?.32F+force*.35F:.65F;
        player.level().playSound(null,at.x,at.y,at.z,sound.get(),SoundSource.PLAYERS,volume,
                (contact?1.12F-force*.22F:1F)*(.97F+player.getRandom().nextFloat()*.06F));
    }
    private ModWeaponSounds(){}
    public static void register(IEventBus bus){SOUNDS.register(bus);}
    /** Shared unarmed/air clips still use the equipped weapon's sound bank. */
    public static PowerForm equipped(ServerPlayer player){return ModAttachments.getData(player,ModAttachments.PLAYER_STATE).getActivePower();}
    public static void play(ServerPlayer player,PowerForm form,String cue,Vec3 at,float volume,float pitch){
        var sound=EVENTS.get("weapon."+form.id()+"."+cue);if(sound==null)return;
        player.level().playSound(null,at.x,at.y,at.z,sound.get(),SoundSource.PLAYERS,volume,pitch*(.97F+player.getRandom().nextFloat()*.06F));
    }
}
