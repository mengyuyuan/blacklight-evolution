package com.codex.blacklight.client.renderer;

import com.codex.blacklight.combat.ConsumeReceiverEffect;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConsumeSkinPathTest {
    private static AdaptiveRagdoll.Part part(int region, Vec3 pivot, Vec3 center, double radius) {
        Object id = new Object();
        var vertices = List.of(vertex(id, center.add(radius,radius,radius)), vertex(id, center.add(-radius,-radius,-radius)));
        return new AdaptiveRagdoll.Part(id,vertices,new AdaptiveRagdoll.Node(id,null,pivot,new Quaternionf(),region));
    }

    private static AdaptiveRagdoll.Vertex vertex(Object id, Vec3 position) {
        return new AdaptiveRagdoll.Vertex(id,null,position,new Vec3(0,1,0),0,0,-1,0,0);
    }

    @Test void foldedHandRoutesThroughShoulderEvenWhenItTouchesTheChest() {
        Vec3 neck=new Vec3(0,1.55,0), hip=new Vec3(0,.9,0), shoulder=new Vec3(.30,1.50,0);
        Vec3 spine=hip.lerp(neck,.34);
        var arm=part(2,shoulder,new Vec3(.23,1.28,0),.09);
        var parts=List.of(part(32,hip,spine,.22),part(1,neck,neck.add(0,.18,0),.17),arm,
                part(8,hip.add(.1,0,0),new Vec3(.1,.48,0),.1),part(16,hip.add(-.1,0,0),new Vec3(-.1,.48,0),.1));
        var path=ConsumeSkinPath.bind(parts);
        var cue=ConsumeReceiverEffect.forClip("normal");
        var weights=ConsumeSkinPath.playback(cue).sample(.20F);
        assertTrue(path.coverage(weights,32,spine)>.12F);
        assertEquals(0,path.coverage(weights,2,spine),1e-5);
        arm.pos=new Vec3(40,72,9);
        arm.scale=.08F;
        assertEquals(0,path.coverage(weights,2,spine),1e-5);
    }

    @Test void differentWeaponsStartAtTheirAuthoredJoints() {
        Vec3 neck=new Vec3(0,1.5,0),hip=new Vec3(0,.9,0),ankle=new Vec3(-.1,.1,0);
        var parts=List.of(part(32,hip,new Vec3(0,1.2,0),.2),part(1,neck,neck.add(0,.2,0),.2),
                part(16,hip.add(-.1,0,0),new Vec3(-.1,.5,0),.1));
        var path=ConsumeSkinPath.bind(parts);
        var hammer=ConsumeSkinPath.playback(ConsumeReceiverEffect.forClip("hammer")).sample(.08F);
        assertTrue(path.coverage(hammer,1,neck)>0);
        assertEquals(0,path.coverage(hammer,16,ankle),1e-5);
        var muscle=ConsumeSkinPath.playback(ConsumeReceiverEffect.forClip("muscle_heart")).sample(.01F);
        assertEquals(1,path.coverage(muscle,1,neck),1e-5);
        // The authored voxel leg has five pixels each above and below the knee,
        // followed by a toe one pixel down and two forward.
        Vec3 boot=hip.add(-.1,0,0).lerp(ankle,10/(10+Math.sqrt(5)));
        assertTrue(path.coverage(muscle,16,boot)<.02);
    }
}
