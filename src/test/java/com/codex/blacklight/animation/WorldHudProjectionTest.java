package com.codex.blacklight.animation;

import com.codex.blacklight.client.WorldHudProjection;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldHudProjectionTest {
    @Test void denBearingTracksVisibleTargetsAndKeepsOffscreenTargetsAtTheEdge() {
        var front = WorldHudProjection.waypointBearing(new org.joml.Vector4f(.5F, -.5F, 0, 2));
        assertEquals(.25F, front.x); assertEquals(-.25F, front.y);
        var right = WorldHudProjection.waypointBearing(new org.joml.Vector4f(7, 2, 0, -1));
        assertEquals(1F, right.x); assertTrue(right.y > 0);
        var behind = WorldHudProjection.waypointBearing(new org.joml.Vector4f(0, 0, 0, -1));
        assertEquals(0F, behind.x); assertEquals(-1F, behind.y);
    }
    @Test void worldPerspectiveProjectsBeforeGuiOrthographicReplacesIt() {
        var view=new Matrix4f();var projection=new Matrix4f().perspective((float)Math.toRadians(70),16F/9,.05F,512);
        var copy=new Matrix4f(projection);
        var center=WorldHudProjection.project(view,projection,30_000_000,100,-30_000_000,30_000_000,100,-30_000_010,640,360);
        assertTrue(center.onScreen());assertEquals(320,center.x(),.00001);assertEquals(180,center.y(),.00001);
        var right=WorldHudProjection.project(view,projection,0,0,0,2,0,-10,640,360);
        assertTrue(right.onScreen());assertTrue(right.x()>center.x());assertTrue(projection.equals(copy));
    }
    @Test void reversedFrontCameraAndBehindNearPlaneNeverDrawAWorldMarkerAtScreenCenter() {
        var projection=new Matrix4f().perspective((float)Math.toRadians(70),16F/9,.05F,512);
        var behind=WorldHudProjection.project(new Matrix4f(),projection,0,0,0,0,0,10,640,360);
        assertFalse(behind.inFront());assertFalse(behind.onScreen());
        var reverse=WorldHudProjection.project(new Matrix4f().rotateY((float)Math.PI),projection,0,0,0,0,0,10,640,360);
        assertTrue(reverse.onScreen());assertEquals(320,reverse.x(),.001);
        var outside=WorldHudProjection.project(new Matrix4f(),projection,0,0,0,40,0,-10,640,360);
        assertTrue(outside.inFront());assertFalse(outside.onScreen());
    }
}
