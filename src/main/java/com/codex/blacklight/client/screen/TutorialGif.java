package com.codex.blacklight.client.screen;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Only the selected clip is decoded. One GPU texture, released when leaving the page. */
final class TutorialGif implements AutoCloseable {
    private static final java.util.concurrent.Executor DECODER=java.util.concurrent.Executors.newSingleThreadExecutor(task->{
        var thread=new Thread(task,"blacklight-tutorial-decoder");thread.setDaemon(true);return thread;
    });
    private record Frames(int width,int height,List<int[]> pixels,int[] ends,int duration){}
    private CompletableFuture<Frames> loading;
    private java.util.concurrent.atomic.AtomicBoolean cancelled;
    private Frames frames;
    private DynamicTexture texture;
    private ResourceLocation textureId;
    private String selected="";
    private int last=-1;
    private long started;
    boolean failed;
    void select(String family) {
        if(selected.equals(family))return;
        close(); selected=family;failed=false;started=System.nanoTime();
        var manager=Minecraft.getInstance().getResourceManager();
        var resource=ResourceLocation.fromNamespaceAndPath("blacklight","tutorials/"+family+".gif");
        var cancellation=new java.util.concurrent.atomic.AtomicBoolean();cancelled=cancellation;
        loading=CompletableFuture.supplyAsync(()->{
            try(var stream=manager.open(resource)){
                byte[] bytes=stream.readNBytes(16*1024*1024+1);
                if(bytes.length>16*1024*1024)throw new IllegalArgumentException("Tutorial too large");
                return decode(bytes,cancellation::get);
            }catch(Exception e){return null;}
        },DECODER);
    }
    static Frames decode(byte[] bytes,java.util.function.BooleanSupplier cancelled)throws Exception {
        if(cancelled.getAsBoolean())throw new java.util.concurrent.CancellationException();
        try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){
            var reader=ImageIO.getImageReadersByFormatName("gif").next();
            try {
                reader.setInput(input);int count=reader.getNumImages(true),w=reader.getWidth(0),h=reader.getHeight(0);
                if(count<2||count>180||w<1||h<1||w>640||h>360||(long)count*w*h>16_000_000)throw new IllegalArgumentException("Invalid tutorial GIF");
                var pixels=new ArrayList<int[]>();int[] ends=new int[count];int clock=0;
                // Bundled clips contain complete opaque frames. Composite offsets also
                // support GIF optimizers that store only the changed rectangle.
                var canvas=new java.awt.image.BufferedImage(w,h,java.awt.image.BufferedImage.TYPE_INT_ARGB);
                for(int i=0;i<count;i++){
                    if(cancelled.getAsBoolean())throw new java.util.concurrent.CancellationException();
                    // An optimized GIF may change frame dimensions. Validate each
                    // descriptor before ImageIO allocates that frame's raster.
                    if(reader.getWidth(i)>w||reader.getHeight(i)>h)
                        throw new IllegalArgumentException("Tutorial frame exceeds canvas");
                    var meta=reader.getImageMetadata(i).getAsTree("javax_imageio_gif_image_1.0");
                    int delay=67,x=0,y=0;
                    for(var node=meta.getFirstChild();node!=null;node=node.getNextSibling()){
                        var a=node.getAttributes();
                        if(node.getNodeName().equals("GraphicControlExtension"))delay=Math.max(20,Integer.parseInt(a.getNamedItem("delayTime").getNodeValue())*10);
                        if(node.getNodeName().equals("ImageDescriptor")){x=Integer.parseInt(a.getNamedItem("imageLeftPosition").getNodeValue());y=Integer.parseInt(a.getNamedItem("imageTopPosition").getNodeValue());}
                    }
                    var g=canvas.createGraphics();try{g.drawImage(reader.read(i),x,y,null);}finally{g.dispose();}
                    pixels.add(canvas.getRGB(0,0,w,h,null,0,w));clock+=delay;ends[i]=clock;
                }
                return new Frames(w,h,List.copyOf(pixels),ends,clock);
            }finally{reader.dispose();}
        }
    }
    boolean render(GuiGraphics g,int x,int y,int w,int h,long elapsedMillis) {
        if(frames==null&&loading!=null&&loading.isDone()){
            frames=loading.join();loading=null;failed=frames==null;
            if(frames!=null){texture=new DynamicTexture(frames.width,frames.height,true);textureId=Minecraft.getInstance().getTextureManager().register("blacklight_tutorial",texture);}
        }
        if(frames==null)return false;
        int time=(int)(elapsedMillis%frames.duration),index=0;
        while(index<frames.ends.length-1&&time>=frames.ends[index])index++;
        if(index!=last){
            int[] pixels=frames.pixels.get(index);NativeImage target=texture.getPixels();
            for(int py=0;py<frames.height;py++)for(int px=0;px<frames.width;px++){
                int c=pixels[py*frames.width+px];target.setPixelRGBA(px,py,(c&0xFF00FF00)|((c>>16)&255)|((c&255)<<16));
            }
            texture.upload();last=index;
        }
        g.blit(textureId,x,y,0,0,w,h,w,h);return true;
    }
    @Override public void close(){
        if(cancelled!=null)cancelled.set(true);cancelled=null;
        if(loading!=null)loading.cancel(false);loading=null;frames=null;last=-1;
        if(textureId!=null)Minecraft.getInstance().getTextureManager().release(textureId);
        texture=null;textureId=null;selected="";
    }
}
