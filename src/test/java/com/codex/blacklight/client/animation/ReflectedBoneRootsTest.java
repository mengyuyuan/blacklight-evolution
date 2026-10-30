package com.codex.blacklight.client.animation;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReflectedBoneRootsTest {
    static class Bone {final List<Bone> children=new ArrayList<>();}
    static class Base {private final Bone leg=new Bone();}
    static class Model extends Base {
        static final Bone IGNORED=new Bone();
        private final Bone torso=new Bone(),head=new Bone(),alias=torso;
        Model(){torso.children.add(head);}
    }
    @Test void privateFieldsAndInheritedPartsProduceOneCopyPerRootWithoutEditingTheSkeleton(){
        var model=new Model();var roots=ReflectedBoneRoots.find(model,Bone.class,b->b.children);
        assertEquals(2,roots.size());assertTrue(roots.containsValue(model.torso));assertFalse(roots.containsValue(model.head));
        assertFalse(roots.containsValue(Model.IGNORED));assertSame(model.head,model.torso.children.get(0));
        assertThrows(UnsupportedOperationException.class,()->roots.clear());
    }
    @Test void cyclesFailClosedAndEmptyModelsStayUnsupported(){
        class Cyclic {final Bone a=new Bone(),b=new Bone();Cyclic(){a.children.add(b);b.children.add(a);}}
        assertTrue(ReflectedBoneRoots.find(new Cyclic(),Bone.class,b->b.children).isEmpty());
        assertTrue(ReflectedBoneRoots.find(new Object(),Bone.class,b->b.children).isEmpty());
    }
    @Test void equalButDistinctRootsAreNotMergedByValueEquality(){
        class Equal {final Object a=new String("bone"),b=new String("bone");}
        assertEquals(2,ReflectedBoneRoots.find(new Equal(),Object.class,b->List.of()).size());
    }
}
