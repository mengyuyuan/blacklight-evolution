package com.codex.blacklight.client.renderer;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AuthoredGpuTransformsTest {
    @Test void cachedSpikeTransformsRetainEveryAuthoredVertexAndNormalAcrossGrowth() throws Exception {
        var stack=new PoseStack();
        stack.translate(-37.25,-68.5,18.75);
        stack.mulPose(new org.joml.Quaternionf().rotateYXZ(.43F,-.17F,.09F));
        try(var reader=new InputStreamReader(getClass().getResourceAsStream(
                "/assets/blacklight/models/devastators/spikes.json"),StandardCharsets.UTF_8)){
            var meshes=JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("meshes");
            int compared=0;
            for(var entry:meshes.entrySet()){
                var model=entry.getValue().getAsJsonObject();var p=model.getAsJsonArray("positions");var n=model.getAsJsonArray("normals");
                for(double growth:new double[]{0,.005,.2,.67,1})for(float yaw:new float[]{-2.1F,0,1.7F}){
                    var base=new Vec3(40.5,67.2,-16.75);double size=.73;
                    var pose=AuthoredDevastatorMesh.rigidPose(stack,base,yaw,growth,size);
                    var normal=AuthoredDevastatorMesh.rigidNormal(stack,yaw,growth);
                    double vertical=Math.max(.001,growth),horizontal=Math.sqrt(vertical),c=Math.cos(yaw),s=Math.sin(yaw);
                    for(int i=0;i<p.size();i+=3){
                        var local=new Vector3f(p.get(i).getAsFloat(),p.get(i+1).getAsFloat(),p.get(i+2).getAsFloat());
                        double x=local.x*size*horizontal,y=local.y*size*vertical,z=local.z*size*horizontal;
                        // Independent former CPU consumer, including its Y-up
                        // yaw sign and root translation before the world pose.
                        var expected=new Vector3f((float)(base.x+x*c-z*s),(float)(base.y+y),(float)(base.z+x*s+z*c));
                        stack.last().pose().transformPosition(expected);
                        var actual=pose.transformPosition(new Vector3f(local));
                        assertEquals(expected.x,actual.x,2e-5,entry.getKey()+" x");
                        assertEquals(expected.y,actual.y,2e-5,entry.getKey()+" y");
                        assertEquals(expected.z,actual.z,2e-5,entry.getKey()+" z");
                        double nx=n.get(i).getAsDouble()/horizontal,ny=n.get(i+1).getAsDouble()/vertical,nz=n.get(i+2).getAsDouble()/horizontal;
                        var expectedNormal=new Vector3f((float)(nx*c-nz*s),(float)ny,(float)(nx*s+nz*c)).normalize();
                        stack.last().normal().transform(expectedNormal).normalize();
                        var actualNormal=normal.transform(new Vector3f(n.get(i).getAsFloat(),n.get(i+1).getAsFloat(),n.get(i+2).getAsFloat())).normalize();
                        assertTrue(expectedNormal.dot(actualNormal)>.999999F,entry.getKey()+" normal");
                        compared++;
                    }
                }
            }
            assertTrue(compared>100_000,"Compare the complete full and LOD geometry");
        }
    }
    @Test void curveParametersPreserveWorldCoordinatesAndFixedPerStrandReferenceFarFromOrigin(){
        var root=new Vec3(15_000_000.25,73.5,-14_000_000.75);
        var control=root.add(-.35,.27,8.9);var tip=root.add(.2,-1.2,20);
        var curve=RigidBiomassMaterial.Curve.of(root,control,tip,-.02F,1.31,root.distanceTo(tip),1.7,.81);
        assertEquals(-.35,curve.bx(),1e-6);assertEquals(.27,curve.by(),1e-6);assertEquals(8.9,curve.bz(),1e-6);
        assertEquals(.2,curve.cx(),1e-6);assertEquals(-1.2,curve.cy(),1e-6);assertEquals(20,curve.cz(),1e-6);
        assertEquals(0,curve.referenceX()*curve.cx()+curve.referenceZ()*curve.cz(),1e-6);
        assertEquals(1,curve.referenceX()*curve.referenceX()+curve.referenceZ()*curve.referenceZ(),1e-6);
        assertEquals(root.distanceTo(tip),curve.length(),2e-6);
        assertEquals(1,curve.cosine()*curve.cosine()+curve.sine()*curve.sine(),1e-6);
        var before=new Matrix4f().translation(-15_000_000.25F,-73.5F,14_000_000.75F);
        // Subtracting the root before GPU curve evaluation prevents all control
        // offsets from being rounded to one-block precision in shader floats.
        var matrix=new Matrix4f(before).translate((float)root.x,(float)root.y,(float)root.z);
        var relative=matrix.transformPosition(new Vector3f(curve.bx(),curve.by(),curve.bz()));
        assertEquals(.27,relative.y,1e-6);
    }
}
