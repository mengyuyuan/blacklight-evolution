package com.codex.blacklight.infection;

/** Rooted secondary motion sampled from world time and the synced attack clock.
 * No frame integration, host scaling or writes to a shared native skeleton. */
public final class TissueMotion {
    public record State(float jaw,float split,float hook,float toe,float pulse) {}
    private static final State STILL=new State(0,0,0,0,0);
    private TissueMotion() {}

    public static State sample(long seed,double worldTicks,float growth,float melee,float strikeElapsed) {
        float g=Math.clamp(growth,0,1),hit=Math.clamp(melee,0,1);
        if(g==0)return STILL;
        double period=72+16*TissueGrowth.sample(seed,531);
        float breath=(float)Math.sin((worldTicks%period)/period*Math.PI*2+TissueGrowth.sample(seed,532)*Math.PI*2);
        float windup=strikeElapsed<0||strikeElapsed>=TissueOrgans.HIT?0:
                TissueAnatomy.smooth(strikeElapsed/TissueOrgans.WINDUP)
                *(1-TissueAnatomy.smooth((strikeElapsed-TissueOrgans.WINDUP)/(TissueOrgans.HIT-TissueOrgans.WINDUP)));
        float extension=TissueOrgans.extension(strikeElapsed);
        return new State(g*(.18F+.0125F*(1+breath)+.38F*hit+.09F*windup+.23F*extension),
                g*(.10F+.0075F*(1+breath)+.28F*hit+.06F*windup+.18F*extension),
                g*(.20F*hit-.035F*windup+.08F*extension),
                g*(.06F*hit+.025F*extension),
                g*(.010F*breath+.016F*windup+.012F*extension));
    }

    /** Radial breathing around each section's own centre. The root never moves;
     * long skin envelopes also pin their distal socket/contact section. */
    public static void flex(float[] vertices,int ringSize,float amount,boolean pinDistal) {
        if(amount==0||ringSize<3||vertices.length%(ringSize*3)!=0)return;
        int rings=vertices.length/(ringSize*3);if(rings<2)return;
        for(int ring=1;ring<rings;ring++) {
            if(pinDistal&&ring==rings-1)continue;
            float t=(float)ring/(rings-1),weight=pinDistal?(float)Math.sin(Math.PI*t):t;
            float scale=1+amount*weight;
            int first=ring*ringSize*3;
            float x=0,y=0,z=0;
            for(int i=first;i<first+ringSize*3;i+=3){x+=vertices[i];y+=vertices[i+1];z+=vertices[i+2];}
            x/=ringSize;y/=ringSize;z/=ringSize;
            for(int i=first;i<first+ringSize*3;i+=3) {
                vertices[i]=x+(vertices[i]-x)*scale;
                vertices[i+1]=y+(vertices[i+1]-y)*scale;
                vertices[i+2]=z+(vertices[i+2]-z)*scale;
            }
        }
    }
}
