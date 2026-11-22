package com.codex.blacklight.combat;

import com.codex.blacklight.entity.EncounterMob;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Surface clusters exported separately for each articulated joint of the custom spider. */
public final class SpiderAnatomy {
    public static final int MOUTH=0, RIM=1, SHELL=2, SAC=3, CROWN=10, FIRST_ROOT=11, FIRST_LEG=14;
    private record Patch(String joint,Vec3 center,Vec3 half) {}
    private static final List<List<Patch>> PATCHES=new ArrayList<>();
    public static final List<String> NAMES=new ArrayList<>();
    // This order is also the vertex joint index in the player's exported 39-bone spider.
    public static final List<String> MOLT_JOINTS;
    public static final Map<String,Vec3> TIPS=new HashMap<>();
    public static final Map<String,String> PARENTS=new HashMap<>();
    public static final Map<String,Vec3> PIVOTS=new HashMap<>();
    static {
        try(var stream=SpiderAnatomy.class.getResourceAsStream("/assets/blacklight/models/greene_spider_parts.json");
            var reader=new InputStreamReader(Objects.requireNonNull(stream),StandardCharsets.UTF_8)){
            var data=JsonParser.parseReader(reader).getAsJsonObject();
            for(var entry:data.getAsJsonObject("regions").entrySet()){
                NAMES.add(entry.getKey());
                var patches=new ArrayList<Patch>();
                for(var element:entry.getValue().getAsJsonArray()){
                    var row=element.getAsJsonArray();
                    patches.add(new Patch(row.get(0).getAsString(),vector(row,1),vector(row,4)));
                }
                PATCHES.add(List.copyOf(patches));
            }
            for(var e:data.getAsJsonObject("tips").entrySet())TIPS.put(e.getKey(),vector(e.getValue().getAsJsonArray(),0));
            for(var e:data.getAsJsonObject("parents").entrySet())PARENTS.put(e.getKey(),e.getValue().getAsString());
            for(var e:data.getAsJsonObject("joints").entrySet())PIVOTS.put(e.getKey(),vector(e.getValue().getAsJsonArray(),0));
            MOLT_JOINTS=List.copyOf(data.getAsJsonObject("joints").keySet());
            if(MOLT_JOINTS.size()!=39 || !MOLT_JOINTS.get(13).equals("coxa_L1"))throw new IllegalStateException("Molt rig joint order");
        }catch(Exception ex){throw new IllegalStateException("Cannot read Greene spider rig",ex);}
    }
    private static Vec3 vector(com.google.gson.JsonArray a,int i){
        return new Vec3(a.get(i).getAsDouble(),a.get(i+1).getAsDouble(),a.get(i+2).getAsDouble());
    }
    public static int leg(String joint){
        if(!joint.startsWith("coxa_")&&!joint.startsWith("tibia_"))return -1;
        int side=joint.length()-2;
        return (joint.charAt(side)=='L'?0:4)+joint.charAt(side+1)-'1';
    }
    public static String shin(int leg){return "tibia_"+(leg<4?"L":"R")+(leg%4+1);}
    public static Vec3 shinCenter(int leg){
        var name=shin(leg);var region=PATCHES.get(FIRST_LEG+2*leg);
        var bounds=region.stream().filter(p->p.joint().equals(name)).map(p->new AABB(p.center().subtract(p.half()),p.center().add(p.half()))).reduce(AABB::minmax);
        return bounds.map(AABB::getCenter).orElse(PIVOTS.get(name));
    }
    public static int legForRegion(int region){return region>=FIRST_LEG?(region-FIRST_LEG)/2:-1;}
    public static boolean tendon(int region){return region>=FIRST_LEG && (region-FIRST_LEG)%2==1;}
    public static String label(int region){return "boss.blacklight.greene_spider."+NAMES.get(region);}
    public static Matrix4f root(EncounterMob boss,float partial){
        var offset=boss.spider.actions.leapOffset(partial);
        var result=new Matrix4f().translate((float)offset.x,(float)offset.y,(float)offset.z).rotateY((float)Math.toRadians(180-boss.skinYaw(partial))).scale(1.9824204F);
        float tilt=boss.spider.tilt(partial);
        SpiderGesture.balance(boss,partial,result).translate(0,-1.15F*Math.abs(tilt),0).rotateZ(tilt*.54F);
        return boss.spider.seatShell(result,partial);
    }
    public static Vec3 point(EncounterMob boss,float partial,Vec3 local){
        return boss.getPosition(partial).add(new Vec3(root(boss,partial).transformPosition(local.toVector3f())));
    }
    public static List<HydraContact.Region> regions(EncounterMob boss,Vec3 origin,float partial){
        var root=root(boss,partial);
        var matrices=new HashMap<String,Matrix4f>();
        var result=new ArrayList<HydraContact.Region>(PATCHES.size());
        for(int id=0;id<PATCHES.size();id++){
            var boxes=new ArrayList<HydraContact.Box>();
            int leg=legForRegion(id);
            if(boss.greeneBattle().targetable() && (!tendon(id) || !boss.spider.broken(leg))){
                for(var p:PATCHES.get(id)){
                    if(leg>=0 && boss.spider.severed(leg) && p.joint().equals(shin(leg)))continue;
                    var m=matrices.computeIfAbsent(p.joint(),joint->boss.spider.jointMatrix(root,joint,partial));
                    Vec3 center=origin.add(new Vec3(m.transformPosition(p.center().toVector3f())));
                    boxes.add(HydraContact.affine(center,p.half(),axis(m,1,0,0),axis(m,0,1,0),axis(m,0,0,1)));
                }
                if(leg>=0 && !tendon(id))SpiderLegSkin.add(boss,leg,partial,root,origin,matrices,boxes);
                if(id==CROWN)boss.spider.addRootBoxes(-1,partial,boxes);
                if(id>=FIRST_ROOT && id<FIRST_LEG)boss.spider.addRootBoxes(id-FIRST_ROOT,partial,boxes);
            }
            var bounds=boxes.isEmpty()?new AABB(origin,origin):boxes.get(0).bounds;
            for(int i=1;i<boxes.size();i++)bounds=bounds.minmax(boxes.get(i).bounds);
            result.add(new HydraContact.Region(NAMES.get(id),List.copyOf(boxes),bounds));
        }
        return List.copyOf(result);
    }
    private static Vec3 axis(Matrix4f m,float x,float y,float z){return new Vec3(m.transformDirection(new Vector3f(x,y,z)));}
    private SpiderAnatomy(){}
}
