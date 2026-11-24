package com.codex.blacklight.client.renderer;

/** Skin points from the mouth seam, lip, chin and underside of the artist's fitted portrait. */
final class GoddessJaw {
    private static final float[][] POINTS={
        {-0.48287648F,0.29119632F,-0.94537818F,0.38291F},
        {-0.46704423F,0.18916968F,-0.89909941F,0.54332F},
        {-0.42839736F,0.31520993F,-0.93953985F,0.64869F},
        {-0.36939612F,0.46006757F,-0.98182577F,0.83121F},
        {-0.32722184F,0.35980669F,-0.92869741F,1.00000F},
        {-0.28783485F,0.43438548F,-0.94982266F,1.00000F},
        {-0.27297387F,0.47511312F,-0.96480143F,1.00000F},
        {-0.20694475F,0.16643076F,-0.81503576F,1.00000F},
        {-0.18895163F,0.27735317F,-0.85362214F,1.00000F},
        {-0.17792358F,0.34533784F,-0.87727189F,1.00000F},
        {-0.16322283F,0.40897509F,-0.90879774F,1.00000F},
        {-0.15222815F,0.47197458F,-0.93237597F,1.00000F},
        {-0.05169678F,0.43438548F,-0.83970976F,1.00000F},
        {-0.04977490F,0.47511312F,-0.86072206F,1.00000F},
        {-0.04256226F,0.35980669F,-0.79595846F,1.00000F},
        {0.02464051F,0.18916968F,-0.66982317F,0.54333F},
        {0.02524555F,0.46006757F,-0.79780138F,0.83121F},
        {0.03077796F,0.31520993F,-0.72542298F,0.64869F},
        {0.07026889F,0.29119632F,-0.68744236F,0.38291F},
    };
    private final GreeneGoddessRenderer.Group original, posed;
    private final int[] indices;
    private final float[] weights;
    GoddessJaw(GreeneGoddessRenderer.Group head) {
        original=head;
        posed=new GreeneGoddessRenderer.Group(head.transforms(),head.vertices().clone(),head.colors(),head.skin(),head.carrier(),head.joint());
        var found=new java.util.ArrayList<Integer>();
        var values=new java.util.ArrayList<Float>();
        float[] v=head.vertices();
        for(int i=0;i<v.length;i+=8)for(float[] row:POINTS){
            if(Math.abs(v[i]-row[0])>.00002F || Math.abs(v[i+1]-row[1])>.00002F || Math.abs(v[i+2]-row[2])>.00002F)continue;
            found.add(i);values.add(row[3]);break;
        }
        indices=found.stream().mapToInt(Integer::intValue).toArray();weights=new float[indices.length];
        for(int i=0;i<indices.length;i++)weights[i]=values.get(i);
    }
    GreeneGoddessRenderer.Group pose(float opening) {
        float[] rest=original.vertices(), live=posed.vertices();
        for(int i=0;i<indices.length;i++){
            int k=indices[i];float travel=weights[i]*opening;
            live[k]=rest[k]+travel*.043F;
            live[k+1]=rest[k+1]-travel*.31F;
            live[k+2]=rest[k+2]-travel*.092F;
        }
        return posed;
    }
}
