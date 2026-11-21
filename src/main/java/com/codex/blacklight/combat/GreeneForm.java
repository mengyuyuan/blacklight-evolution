package com.codex.blacklight.combat;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.google.gson.JsonParser;
import com.codex.blacklight.entity.EncounterMob;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Both peers select the shell from the persisted armour wave and follow the same combat torso. */
public enum GreeneForm {
    SPIDER("spider", .82F), GODDESS("goddess", .54F);

    private record Patch(Vec3 center, Vec3 half) {}
    private final List<Patch> patches;
    private final float scale;
    private static final int TORSO = Arrays.asList(GreeneMotion.table().bones).indexOf("torso");
    private static final Matrix4f REST_INVERSE = GreeneMotion.table().transforms(GreeneMotion.sample("idle",0,true))[TORSO].invert();

    GreeneForm(String key, float scale) {
        this.scale = scale;
        try (var stream = GreeneForm.class.getResourceAsStream("/assets/blacklight/models/greene_form_contacts.json");
             var reader = new InputStreamReader(Objects.requireNonNull(stream), StandardCharsets.UTF_8)) {
            var result = new ArrayList<Patch>();
            for (var row : JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray(key)) {
                var a=row.getAsJsonArray();
                result.add(new Patch(new Vec3(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble()),
                        new Vec3(a.get(3).getAsDouble(),a.get(4).getAsDouble(),a.get(5).getAsDouble())));
            }
            patches = List.copyOf(result);
        } catch (Exception e) { throw new IllegalStateException("Invalid Greene contact mesh: "+key,e); }
    }

    public static GreeneForm of(EncounterMob boss) {
        return boss.greeneBattle().armourWave() == 1 ? SPIDER : GODDESS;
    }

    public Matrix4f transform(Matrix4f[] combatPose, float yaw) {
        return new Matrix4f().rotateY((float)Math.toRadians(180-yaw)).mul(combatPose[TORSO]).mul(REST_INVERSE).scale(scale);
    }

    /** The carapace and its legs, or the forearms and basal spiral, carry the three armour pools. */
    private int armourSlot(Vec3 center) {
        if(this==SPIDER) {
            if(center.x < -3.25) return 1;
            if(center.x > 3.25) return 0;
            if(center.y<7 && center.z<0) return 2;
        } else {
            if(center.y<6.0) return 2;
            if(center.y<16 && center.x < -2.6) return 1;
            if(center.y<16 && center.x > 1.35) return 0;
        }
        return -1;
    }

    void addSurfaces(List<GreeneContact.Surface> out, Matrix4f[] pose, float yaw, Vec3 origin, int broken) {
        var m = transform(pose,yaw);
        var x = new Vec3(m.transformDirection(new Vector3f(1,0,0)));
        var y = new Vec3(m.transformDirection(new Vector3f(0,1,0)));
        var z = new Vec3(m.transformDirection(new Vector3f(0,0,1)));
        for (var patch : patches) {
            var center = origin.add(new Vec3(m.transformPosition(patch.center.toVector3f())));
            int slot=armourSlot(patch.center);
            String region=slot>=0 && (broken & (1<<slot))==0 ? "weak_"+slot : "torso";
            out.add(new GreeneContact.Surface(region, HydraContact.affine(center,patch.half,x,y,z)));
        }
    }
}
