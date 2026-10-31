package com.codex.blacklight.infection;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.codex.blacklight.infection.TissueMorphogenesis.*;

class TissueMorphogenesisTest {
    private static final V UP=new V(0,-1,0),FRONT=new V(0,0,-1),RIGHT=new V(1,0,0);
    private static Bone bone(String path,int role,float x,float y,float z,float X,float Y,float Z) {
        return new Bone(path,role,new Box(new V(x,y,z),new V(X,Y,Z)),UP,FRONT,RIGHT,new V(0,0,0));
    }
    private static List<Bone> biped(){return List.of(bone("body",0,-4,0,-2,4,12,2),bone("head",1,-4,-8,-4,4,0,4),
            bone("right_arm",2,-3,-2,-2,1,10,2),bone("left_arm",3,-1,-2,-2,3,10,2),
            bone("right_leg",4,-2,0,-2,2,12,2),bone("left_leg",5,-2,0,-2,2,12,2));}
    @Test void sameSeedAndSkeletonAreStableAcrossOrderAndObservers() {
        var bones=new ArrayList<>(biped());var gson=new Gson();var before=gson.toJson(generate(42,bones,true));
        Collections.reverse(bones);assertEquals(before,gson.toJson(generate(42,bones,true)));
        assertNotEquals(before,gson.toJson(generate(43,bones,true)),"host seed changes organ placement and mass rather than frame randomness");
    }
    @Test void unfamiliarSkeletonProducesTrueOrgansWithoutSpeciesAssets() {
        var bones=new ArrayList<Bone>();bones.add(bone("alien_core",0,-7,-3,-10,7,3,10));
        bones.add(bone("alien_head",1,-2,-2,-5,2,2,1));
        for(int i=0;i<8;i++)bones.add(bone("radial_appendage_"+i,2+i%4,-14,-1,-1,0,1,1));
        var plan=generate(8,bones,true);assertEquals(10,plan.regions().size());assertTrue(plan.organs()>2);
        assertTrue(plan.regions().stream().flatMap(r->r.meshes().stream()).anyMatch(m->!m.skin()&&m.name().startsWith("claw")));
        assertEquals(1,plan.regions().stream().filter(r->r.outlet()!=null).count());
        assertTrue(plan.faces()<=MAX_FACES);
    }
    @Test void generatedGeometryIsFiniteBoundedAndGrowsOutsideNativeSkin() {
        for(int seed=0;seed<64;seed++)for(var r:generate(seed,biped(),true).regions())for(var m:r.meshes()) {
            assertEquals(5,m.stages().length);int length=m.stages()[0].length;
            for(var stage:m.stages()) {assertEquals(length,stage.length);for(float v:stage)assertTrue(Float.isFinite(v)&&Math.abs(v)<80);}
            for(var f:m.faces()){assertEquals(7,f.length);for(int i=0;i<4;i++)assertTrue(f[i]>=0&&f[i]<length/3);assertTrue(f[6]>f[5]);}
            if(m.skin()) {
                assertFalse(Arrays.equals(m.stages()[0],m.stages()[2]),"host structure changes instead of only receiving decals");
                for(int i=0;i<length;i++)assertTrue(m.stages()[0][i]>=r.reference()[i%3]-.001F&&m.stages()[0][i]<=r.reference()[i%3+3]+.001F);
            }
        }
    }
    @Test void geometryAndRuntimeBudgetsSurviveLargeOrMalformedModels() {
        var bones=new ArrayList<Bone>();
        for(int i=0;i<300;i++)bones.add(bone("bone_"+i,i%7,-2,-4,-2,2,4,2));
        var plan=generate(123,bones,true);assertTrue(plan.regions().size()<=MAX_BONES);assertTrue(plan.faces()<=MAX_FACES);
        var invalid=List.of(bone("flat",0,0,0,0,1,1,0),bone("nan",0,Float.NaN,0,0,1,1,1),bone("giant",0,-100,0,0,100,20,20));
        assertTrue(generate(1,invalid,true).regions().isEmpty());
    }
    @Test void coatFollowsTheSameSkinDeformationWithoutDuplicatingOrgans() {
        var skin=generate(7,biped(),true);var coat=generate(7,biped(),false);
        assertEquals(skin.regions().size(),coat.regions().size());assertEquals(0,coat.organs());
        for(int r=0;r<skin.regions().size();r++) {
            var first=skin.regions().get(r).meshes().stream().filter(Mesh::skin).toList();var second=coat.regions().get(r).meshes();
            assertEquals(first.size(),second.size());
            for(int m=0;m<first.size();m++)for(int k=0;k<5;k++)assertArrayEquals(first.get(m).stages()[k],second.get(m).stages()[k]);
        }
    }
    @Test void unnamedCustomBonesCanBeClassifiedByGeometryAndJointPositions() {
        var named=biped();var bones=new ArrayList<Bone>();
        V[] positions={new V(0,6,0),new V(0,-4,0),new V(-6,5,0),new V(6,5,0),new V(-2,18,0),new V(2,18,0)};
        for(int i=0;i<named.size();i++){var b=named.get(i);bones.add(new Bone("unfamiliar_"+i,-1,b.box(),b.up(),b.forward(),b.right(),positions[i]));}
        var inferred=inferRoles(bones);var roles=inferred.stream().map(Bone::role).collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of(0,1,2,3,4,5),roles);
        var plan=generate(4,bones,true);assertTrue(plan.organs()>2);assertTrue(plan.regions().stream().anyMatch(r->r.meshes().stream().anyMatch(m->m.motion().equals("jaw"))));
    }
    @Test void quadrupedClawsGrowAlongSupportPlaneInsteadOfBelowTheFeet() {
        var trunk=new Bone("trunk",0,new Box(new V(-6,-10,-7),new V(6,8,3)),new V(0,0,1),new V(0,-1,0),RIGHT,new V(0,6,0));
        var leg=bone("foreleg",3,-2,0,-2,2,12,2);int checked=0;
        for(int seed=0;seed<64;seed++)for(var r:generate(seed,List.of(trunk,leg),true).regions())if(r.name().equals("foreleg"))for(var m:r.meshes())if(!m.skin()) {
            checked++;for(int i=1;i<m.stages()[2].length;i+=3)assertTrue(m.stages()[2][i]<=12.1F,"toes remain above the native foot contact plane");
        }
        assertTrue(checked>0);
    }
    @Test void semanticWordsDoNotMisclassifyUnfamiliarNamesBySubstring() {
        assertEquals(-1,semanticRole("heart"));assertEquals(-1,semanticRole("reinforcedCore"));assertEquals(-1,semanticRole("gearbox"));
        assertEquals(1,semanticRole("real_head"));assertEquals(3,semanticRole("leftUpperArm"));assertEquals(4,semanticRole("right_hind_leg"));
    }
    @Test void plantedDigitsRemainAboveGroundWhenTheirAttackHingeMoves() {
        var trunk=new Bone("trunk",0,new Box(new V(-6,-10,-7),new V(6,8,3)),new V(0,0,1),new V(0,-1,0),RIGHT,new V(0,6,0));
        var leg=bone("foreleg",3,-2,0,-2,2,12,2);int checked=0;
        for(int seed=0;seed<64;seed++)for(var region:generate(seed,List.of(trunk,leg),true).regions())for(var mesh:region.meshes())if(mesh.motion().equals("toe")) {
            checked++;V axis=new V(mesh.axis()[0],mesh.axis()[1],mesh.axis()[2]);
            float angle=TissueMotion.sample(seed,100,1,1,TissueOrgans.HIT).toe();
            for(int stage=2;stage<5;stage++)for(int i=0;i<mesh.stages()[stage].length;i+=3) {
                V pivot=new V(mesh.pivots()[stage][0],mesh.pivots()[stage][1],mesh.pivots()[stage][2]);
                V p=new V(mesh.stages()[stage][i],mesh.stages()[stage][i+1],mesh.stages()[stage][i+2]).sub(pivot);
                V rotated=p.mul((float)Math.cos(angle)).add(axis.cross(p).mul((float)Math.sin(angle)))
                        .add(axis.mul(axis.dot(p)*(1-(float)Math.cos(angle)))).add(pivot);
                assertTrue(rotated.y()<=12.1F,"an independent claw curl cannot penetrate the support plane");
            }
        }
        assertTrue(checked>0);
    }
    @Test void uprightFourFootedHostsDoNotGetHangingHandClawsOnFrontLegs() {
        var trunk=bone("body",0,-4,0,-2,4,12,2);
        var leg=bone("left_front_leg",3,-2,0,-2,2,6,2);
        var plan=generate(1,List.of(trunk,leg),true);
        var digits=plan.regions().stream().filter(r->r.name().equals("left_front_leg")).flatMap(r->r.meshes().stream())
                .filter(m->m.name().startsWith("claw")||m.name().startsWith("blade")).toList();
        assertFalse(digits.isEmpty());assertTrue(digits.stream().allMatch(m->m.motion().equals("toe")));
        for(var digit:digits)for(int i=1;i<digit.stages()[2].length;i+=3)assertTrue(digit.stages()[2][i]<=6.1F);
    }
    @Test void bodyPlanChangesHeadConstructionWithoutLookingUpASpecies() {
        var radial=new ArrayList<>(biped());
        for(int i=0;i<4;i++)radial.add(bone("extra_appendage_"+i,2+i%4,-12,-1,-1,0,1,1));
        var upright=generate(9,biped(),true);var manyLegs=generate(9,radial,true);
        var a=upright.regions().stream().filter(r->r.anatomy()==1).findFirst().orElseThrow();
        var b=manyLegs.regions().stream().filter(r->r.anatomy()==1).findFirst().orElseThrow();
        var skullA=a.meshes().stream().filter(m->m.name().equals("skull")).findFirst().orElseThrow();
        var skullB=b.meshes().stream().filter(m->m.name().equals("skull")).findFirst().orElseThrow();
        assertFalse(Arrays.equals(skullA.stages()[2],skullB.stages()[2]),"different anatomy must not receive the same mature head");
        assertTrue(a.meshes().stream().anyMatch(m->m.motion().equals("split")),"an upright head opens independently at both cranial hinges");
        assertFalse(b.meshes().stream().anyMatch(m->m.motion().equals("split")),"radial mandibles do not receive the humanoid head joints");
        assertTrue(a.meshes().stream().flatMap(m->Arrays.stream(m.faces())).anyMatch(f->f[4]==0),"retain some host skin");
    }
}
