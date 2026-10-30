package com.codex.blacklight.city;

/** Voxel silhouettes based on the architects' published massing; dimensions come from OSM. */
final class LandmarkShape {
    static double scale(int profile,double t){
        t=Math.max(0,Math.min(1,t));
        if(profile==1){ // Ping An: chamfered shaft, tapering crown, no invented antenna.
            return t<.88?1-.22*t/.88:Math.max(.035,.78*(1-(t-.88)/.12));
        }
        if(profile==2){ // China Resources: a rounded bamboo shoot converging to one apex.
            if(t<.18)return .88+.12*t/.18;
            double u=(t-.18)/.82;
            return Math.max(.035,Math.sqrt(Math.max(0,1-u*u))*(1-.28*u));
        }
        return 1;
    }
    static double radius(int profile,double x,double z){
        return profile==2?Math.hypot(x,z):Math.max(Math.max(Math.abs(x),Math.abs(z)),(Math.abs(x)+Math.abs(z))*.67);
    }
    private LandmarkShape(){}
}
