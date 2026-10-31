package com.codex.blacklight.infection;

/** One organism's structural budget. The same plan drives fitted body axes,
 * health density and secondary sockets; it never adds whole donor bodies. */
public final class TissueBodyPlan {
    public record Frame(float width,float height,float depth,float density,int accessorySockets){
        public double vitality(float actualWidth,float actualHeight,float actualDepth){
            return Math.sqrt(Math.max(1,actualWidth*actualHeight*actualDepth))*(1+density);
        }
    }
    private TissueBodyPlan(){}
    public static Frame resolve(int nativePlan,TissueCore nativeCore,long seed,long age,TissueInheritance genes){
        var shape=genes.appearance(TissueEvolution.nativeState(nativeCore,seed,age,false),nativeCore);
        double load=0;for(var organ:genes.organs().values())load+=Math.log1p(organ.integrated()/100);
        float maturity=TissueEvolution.attachmentGrowth(age),radial=0;
        for(var source:genes.sources().values())if(source.plan()==2)radial=Math.max(radial,TissueChimera.growth(source,0));
        float width=1+Math.min(.50F,.08F*shape.muscle()+.06F*shape.carapace()+(float)(.03*Math.log1p(load)));
        float height=1+Math.min(.15F,.025F*shape.muscle()+.02F*shape.carapace());
        float depth=1+Math.min(.40F,.06F*shape.muscle()+.04F*shape.carapace()+(float)(.02*Math.log1p(load)));
        if(nativePlan==2){width+=.05F*maturity;depth+=.05F*maturity;}
        if(nativePlan==1){width+=.08F*radial;height-=.06F*radial;depth+=.05F*radial;}
        float density=Math.min(.75F,.08F*shape.muscle()+.12F*shape.carapace()+(float)(.04*Math.log1p(load)));
        var hydrostat=genes.organ(TissueCore.HYDROSTAT);double acquired=hydrostat==null?0:hydrostat.integrated();
        int sockets=Math.min(6,2+(int)Math.floor(Math.log1p(acquired/200)));
        return new Frame(width,height,depth,density,sockets);
    }
}
