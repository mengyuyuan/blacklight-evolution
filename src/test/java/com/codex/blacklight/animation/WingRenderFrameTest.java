package com.codex.blacklight.animation;
import com.codex.blacklight.client.model.SovereignWingModel;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class WingRenderFrameTest {
 @Test void sharedRenderPassPreservesPositionsNormalsAndRefreshesForEveryActor() throws Exception {
  var model=new SovereignWingModel();var sourceField=SovereignWingModel.class.getDeclaredField("prepared");sourceField.setAccessible(true);
  var viewField=SovereignWingModel.class.getDeclaredField("viewPrepared");viewField.setAccessible(true);
  for(int order=0;order<2;order++)for(float air:new float[]{0,.5F,1}){
   model.prepare(SovereignWingMotion.sample(23,1,air,0,0,0,0,0,0),new Vector3f(0,0,1),1,air,order);
   var local=((float[])sourceField.get(model)).clone();
   var stack=new PoseStack();stack.translate(7,-3,12);stack.mulPose(new Quaternionf().rotationXYZ(.2F,-.7F,.4F));stack.scale(.8F,1.3F,-1.1F);
   model.prepareRender(stack);var view=(float[])viewField.get(model);
   assertEquals(local.length,view.length);assertArrayEquals(local,(float[])sourceField.get(model));
   for(int i=0;i<local.length;i+=6){
    var expected=stack.last().pose().transformPosition(new Vector3f(local[i],local[i+1],local[i+2]));
    var expectedNormal=new Vector3f(local[i+3],local[i+4],local[i+5]);stack.last().normal().transform(expectedNormal);
    assertEquals(expected.x,view[i],.00001F);assertEquals(expected.y,view[i+1],.00001F);assertEquals(expected.z,view[i+2],.00001F);
    assertEquals(expectedNormal.x,view[i+3],.00001F);assertEquals(expectedNormal.y,view[i+4],.00001F);assertEquals(expectedNormal.z,view[i+5],.00001F);
   }
   model.prepareRender(new PoseStack());assertArrayEquals(local,(float[])viewField.get(model),.00001F);
  }
 }
}
