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
        return armor&&key.equals("basic_walk")?Math.max(authored,.14*scale):authored;
    }
    public static double rateLimit(String key,boolean armor) {
        return armor&&key.equals("basic_walk")?1.2:8;
    }
    public static void apply(BlacklightPose pose,String key,boolean armor) {
        // A stride's root carries the authored pelvis twist and lateral weight
        // transfer. Removing its instantaneous heading straightens the whole gait,
        // even when every child joint is preserved. Only stationary/stop clips
        // need scene-heading correction; world movement never comes from Root.
        if(!key.startsWith("basic_idle")&&!key.equals("basic_brake")&&!key.equals("basic_sprint_brake"))return;
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
        // Preserve all authored limb poses; armor only changes the gait clock.
    }
}
