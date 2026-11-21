package com.codex.blacklight.client.model;

import com.codex.blacklight.animation.PlayerMotionController;
import com.codex.blacklight.client.renderer.ConsumeAnatomy;
import org.joml.Vector3f;

/** Raised folds for block skins. The placement is fitted to the MC silhouette; the
 * material and its travelling surface pattern come from the original character shader. */
final class ConsumeBodyCords {
    private ConsumeBodyCords(){}
    // Short growths at the exterior strands' attachment points leave the skin between them open.
    private record Route(float radius,float[] path){}
    private static final Route[] ROUTES={
        new Route(.36F,new float[]{-1.9F,6,2.25F, .1F,3.5F,2.55F, 3.1F,.65F,2.6F, 5.7F,-.65F,1.55F, 7.1F,1.05F,.7F}),
        new Route(.31F,new float[]{1.8F,7.3F,2.2F, -.6F,5.4F,2.65F, -3.3F,2.9F,2.5F, -5.6F,-.50F,1.70F, -6.9F,.85F,.88F}),
        new Route(.21F,new float[]{4.7F,.20F,2.05F, 6.12F,1.0F,2.4F, 7.55F,2.3F,.85F, 7.8F,4F,-.4F}),
        new Route(.18F,new float[]{-4.95F,1.1F,1.9F, -6.3F,1.7F,2.36F, -7.7F,2.9F,.92F, -7.85F,4.2F,.05F})
    };
    private static Vector3f point(float[] p,int i){
        int k=3*Math.max(0,Math.min(p.length/3-1,i));return new Vector3f(p[k],p[k+1],p[k+2]);
    }
    private static Vector3f curve(float[] p,float u){
        int last=p.length/3-1;
        float x=Math.max(0,Math.min(1,u))*last;int j=Math.min(last-1,(int)x);float t=x-j;
        var a=point(p,j-1);var b=point(p,j);var c=point(p,j+1);var d=point(p,j+2);
        float t2=t*t,t3=t2*t;
        return new Vector3f(a).mul((2*t2-t-t3)*.5F).fma(1+(3*t3-5*t2)*.5F,b)
                .fma((t+4*t2-3*t3)*.5F,c).fma((t3-t2)*.5F,d);
    }
    private static String mercerBone(String source){
        return switch(source){
            case "Root" -> "AllBody";case "Torso","Chest" -> "UpBody";
            case "Arm_R" -> "RightArm";case "Hand_R" -> "RightForeArm";
            case "Arm_L" -> "LeftArm";case "Hand_L" -> "LeftForeArm";
            default -> throw new IllegalArgumentException(source);};
    }
    static void add(ConsumeBodySurface mesh,boolean slim,MercerRig mercer){
        int routeId=0;
        for(var route:ROUTES){
            final int sides=6,steps=(route.path.length/3-1)*4;
            ConsumeBodySurface.Point[] prev=null;
            Vector3f lastNormal=new Vector3f(0,0,1);
            for(int row=0;row<=steps;row++){
                float u=row/(float)steps;
                Vector3f center=curve(route.path,u);
                Vector3f tangent=curve(route.path,u+.002F).sub(curve(route.path,u-.002F)).normalize();
                lastNormal.fma(-lastNormal.dot(tangent),tangent).normalize();
                Vector3f binormal=new Vector3f(tangent).cross(lastNormal).normalize();
                int region=Math.abs(center.x)<4.35F?ConsumeAnatomy.BODY:center.x>0?ConsumeAnatomy.RIGHT_ARM:ConsumeAnatomy.LEFT_ARM;
                var blend=BlacklightSkinWeights.sample(region,center.y,false);
                var bones=PlayerMotionController.skeleton();
                int a=mercer==null?bones.searchJointByName(blend.first()).getId():mercer.index(mercerBone(blend.first()));
                int b=mercer==null?bones.searchJointByName(blend.second()).getId():mercer.index(mercerBone(blend.second()));
                ConsumeBodySurface.Point[] ring=new ConsumeBodySurface.Point[sides+1];
                float taper=(float)Math.pow(Math.max(0,Math.sin(Math.PI*u)),.53);
                float size=route.radius*(.06F+.94F*taper);
                for(int s=0;s<=sides;s++){
                    double theta=2*Math.PI*s/sides+u*1.8;
                    Vector3f normal=new Vector3f(lastNormal).mul((float)Math.cos(theta)).fma((float)Math.sin(theta),binormal);
                    Vector3f v=new Vector3f(center).fma(size*(1+.13F*(float)Math.sin(3*theta+u*11)),normal);
                    // Map continuous human-width shoulder folds to each rig's actual jacket and sleeves.
                    float x=v.x,y=1.501F-v.y/16,z=v.z/16;
                    if(mercer!=null){
                        float abs=Math.abs(x);
                        x=Math.copySign(Math.min(abs,4)*.053F+Math.max(0,abs-4)*.030F,x);
                        float sleeve=Math.min(1,Math.max(0,(abs-3.5F)/2));
                        y=1.56F-.056F*v.y-.02F*sleeve;z*=.85F-.24F*sleeve;
                    }else{
                        if(slim&&Math.abs(x)>4)x=Math.copySign(4+(Math.abs(x)-4)*.75F,x);
                        x/=16;
                    }
                    float texU=.23F+.097F*routeId+.09F*s/sides;
                    float texV=.14F+u*.68F;
                    ring[s]=new ConsumeBodySurface.Point(x,y,z,normal.x,-normal.y,normal.z,a,b,blend.secondWeight(),
                            texU,texV,.025F+.26F*u+.014F*(routeId%3),.46F);
                }
                if(prev!=null)mesh.addCordStrip(region,prev,ring);
                prev=ring;
            }
            routeId++;
        }
    }
}
