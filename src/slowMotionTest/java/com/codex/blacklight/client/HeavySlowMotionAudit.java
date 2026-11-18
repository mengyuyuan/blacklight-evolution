package com.codex.blacklight.client;

import com.codex.blacklight.combat.HeavySlowMotion;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Timer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/** Exercises both applied Mixins against the real integrated scheduler, not a mocked timer. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class HeavySlowMotionAudit {
    private static volatile boolean started,finished;
    private static volatile int ticks;
    private static int slowClient,normalClient;
    private static long previous;
    private static boolean previousSlow;
    private static final List<Double> slowIntervals=new ArrayList<>(),normalIntervals=new ArrayList<>();
    @SubscribeEvent public static void client(TickEvent.ClientTickEvent e) throws Exception {
        if(e.phase!=TickEvent.Phase.END||finished||System.getProperty("blacklight.test.slowMotion","").isBlank())return;
        var mc=Minecraft.getInstance();var server=mc.getSingleplayerServer();
        if(server==null||mc.player==null||mc.level==null)return;
        if(!started){
            if(!mc.gameDirectory.toPath().toAbsolutePath().normalize().toString().replace('\\','/').equalsIgnoreCase("D:/blacklight-evolution-forge-1.20.1/run-ragdoll-qa")
                    ||!server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName().toString().equals("Blacklight Ragdoll QA"))throw new IllegalStateException("Refused non-QA world");
            mc.options.pauseOnLostFocus=false;mc.setScreen(null);mc.getWindow().setTitle("Forge heavy slow motion QA");
            started=true;
        }
        boolean slow=HeavySlowMotion.active(server);
        // Timer.advanceTime is the game's transformed method; a fresh 100 ms span
        // must contain one slow tick or two ordinary ticks, without touching its fields.
        int advanced=new Timer(20F,0).advanceTime(100);
        if(slow&&advanced==1)slowClient++;if(!slow&&advanced==2)normalClient++;
        if(ticks>=28){
            finished=true;
            double slowMean=slowIntervals.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double normalMean=normalIntervals.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            var result=new JsonObject();result.addProperty("slowClientSamples",slowClient);result.addProperty("normalClientSamples",normalClient);
            result.add("slowServerIntervalsMs",new Gson().toJsonTree(slowIntervals));result.add("normalServerIntervalsMs",new Gson().toJsonTree(normalIntervals));
            result.addProperty("slowMeanMillis",slowMean);result.addProperty("normalMeanMillis",normalMean);
            result.addProperty("restored",!HeavySlowMotion.active(server));
            result.addProperty("passed",slowClient>0&&normalClient>0&&slowIntervals.size()>=2&&slowMean>=85&&slowMean<=140&&normalMean<85&&!HeavySlowMotion.active(server));
            Path output=Path.of(System.getProperty("blacklight.test.slowMotion")).toAbsolutePath().normalize();
            if(!output.toString().toLowerCase(Locale.ROOT).startsWith("d:"))throw new IllegalStateException("D: required");
            Files.createDirectories(output.getParent());Files.writeString(output,new GsonBuilder().setPrettyPrinting().create().toJson(result));mc.stop();
        }
    }
    @SubscribeEvent public static void server(TickEvent.ServerTickEvent e){
        if(e.phase!=TickEvent.Phase.END||!started||finished)return;
        long now=System.nanoTime();boolean slow=HeavySlowMotion.active(e.getServer());
        // Exclude the transition tick: scheduler changes apply at the next loop boundary.
        if(previous!=0&&ticks>3&&slow==previousSlow)(slow?slowIntervals:normalIntervals).add((now-previous)/1e6);
        previous=now;previousSlow=slow;ticks++;
        if(ticks==8){var players=e.getServer().getPlayerList().getPlayers();if(players.size()==1)HeavySlowMotion.trigger(players.get(0),3);}
    }
}
