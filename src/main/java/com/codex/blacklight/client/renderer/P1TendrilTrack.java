package com.codex.blacklight.client.renderer;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.phys.Vec3;

/** Fields of one original FIG TendrilTrack. World-space interpolation is the Minecraft adapter. */
record P1TendrilTrack(String joint,double width,double forward,double reverse,int sections,
                      double speed,double wavelength,double amplitude,double rampUp,double rampDown) {
    private static final double SCALE=com.codex.blacklight.combat.DevastatorEffectTimeline.WORLD_SCALE;
    private static final Map<String,List<P1TendrilTrack>> FAMILIES=load();

    private static Map<String,List<P1TendrilTrack>> load() {
        var families=new HashMap<String,List<P1TendrilTrack>>();
        try (var in=P1TendrilTrack.class.getResourceAsStream("/assets/blacklight/models/devastators/tendril_tracks.json")) {
            if (in==null) return Map.of();
            var data=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
            for (var family:data.entrySet()) {
                var tracks=new java.util.ArrayList<P1TendrilTrack>();
                for (var element:family.getValue().getAsJsonArray()) {
                    var row=element.getAsJsonObject();
                    tracks.add(new P1TendrilTrack(row.get("joint").getAsString(),row.get("Scale").getAsDouble(),
                            row.get("ForwardVelocity").getAsDouble(),row.get("ReverseVelocity").getAsDouble(),
                            row.get("SegmentCount").getAsInt(),row.get("WaveSpeed").getAsDouble(),row.get("WaveLength").getAsDouble(),
                            row.get("WaveAmplitude").getAsDouble(),row.get("WaveAmplitudeRampUp").getAsDouble(),
                            row.get("WaveAmplitudeRampDown").getAsDouble()));
                }
                families.put(family.getKey(),List.copyOf(tracks));
            }
        } catch (java.io.IOException error) { throw new IllegalStateException("Cannot read P1 tendril tracks",error); }
        return Map.copyOf(families);
    }

    static P1TendrilTrack select(String family,int branch) {
        var rows=FAMILIES.get(family);
        return rows==null ? null : rows.get(branch%rows.size());
    }

    Vec3 root(DevastatorEffectRenderer.BodyRoots body,Vec3 target) {
        if (joint.equals("Shoulder_R")) return body.rightShoulder();
        if (joint.equals("Shoulder_L")) return body.leftShoulder();
        var band=joint.equals("Spine_1")?body.torsoSkin():body.chestSkin();
        int face=0;
        for (int i=1;i<band.length;i++) if (target.distanceToSqr(band[i])<target.distanceToSqr(band[face])) face=i;
        return joint.equals("Spine_2")?body.torsoSkin()[face].lerp(body.chestSkin()[face],.5):band[face];
    }

    void render(PoseStack stack,int light,Vec3 root,Vec3 end,double age,double remaining,
                double phase,boolean detailed) {
        Vec3 delta=end.subtract(root);
        double length=delta.length();
        if (length<.025) return;
        Vec3 side=delta.cross(new Vec3(0,1,0)).normalize();
        if (side.lengthSqr()<.5) side=new Vec3(1,0,0);
        double ramp=Math.min(1,Math.max(0,age/rampUp))*Math.min(1,Math.max(0,remaining/rampDown));
        String mesh=detailed?"skill_tendril_segment":"skill_tendril_segment_lod";
        Vec3 a=root;
        for (int i=0;i<sections;i++) {
            double t=(i+1.0)/sections;
            Vec3 b=sample(root,delta,side,length,t,age,phase,ramp);
            Vec3 middle=sample(root,delta,side,length,t-.5/sections,age,phase,ramp);
            Vec3 control=middle.scale(2).subtract(a.lerp(b,.5));
            DevastatorEffectRenderer.nativeStrand(stack,light,mesh,a,control,b,width,255,phase);
            a=b;
        }
    }

    private Vec3 sample(Vec3 root,Vec3 delta,Vec3 side,double length,double t,double age,double phase,double ramp) {
        double wave=Math.sin(phase+t*length/(wavelength*SCALE)*2*Math.PI-age*speed);
        return root.add(delta.scale(t)).add(side.scale(wave*Math.sin(Math.PI*t)*amplitude*SCALE*ramp));
    }
}
