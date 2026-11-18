package com.codex.blacklight.military.aircraft;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import static org.junit.jupiter.api.Assertions.*;

class HelicopterArmamentTest {
    @Test void shotAxisMatchesRenderedTurretUnderYawBankAndDepression(){
        for(float heading:new float[]{-179,-90,0,125})for(float bank:new float[]{-14,0,14})for(float yaw:new float[]{-55,0,55})for(float pitch:new float[]{0,35,65}){
            var matrix=new Matrix4f().rotateY((float)Math.toRadians(heading)).rotateZ((float)Math.toRadians(bank)).rotateY((float)Math.toRadians(yaw)).rotateX((float)Math.toRadians(pitch));
            var rendered=matrix.transformDirection(new Vector3f(0,0,1));
            var shot=HelicopterArmament.rotate(HelicopterArmament.direction(yaw,pitch),heading,bank);
            assertEquals(rendered.x,shot.x,1e-6);assertEquals(rendered.y,shot.y,1e-6);assertEquals(rendered.z,shot.z,1e-6);
        }
    }
    @Test void eachArticulatedMountConvergesOnTheSameWorldTarget(){
        Vec3 target=new Vec3(12,-15,25);
        for(var pivot:new Vec3[]{HelicopterArmament.GUN,HelicopterArmament.pod(-1),HelicopterArmament.pod(1)}){
            Vec3 worldPivot=HelicopterArmament.rotate(pivot,37,12),delta=target.subtract(worldPivot);
            var aim=HelicopterArmament.aim(delta,37,12);
            var direction=HelicopterArmament.rotate(HelicopterArmament.direction(aim.yaw(),aim.pitch()),37,12);
            assertEquals(1,direction.dot(delta.normalize()),1e-6,"pod convergence must include bank and individual mount position");
        }
    }
}
