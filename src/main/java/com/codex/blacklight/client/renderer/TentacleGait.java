package com.codex.blacklight.client.renderer;

import net.minecraft.world.phys.Vec3;
import java.util.function.UnaryOperator;

/** Visual contact solver. Planted tips stay in world space while the body advances. */
public final class TentacleGait {
    public static final int ARMS=8;
    private final Vec3[] feet=new Vec3[ARMS],start=new Vec3[ARMS],goal=new Vec3[ARMS];
    private Vec3 last;
    private double lastTime,started=-100,duration=5;
    private int swinging=-1,nextGroup;
    private boolean wasGrounded;

    public void update(double now,Vec3 body,float yaw,boolean grounded,double reach,UnaryOperator<Vec3> ground){
        boolean reset=last==null||body.distanceToSqr(last)>9||now<lastTime||now-lastTime>10||grounded!=wasGrounded;
        if(reset){
            for(int i=0;i<ARMS;i++)feet[i]=grounded?ground.apply(home(body,yaw,reach,i)):home(body,yaw,reach,i);
            swinging=-1;nextGroup=0;
        }
        double dt=reset?0:now-lastTime;
        Vec3 velocity=dt>0.0001?body.subtract(last).scale(1/dt):Vec3.ZERO;
        if(swinging>=0){
            double t=Math.min(1,Math.max(0,(now-started)/duration)),ease=t*t*(3-2*t);
            for(int i=swinging;i<ARMS;i+=2)feet[i]=start[i].lerp(goal[i],ease).add(0,Math.sin(t*Math.PI)*.16,0);
            if(t>=1){nextGroup=1-swinging;swinging=-1;}
        }
        if(grounded&&swinging<0&&!reset){
            double error=0;
            for(int i=nextGroup;i<ARMS;i+=2)error=Math.max(error,feet[i].distanceToSqr(home(body,yaw,reach,i)));
            if(error>.24*.24){
                swinging=nextGroup;started=now;duration=Math.max(3,6-velocity.horizontalDistance()*12);
                Vec3 lead=new Vec3(velocity.x,0,velocity.z).scale(duration*.8);
                if(lead.lengthSqr()>.30*.30)lead=lead.normalize().scale(.30);
                for(int i=swinging;i<ARMS;i+=2){start[i]=feet[i];goal[i]=ground.apply(home(body,yaw,reach,i).add(lead));}
            }
        }else if(!grounded){
            for(int i=0;i<ARMS;i++)feet[i]=home(body,yaw,reach*.7,i).add(0,-.12,0);
        }
        last=body;lastTime=now;wasGrounded=grounded;
    }
    private static Vec3 home(Vec3 body,float yaw,double reach,int arm){
        double a=arm*Math.PI*2/ARMS+.12*Math.sin(arm*2.4);
        reach*=1+.13*Math.sin(arm*1.7);
        return body.add(new Vec3(Math.cos(a)*reach,.025,Math.sin(a)*reach).yRot((float)Math.toRadians(-yaw)));
    }
    public Vec3 tip(int arm){return feet[arm];}
    public boolean swinging(int arm){return swinging>=0&&(arm&1)==swinging;}
    public double swingProgress(double now){return swinging<0?0:Math.min(1,Math.max(0,(now-started)/duration));}
}
