package com.codex.blacklight.client.renderer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class WeaveTransportTest {
    @Test void threeBolusesTravelFromEachWristThroughItsPortIntoTheIris(){
        for(int side:new int[]{-1,1}){
            for(int pulse=0;pulse<3;pulse++){
                double near=30+pulse*16+(side>0?2.3:0)+2;
                for(double distance:new double[]{0,.5,1,1.5,2}){
                    double transit=Math.min(distance,1)*10.5+Math.max(distance-1,0)*8;
                    for(int fiber=0;fiber<7;fiber++){
                        assertTrue(WeaveTransport.surge(near+transit,side,fiber,distance)>.94);
                        assertEquals(0,WeaveTransport.surge(near+transit-4,side,fiber,distance),.001);
                    }
                }
            }
            for(double distance:new double[]{0,.5,1,1.5,2}){
                assertEquals(0,WeaveTransport.surge(0,side,1,distance),.001);
                assertEquals(0,WeaveTransport.surge(96,side,1,distance),.001);
            }
        }
    }
}
