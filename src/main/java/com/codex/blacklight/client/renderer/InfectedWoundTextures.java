package com.codex.blacklight.client.renderer;

import com.codex.blacklight.BlacklightNeoMod;
import com.codex.blacklight.infection.TissueGrowth;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Blacklight overtakes the host's own pixels instead of covering it with
 * foreign parts. Bite sites rot first; with age dark flesh spreads along
 * noise fronts and finally hardens into black chitin plates whose seams glow.
 * Work is done on the species' native pixel grid so resource packs keep the
 * chunky Minecraft read. Native textures are copied, never overwritten. */
public final class InfectedWoundTextures {
    public static final int LEVELS=8;
    public record Skin(ResourceLocation texture,ResourceLocation glow){}
    private record Key(ResourceLocation texture,String species,List<AuthoredInfectionParts.Wound> wounds,int level,int variant) {}
    private static final Map<Key,Skin> CACHE=new LinkedHashMap<>(192,.75F,true);
    private InfectedWoundTextures(){}
    public static ResourceLocation get(ResourceLocation source,String species,List<AuthoredInfectionParts.Wound> wounds){
        return blight(source,species,wounds,0,0).texture();
    }
    /** 0 while freshly bitten, about half at maturity, full blight once ancient. */
    public static int level(long age){
        float coverage=.45F*TissueGrowth.progress(age)+.55F*TissueGrowth.overgrowth(age)*.5F;
        return Math.max(0,Math.min(LEVELS,Math.round(coverage*LEVELS)));
    }
    public static Skin blight(ResourceLocation source,String species,List<AuthoredInfectionParts.Wound> wounds,int level,int variant){
        level=Math.max(0,Math.min(LEVELS+3,level));variant&=3;
        if(wounds.isEmpty()&&level==0)return new Skin(source,null);
        Key key=new Key(source,species,List.copyOf(wounds),level,variant);var existing=CACHE.get(key);if(existing!=null)return existing;
        var mc=Minecraft.getInstance();var resource=mc.getResourceManager().getResource(source);
        if(resource.isEmpty())return new Skin(source,null);
        try(var stream=resource.get().open();var original=NativeImage.read(stream)){
            int width=original.getWidth(),height=original.getHeight();
            NativeImage image=new NativeImage(width,height,false);image.copyFrom(original);
            NativeImage glow=new NativeImage(width,height,true);
            boolean glowing=paint(original,image,glow,species,wounds,level,variant);
            var tm=mc.getTextureManager();
            var id=tm.register("blacklight/infected_wounds",new DynamicTexture(image));
            ResourceLocation glowId=null;
            if(glowing)glowId=tm.register("blacklight/infected_glow",new DynamicTexture(glow));else glow.close();
            var skin=new Skin(id,glowId);CACHE.put(key,skin);
            if(CACHE.size()>192){var first=CACHE.entrySet().iterator();var old=first.next().getValue();release(old);first.remove();}
            return skin;
        }catch(Exception e){BlacklightNeoMod.LOGGER.warn("Cannot prepare native wound texture {} for {}",source,species,e);return new Skin(source,null);}
    }
    private static void release(Skin skin){var tm=Minecraft.getInstance().getTextureManager();tm.release(skin.texture());if(skin.glow()!=null)tm.release(skin.glow());}
    public static void reload(){for(var skin:CACHE.values())release(skin);CACHE.clear();}

