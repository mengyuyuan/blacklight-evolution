package com.codex.blacklight.client.renderer;

import com.codex.blacklight.BlacklightNeoMod;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Pixel wounds around the exact authored missing faces. Native variants and
 * resource-pack pixels are copied at runtime; no vanilla texture is overwritten. */
public final class InfectedWoundTextures {
    private record Key(ResourceLocation texture,String species,List<AuthoredInfectionParts.Wound> wounds) {}
    private static final Map<Key,ResourceLocation> CACHE=new LinkedHashMap<>(192,.75F,true);
    private InfectedWoundTextures(){}
    public static ResourceLocation get(ResourceLocation source,String species,List<AuthoredInfectionParts.Wound> wounds){
        if(wounds.isEmpty())return source;
        Key key=new Key(source,species,wounds);var existing=CACHE.get(key);if(existing!=null)return existing;
        var mc=Minecraft.getInstance();var resource=mc.getResourceManager().getResource(source);
        if(resource.isEmpty())return source;
        try(var stream=resource.get().open();var original=NativeImage.read(stream)){
            int width=original.getWidth(),height=original.getHeight();
            NativeImage image=new NativeImage(width,height,false);image.copyFrom(original);
            int radius=Math.clamp(width/64,1,16),limit=2*radius+1;
            int[] distances=new int[width*height];Arrays.fill(distances,limit);
            for(var wound:wounds){
                int x0=Math.clamp((int)Math.floor((wound.u()-wound.width()*.5)*width),0,width-1),x1=Math.clamp((int)Math.ceil((wound.u()+wound.width()*.5)*width),0,width);
                int y0=Math.clamp((int)Math.floor((wound.v()-wound.height()*.5)*height),0,height-1),y1=Math.clamp((int)Math.ceil((wound.v()+wound.height()*.5)*height),0,height);
                for(int y=y0;y<y1;y++)for(int x=x0;x<x1;x++)distances[y*width+x]=0;
            }
            // Two linear passes avoid a per-pixel radius search on high-resolution packs.
            for(int y=0;y<height;y++)for(int x=0;x<width;x++){
                int at=y*width+x;if(x>0)distances[at]=Math.min(distances[at],distances[at-1]+1);if(y>0)distances[at]=Math.min(distances[at],distances[at-width]+1);
            }
            for(int y=height-1;y>=0;y--)for(int x=width-1;x>=0;x--){
                int at=y*width+x;if(x+1<width)distances[at]=Math.min(distances[at],distances[at+1]+1);if(y+1<height)distances[at]=Math.min(distances[at],distances[at+width]+1);
            }
            boolean wet=species.endsWith(":drowned")||species.contains("fish")||species.contains("squid");
            for(int y=0;y<height;y++)for(int x=0;x<width;x++){
                int distance=distances[y*width+x];
                if(distance>2*radius)continue;
                int rgba=original.getPixelRGBA(x,y),alpha=rgba>>>24;if(alpha==0)continue;
                int noise=Math.floorMod(x*31+y*17+species.hashCode(),7);
                float weight=distance<=radius?.72F:.35F;
                int r=(rgba&255),g=rgba>>8&255,b=rgba>>16&255;
                int bloodR=wet?53+noise:74+noise*2,bloodG=wet?33+noise:17+noise,bloodB=wet?31+noise:20+noise;
                r=Math.round(r*(1-weight)+bloodR*weight);g=Math.round(g*(1-weight)+bloodG*weight);b=Math.round(b*(1-weight)+bloodB*weight);
                image.setPixelRGBA(x,y,alpha<<24|b<<16|g<<8|r);
            }
            var id=mc.getTextureManager().register("blacklight/infected_wounds",new DynamicTexture(image));CACHE.put(key,id);
            if(CACHE.size()>192){var first=CACHE.entrySet().iterator();var old=first.next();mc.getTextureManager().release(old.getValue());first.remove();}
            return id;
        }catch(Exception e){BlacklightNeoMod.LOGGER.warn("Cannot prepare native wound texture {} for {}",source,species,e);return source;}
    }
    public static void reload(){for(var id:CACHE.values())Minecraft.getInstance().getTextureManager().release(id);CACHE.clear();}
}
