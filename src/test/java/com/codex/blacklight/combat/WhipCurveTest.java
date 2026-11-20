package com.codex.blacklight.combat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WhipCurveTest {
    @Test void ordinaryCableAndReturningHookHaveDistinctFatalContacts(){
        assertEquals(WeaponCutContact.Kind.BLUNT,WeaponCutContact.contactKind(WeaponMove.WHIP_LASH));
        assertEquals(WeaponCutContact.Kind.SLICE,WeaponCutContact.contactKind(WeaponMove.WHIP_RETURN));
        assertEquals(WeaponCutContact.Kind.SLICE,WeaponCutContact.contactKind(WeaponMove.WHIP_SWEEP_CHARGED));
        assertEquals(WeaponCutContact.Kind.PUNCTURE,WeaponCutContact.contactKind(WeaponMove.WHIP_LONGSHOT));
    }
    private JsonObject curve() {
        return JsonParser.parseString("""
                {"space":"actor-feet","radius":0.045,"collisionRadius":0.436066,
                "samples":[{"time":0,"points":[[0,1,0],[0,1,2]]},
                           {"time":1,"points":[[1,1,0],[1,1,16]]}]}
                """).getAsJsonObject();
    }

    @Test void interpolationUsesActorCoordinatesAndKeepsVisualThicknessSeparateFromHits() {
        var json = curve(); var curve = WhipCurve.parse(json, 1);
        assertEquals(.045, curve.radius(), 1e-8);
        assertEquals(.436066, curve.collisionRadius(), 1e-8);
        assertEquals(new WhipCurve.Point(.5, 1, 9), curve.sample(.5).get(1));
        assertEquals(new WhipCurve.Point(0, 1, 2), curve.sample(-1).get(1));
        assertEquals(new WhipCurve.Point(1, 1, 16), curve.sample(2).get(1));
        assertThrows(UnsupportedOperationException.class, () -> curve.samples().clear());
        assertThrows(UnsupportedOperationException.class, () -> curve.samples().getFirst().points().clear());
        assertThrows(IllegalArgumentException.class, () -> curve.sample(Double.NaN));
        json.getAsJsonArray("samples").get(1).getAsJsonObject().getAsJsonArray("points").get(1)
                .getAsJsonArray().set(2, new JsonPrimitive(30));
        assertEquals(16, curve.sample(1).get(1).forward());
    }

    @Test void rejectsUnboundedOrDesynchronizedCurvesBeforeTheyReachRenderingOrCombat() {
        var mismatched = curve();
        mismatched.getAsJsonArray("samples").get(1).getAsJsonObject().getAsJsonArray("points").remove(0);
        assertThrows(IllegalArgumentException.class, () -> WhipCurve.parse(mismatched, 1));
        var time = curve(); time.getAsJsonArray("samples").get(1).getAsJsonObject().addProperty("time", .8);
        assertThrows(IllegalArgumentException.class, () -> WhipCurve.parse(time, 1));
        var badPoint = curve(); badPoint.getAsJsonArray("samples").get(1).getAsJsonObject().getAsJsonArray("points")
                .get(1).getAsJsonArray().set(0, new JsonPrimitive(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> WhipCurve.parse(badPoint, 1));
        var missingCollision = curve(); missingCollision.remove("collisionRadius");
        assertThrows(IllegalArgumentException.class, () -> WhipCurve.parse(missingCollision, 1));
        var confusedRadius = curve(); confusedRadius.addProperty("radius", .436066);
        assertThrows(IllegalArgumentException.class, () -> WhipCurve.parse(confusedRadius, 1));
    }

    @Test void combatAndVisualDefinitionsReadTheSameValidatedCurve() {
        var main = LocalWeaponMotionTest.motion(); main.getAsJsonObject("blacklight").add("whipCurve", curve());
        var charge = LocalVisualMotionTest.visual(); charge.getAsJsonObject("blacklight").add("whipCurve", curve());
        assertEquals(LocalWeaponMotion.parse(main).whipCurve(), LocalVisualMotion.parse(charge).whipCurve());
    }
}
