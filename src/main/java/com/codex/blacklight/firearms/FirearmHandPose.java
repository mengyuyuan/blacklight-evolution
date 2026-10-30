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
        var f=FirearmReloadMotion.sample(progress);double release=f.release(),sign=leftMain?-1:1;
        var grip=BlackwatchReferenceProfile.load().firstPersonGrip(weapon,true);
        return new Hand(!leftMain,sign*(grip[0]+.09*release),grip[1]-f.handDown(),grip[2]+f.handBack(),
                -.87+release*.30,-sign*(.60+f.bolt()*.24),-sign*(.60+release*.22));
    }
}
