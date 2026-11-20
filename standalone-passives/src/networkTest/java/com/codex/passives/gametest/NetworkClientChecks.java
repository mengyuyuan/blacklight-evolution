
package com.codex.passives.gametest;
import com.codex.passives.*;
import java.nio.file.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
@EventBusSubscriber(modid="adaptive_passives",value=Dist.CLIENT)
public final class NetworkClientChecks {
    private static final Path DIR=Path.of(System.getProperty("passive.qa.dir"));
    private static boolean respawnSent, done, settings;
    private static void write(String name,String text) throws Exception { Files.createDirectories(DIR); Files.writeString(DIR.resolve(name),text); }
    private static void check(boolean b,String reason) { if(!b) throw new IllegalStateException(reason); }
    private static void verifyHiddenMarkers(){
        for(String id:java.util.List.of("adaptation","tissue_regeneration")){
            var type=net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getHolder(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.MOB_EFFECT,net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("blacklight",id))).orElseThrow();
            var marker=new net.minecraft.world.effect.MobEffectInstance(type,200);
            var presentation=net.neoforged.neoforge.client.extensions.common.IClientMobEffectExtensions.of(marker);
            check(!presentation.isVisibleInInventory(marker)&&!presentation.isVisibleInGui(marker),"Hidden Blacklight passive "+id);
        }
    }
    private static void verifySettings(Minecraft mc) throws Exception {
        boolean installed=net.neoforged.fml.ModList.get().isLoaded("blacklight");
        boolean before=PassiveDisplayConfig.SHOW_STACKS.get();
        if(installed)verifyHiddenMarkers();
        try{
            PassiveDisplayConfig.toggle();
            check(PassiveDisplayConfig.SHOW_STACKS.get()!=before,"Potion switch toggles");
            check(Files.readString(mc.gameDirectory.toPath().resolve("config/adaptive_passives-client.toml")).contains("showPassiveStacks = "+!before),"Potion preference saved");
            if(installed)verifyHiddenMarkers();
        }finally{PassiveDisplayConfig.toggle();}
        write("settings-pass","Potion preference persisted and restored independently of Blacklight markers.");
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        var mc=Minecraft.getInstance(); if(done) return;
        try {
            if(Files.exists(DIR.resolve("server-complete"))||Files.exists(DIR.resolve("server-failed"))) { done=true; mc.stop(); return; }
            var p=mc.player; if(p==null) return;
            if(!p.isAlive()) {
                if(!respawnSent) { p.connection.send(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN)); respawnSent=true; }
                return;
            }
            respawnSent=false;
            if(!Files.exists(DIR.resolve("expected"))) return;
            String[] values=Files.readString(DIR.resolve("expected")).split(","); if(values.length!=4) return;
            int phase=Integer.parseInt(values[0]); if(Files.exists(DIR.resolve("ack-"+phase))) return;
            var d=PassiveData.read(p);
            if(Math.abs(p.getHealth()-Float.parseFloat(values[1]))>.002||d.adaptation!=Integer.parseInt(values[2])||d.regeneration!=Integer.parseInt(values[3])||d.adaptationTier!=1||d.regenerationTier!=1) {
                write("client-observed","phase="+phase+" health="+p.getHealth()+" data="+d.encode()); return;
            }
            if(!settings) { verifySettings(mc); settings=true; }
            write("ack-"+phase,"PASS health="+p.getHealth()+" data="+d.encode());
        } catch(Throwable failure) { try { write("client-failed",failure.toString()); } catch(Exception ignored) {} done=true; failure.printStackTrace(); mc.stop(); }
    }
}
