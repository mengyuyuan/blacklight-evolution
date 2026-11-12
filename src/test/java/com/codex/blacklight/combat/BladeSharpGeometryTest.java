package com.codex.blacklight.combat;

import com.codex.blacklight.power.PowerForm;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BladeSharpGeometryTest {
    @Test void approvedBladeIsNotEnlargedAgainByTheContactSampler(){
        for(var face:WeaponCutContact.geometry(PowerForm.BLADE))for(var p:List.of(face.a(),face.b(),face.c()))
            assertEquals(p,WeaponCutContact.surface(p,PowerForm.BLADE,face.bone(),WeaponAttackScales.Shape.IDENTITY));
    }
    @Test void bladeContactUsesTheSameExpandedVerticesAsTheVisibleModel()throws Exception{
        com.google.gson.JsonObject json;
        try(var input=getClass().getResourceAsStream("/assets/blacklight/models/form_blade.json")){assertNotNull(input);json=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(input,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();}
        var visible=new HashSet<net.minecraft.world.phys.Vec3>();
        for(var element:json.getAsJsonObject("joints").getAsJsonArray("Hand_R"))for(var vertex:element.getAsJsonObject().getAsJsonArray("p")){
            var p=vertex.getAsJsonArray();var v=json.has("approvedProportions")?new net.minecraft.world.phys.Vec3(p.get(0).getAsFloat(),p.get(1).getAsFloat(),p.get(2).getAsFloat()):com.codex.blacklight.client.model.BladeShape.point(p.get(0).getAsFloat(),p.get(1).getAsFloat(),p.get(2).getAsFloat());
            visible.add(new net.minecraft.world.phys.Vec3((float)v.x,(float)v.y,(float)v.z));
        }
        for(var face:WeaponCutContact.geometry(PowerForm.BLADE))for(var p:List.of(face.a(),face.b(),face.c()))assertTrue(visible.contains(p),"Contact geometry differs from the rendered blade: "+p);
    }
    @Test void bladeBacksAreRealDistinctFacesAndCannotAuthorizeASever(){
        for(var form:List.of(PowerForm.CLAWS,PowerForm.BLADE)){
            var sharp=WeaponCutContact.geometry(form);var blunt=WeaponCutContact.bluntGeometry(form);
            assertFalse(blunt.isEmpty());assertTrue(sharp.stream().allMatch(WeaponCutContact.Face::sharp));assertTrue(blunt.stream().noneMatch(WeaponCutContact.Face::sharp));
            for(var face:blunt){
                assertTrue(face.b().subtract(face.a()).cross(face.c().subtract(face.a())).lengthSqr()>1e-12);
                assertFalse(sharp.stream().anyMatch(f->f.bone().equals(face.bone())&&Set.of(f.a(),f.b(),f.c()).equals(Set.of(face.a(),face.b(),face.c()))));
                var hit=new WeaponCutContact.Contact(32,face.edge(),face.a(),new net.minecraft.world.phys.Vec3(0,0,1),net.minecraft.world.phys.Vec3.ZERO);
                assertEquals(0,new WeaponCutContact.Report(WeaponCutContact.Kind.NONE,List.of(hit)).severMask());
            }
        }
        assertTrue(WeaponCutContact.bluntGeometry(PowerForm.HAMMERFISTS).isEmpty());
    }
    @Test void forwardAndReverseBladesDoNotCutThroughTheBluntRootBetweenThem(){
        var faces=WeaponCutContact.geometry(PowerForm.BLADE);
        var groups=faces.stream().collect(Collectors.groupingBy(WeaponCutContact.Face::edge));
        assertEquals(2,groups.size(),"The exported continuous blade has two disconnected sharpened patches");
        var axes=groups.values().stream().map(List::getFirst).toList();
        assertNotEquals(axes.get(0).root(),axes.get(1).root());assertNotEquals(axes.get(0).tip(),axes.get(1).tip());
        for(var group:groups.values()){
            var axis=group.getFirst();
            for(var face:group){assertEquals(axis.root(),face.root());assertEquals(axis.tip(),face.tip());}
            assertEquals(6,axis.profile().size());
            for(var point:axis.profile())assertTrue(group.stream().anyMatch(f->WeaponCutContact.triangleBox(f.a(),f.b(),f.c(),new net.minecraft.world.phys.AABB(point,point).inflate(.025))),"Profile must stay on its own sharp patch");
        }
        assertTrue(axes.stream().anyMatch(a->a.profile().stream().anyMatch(p->{var axis=a.tip().subtract(a.root()).normalize();return p.subtract(a.root()).cross(axis).length()>.005;})),"Curved blade must not collapse to its endpoint chord");
    }
    @Test void eachHandRetainsItsFourIndependentClaws(){
        var faces=WeaponCutContact.geometry(PowerForm.CLAWS);
        for(var hand:List.of("Hand_R","Hand_L"))assertEquals(4,faces.stream().filter(f->f.bone().equals(hand)).map(WeaponCutContact.Face::edge).distinct().count(),hand);
    }
    @Test void theExportedBladeHasBluntOnlyContactLocations(){
        var sharp=WeaponCutContact.geometry(PowerForm.BLADE);var blunt=WeaponCutContact.bluntGeometry(PowerForm.BLADE);
        assertTrue(blunt.stream().anyMatch(face->{
            var at=face.a().add(face.b()).add(face.c()).scale(1.0/3);var box=new net.minecraft.world.phys.AABB(at,at).inflate(.004);
            return WeaponCutContact.triangleBox(face.a(),face.b(),face.c(),box)
                    &&sharp.stream().noneMatch(edge->WeaponCutContact.triangleBox(edge.a(),edge.b(),edge.c(),box));
        }),"A real back/root contact must remain a hit without pretending to be a sharp edge");
    }
}
