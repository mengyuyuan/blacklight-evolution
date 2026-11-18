package com.codex.blacklight.client.renderer;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RagdollRootSelectionTest {
    private record Part(Object key,int region,double size) {}
    private static Part pick(List<Part> parts,Map<Object,Object> parents){
        return RagdollRootSelection.pick(parts,Part::key,parents::get,Part::region,Part::size);
    }
    @Test void aWideWingCannotReplaceItsMeshBearingTorso(){
        var torso=new Part(new Object(),32,1);var wing=new Part(new Object(),2,50);
        assertSame(torso,pick(List.of(wing,torso),Map.of(wing.key,torso.key)));
    }
    @Test void followsMeshlessPivotsBeforeChoosingTheRoot(){
        var body=new Part(new Object(),32,1);var membrane=new Part(new Object(),32,100);
        Object shoulder=new Object(),arm=new Object();
        assertSame(body,pick(List.of(membrane,body),Map.of(membrane.key,arm,arm,shoulder,shoulder,body.key)));
    }
    @Test void siblingHumanoidPartsChooseTheTrunk(){
        Object root=new Object();var head=new Part(new Object(),1,10);var body=new Part(new Object(),32,1);var arm=new Part(new Object(),2,20);
        assertSame(body,pick(List.of(head,arm,body),Map.of(head.key,root,body.key,root,arm.key,root)));
    }
    @Test void unknownSingleMeshesAndMalformedCyclesRemainFinite(){
        var one=new Part(new Object(),0,1);assertSame(one,pick(List.of(one),Map.of()));
        var body=new Part(new Object(),32,1);var limb=new Part(new Object(),2,20);
        assertSame(body,pick(List.of(limb,body),Map.of(body.key,limb.key,limb.key,body.key)));
    }
}
