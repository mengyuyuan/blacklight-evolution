
package com.codex.passives.gametest;
import com.codex.passives.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent.PlayerTickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
@EventBusSubscriber(modid="adaptive_passives")
public final class NetworkServerChecks {
    private static final Path DIR=Path.of(System.getProperty("passive.qa.dir"));
    private static int phase=-1, wait; private static boolean delivering; private static ServerPlayer dead;
    private static void write(String name,String text) { try { Files.createDirectories(DIR); Files.writeString(DIR.resolve(name),text); } catch(Exception e) { throw new RuntimeException(e); } }
    private static void check(boolean ok,String message) { if(!ok) throw new IllegalStateException(message); }
    private static void expect(int step,float hp,int regen) { write("expected",step+","+hp+",142,"+regen); phase=step; wait=0; }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void guard(LivingAttackEvent e) {
        if(e.getEntity() instanceof ServerPlayer && !delivering) e.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void tick(PlayerTickEvent e) {
        if(e.phase!=net.minecraftforge.event.TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        try {
            if(phase<0) {
                if(p.tickCount<100) return;
                p.level().getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION).set(false,p.getServer());
                p.setGameMode(GameType.SURVIVAL); p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000); p.setHealth(1000); p.setAbsorptionAmount(0);
                p.getInventory().clearContent();
                new PassiveData().save(p);
                PassivesMod.ADAPTATION_SERUM.get().finishUsingItem(new ItemStack(PassivesMod.ADAPTATION_SERUM.get()),p.level(),p);
                PassivesMod.REGENERATION_SERUM.get().finishUsingItem(new ItemStack(PassivesMod.REGENERATION_SERUM.get()),p.level(),p);
                var d=PassiveData.read(p); d.adaptation=100; d.save(p);
                delivering=true; try { p.invulnerableTime=0; p.hurt(p.damageSources().generic(),300); } finally { delivering=false; }
                check(Math.abs(p.getHealth()-730)<.001,"server hit health");
                check(PassiveData.read(p).adaptation==142&&PassiveData.read(p).regeneration==45,"server hit layers");
                expect(0,730,45); write("server-progress","real damage and potion grant passed");
            }
            if(!p.isAlive()) return;
            var d=PassiveData.read(p); if(d.regeneration>0) { d.nextHeal=p.level().getGameTime()+20; d.save(p); }
            if(++wait>1200) throw new IllegalStateException("Client acknowledgement timeout phase="+phase);
            if(phase==0&&Files.exists(DIR.resolve("ack-0"))) {
                d.nextHeal=p.level().getGameTime(); d.save(p); PassiveSystem.tick(p);
                check(Math.abs(p.getHealth()-775)<.001,"server one healing pulse"); expect(1,775,36);
            } else if(phase==1&&Files.exists(DIR.resolve("ack-1"))) {
                phase=2; wait=0; dead=p;
                delivering=true; try { p.invulnerableTime=0; p.hurt(p.damageSources().fellOutOfWorld(),Float.MAX_VALUE); } finally { delivering=false; }
                check(!p.isAlive(),"real death must occur"); write("server-progress","real death sent; waiting for client respawn");
            } else if(phase==2&&p!=dead) {
                d=PassiveData.read(p); check(d.adaptationTier==1&&d.regenerationTier==1&&d.adaptation==142&&d.regeneration==36,"real respawn preserves state");
                p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000); p.setHealth(1000); PassiveSystem.sync(p,true); expect(3,1000,36);
            } else if(phase==3&&Files.exists(DIR.resolve("ack-3"))) {
                write("server-complete","PASS real potion grant, damage, heal, death/respawn, S2C state, client display toggles"); phase=4;
                p.getServer().halt(false);
            }
        } catch(Throwable failure) { write("server-failed",failure.toString()); failure.printStackTrace(); phase=4; p.getServer().halt(false); }
    }
}
