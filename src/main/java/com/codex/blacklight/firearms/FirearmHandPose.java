package com.codex.blacklight.firearms;

import com.codex.blacklight.client.model.BlackwatchReferenceProfile;

/** One camera-space binding shared by the live skin-arm renderer and actual-asset preview. */
public final class FirearmHandPose {
    public record Hand(boolean left,double x,double y,double z,double pitch,double yaw,double roll){}
    private FirearmHandPose(){}
    public static Hand primary(String weapon,boolean leftMain,double progress){
        var grip=BlackwatchReferenceProfile.load().firstPersonGrip(weapon,false);double sign=leftMain?-1:1;
        return new Hand(leftMain,sign*grip[0],grip[1],grip[2],-.90,sign*.40,sign*.35);
    }
    public static Hand support(String weapon,boolean leftMain,double progress){
        double p=Double.isFinite(progress)?Math.max(0,Math.min(1,progress)):0,release=Math.pow(Math.sin(Math.PI*p),2),sign=leftMain?-1:1;
        var grip=BlackwatchReferenceProfile.load().firstPersonGrip(weapon,true);
        return new Hand(!leftMain,sign*(grip[0]+.10*release),grip[1]-.18*release,grip[2]+.30*release,
                -.87+release*.20,-sign*.60,-sign*(.60+release*.15));
    }
}
