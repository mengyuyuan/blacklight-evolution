package com.codex.blacklight.client.renderer;

import com.codex.blacklight.combat.GoddessEncounter;
import com.codex.blacklight.combat.GoddessRig;
import com.codex.blacklight.entity.EncounterMob;
import com.mojang.blaze3d.vertex.PoseStack;
import java.io.*;
import java.util.Objects;
import java.util.zip.GZIPInputStream;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Camera cuts, glove wraps and tearing fibres sampled from the paired Blender shot. */
public final class GoddessExecutionTracks {
    private static final int FRAMES=170, STRANDS=40;
    private static final int[] lengths=new int[STRANDS], colors=new int[STRANDS];
    private static final int[][] active=new int[3][FRAMES*STRANDS];
    private static final short[][] coordinates=new short[3][];
    private static final float[][] cameras=new float[3][FRAMES*7];
    private static final int[][] cuts=new int[3][FRAMES];
    private static final int stride;
    static {
        var file=GoddessExecutionTracks.class.getResourceAsStream("/assets/blacklight/models/greene_execution_strands.bin.gz");
        try(var in=new DataInputStream(new BufferedInputStream(new GZIPInputStream(Objects.requireNonNull(file))))){
            if(in.readInt()!=0x47584631 || in.readInt()!=FRAMES || in.readInt()!=STRANDS || in.readInt()!=3)
                throw new IOException("Invalid execution strand track");
            int width=0;
            for(int i=0;i<STRANDS;i++){lengths[i]=in.readUnsignedShort();colors[i]=in.readInt();width+=lengths[i]*4;}
            stride=width;
            for(int stage=0;stage<3;stage++){
                var xyz=coordinates[stage]=new short[width*FRAMES];
                for(int frame=0;frame<FRAMES;frame++){
                    int pos=frame*width;
                    for(int lane=0;lane<STRANDS;lane++){
                        for(int j=0;j<lengths[lane];j++,pos+=4){
                            for(int v=0;v<4;v++)xyz[pos+v]=in.readShort();
                            active[stage][frame*STRANDS+lane]|=Short.toUnsignedInt(xyz[pos+3]);
                        }
                    }
                    for(int i=0;i<7;i++)cameras[stage][frame*7+i]=in.readFloat();
                    cuts[stage][frame]=in.readUnsignedShort();
                }
            }
        }catch(IOException e){throw new ExceptionInInitializerError(e);}
    }
    private static float frame(EncounterMob boss,float partial){return Math.min(FRAMES-1,Math.max(0,boss.goddess.age(partial)*1.2F));}
    public record Shot(Vec3 eye,Vec3 focus,float lens){}
    public static Shot shot(EncounterMob boss,float partial){
        int stage=Integer.bitCount(boss.goddess.poseMask()&3);
        float f=frame(boss,partial);int a=(int)f,b=Math.min(FRAMES-1,a+1);
        float mix=cuts[stage][a]==cuts[stage][b]?f-a:0;
        var track=cameras[stage];float[] values=new float[7];
        for(int i=0;i<7;i++)values[i]=track[a*7+i]+(track[b*7+i]-track[a*7+i])*mix;
        var mat=GoddessRig.root(boss,partial);var origin=boss.getPosition(partial);
        return new Shot(origin.add(new Vec3(mat.transformPosition(new Vector3f(values[0],values[1],values[2])))),
                origin.add(new Vec3(mat.transformPosition(new Vector3f(values[3],values[4],values[5])))), values[6]);
    }
    static void draw(EncounterMob boss,float partial,PoseStack stack,MultiBufferSource buffers,int light){
        if(boss.goddess.action()!=GoddessEncounter.Action.EXTRACTING)return;
        int stage=Integer.bitCount(boss.goddess.poseMask()&3);
        float f=frame(boss,partial);int a=(int)f,b=Math.min(FRAMES-1,a+1);
        int p=a*stride,q=b*stride;float t=f-a;
        var xyz=coordinates[stage];
        // The charcoal atlas already carries a deep black base. Preserve its veins in cave lighting.
        int fibreLight=(light & 0xffff0000) | Math.max(light & 0xffff, 176);
        var out=buffers.getBuffer(RenderType.entityCutoutNoCull(ConsumeTendrilSegmentMesh.TEXTURE));
        for(int i=0;i<STRANDS;i++){
            int count=lengths[i];
            if((active[stage][a*STRANDS+i]|active[stage][b*STRANDS+i])>4){
                Vec3[] points=new Vec3[count];double[] widths=new double[count];
                for(int j=0;j<count;j++){
                    int m=p+j*4,n=q+j*4;
                    points[j]=new Vec3((xyz[m]+t*(xyz[n]-xyz[m]))/512.,(xyz[m+1]+t*(xyz[n+1]-xyz[m+1]))/512.,
                            (xyz[m+2]+t*(xyz[n+2]-xyz[m+2]))/512.);
                    int wa=Short.toUnsignedInt(xyz[m+3]),wb=Short.toUnsignedInt(xyz[n+3]);
                    widths[j]=(wa+t*(wb-wa))/16384.;
                }
                ConsumeTendrilSegmentMesh.draw(stack,out,fibreLight,points,widths,new Vec3(0,1,0),colors[i],false);
            }
            p+=count*4;q+=count*4;
        }
    }
    private GoddessExecutionTracks(){}
}
