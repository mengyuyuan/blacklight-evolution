package com.codex.blacklight.client;

import com.codex.blacklight.BlacklightNeoMod;
import com.codex.blacklight.client.renderer.AuthoredInfectionParts;
import com.codex.blacklight.entity.BlacklightInfectedHost;
import com.codex.blacklight.registry.ModEntities;
import com.google.gson.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import java.nio.file.*;
import java.util.*;

/** Real Minecraft native-render comparisons in a disposable QA world. */
@EventBusSubscriber(modid=BlacklightNeoMod.MOD_ID,value=Dist.CLIENT)
public final class InfectedCompositionSmoke {
    private static final String PROPERTY="blacklight.test.infectedComposition";
    private static final List<String> PRIORITY=List.of("drowned","zombie_villager","horse","bee","guardian","shulker","zombie","cow","sheep","wolf","spider","sniffer");
    private static int ticks,frames,page,oldScale;private static boolean initialized,done,capture;
    private static Review screen;private static Path output;
    private record Item(String id,Mob vanilla,BlacklightInfectedHost infected){}
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent event){if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        String folder=System.getProperty(PROPERTY);if(folder==null||done)return;
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.getSingleplayerServer()==null)return;
        if(!mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toString().contains("Blacklight Standalone QA"))throw new IllegalStateException("Infected capture needs a copied QA world");
        if(!initialized){
            if(++ticks<60)return;
            try{
                output=Path.of(folder);Files.createDirectories(output.resolve("screenshots"));
                var resource=mc.getResourceManager().getResource(new ResourceLocation("blacklight","infection_parts/manifest.json")).orElseThrow();
                JsonObject manifest;try(var reader=new java.io.InputStreamReader(resource.open(),java.nio.charset.StandardCharsets.UTF_8)){manifest=JsonParser.parseReader(reader).getAsJsonObject();}
                List<String> ids=new ArrayList<>(PRIORITY);for(String id:manifest.getAsJsonObject("species").keySet())if(!ids.contains(id))ids.add(id);
                List<Item> items=new ArrayList<>();for(String id:ids){
                    var type=BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.withDefaultNamespace(id));
                    if(!(type.create(mc.level) instanceof Mob vanilla))continue;
                    if(vanilla instanceof net.minecraft.world.entity.animal.Sheep sheep)sheep.setColor(net.minecraft.world.item.DyeColor.ORANGE);
                    if(vanilla instanceof net.minecraft.world.entity.monster.Shulker shulker){var tag=shulker.saveWithoutId(new net.minecraft.nbt.CompoundTag());tag.putByte("Peek",(byte)60);shulker.readAdditionalSaveData(tag);for(int warm=0;warm<20;warm++)shulker.tick();}
                    var infected=ModEntities.INFECTED_HOST.get().create(mc.level);infected.inherit(vanilla,false);infected.tickCount=200;infected.setNoAi(true);
                    if(infected.appearance() instanceof net.minecraft.world.entity.monster.Shulker shulker)for(int warm=0;warm<20;warm++)shulker.tick();
                    items.add(new Item(id,vanilla,infected));
                }
                screen=new Review(items);oldScale=mc.options.guiScale().get();mc.options.guiScale().set(1);mc.resizeDisplay();mc.options.pauseOnLostFocus=false;
                mc.setScreen(screen);initialized=true;
            }catch(Exception e){throw new IllegalStateException("Cannot prepare infection model QA",e);}
        }
    }
    @SubscribeEvent public static void frame(net.minecraftforge.event.TickEvent.RenderTickEvent event){if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        if(!initialized||done)return;
        if(++frames%18!=0)return;
        var mc=Minecraft.getInstance();String name=String.format("native-plus-approved-%02d.png",page+1);
        Screenshot.grab(output.toFile(),name,mc.getMainRenderTarget(),m->{});
        page++;screen.page=page;
        if(page*6>=screen.items.size()){
            try{Files.writeString(output.resolve("binding-report.json"),new GsonBuilder().setPrettyPrinting().create().toJson(screen.report));}catch(Exception e){throw new IllegalStateException(e);}
            mc.options.guiScale().set(oldScale);done=true;BlacklightNeoMod.LOGGER.info("Infected model composition QA complete: {} species",screen.items.size());mc.stop();
        }
    }
    private static final class Review extends Screen {
        final List<Item> items;int page;final JsonObject report=new JsonObject();
        Review(List<Item> items){super(Component.literal("Infected model composition"));this.items=items;}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){
            g.fill(0,0,width,height,0xff17232d);g.drawString(font,"BLACKLIGHT / 原版贴图 + 已确认感染模型",24,18,0xfff1f0e8,false);
            g.drawString(font,"实际 Minecraft 渲染 · 左侧原版 / 右侧感染 · "+(page+1)+" / "+((items.size()+5)/6),24,38,0xffaebfc9,false);
            int cellW=(width-40)/3,cellH=(height-90)/2;
            for(int i=0;i<6&&page*6+i<items.size();i++){
                var item=items.get(page*6+i);int x=20+(i%3)*cellW,y=65+(i/3)*cellH;
                g.fill(x,y,x+cellW-12,y+cellH-12,0xff44505c);
                g.drawString(font,item.vanilla.getType().getDescription(),x+10,y+10,0xffffffff,false);
                g.drawString(font,item.id,x+10,y+25,0xffbdc7ce,false);
                int half=(cellW-12)/2;float max=Math.max(item.vanilla.getBbHeight(),item.infected.getBbHeight());
                int scale=(int)Math.min(Math.min(100,(cellH-70)/Math.max(max+1,.7)),half/Math.max(item.vanilla.getBbWidth()*1.9,1.2));
                InventoryScreen.renderEntityInInventoryFollowsAngle(g,x,y+42,x+half,y+cellH-36,scale,0,-1.1F,.45F,item.vanilla);
                InventoryScreen.renderEntityInInventoryFollowsAngle(g,x+half,y+42,x+cellW-12,y+cellH-36,scale,0,-1.1F,.45F,item.infected);
                g.drawString(font,"原版",x+half/2-12,y+cellH-30,0xffe2e6e5,false);g.drawString(font,"原版 + 专属变异",x+half+half/2-42,y+cellH-30,0xfff2a197,false);
                var proxy=item.infected.appearance();var renderer=Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(proxy);
                if(renderer instanceof net.minecraft.client.renderer.entity.LivingEntityRenderer<?,?> living){
                    JsonArray bindings=new JsonArray();for(String assignment:AuthoredInfectionParts.bind("minecraft:"+item.id,living.getModel()).assignments())bindings.add(assignment);
                    report.add(item.id,bindings);
                }
            }
        }
    }
}