    /** Package-visible for tests: returns whether any glowing pixel was written. */
    static boolean paint(NativeImage original,NativeImage image,NativeImage glow,String species,
            List<AuthoredInfectionParts.Wound> wounds,int level,int variant){
        int width=original.getWidth(),height=original.getHeight();
        // One Minecraft pixel; 64-wide vanilla sheets give 1, 16x packs give 16.
        int cell=Math.max(1,width/64),gw=(width+cell-1)/cell,gh=(height+cell-1)/cell;
        int seed=species.hashCode()*31+variant*0x2f1b;
        int[] distance=woundDistance(wounds,gw,gh);
        float coverage=level/(float)LEVELS*.62F;
        boolean wet=species.endsWith(":drowned")||species.contains("fish")||species.contains("squid");
        double lumSum=0;int lumCount=0;
        for(int y=0;y<height;y+=cell)for(int x=0;x<width;x+=cell){int p=original.getPixelRGBA(x,y);if(p>>>24!=0){lumSum+=lum(p);lumCount++;}}
        float mean=lumCount==0?.5F:(float)(lumSum/lumCount);
        boolean glowing=false;
        for(int y=0;y<height;y++)for(int x=0;x<width;x++){
            int rgba=original.getPixelRGBA(x,y),alpha=rgba>>>24;if(alpha==0)continue;
            int gx=x/cell,gy=y/cell,d=distance[gy*gw+gx];
            int r=rgba&255,g=rgba>>8&255,b=rgba>>16&255;
            // Fresh bite rim: identical to the earliest infection read.
            if(d<=2&&!wounds.isEmpty()){
                int noise=Math.floorMod(gx*31+gy*17+species.hashCode(),7);float weight=d<=1?.72F:.35F;
                int br=wet?53+noise:74+noise*2,bg=wet?33+noise:17+noise,bb=wet?31+noise:20+noise;
                r=mix(r,br,weight);g=mix(g,bg,weight);b=mix(b,bb,weight);
            }
            if(level>0){
                float n=fbm(gx,gy,seed);
                float boost=d>=99?0:Math.max(0,1-d/(3+14*coverage))*.42F;
                float t=coverage+boost+(.5F-n)*.9F;
                float shade=clamp(1+(lum(rgba)-mean)*.8F,.72F,1.22F);
                int hash=hash(gx,gy,seed^0x51ed);
                if(t>.70F){
                    // Chitin: Voronoi plates, domed centres, dark seams.
                    float[] plate=voronoi(gx,gy,seed,4.2F);
                    if(plate[1]-plate[0]<.7F){
                        // Seams are dark; only a sparse, deep subset carries light.
                        // Only some seam runs are live cracks: a coarse mask keeps
                        // them as short continuous lines instead of speckle.
                        boolean hot=t>.88F&&coverage>.3F&&value(gx/3.5F,gy/3.5F,seed^0x1d2)>.62F;
                        r=hot?70:10;g=hot?8:7;b=hot?14:10;
                        if(hot){float k=clamp((t-.86F)*2.5F,.25F,.6F);glow.setPixelRGBA(x,y,255<<24|rgb(220*k,26*k,34*k));glowing=true;}
                    }else{
                        int tone=plate[0]<1.1F?2:plate[0]<2.3F?1:0;if((hash&7)==0)tone=Math.max(0,tone-1);
                        if(plate[2]<0&&plate[0]>.6F&&tone<2)tone++;
                        int[][] chitin={{24,18,30},{44,34,54},{84,68,98}};
                        r=Math.round(chitin[tone][0]*shade);g=Math.round(chitin[tone][1]*shade);b=Math.round(chitin[tone][2]*shade);
                    }
                }else if(t>.50F){
                    // Exposed blacklight flesh, dithered, keeps the host's form shading.
                    float f=fbm(gx*2+17,gy*2+5,seed^0x3a7);
                    int tone=f<.36F?0:f<.62F?1:2;if((hash&3)==0)tone=(tone+1)%3;
                    int[][] flesh=wet?new int[][]{{50,20,28},{74,28,36},{98,40,46}}:new int[][]{{92,14,24},{128,22,32},{170,38,44}};
                    r=Math.round(flesh[tone][0]*shade);g=Math.round(flesh[tone][1]*shade);b=Math.round(flesh[tone][2]*shade);
                    if(t>.66F&&coverage>.4F&&(hash&63)==0){glow.setPixelRGBA(x,y,255<<24|rgb(110,12,16));glowing=true;}
                }else if(t>.43F){
                    // Necrotic rim between healthy and infected pixels.
                    r=mix(r,72,.55F);g=mix(g,38,.55F);b=mix(b,50,.55F);
                }else{
                    // Veins run ahead of the front into healthy skin.
                    float v=value(gx/3.0F,gy/3.0F,seed^0x77);float ridge=1-Math.abs(2*v-1);
                    if(ridge>.92F&&t>.20F){r=mix(r,62,.8F);g=mix(g,12,.8F);b=mix(b,20,.8F);}
                }
            }
            image.setPixelRGBA(x,y,alpha<<24|clampByte(b)<<16|clampByte(g)<<8|clampByte(r));
        }
        return glowing;
    }
    private static int[] woundDistance(List<AuthoredInfectionParts.Wound> wounds,int gw,int gh){
        int[] distances=new int[gw*gh];Arrays.fill(distances,99);
        if(wounds.isEmpty())return distances;
        for(var wound:wounds){
            int x0=clamp((int)Math.floor((wound.u()-wound.width()*.5)*gw),0,gw-1),x1=clamp((int)Math.ceil((wound.u()+wound.width()*.5)*gw),0,gw);
            int y0=clamp((int)Math.floor((wound.v()-wound.height()*.5)*gh),0,gh-1),y1=clamp((int)Math.ceil((wound.v()+wound.height()*.5)*gh),0,gh);
            for(int y=y0;y<y1;y++)for(int x=x0;x<x1;x++)distances[y*gw+x]=0;
        }
        for(int y=0;y<gh;y++)for(int x=0;x<gw;x++){int at=y*gw+x;if(x>0)distances[at]=Math.min(distances[at],distances[at-1]+1);if(y>0)distances[at]=Math.min(distances[at],distances[at-gw]+1);}
        for(int y=gh-1;y>=0;y--)for(int x=gw-1;x>=0;x--){int at=y*gw+x;if(x+1<gw)distances[at]=Math.min(distances[at],distances[at+1]+1);if(y+1<gh)distances[at]=Math.min(distances[at],distances[at+gw]+1);}
        return distances;
    }
    /** {nearest, second nearest, vertical offset to nearest centre}. */
    private static float[] voronoi(int x,int y,int seed,float size){
        float px=x/size,py=y/size;int cx=(int)Math.floor(px),cy=(int)Math.floor(py);
        float d1=99,d2=99,dy=0;
        for(int j=-1;j<=1;j++)for(int i=-1;i<=1;i++){
            int h=hash(cx+i,cy+j,seed^0x9e37);
            float fx=cx+i+.15F+.7F*((h&255)/255F),fy=cy+j+.15F+.7F*((h>>>8&255)/255F);
            float d=(float)Math.hypot(fx-px,fy-py)*size;
            if(d<d1){d2=d1;d1=d;dy=(py-fy)*size;}else if(d<d2)d2=d;
        }
        return new float[]{d1,d2,dy};
    }
    private static float fbm(int x,int y,int seed){return value(x/7.0F,y/7.0F,seed)*.68F+value(x/2.5F,y/2.5F,seed^0x5bd1)*.32F;}
    private static float value(float x,float y,int seed){
        int x0=(int)Math.floor(x),y0=(int)Math.floor(y);float fx=x-x0,fy=y-y0;fx=fx*fx*(3-2*fx);fy=fy*fy*(3-2*fy);
        float a=unit(x0,y0,seed),b=unit(x0+1,y0,seed),c=unit(x0,y0+1,seed),d=unit(x0+1,y0+1,seed);
        return (a+(b-a)*fx)+((c+(d-c)*fx)-(a+(b-a)*fx))*fy;
    }
    private static float unit(int x,int y,int seed){return (hash(x,y,seed)>>>8)/16777216F;}
    private static int hash(int x,int y,int seed){int h=x*0x27d4eb2d^y*0x165667b1^seed*0x2545f491;h^=h>>>15;h*=0x85ebca6b;h^=h>>>13;h*=0xc2b2ae35;return h^(h>>>16);}
    private static float lum(int abgr){return ((abgr&255)*.299F+(abgr>>8&255)*.587F+(abgr>>16&255)*.114F)/255F;}
    private static int mix(int a,int b,float w){return Math.round(a*(1-w)+b*w);}
    private static int rgb(float r,float g,float b){return clampByte(Math.round(b))<<16|clampByte(Math.round(g))<<8|clampByte(Math.round(r));}
    private static int clampByte(int v){return v<0?0:v>255?255:v;}
    private static int clamp(int v,int lo,int hi){return v<lo?lo:v>hi?hi:v;}
    private static float clamp(float v,float lo,float hi){return v<lo?lo:v>hi?hi:v;}
}