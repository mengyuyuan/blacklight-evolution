package com.codex.blacklight.combat;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CrowdPressureTest {
 @Test void crowdCannotJuggle(){
  var p=new CrowdPressure();
  assertEquals(1F,p.accept(100,false));
  assertEquals(.5F,p.accept(104,false));
  assertEquals(0F,p.accept(108,false));
  assertEquals(0F,p.accept(119,false));
  assertEquals(1F,p.accept(120,false));
 }
 @Test void attackingKeepsFooting(){
  assertEquals(.35F,new CrowdPressure().accept(0,true),1e-6);
 }
}