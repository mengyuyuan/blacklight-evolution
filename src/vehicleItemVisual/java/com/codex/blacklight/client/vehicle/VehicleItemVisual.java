package com.codex.blacklight.client.vehicle;

import com.google.gson.*;
import com.mojang.blaze3d.platform.Lighting;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent;


/** Disposable main-menu harness: production item bakes and render contexts; opens no world. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class VehicleItemVisual {
    private static final String[] ITEMS={"military_truck_chassis","military_apc_chassis","military_tank_chassis",
        "blackwatch_truck_chassis","blackwatch_apc_chassis","blackwatch_tank_chassis","military_fuel_can",
        "tank_he_shell","tank_ap_shell","autocannon_round","vehicle_repair_parts"};
    private static final ItemDisplayContext[] CONTEXTS={ItemDisplayContext.GUI,ItemDisplayContext.GROUND,ItemDisplayContext.FIXED,ItemDisplayContext.HEAD,
        ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
        ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,ItemDisplayContext.THIRD_PERSON_LEFT_HAND};
    private static Path output;
    private static int scene,frames;
    private static boolean started,done;
    private static final JsonArray receipt=new JsonArray();
    private static ItemStack stack(String id){return new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation("blacklight",id)));}
    private static String hash(String resource) throws Exception {
        try(var stream=Minecraft.getInstance().getResourceManager().open(new ResourceLocation(resource))){
            return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));
        }
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        if(done||started||System.getProperty("blacklight.test.vehicleItems","").isBlank()
                ||!(mc.screen instanceof TitleScreen)||mc.getOverlay()!=null)return;
        try{
            if(mc.level!=null||!mc.gameDirectory.toPath().toAbsolutePath().normalize().toString().replace('\\','/').startsWith("D:/CodexCache/"))
                throw new IllegalStateException("Only disposable D: main-menu QA is allowed");
            output=Path.of(System.getProperty("blacklight.test.vehicleItems")).toAbsolutePath().normalize();
            if(!output.startsWith(Path.of("D:/CodexCache")))throw new IllegalStateException("D: QA output required");
            Files.createDirectories(output);Files.writeString(output.resolve("status.txt"),"Running production item renders");
            mc.options.guiScale().set(1);mc.resizeDisplay();mc.options.framerateLimit().set(30);
            mc.options.pauseOnLostFocus=false;mc.getToasts().clear();mc.mouseHandler.releaseMouse();
            for(var id:ITEMS){
                var item=stack(id);if(item.isEmpty())throw new IllegalStateException("Missing item "+id);
                var baked=mc.getItemRenderer().getModel(item,null,null,0);
                if(!baked.isGui3d()||baked.isCustomRenderer())throw new IllegalStateException("Not a normal 3D model "+id);
                var quads=new ArrayList<>(baked.getQuads(null,null,RandomSource.create(0)));
                for(var direction:net.minecraft.core.Direction.values())quads.addAll(baked.getQuads(null,direction,RandomSource.create(0)));
                var sprites=new TreeSet<String>();for(var quad:quads)sprites.add(quad.getSprite().contents().name().toString());
                if(quads.size()<=6||sprites.stream().anyMatch(s->s.contains("missingno")))throw new IllegalStateException("Missing geometry/texture "+id);
                var row=new JsonObject();row.addProperty("item",id);row.addProperty("quads",quads.size());
                var hashes=new JsonObject();String model="blacklight:models/item/"+id+".json";hashes.addProperty(model,hash(model));
                for(var sprite:sprites){String path=sprite.replace(":",":textures/")+".png";hashes.addProperty(path,hash(path));}
                row.add("resourceSha256",hashes);
                row.add("sprites",new Gson().toJsonTree(sprites));row.addProperty("gui3d",baked.isGui3d());receipt.add(row);
            }
            started=true;mc.setScreen(new Gallery());
        }catch(Exception failure){fail(failure);}
    }
    @SubscribeEvent public static void render(TickEvent.RenderTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();if(done||!started||!(mc.screen instanceof Gallery)||mc.getOverlay()!=null)return;
        if(++frames<8)return;
        try{
            String name=scene==0?"inventory-board":ITEMS[scene-1]+"-contexts";
            try(var shot=Screenshot.takeScreenshot(mc.getMainRenderTarget())){shot.writeToFile(output.resolve(name+".png"));}
            if(++scene>ITEMS.length){
                Files.writeString(output.resolve("baked-models.json"),new GsonBuilder().setPrettyPrinting().create().toJson(receipt));
                Files.writeString(output.resolve("status.txt"),"PASS: 11 production model bakes, 11 GUI icons, 88 context renders; visual review required. No world opened.");
                done=true;mc.stop();
            }else frames=0;
        }catch(Exception failure){fail(failure);}
    }
    private static void fail(Exception failure){
        done=true;failure.printStackTrace();try{if(output!=null)Files.writeString(output.resolve("status.txt"),"FAIL: "+failure);}catch(Exception ignored){}
        Minecraft.getInstance().stop();
    }
    private static class Gallery extends Screen {
        Gallery(){super(Component.literal("Vehicle item model QA"));}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void render(GuiGraphics g,int mx,int my,float partial){
            g.fill(0,0,width,height,0xFF20292F);
            g.drawString(font,scene==0?"PRODUCTION 3D ITEMS / GUI + 16px icon":ITEMS[scene-1]+" / production display transforms",24,20,0xE1E4D7,false);
            g.drawString(font,"Native Minecraft render. Main menu only. Fuel can preserved / inactive.",24,38,0xA7B8BC,false);
            int columns=4,rows=scene==0?3:2,cw=width/columns,ch=(height-80)/rows;
            int count=scene==0?ITEMS.length:CONTEXTS.length;
            for(int i=0;i<count;i++){
                int x=i%columns*cw,y=65+i/columns*ch;var item=stack(ITEMS[scene==0?i:scene-1]);
                String label=scene==0?ITEMS[i]:CONTEXTS[i].name();
                g.fill(x+8,y,x+cw-8,y+ch-10,0xFF35434B);
                g.drawString(font,label,x+15,y+8,0xD3DCCF,false);
                if(scene==0){
                    float factor=Math.min(cw-32,ch-60)/16F;
                    g.pose().pushPose();g.pose().translate(x+cw/2F-factor*8,y+ch/2F-factor*8,0);g.pose().scale(factor,factor,1);
                    g.renderItem(item,0,0);g.pose().popPose();g.renderItem(item,x+20,y+ch-35);
                }else{
                    g.flush();Lighting.setupFor3DItems();g.pose().pushPose();
                    g.pose().translate(x+cw/2F,y+ch*.52,150);float scale=Math.min(cw-45,ch-85);
                    g.pose().scale(scale,-scale,scale);
                    var context=CONTEXTS[i];boolean left=context==ItemDisplayContext.FIRST_PERSON_LEFT_HAND||context==ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
                    minecraft.getItemRenderer().renderStatic(null,item,context,left,g.pose(),g.bufferSource(),null,15728880,OverlayTexture.NO_OVERLAY,0);
                    g.flush();g.pose().popPose();Lighting.setupFor3DItems();
                }
            }
        }
    }
}
