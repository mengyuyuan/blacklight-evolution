package com.codex.blacklight.client.renderer;

import net.minecraft.world.phys.Vec3;

/** Authored square-section fibre swept along a supplied path. All dimensions are multiples of its local width. */
final class ConsumeFiberShape {
    interface Faces {
        void quad(int span,boolean fork,int side,Vec3 a,Vec3 b,Vec3 c,Vec3 d,float v0,float v1);
    }
    private static final double EPS=1e-8;
    private ConsumeFiberShape() {}

    static void draw(Vec3[] path,double[] widths,Vec3 initialSide,Faces faces){
        if(path.length<2||widths.length!=path.length)return;
        int first=0;
        while(first+1<path.length&&path[first].distanceToSqr(path[first+1])<EPS)first++;
        if(first+1>=path.length)return;
        Vec3 forward=path[first+1].subtract(path[first]).normalize();
        Vec3 side=perpendicular(initialSide,forward);
        Vec3[] previous=ring(path[first],side,forward,Math.max(0,widths[first]));
        if(widths[first]>0)faces.quad(first,false,4,previous[3],previous[2],previous[1],previous[0],0,.25F);
        for(int i=first;i+1<path.length;i++){
            Vec3 start=path[i],end=path[i+1],delta=end.subtract(start);
            if(delta.lengthSqr()<EPS)continue;
            Vec3 spanForward=delta.normalize(),endForward=spanForward;
            if(i+2<path.length){
                Vec3 next=path[i+2].subtract(end).normalize();
                Vec3 bisector=spanForward.add(next);
                if(bisector.lengthSqr()>.01)endForward=bisector.normalize();
            }
            double w0=Math.max(0,widths[i]),w1=Math.max(0,widths[i+1]);
            Vec3 middle=start.lerp(end,.5).add(forward.subtract(endForward).scale(delta.length()*.09));
            Vec3 midSide=perpendicular(side,spanForward);
            Vec3 endSide=perpendicular(midSide,endForward);
            double collarWidth=(w0+w1)*.57;
            Vec3[] collar=ring(middle,midSide,spanForward,collarWidth);
            Vec3[] next=ring(end,endSide,endForward,w1);
            if(w0+w1>1e-6){
                sleeve(faces,i,false,previous,collar,0,.5F);
                sleeve(faces,i,false,collar,next,.5F,1);
                // Isolated alternating side buds leave a clear unbroken main fibre.
                if(i%7==1||i%7==4)fork(faces,i,middle,midSide,spanForward,collarWidth,delta.length());
            }
            previous=next;forward=endForward;side=endSide;
        }
        if(widths[widths.length-1]>0)faces.quad(path.length-2,false,4,previous[0],previous[1],previous[2],previous[3],.75F,1);
    }

    private static Vec3 perpendicular(Vec3 previous,Vec3 forward){
        Vec3 side=previous.subtract(forward.scale(previous.dot(forward)));
        if(side.lengthSqr()<.0001){
            Vec3 axis=Math.abs(forward.y)<.75?new Vec3(0,1,0):new Vec3(1,0,0);
            side=axis.subtract(forward.scale(axis.dot(forward)));
        }
        return side.normalize();
    }

    private static Vec3[] ring(Vec3 at,Vec3 side,Vec3 forward,double width){
        Vec3 x=side.scale(width),z=forward.cross(side).scale(.72*width);
        return new Vec3[]{at.add(x).add(z),at.subtract(x).add(z),at.subtract(x).subtract(z),at.add(x).subtract(z)};
    }

    private static void sleeve(Faces faces,int span,boolean fork,Vec3[] a,Vec3[] b,float v0,float v1){
        for(int j=0;j<4;j++){
            int k=(j+1)%4;
            faces.quad(span,fork,j,a[j],a[k],b[k],b[j],v0,v1);
        }
    }

    private static void fork(Faces faces,int span,Vec3 middle,Vec3 side,Vec3 forward,double width,double length){
        Vec3 flank=(span%2==0?side:forward.cross(side)).scale((span%4<2)?1:-1);
        double reach=Math.min(width*2.9,length*.72);
        if(reach<.00005)return;
        Vec3 base=middle.add(flank.scale(.53*width));
        Vec3 bend=base.add(flank.scale(.81*reach)).add(forward.scale(.52*reach));
        Vec3 tip=bend.add(flank.scale(.27*reach)).add(forward.scale(.85*reach));
        Vec3 stem=bend.subtract(base).normalize(),point=tip.subtract(bend).normalize();
        Vec3 edge=perpendicular(forward,stem);
        Vec3[] opening=ring(base,edge,stem,width*.55);
        Vec3[] knuckle=ring(bend,perpendicular(edge,point),point,width*.32);
        sleeve(faces,span,true,opening,knuckle,0,.5F);
        for(int j=0;j<4;j++)faces.quad(span,true,j,knuckle[j],knuckle[(j+1)%4],tip,tip,.5F,1);
    }
}
