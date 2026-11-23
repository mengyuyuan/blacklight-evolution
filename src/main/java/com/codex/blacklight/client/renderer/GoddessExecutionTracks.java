package com.codex.blacklight.client.renderer;

import com.codex.blacklight.combat.GoddessEncounter;
import com.codex.blacklight.entity.EncounterMob;
import com.mojang.blaze3d.vertex.PoseStack;
import java.io.*;
import java.util.Objects;
import java.util.zip.GZIPInputStream;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;

/** Glove wraps and tearing fibres sampled from the paired Blender shot. */
public final class GoddessExecutionTracks {
    private static final int FRAMES=170, STRANDS=40;
    // Blender sorts the five pathways on the right sleeve after the left sleeve.
    private static final int FIRST_RIGHT_SLEEVE=35;
    private static final int[] lengths=new int[STRANDS], colors=new int[STRANDS];
    private static final int[][] active=new int[3][FRAMES*STRANDS];
    private static final short[][] coordinates=new short[3][];
    private static final net.minecraft.resources.ResourceLocation TISSUE=
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("blacklight","textures/block/tissue_fiber.png");
    private static final int stride;
    static {
        var file=GoddessExecutionTracks.class.getResourceAsStream("/assets/blacklight/models/greene_execution_strands.bin.gz");
        try(var in=new DataInputStream(new BufferedInputStream(new GZIPInputStream(Objects.requireNonNull(file))))){
            if(in.readInt()!=0x47584632 || in.readInt()!=FRAMES || in.readInt()!=STRANDS || in.readInt()!=3)
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
                }
            }
        }catch(IOException e){throw new ExceptionInInitializerError(e);}
    }
    private static float frame(EncounterMob boss,float partial){return Math.min(FRAMES-1,Math.max(0,boss.goddess.age(partial)*1.2F));}
    static void draw(EncounterMob boss,float partial,PoseStack stack,MultiBufferSource buffers,int light){
        if(boss.goddess.action()!=GoddessEncounter.Action.EXTRACTING)return;
        int stage=Integer.bitCount(boss.goddess.poseMask()&3);
        float f=frame(boss,partial);int a=(int)f,b=Math.min(FRAMES-1,a+1);
        int p=a*stride,q=b*stride;float t=f-a;
        var gloveDelta=new Vec3(com.codex.blacklight.combat.GoddessActor.gloveLocal(boss,partial))
                .subtract(com.codex.blacklight.combat.GoddessActor.localPoint(boss,1,partial));
        var xyz=coordinates[stage];
        // Preserve the fine red grain of the existing tissue texture in cave lighting.
        int fibreLight=(light & 0xffff0000) | Math.max(light & 0xffff, 176);
        var out=buffers.getBuffer(RenderType.entityCutoutNoCull(TISSUE));
        for(int i=0;i<STRANDS;i++){
            int count=lengths[i];
            if((active[stage][a*STRANDS+i]|active[stage][b*STRANDS+i])>4){
                Vec3[] points=new Vec3[count];double[] widths=new double[count];
                for(int j=0;j<count;j++){
                    int m=p+j*4,n=q+j*4;
                    points[j]=new Vec3((xyz[m]+t*(xyz[n]-xyz[m]))/512.,(xyz[m+1]+t*(xyz[n+1]-xyz[m+1]))/512.,
                            (xyz[m+2]+t*(xyz[n+2]-xyz[m+2]))/512.);
                    if(i>=FIRST_RIGHT_SLEEVE){
                        double palmWeight=Math.max(0,1-(double)j/(count-1)/.56);
                        points[j]=points[j].add(gloveDelta.scale(palmWeight*palmWeight*(3-2*palmWeight)));
                    }
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
