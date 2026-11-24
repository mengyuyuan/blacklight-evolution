package com.codex.blacklight.combat;

import com.google.gson.JsonParser;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Uses the attachment and donor head of each actual mesh actor for both a posed attack and its receiver. */
public final class GoddessCrownColonies {
    public record Actor(int index,int donor,int bone,int row,int order,Vector3f anchor) {}
    static final List<Actor> ACTORS=new ArrayList<>();
    static final List<Actor> SPITTERS=new ArrayList<>();
    private static final int NATIVE=791;
    private static final int frames,rate;
    private static final short[][] mouths;
    private static InputStream resource(String name) throws IOException {
        var input=GoddessCrownColonies.class.getResourceAsStream("/assets/blacklight/models/"+name);
        if(input==null)throw new FileNotFoundException(name);
        return input;
    }
    static {
        try(var reader=new InputStreamReader(resource("greene_colonies.json"),StandardCharsets.UTF_8);
            var data=new DataInputStream(new BufferedInputStream(new GZIPInputStream(resource("greene_goddess_mouths.bin.gz"))))){
            if(data.readInt()!=0x474d5431)throw new IOException("Invalid goddess mouth clips");
            mouths=new short[data.readInt()][];frames=data.readInt();rate=data.readInt();
            for(int actor=0;actor<mouths.length;actor++){
                mouths[actor]=new short[3*frames];
                for(int i=0;i<3*frames;i++)mouths[actor][i]=data.readShort();
            }
            int[] count=new int[2];
            for(var value:JsonParser.parseReader(reader).getAsJsonArray()){
                var obj=value.getAsJsonObject();var p=obj.getAsJsonArray("point");
                var anchor=new Vector3f(p.get(0).getAsFloat(),p.get(1).getAsFloat(),p.get(2).getAsFloat());
                int bone=obj.get("bone").getAsInt(),donor=obj.get("actor").getAsInt();
                // Inner fringe and outer fringe unfold on opposite beats.
                int row=!obj.get("region").getAsString().equals("hair")?-1:bone==34?1:0;
                int order=row<0?0:count[row]++;
                var actor=new Actor(ACTORS.size(),donor,bone,row,order,anchor);
                ACTORS.add(actor);
                if(row>=0)SPITTERS.add(actor);
            }
        }catch(IOException e){throw new ExceptionInInitializerError(e);}
    }
    static Vector3f mouth(Actor actor,float ticks){
        float sample=Math.max(0,ticks)*rate/20F%frames;
        int a=(int)sample,b=(a+1)%frames;float f=sample-a;
        var track=mouths[actor.donor];
        return new Vector3f(track[3*a],track[3*a+1],track[3*a+2]).lerp(
                new Vector3f(track[3*b],track[3*b+1],track[3*b+2]),f).div(4096);
    }
    // Each hinge is behind the spitter's own chest, so it folds onto the adjacent hair.
    private static Matrix4f hinge(Actor actor,Matrix4f neutral,float fold){
        Vector3f pivot=neutral.transformPosition(new Vector3f(actor.anchor));
        return new Matrix4f().translate(pivot).rotateY((actor.order%3-1)*.22F*fold)
                .rotateZ((actor.row==1?.33F:-.28F)*fold).rotateX(-1.23F*fold)
                .translate(-pivot.x,-pivot.y,-pivot.z);
    }
    private static Matrix4f actorOffset(Actor actor,Matrix4f neutral,Matrix4f carrier,float fold,boolean fringe){
        var posed=new Matrix4f(carrier);
        if(fringe && actor.bone>=33){
            // Hair spreads flat against the floor. Feet stay attached while the donor keeps its own proportions.
            var foot=neutral.transformPosition(new Vector3f(actor.anchor));
            var grip=carrier.transformPosition(new Vector3f(foot));
            var rotation=carrier.getUnnormalizedRotation(new Quaternionf()).normalize();
            posed.translationRotate(grip.x,grip.y,grip.z,rotation).translate(-foot.x,-foot.y,-foot.z);
        }
        if(fold!=0)posed.mul(hinge(actor,neutral,fold));
        return posed;
    }
    static Vec3 position(Actor actor,float ticks,float fold,Matrix4f correction,Matrix4f root,Vec3 origin){
        var neutral=GoddessRig.idle(actor.bone,ticks);
        Vector3f p=neutral.transformPosition(new Vector3f(actor.anchor));
        p.add(mouth(actor,ticks));actorOffset(actor,neutral,correction,fold,true).transformPosition(p);root.transformPosition(p);
        return origin.add(new Vec3(p));
    }
    public static Matrix4f[] expanded(GoddessCrownCast cast,Matrix4f[] base,float ticks,float partial){
        if(base==null || !cast.acting())return base;
        // First 35 entries remain the body bones. Each following entry serves twelve donor joints.
        var offsets=Arrays.copyOf(base,GoddessRig.JOINTS+ACTORS.size());
        var neutral=new Matrix4f[GoddessRig.JOINTS];
        for(var actor:ACTORS){
            float bend=cast.colonyFold(actor,partial);
            boolean fringe=cast.inverted() && actor.row>=0 && actor.bone>=33;
            if((bend!=0 || fringe) && neutral[actor.bone]==null)neutral[actor.bone]=GoddessRig.idle(actor.bone,ticks);
            offsets[GoddessRig.JOINTS+actor.index]=bend==0&&!fringe?base[actor.bone]:
                    actorOffset(actor,neutral[actor.bone],base[actor.bone],bend,fringe);
        }
        return offsets;
    }
    public static int offsetIndex(Matrix4f[] offsets,int group,int carrier){
        return offsets!=null && offsets.length>GoddessRig.JOINTS && group>=NATIVE
                ?GoddessRig.JOINTS+(group-NATIVE)/12:carrier;
    }
    private GoddessCrownColonies(){}
}
