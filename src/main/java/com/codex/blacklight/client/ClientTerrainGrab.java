package com.codex.blacklight.client;
import net.minecraft.client.Minecraft;import net.minecraft.core.BlockPos;import com.codex.blacklight.forge.PacketDistributor;import com.codex.blacklight.network.ClawActionPayload;import java.util.*;
public final class ClientTerrainGrab {
 private static boolean watching;private static Object owner;private static int last;private static List<BlockPos> preview=List.of();private static long received;
 public static void arm(){watching=true;owner=Minecraft.getInstance().player;last=-1;}
 public static void accept(com.codex.blacklight.network.TerrainGrabPreviewPayload p){preview=p.blocks();received=System.nanoTime();}
 public static List<BlockPos> preview(){return System.nanoTime()-received>1_000_000_000L?List.of():preview;}
 private static void send(ClawActionPayload.Action a){PacketDistributor.sendToServer(new ClawActionPayload(a));}
 public static void tick(){var mc=Minecraft.getInstance();if(mc.player!=owner){watching=false;preview=List.of();owner=mc.player;}if(!watching)return;
  if(mc.player==null||mc.screen!=null||!mc.isWindowActive()||!mc.player.isAlive()||SkillBindings.down(mc.options.keyUse)){if(mc.player!=null)send(ClawActionPayload.Action.TERRAIN_GRAB_CANCEL);watching=false;preview=List.of();return;}
  if(!ClientClawEvents.physicalGrabDown()){ClientMovementEvents.sendAttackDirection(mc);send(ClawActionPayload.Action.TERRAIN_GRAB_RELEASE);watching=false;preview=List.of();return;}
  if(last<0||mc.player.tickCount-last>=4){last=mc.player.tickCount;send(ClawActionPayload.Action.TERRAIN_GRAB_KEEPALIVE);}
 }
}
