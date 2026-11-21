package com.codex.blacklight.client.model;

import com.codex.blacklight.animation.PlayerMotionController;
import com.codex.blacklight.client.renderer.ConsumeAnatomy;
import org.joml.Vector3f;

/** Raised folds for block skins. The placement is fitted to the MC silhouette; the
 * material and its travelling surface pattern come from the original character shader. */
final class ConsumeBodyCords {
    private ConsumeBodyCords(){}
    // Cross the shoulder and turn down the outside of the raised forearm. Secondary
    // folds share an origin or reconnect, leaving visible negative space under the arches.
    private record Route(float radius,float[] path){}
    private static final Route[] ROUTES={
        new Route(.78F,new float[]{-2,6,2.4F, 0,3,2.9F, 3.3F,.2F,3.1F, 5.7F,-1.1F,1.4F, 8.5F,1.3F,-.4F, 8.1F,4.3F,-2.2F, 6.7F,7,-2.6F, 4.1F,9.2F,-1.3F}),
        new Route(.61F,new float[]{2,8,2.5F, -.4F,6,3.1F, -3.6F,2.8F,3, -5.3F,-.8F,2.3F, -7.9F,-.5F,1, -8.8F,2.8F,-.2F, -8,6,-2.5F, -5.8F,8.8F,-2.2F}),
        new Route(.59F,new float[]{-3,1.4F,-2.2F, -.5F,3.9F,-3, 2.9F,6.1F,-2.9F, 4.4F,7.8F,-1, 4.4F,9.5F,1.1F, 1.8F,11.5F,2.7F, -2.3F,12.2F,1.9F}),
        new Route(.47F,new float[]{4.1F,1.1F,1.8F, 3.1F,2.8F,3.2F, -.5F,4.6F,3.5F, -3.9F,6.9F,2.9F, -4.5F,9,.5F, -2.3F,10.4F,-2.3F}),
        new Route(.44F,new float[]{1.9F,4.6F,2.7F, 3.9F,2.2F,3.5F, 5.9F,1.7F,3.6F, 8.1F,3.3F,2.7F, 8.8F,5.2F,.4F, 7.8F,6.3F,-1.8F}),
        new Route(.36F,new float[]{-2.5F,3,2.7F, -4.1F,1.3F,3.7F, -5.7F,.2F,3.5F, -7,2.3F,2.9F, -7.9F,4.3F,1.7F}),
        new Route(.38F,new float[]{8.1F,4.1F,-1.8F, 8.8F,6.1F,.3F, 7.9F,8.2F,2.6F, 5.8F,9.2F,2.7F, 4.2F,11.4F,.5F}),
        new Route(.30F,new float[]{-8.1F,4,-1.6F, -8.5F,5.8F,.6F, -7.4F,7.3F,2.6F, -5.3F,9.4F,2.5F, -4.2F,10.9F,.2F}),
        new Route(.30F,new float[]{-2.5F,9,2.7F, -.6F,7.8F,3.4F, 2,5,3.3F, 3.4F,3.8F,2.7F}),
        new Route(.24F,new float[]{-3.3F,4.4F,-2.5F, -4.6F,2,-3.3F, -6.1F,.4F,-3.1F, -8.3F,1.1F,-1.4F})
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
                            texU,texV,.025F+.31F*u+.014F*(routeId%3));
                }
                if(prev!=null)mesh.addCordStrip(region,prev,ring);
                prev=ring;
            }
            routeId++;
        }
    }
}
