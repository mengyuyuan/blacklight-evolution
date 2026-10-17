package com.codex.blacklight.animation;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Visual ground gait calibration; movement speed and combat poses remain authoritative. */
public final class GroundGaitPose {
    private GroundGaitPose() { }
    public static boolean ground(String key) {
        return key.startsWith("basic_idle") || key.equals("basic_walk") || key.equals("basic_run")
                || key.startsWith("basic_sprint") || key.equals("basic_brake");
    }
    public static double nominal(String key,double authored,double scale,boolean armor) {
        return armor&&key.equals("basic_walk")?Math.max(authored,.10*scale):authored;
    }
    public static double rateLimit(String key,boolean armor) {
        return armor&&key.equals("basic_walk")?1.65:8;
    }
    public static void apply(BlacklightPose pose,String key,boolean armor) {
        if(!ground(key))return;
        var root=pose.get("Root");
        if(root!=null) {
            // World-facing is already owned by movement/targeting. Source idle and
            // brake clips contain different scene headings, including a reverse stop.
            // Remove that heading while retaining the actual lean and cushioning.
            var forward=root.rotation().transform(new Vector3f(0,0,1));
            if(forward.x*forward.x+forward.z*forward.z>1e-6F)
                root.rotation().premul(new Quaternionf().rotationY(-(float)Math.atan2(forward.x,forward.z))).normalize();
            root.translation().x=0;root.translation().z=0;
        }
        if(!armor||!key.equals("basic_walk"))return;
        for(String name:new String[]{"Thigh_R","Thigh_L"}) {
            var thigh=pose.get(name);if(thigh==null)continue;
            float swing=thigh.rotation().getEulerAnglesXYZ(new Vector3f()).x;
            thigh.rotation().rotateX(Math.max(-.14F,Math.min(.14F,swing*.20F)));
        }
        // A small outward arm clearance gives the plates space without widening the model.
        var right=pose.get("Arm_R");if(right!=null)right.rotation().rotateZ(-.045F);
        var left=pose.get("Arm_L");if(left!=null)left.rotation().rotateZ(.045F);
    }
}
