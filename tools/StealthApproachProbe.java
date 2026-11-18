import com.codex.blacklight.combat.StealthApproach;

/** Executes production admission geometry without Minecraft or mocked vector math. */
public final class StealthApproachProbe {
    static int checks;
    static void check(boolean condition){checks++;if(!condition)throw new AssertionError("check "+checks);}
    public static void main(String[] args){
        for(int yaw=-720;yaw<=720;yaw+=15){
            double angle=Math.toRadians(yaw),x=Math.sin(angle),z=-Math.cos(angle);
            check(StealthApproach.behind(x,0,z,yaw));
            check(!StealthApproach.behind(-x,0,-z,yaw));
            check(!StealthApproach.behind(z,0,-x,yaw));
            check(!StealthApproach.behind(x*2,0,z*2,yaw));
            check(!StealthApproach.behind(x*.1,0,z*.1,yaw));
            check(!StealthApproach.behind(x,.66,z,yaw));
            check(StealthApproach.behind(x,-.65,z,yaw));
        }
        check(!StealthApproach.behind(0,0,0,0));
        check(StealthApproach.behind(0,0,-.25,0));
        check(StealthApproach.behind(0,0,-1.8,0));
        check(!StealthApproach.behind(Double.NaN,0,-1,0));
        check(!StealthApproach.behind(0,Double.POSITIVE_INFINITY,-1,0));
        check(!StealthApproach.behind(0,0,-1,Float.NaN));
        System.out.println("PASS: "+checks+" production stealth approach checks");
    }
}
