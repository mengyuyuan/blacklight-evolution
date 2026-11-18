package com.codex.blacklight.client;
import com.google.gson.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.Particle;

/** Disposable QA instrumentation. Counts actual native particle render calls. */
public final class ParticleProbe {
    private static final Map<String,Integer> drawn=new TreeMap<>();
    private static JsonObject first;
    public static void reset(){drawn.clear();first=null;}
    public static void draw(Particle p,Camera camera){
        drawn.merge(p.getClass().getSimpleName(),1,Integer::sum);
        if(first==null){first=new JsonObject();first.addProperty("type",p.getClass().getSimpleName());first.add("bounds",new Gson().toJsonTree(p.getBoundingBox()));first.add("camera",new Gson().toJsonTree(camera.getPosition()));first.add("shaderColor",new Gson().toJsonTree(com.mojang.blaze3d.systems.RenderSystem.getShaderColor()));
            var rs=com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix();
            var bounds=p.getBoundingBox().getCenter().subtract(camera.getPosition());
            var clip=new org.joml.Vector4f((float)bounds.x,(float)bounds.y,(float)bounds.z,1).mul(rs).mul(com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix());
            first.add("clip",new Gson().toJsonTree(new float[]{clip.x,clip.y,clip.z,clip.w}));
            first.add("view",new Gson().toJsonTree(rs.get(new float[16])));
            first.add("rotation",new Gson().toJsonTree(camera.rotation().get(new org.joml.Quaternionf())));
            first.addProperty("shader",com.mojang.blaze3d.systems.RenderSystem.getShader().getName());
            first.addProperty("texture0",com.mojang.blaze3d.systems.RenderSystem.getShaderTexture(0));
            first.addProperty("fbo",org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER_BINDING));
            first.addProperty("depthMask",org.lwjgl.opengl.GL11.glGetBoolean(org.lwjgl.opengl.GL11.GL_DEPTH_WRITEMASK));
            for(String name:new String[]{"alpha","rCol","gCol","bCol","age","lifetime","quadSize"})try{first.add(name,new Gson().toJsonTree(field(p,name)));}catch(ReflectiveOperationException ignored){}
        }
    }
    private static Object field(Object o,String name)throws ReflectiveOperationException{for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass())try{var f=c.getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
    public static JsonObject snapshot()throws ReflectiveOperationException{
        var mc=Minecraft.getInstance();var out=new JsonObject();var active=new TreeMap<String,Integer>();
        for(var queue:((Map<?,?>)field(mc.particleEngine,"particles")).values())for(var p:(Iterable<?>)queue)active.merge(p.getClass().getSimpleName(),1,Integer::sum);
        out.add("active",new Gson().toJsonTree(active));out.add("drawn",new Gson().toJsonTree(drawn));out.add("first",first);out.addProperty("queued",((Collection<?>)field(mc.particleEngine,"particlesToAdd")).size());out.addProperty("option",mc.options.particles().get().toString());return out;
    }
}
