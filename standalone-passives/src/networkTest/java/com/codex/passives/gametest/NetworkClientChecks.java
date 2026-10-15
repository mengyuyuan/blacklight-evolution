
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
    private static boolean mainShown() throws Exception {
        var config=Class.forName("com.codex.blacklight.config.PassiveHudConfig"); var value=config.getField("SHOW_STACKS").get(null);
        return (boolean)value.getClass().getMethod("get").invoke(value);
    }
    private static void verifySettings(Minecraft mc) throws Exception {
        boolean installed=net.neoforged.fml.ModList.get().isLoaded("blacklight");
        boolean main=installed&&mainShown(); boolean before=PassiveDisplayConfig.SHOW_STACKS.get();
        PassiveDisplayConfig.toggle(); check(PassiveDisplayConfig.SHOW_STACKS.get()!=before,"potion switch toggles");
        check(!installed||mainShown()==main,"potion setting must not change Blacklight");
        check(Files.readString(mc.gameDirectory.toPath().resolve("config/adaptive_passives-client.toml")).contains("showPassiveStacks = "+!before),"potion preference saved");
        PassiveDisplayConfig.toggle();
        if(installed) {
            var config=Class.forName("com.codex.blacklight.config.PassiveHudConfig"); config.getMethod("toggle").invoke(null);
            check(mainShown()!=main,"main switch toggles"); check(PassiveDisplayConfig.SHOW_STACKS.get()==before,"main setting must not change potion HUD");
            check(Files.readString(mc.gameDirectory.toPath().resolve("config/blacklight-client.toml")).contains("showPassiveStacks = "+!main),"main preference saved");
            config.getMethod("toggle").invoke(null);
        }
        write("settings-pass","Independent toggles persisted and restored; neither changes the other.");
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
