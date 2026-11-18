package com.codex.blacklight.client.model;

import com.codex.blacklight.military.vehicle.VehicleDeformation.Dent;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.lang.reflect.Proxy;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HelicopterDamageModelTest {
    @Test void dentsMoveActualBellyAndSlidingDoorVerticesAndStayOnTheirOwnAircraft() {
        var model=new HelicopterModel();
        // The belly bottom is y=.7. The starboard door's bone pivot is (1.46,1.15,-.2).
        var belly=new Dent("body",new Vec3(0,.7,0),new Vec3(0,1,0),.75,.35);
        var door=new Dent("door_right",new Vec3(.06,.4,-.2),new Vec3(-1,0,0),.75,.35);
        var clean=render(model,List.of());
        var bellyOnly=render(model,List.of(belly));
        var doorOnly=render(model,List.of(door));
        assertTrue(bellyOnly[0]>clean[0] && bellyOnly[1]>clean[1]+4);
        assertEquals(clean[2],bellyOnly[2],"belly strike leaves the door smooth");
        assertTrue(doorOnly[0]>clean[0] && doorOnly[2]>clean[2]+4);
        assertEquals(clean[1],doorOnly[1],"door strike leaves the belly smooth");
        assertArrayEquals(clean,render(model,List.of()),"a second craft uses its own undamaged mesh");
    }

    private static int[] render(HelicopterModel model,List<Dent> dents) {
        int[] counts=new int[3];
        var sink=(VertexConsumer)Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),new Class<?>[]{VertexConsumer.class},(proxy,method,args)->{
            if((method.getName().equals("addVertex") || method.getName().equals("vertex")) && args.length>=3 && args[0] instanceof Number){
                double x=((Number)args[0]).doubleValue(),y=((Number)args[1]).doubleValue(),z=((Number)args[2]).doubleValue();
                counts[0]++;
                if(Math.abs(x)<.22 && Math.abs(z)<.22 && y>.9 && y<1.08)counts[1]++;
                if(x>1.08 && x<1.32 && Math.abs(y-1.55)<.18 && Math.abs(z+.4)<.18)counts[2]++;
            }
            return method.getReturnType()==void.class?null:proxy;
        });
        model.render(new PoseStack(),sink,15728880,0,dents);
        return counts;
    }
}
