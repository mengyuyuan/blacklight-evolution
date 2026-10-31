package com.codex.blacklight.client;
import com.codex.blacklight.infection.*;
import com.codex.blacklight.registry.*;
import com.codex.blacklight.world.BuildingBiomassBlock;
import com.google.gson.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.client.event.*;
import java.nio.file.*;
import java.util.*;
/** Private native-client acceptance, in a new disposable world, without OS input. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class BuildingVisualSmoke {
 private static final String WORLD="Building volume QA "+System.currentTimeMillis();
 private static final Path OUT=Path.of(System.getProperty("blacklight.buildingVisualOut","E:/Blacklight-Building-Biomass-QA-20261023/native"));
 private static boolean creating,setup,prepared;private static volatile boolean ready,done,saved;
 private static int ticks,shot,readyAt;private static final JsonArray records=new JsonArray();
 private static volatile int mass,age,supports,street;private static volatile List<BlockPos> cells=List.of();
 private static void write(boolean success,String error){try{Files.createDirectories(OUT);var r=new JsonObject();r.addProperty("passed",success);r.addProperty("error",error);r.addProperty("captures",shot);r.add("records",records);Files.writeString(OUT.resolve("result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(r));}catch(Exception e){e.printStackTrace();}}
 @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent e){if(e.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
  if(!Boolean.getBoolean("blacklight.buildingVisual"))return;var mc=Minecraft.getInstance();
  org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());mc.mouseHandler.releaseMouse();mc.options.pauseOnLostFocus=false;
  if(done){if(saved)mc.stop();return;}
  if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen)mc.setScreen(new TitleScreen());
  if(!creating&&mc.screen instanceof TitleScreen){creating=true;mc.options.renderDistance().set(6);mc.options.simulationDistance().set(5);mc.options.framerateLimit().set(30);mc.options.fov().set(70);
   mc.createWorldOpenFlows().createFreshLevel(WORLD,new LevelSettings(WORLD,GameType.SPECTATOR,false,Difficulty.NORMAL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(87231L,false,false),r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());return;}
  if(mc.player==null||mc.getSingleplayerServer()==null||!mc.getSingleplayerServer().getWorldData().getLevelName().equals(WORLD))return;
  ticks++;mc.options.hideGui=true;mc.options.setCameraType(CameraType.FIRST_PERSON);mc.setScreen(null);
  if(ticks>1800){write(false,"native client timeout at capture "+shot);done=true;saved=true;return;}
  if(!setup){setup=true;mc.getSingleplayerServer().execute(()->{try{
   var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());var l=p.serverLevel();l.setDayTime(6000);l.setWeatherParameters(60000,0,false,false);
   l.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,l.getServer());l.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0,l.getServer());
   for(int x=-24;x<=24;x++)for(int z=-34;z<=20;z++)l.setBlock(new BlockPos(x,100,z),Blocks.SMOOTH_STONE.defaultBlockState(),2);
   for(int x=-11;x<=11;x++)for(int z=0;z<=14;z++)for(int y=101;y<=137;y++){
    if(x!=-11&&x!=11&&z!=0&&z!=14&&y!=137)continue;
    var block=y%6==0?Blocks.GRAY_CONCRETE:Blocks.LIGHT_GRAY_CONCRETE;
    if(y%6>=2&&y%6<=4&&x%4!=0&&(z==0||z==14))block=Blocks.GLASS;
    l.setBlock(new BlockPos(x,y,z),block.defaultBlockState(),2);
   }
   for(int x=-20;x<=-9;x++)for(int z=-31;z<=-23;z++)for(int y=101;y<=129;y++) {
    if(x!=-20&&x!=-9&&z!=-31&&z!=-23&&y!=129)continue;
    var block=y%6==0?Blocks.GRAY_CONCRETE:Blocks.WHITE_CONCRETE;
    if(y%6>=2&&y%6<=4&&x%4!=0&&(z==-23||z==-31))block=Blocks.GLASS;
    l.setBlock(new BlockPos(x,y,z),block.defaultBlockState(),2);
   }
   var person=net.minecraft.world.entity.EntityType.ARMOR_STAND.create(l);person.moveTo(-12.5,101,-5.5,160,0);
   person.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE));l.addFreshEntity(person);
   l.setBlock(new BlockPos(0,101,-1),ModBlocks.BUILDING_CORE.get().defaultBlockState(),3);ready=true;
  }catch(Throwable t){write(false,t.toString());done=true;saved=true;}});return;}
  if(!ready)return;
  if(!prepared){prepared=true;ready=false;final int phase=shot;mc.getSingleplayerServer().execute(()->{try{
   var server=mc.getSingleplayerServer();var p=server.getPlayerList().getPlayer(mc.player.getUUID());var l=p.serverLevel();var data=BuildingInfectionData.get(l);
   int target=phase==0?0:phase==1?4000:phase==2?7800:12000;
   if(phase>0&&phase<=3)for(int i=0;i<9000;i++){for(var c:data.colonies){c.activeTicks=target;c.stage=Math.max(c.stage,target>=12000?3:target>=6000?2:1);}BuildingInfectionSystem.advance(l);}
   var c=data.colonies.get(0);mass=c.massBlocks;age=target;supports=c.mass==null?0:c.mass.supports.size();
   street=c.mass==null?0:(int)c.mass.supports.stream().filter(v->v.getZ()<c.origin.getZ()-8).count();
   cells=c.mass==null?List.of():c.mass.cells().stream().map(v->c.origin.offset(v.x(),v.y(),v.z())).toList();
   if(phase<=3)p.teleportTo(l,44,129,-58,37,14);else if(phase==4)p.teleportTo(l,29,117,-14,91,5);else p.teleportTo(l,3,111,-15,14,-1);
   p.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);readyAt=ticks;ready=true;
  }catch(Throwable t){write(false,t.toString());done=true;saved=true;}});}
 }
 @SubscribeEvent public static void capture(RenderLevelStageEvent e){
  if(!Boolean.getBoolean("blacklight.buildingVisual")||!ready||done||!prepared||ticks-readyAt<50||e.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL)return;
  var mc=Minecraft.getInstance();try{
   int solid=0,boundary=0,quads=0;for(var p:cells){var s=mc.level.getBlockState(p);if(!s.is(ModBlocks.BUILDING_BIOMASS.get()))continue;solid++;
    if(s.getValue(BuildingBiomassBlock.CORNERS)!=255){boundary++;quads+=mc.getBlockRenderer().getBlockModel(s).getQuads(s,null,net.minecraft.util.RandomSource.create(1)).size();}}
   if(shot>=3&&(solid<200||boundary==0||quads==0||street==0))throw new AssertionError("solid/client boundary or street bridge missing: "+solid+" / "+boundary+" / "+quads+" / "+street);
   Files.createDirectories(OUT);String name=String.format("stage-%d-%s.png",shot,shot==4?"side":shot==5?"roots":"front");
   try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(OUT.resolve(name));}
   var r=new JsonObject();r.addProperty("image",name);r.addProperty("age",age);r.addProperty("serverBlocks",mass);r.addProperty("clientBlocks",solid);r.addProperty("boundaryBlocks",boundary);r.addProperty("quads",quads);r.addProperty("supports",supports);r.addProperty("streetBridges",street);records.add(r);
   shot++;prepared=false;
   if(shot>=6){done=true;mc.getSingleplayerServer().execute(()->{try{mc.getSingleplayerServer().saveEverything(true,false,true);write(true,"");}catch(Throwable t){write(false,t.toString());}finally{saved=true;}});}
  }catch(Throwable t){write(false,t.toString());done=true;saved=true;}
 }
}
