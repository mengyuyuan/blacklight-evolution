package com.codex.blacklight.combat;

import com.codex.blacklight.infection.InfectedAttackStyle;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Short-lived marks from accepted quill contacts, in the receiving part's local coordinates. */
public final class QuillWedge {
    public record Site(String bone, int box, Vec3 local, Vec3 point) {
        boolean near(Site other) {
            return bone.equals(other.bone) && box == other.box && local.distanceToSqr(other.local) <= .26 * .26;
        }
    }
    private record Mark(UUID owner, Site site, long expires) {}
    private static final Map<Entity, ArrayList<Mark>> marks = new WeakHashMap<>();

    private QuillWedge() {}

    public static Site site(Entity receiver, LivingEntity target, Vec3 from, Vec3 end, Vec3 entry) {
        var body = AnimatedHurtBody.of(target);
        if (body != null) {
            var regions = body.hurtRegions(0);
            var hit = HydraHurtContact.ray(regions, from, end, InfectedAttackStyle.PIERCE);
            if (hit == null || !receiver.getBoundingBox().inflate(.05).contains(hit.point())) return null;
            for (var region : regions) {
                if (!region.bone().equals(hit.bone())) continue;
                for (int i = 0; i < region.boxes().size(); i++) {
                    var box = region.boxes().get(i);
                    if (box.contains(hit.point()))
                        return new Site(region.bone(), i, box.localPoint(hit.point()), hit.point());
                }
            }
            return null;
        }
        var local = entry.subtract(receiver.getBoundingBox().getCenter()).yRot((float)Math.toRadians(target.yBodyRot));
        double height = (entry.y - receiver.getBoundingBox().minY) / Math.max(.01, receiver.getBoundingBox().getYsize());
        String region = height > .82 ? "head" : height < .4 ? "leg" : "body";
        int side = Math.abs(local.x) < receiver.getBoundingBox().getXsize() * .23 ? 0 : local.x < 0 ? -1 : 1;
        return new Site(region, side, local, entry);
    }

    public static boolean matches(Entity receiver, UUID owner, Site site, long now) {
        var existing = marks.get(receiver);
        if (existing == null || site == null) return false;
        existing.removeIf(mark -> mark.expires <= now);
        return existing.stream().anyMatch(mark -> mark.owner.equals(owner) && mark.site.near(site));
    }

    public static void accepted(Entity receiver, UUID owner, Site site, long now, boolean heavy) {
        if (site == null) return;
        var existing = marks.computeIfAbsent(receiver, ignored -> new ArrayList<>());
        existing.removeIf(mark -> mark.expires <= now || (mark.owner.equals(owner)
                && (heavy ? mark.site.near(site) : mark.site.bone.equals(site.bone) && mark.site.box == site.box)));
        if (!heavy) {
            if (existing.size() >= 24) existing.remove(0);
            existing.add(new Mark(owner, site, now + 40));
        }
        if (existing.isEmpty()) marks.remove(receiver);
    }
}
