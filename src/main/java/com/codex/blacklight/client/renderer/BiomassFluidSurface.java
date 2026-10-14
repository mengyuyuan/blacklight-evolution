package com.codex.blacklight.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;

/** Small local liquid simulation: independently lagging masses feed a volume-conserving pixel surface.
 * World displacement advects the masses, so corners bend the tail instead of rotating a rigid mesh.
 */
public final class BiomassFluidSurface {
    private static final int N=25,COUNT=9;
    private static final double CELL=1D/16,VOLUME=.052;
    private final double[] x=new double[COUNT],z=new double[COUNT],vx=new double[COUNT],vz=new double[COUNT];
    private final double[] height=new double[N*N],target=new double[N*N];
    private Vec3 previous;
    private double stamp=Double.NaN, directionX, directionZ=1, moving, cycle;
    public void update(Vec3 position,double time,float scale){
        boolean reset=previous==null||!Double.isFinite(stamp)||time<stamp||time-stamp>10||position.distanceToSqr(previous)>9;
        double dt=reset?1:Math.max(0,Math.min(2,time-stamp));
        if(dt==0)return;
        double dx=reset?0:(position.x-previous.x)/scale,dz=reset?0:(position.z-previous.z)/scale;
        previous=position;stamp=time;
        if(reset){java.util.Arrays.fill(vx,0);java.util.Arrays.fill(vz,0);for(int i=0;i<COUNT;i++){double a=i*Math.PI*.25;x[i]=i==0?0:Math.cos(a)*.16;z[i]=i==0?0:Math.sin(a)*.16;}}
        double speed=Math.hypot(dx,dz)/dt;
        double response=1-Math.exp(-dt*.24);
        moving+=(Math.min(1,speed/.045)-moving)*response;
        if(speed>.003){
            double ax=dx/Math.hypot(dx,dz),az=dz/Math.hypot(dx,dz);
            directionX+=(ax-directionX)*response;directionZ+=(az-directionZ)*response;
            double length=Math.hypot(directionX,directionZ);
            if(length>.01){directionX/=length;directionZ/=length;}
        }
        if(reset){moving=0;cycle=0;}
        // One local contraction travels from the tail to the leading edge every ~0.9 seconds.
        // It keeps cycling at constant speed; straight movement cannot settle into a rigid puddle.
        cycle+=dt*(.10+.25*moving);
        int steps=(int)Math.ceil(dt*2);double h=dt/steps;
        for(int step=0;step<steps;step++)for(int i=0;i<COUNT;i++){
            double a=i*Math.PI*.25;
            // At rest the liquid exchanges volume between lobes, rather than bobbing as one object.
            double radius=i==0?0:.14+.045*Math.sin(time*.11+i*1.7);
            double tx=Math.cos(a)*radius,tz=Math.sin(a)*radius;
            double along=tx*directionX+tz*directionZ;
            double side=-tx*directionZ+tz*directionX;
            double pulse=Math.sin(cycle-along*10);
            double reach=moving*(.10*pulse+.065*Math.sin(cycle-side*12+i*.4));
            tx+=directionX*reach-directionZ*.045*moving*Math.sin(cycle+along*9+i);
            tz+=directionZ*reach+directionX*.045*moving*Math.sin(cycle+along*9+i);
            x[i]-=dx/steps;z[i]-=dz/steps;
            double spring=i==0?.24:.12+(i%3)*.025,damping=Math.pow(i==0?.48:.68,h);
            vx[i]=(vx[i]+(tx-x[i])*spring*h)*damping;vz[i]=(vz[i]+(tz-z[i])*spring*h)*damping;
            x[i]=Math.max(-.49,Math.min(.49,x[i]+vx[i]*h));z[i]=Math.max(-.49,Math.min(.49,z[i]+vz[i]*h));
        }
        double sum=0;
        for(int ix=0;ix<N;ix++)for(int iz=0;iz<N;iz++){
            double px=(ix-N/2)*CELL,pz=(iz-N/2)*CELL,density=0;
            for(int i=0;i<COUNT;i++){
                double radius=i==0?.34:.235+.018*Math.sin(time*.12+i),q=((px-x[i])*(px-x[i])+(pz-z[i])*(pz-z[i]))/(radius*radius);
                if(q<1)density+=(i==0?2.1:.68)*Math.pow(1-q,2);
            }
            // Local lobes extend ahead, filling alternately on either side; the rear pinches shut.
            double along=px*directionX+pz*directionZ,side=-px*directionZ+pz*directionX;
            for(int lobe=-1;lobe<=1;lobe+=2){
                double phase=cycle+lobe*.85;
                double reach=.25+.115*Math.sin(phase);
                double sideCenter=lobe*(.13+.045*Math.cos(phase));
                double q=(along-reach)*(along-reach)/(.205*.205)+(side-sideCenter)*(side-sideCenter)/(.155*.155);
                if(q<1)density+=moving*1.4*Math.pow(1-q,2);
            }
            // A thin connecting membrane carries material from the lagging core to the front.
            // Fast forward travel must not turn the main body into disconnected islands.
            double ex=directionX*.30-x[0],ez=directionZ*.30-z[0];
            double u=Math.max(0,Math.min(1,((px-x[0])*ex+(pz-z[0])*ez)/Math.max(.001,ex*ex+ez*ez)));
            double bx=px-(x[0]+ex*u),bz=pz-(z[0]+ez*u),bridge=1-(bx*bx+bz*bz)/(.18*.18);
            if(bridge>0)density+=moving*.8*bridge*bridge;
            // Strong enough to read from the following camera: raised material rolls forward,
            // not just the texture, while the density normalization preserves total volume.
            density*=1+moving*.55*Math.sin(cycle-along*10+side*3)
                +.12*Math.sin(px*15+pz*10-time*.30)*Math.sin(pz*12-time*.17);
            target[ix*N+iz]=density;sum+=density;
        }
        double gain=VOLUME/(Math.max(.00001,sum)*CELL*CELL),blend=reset?1:1-Math.exp(-dt*.55),total=0;
        for(int i=0;i<height.length;i++){height[i]+=(target[i]*gain-height[i])*blend;total+=height[i];}
        // The footprint spreads and contracts but its apparent material volume stays constant.
        double conserve=VOLUME/(Math.max(.00001,total)*CELL*CELL);
        for(int i=0;i<height.length;i++)height[i]*=conserve;
    }
    public void render(PoseStack stack,VertexConsumer out,int light,double time){
        for(int ix=0;ix<N;ix++)for(int iz=0;iz<N;iz++){
            double h=height[ix*N+iz];if(h<.004)continue;
            double x0=(ix-N/2-.5)*CELL,z0=(iz-N/2-.5)*CELL;
            double flow=Math.sin(ix*.73+iz*.61-time*.12)+Math.sin(ix*.24-iz*.9+time*.08);
            int color=flow>1.5?0xff494046:flow>.2?0xff302a31:0xff211e25;
            if(h>.10&&Math.sin(ix*.7+iz*.8-time*.18)> .97)color=0xff532b36;
            // Neighbour faces terminate at the neighbouring surface: no internal coplanar flicker.
            BiomassGeometry.liquidCell(stack,out,light,x0,z0,CELL,.012,h,color,
                ix>0?height[(ix-1)*N+iz]:0,ix<N-1?height[(ix+1)*N+iz]:0,
                iz>0?height[ix*N+iz-1]:0,iz<N-1?height[ix*N+iz+1]:0);
        }
    }
}
