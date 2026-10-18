package com.codex.blacklight.client.renderer;
import org.junit.jupiter.api.Test;
import org.joml.Vector3f;
import static org.junit.jupiter.api.Assertions.*;
class BiomassSurfaceBoundsTest {
 @Test void frontUsesActualOffsetAndCrouchedModelSurface(){
  for(float[] extent:new float[][]{{0,1.8F},{-.7F,.5F},{2,7},{-10,-6}}){
   var b=new BiomassSurfaceBounds();b.include(-1,extent[0],-2);b.include(1,extent[1],2);
   assertEquals(0,b.heightFraction(extent[0]),1e-5F);assertEquals(.5F,b.heightFraction((extent[0]+extent[1])/2),1e-5F);assertEquals(1,b.heightFraction(extent[1]),1e-5F);
  }
 }
 @Test void differentSpeciesMeetAtSameEnvelopeWithoutAFrameJump(){
  var human=new BiomassSurfaceBounds();human.include(-.5F,0,-.2F);human.include(.5F,1.8F,.2F);
  var beast=new BiomassSurfaceBounds();beast.include(-2,-1,-3);beast.include(2,4,5);
  for(float x:new float[]{0,.3F,1})for(float y:new float[]{0,.4F,1})for(float z:new float[]{0,.7F,1}){
   var a=human.reshape(new Vector3f(-.5F+x,y*1.8F,-.2F+z*.4F),1);
   var b=beast.reshape(new Vector3f(-2+x*4,-1+y*5,-3+z*8),1);
   assertEquals(a.x,b.x,1e-5F);assertEquals(a.y,b.y,1e-5F);assertEquals(a.z,b.z,1e-5F);
   assertTrue(a.y>=0&&a.y<=1.35F);
  }
 }
 @Test void coatingDoesNotDisplaceVerticesAndMorphHasNoOvershoot(){
  var b=new BiomassSurfaceBounds();b.include(-2,0,-3);b.include(2,3,3);
  var p=new Vector3f(2,3,3);assertEquals(p,b.reshape(new Vector3f(p),0));
  var end=b.reshape(new Vector3f(p),1);float prior=p.distance(end);
  for(int frame=0;frame<=100;frame++){var v=b.reshape(new Vector3f(p),BiomassSurfaceBounds.ease(frame/100F));assertTrue(v.distance(end)<=prior+.00001F);prior=v.distance(end);}
  assertEquals(0,BiomassSurfaceBounds.ease(-1));assertEquals(1,BiomassSurfaceBounds.ease(2));
 }
 @Test void emptyFlatAndMalformedBoundsStayFinite(){
  var b=new BiomassSurfaceBounds();b.include(Float.NaN,0,0);assertTrue(b.empty());
  b.include(2,2,2);assertTrue(b.reshape(new Vector3f(2,2,2),1).isFinite());
 }
}
