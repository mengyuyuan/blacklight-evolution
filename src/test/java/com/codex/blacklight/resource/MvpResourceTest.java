package com.codex.blacklight.resource;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MvpResourceTest {
    @Test
    void authoredActionsHaveCompleteNormalizedTracksAndMatchServerDurations() throws IOException {
        Map<String, Double> clips = Map.ofEntries(

                Map.entry("action/consume", 2.3),
                Map.entry("action/consume_normal", 2.3), Map.entry("action/consume_hammer", 2.8),
                Map.entry("action/consume_blade", 2.4), Map.entry("action/consume_whip", 1.9),
                Map.entry("action/grab_reach", .6), Map.entry("action/grab_hold", 2.0),
                Map.entry("action/grab_blade", 1.0), Map.entry("action/grab_blade_hold", 2.0), Map.entry("action/grab_blade_throw", .7),
                Map.entry("action/grab_throw", .7), Map.entry("action/grab_whip", 1.8),
                Map.entry("action/transform_claws", 1.2),
                Map.entry("combat/claw_auto1", 0.7),
                Map.entry("combat/claw_auto2", 0.8),
                Map.entry("combat/claw_auto3", 1.1),
                Map.entry("combat/claw_dashing_slice", 1.0),
                Map.entry("combat/claw_groundspike", 2.2),
                Map.entry("combat/claw_sprint_attack", 0.8));

        for (var entry : clips.entrySet()) {
            var data = tracks(readAnimation("biped/" + entry.getKey() + ".json"));
            assertEquals(20, data.size(), entry.getKey() + " must animate the complete skeleton");
            for (var track : data.values()) {
                JsonArray times = track.getAsJsonArray("time");
                assertEquals(entry.getValue(), times.get(times.size() - 1).getAsDouble(), 1e-6,
                        entry.getKey() + " must finish with its server action");
                double previous = -1;
                for (var t : times) {
                    assertTrue(t.getAsDouble() > previous, "key times must increase");
                    previous = t.getAsDouble();
                }
                for (var f : track.getAsJsonArray("transform")) {
                    double norm = 0;
                    for (var component : f.getAsJsonObject().getAsJsonArray("rot")) {
                        double v = component.getAsDouble();
                        assertTrue(Double.isFinite(v));
                        norm += v * v;
                    }
                    assertEquals(1, norm, 1e-5, "non-unit quaternion changes limb orientation/scale");
                }
            }
        }
    }

    @Test
    void everyServerTimedMvpActionHasANativeBlacklightAnimation() {
        assertResource("biped/combat/claw_auto1.json");
        assertResource("biped/combat/claw_auto2.json");
        assertResource("biped/combat/claw_auto3.json");
        assertResource("biped/combat/claw_groundspike.json");
        assertResource("biped/combat/claw_dashing_slice.json");
        assertResource("biped/action/transform_claws.json");

    }

    @Test
    void groundedCrouchesKeepTheFeetAtTheFloorInsteadOfSinkingTheWholeBody() throws IOException {
        for (String path : new String[] {"combat/claw_groundspike"}) {
            var clip=readAnimation("biped/"+path+".json");
            var times=tracks(clip).get("Root").getAsJsonArray("time");
            double contact=path.endsWith("light")?.07:path.endsWith("heavy")?.12:path.endsWith("devastating")?.18:0;
            for (int i=0;i<times.size();i++) {
                double t=times.get(i).getAsDouble(); if(t+1e-6<contact) continue;
                var pose=worldPose(clip,t);
                for(String side:new String[]{"R","L"})
                    assertEquals(.015,point(pose.get("Leg_"+side),0,.37472,0)[2],.025,path+" foot "+side+" at "+t);
            }
        }
    }

    @Test
    void virusAmpouleHasAThreeDimensionalItemModel() {
        assertNotNull(MvpResourceTest.class.getResource(
                "/assets/blacklight/models/item/blacklight_sample.json"));
        assertNotNull(MvpResourceTest.class.getResource(
                "/assets/blacklight/textures/item/biomass.png"));
    }

    @Test
    void importedImpactParticlesShipWithTexturesDefinitionsAndLicense() {
        for (String name : new String[] {"biomass_slash", "destruction_burst", "impact_smoke"}) {
            assertNotNull(MvpResourceTest.class.getResource(
                    "/assets/blacklight/particles/" + name + ".json"), name + " definition");
            assertNotNull(MvpResourceTest.class.getResource(
                    "/assets/blacklight/textures/particle/" + name + ".png"), name + " texture");
        }
        assertNotNull(MvpResourceTest.class.getResource(
                "/META-INF/third-party/kenney-particle-pack-CC0.txt"));
    }

    @Test
    void sprintShockwaveShipsWithItsHorizontalRingTexture() {
        assertNotNull(MvpResourceTest.class.getResource(
                "/assets/blacklight/particles/sprint_shockwave.json"));
        assertNotNull(MvpResourceTest.class.getResource(
                "/assets/blacklight/textures/particle/sprint_shockwave.png"));
    }

    @Test
    void velocityCorrectedSpeedPostEffectShipsAsACompleteLicensedChain() throws IOException {
        JsonObject chain = readJson("/assets/blacklight/shaders/post/prototype_speed.json");
        JsonArray passes = chain.getAsJsonArray("passes");
        assertEquals(2, passes.size(), "speed effect must render off-screen then blit to main");
        assertEquals("blacklight:prototype_speed",
                passes.get(0).getAsJsonObject().get("name").getAsString());
        assertEquals("minecraft:main",
                passes.get(1).getAsJsonObject().get("outtarget").getAsString());

        JsonObject program = readJson("/assets/blacklight/shaders/program/prototype_speed.json");
        Set<String> uniforms = new java.util.HashSet<>();
        for (JsonElement element : program.getAsJsonArray("uniforms")) {
            uniforms.add(element.getAsJsonObject().get("name").getAsString());
        }
        assertTrue(uniforms.containsAll(Set.of(
                "ProjMat", "InSize", "OutSize", "Time", "Intensity", "BiasX", "BiasY")));
        assertNotNull(MvpResourceTest.class.getResource(
                "/assets/blacklight/shaders/program/prototype_speed.fsh"));
        assertNotNull(MvpResourceTest.class.getResource(
                "/META-INF/third-party/anime-speed-lines-MIT.txt"));
    }

    @Test
    void groundSpikeKeepsThePlayerUprightPlantsOnlyTheRightClawAndEruptsUpward()
            throws IOException {
        var clip=readAnimation("biped/combat/claw_groundspike.json");
        var wind=worldPose(clip,.24);var plant=worldPose(clip,.55);
        double[] raised=point(wind.get("Hand_R"),0,.272858,0);
        double[] planted=point(plant.get("Hand_R"),0,.272858,0);
        double[] free=point(plant.get("Hand_L"),0,.272858,0);
        assertTrue(raised[2]>planted[2]+.8,"right claw must drive down from a raised windup");
        assertTrue(free[2]>planted[2]+.25,"free claw balances above the planted claw");
        for(var frame:tracks(clip).get("Root").getAsJsonArray("transform"))
            assertIdentity(frame.getAsJsonObject().getAsJsonArray("rot"));

        JsonObject geometry = readJson("/assets/blacklight/geo/ground_spike.geo.json");
        JsonArray spikeBones = geometry.getAsJsonArray("minecraft:geometry").get(0)
                .getAsJsonObject().getAsJsonArray("bones");
        double tallest = 0.0D;
        JsonObject eruption = readJson("/assets/blacklight/animations/ground_spike.animation.json")
                .getAsJsonObject("animations")
                .getAsJsonObject("animation.blacklight.ground_spike.erupt");
        int animatedSpires = 0;
        for (JsonElement element : spikeBones) {
            JsonObject bone = element.getAsJsonObject();
            if (!bone.has("cubes")) continue;
            double top = 0.0D;
            for (JsonElement elementCube : bone.getAsJsonArray("cubes")) {
                JsonObject cube = elementCube.getAsJsonObject();
                // A segmented spire must be judged by its whole height, not one tier.
                top = Math.max(top, cube.getAsJsonArray("origin").get(1).getAsDouble()
                        + cube.getAsJsonArray("size").get(1).getAsDouble());
            }
            tallest = Math.max(tallest, top);
            if (top < 32.0D) continue;
            JsonObject position = eruption.getAsJsonObject("bones")
                    .getAsJsonObject(bone.get("name").getAsString()).getAsJsonObject("position");
            assertTrue(position.getAsJsonArray("0.0").get(1).getAsDouble() + top < 1.0D,
                    "each spire must start below the surface");
            assertTrue(position.entrySet().stream().anyMatch(frame ->
                            frame.getValue().getAsJsonArray().get(1).getAsDouble() >= 0.0D),
                    "each spire must emerge fully above ground");
            assertTrue(position.getAsJsonArray("1.1").get(1).getAsDouble() + top < 1.0D,
                    "each spire must retract before the entity expires");
            animatedSpires++;
        }
        assertTrue(tallest >= 64.0D, "groundspike must be visibly tall");
        assertTrue(animatedSpires >= 5, "the full cluster must animate, not just its base");
        assertEquals(1.1D, eruption.get("animation_length").getAsDouble(), 0.00001D,
                "the eruption must retain the server's 22-tick lifetime");
    }

    @Test
    void removedTraversalFallbacksCannotReturnToTheDistributedResources() {
        for(String name:new String[]{"claw_idle","claw_walk","claw_sprint","prototype_jump_charge",
                "prototype_jump_rise","prototype_jump_fall","prototype_glide","wall_run","wall_climb",
                "wall_cling","wall_leap","wall_mantle","wall_vault","landing_light","landing_moving",
                "landing_heavy","landing_devastating"})
            org.junit.jupiter.api.Assertions.assertNull(MvpResourceTest.class.getResource(
                    "/assets/blacklight/native_animations/biped/living/"+name+".json"),name);
        org.junit.jupiter.api.Assertions.assertNull(MvpResourceTest.class.getResource(
                "/assets/blacklight/native_animations/biped/action/air_dash.json"));
    }

    private static void assertIdentity(JsonArray q) {
        for(int i=0;i<4;i++) assertEquals(i==0?1:0,q.get(i).getAsDouble(),.00001);
    }

    @Test
    void clawThumbsMatchTheirAnatomicalHands() throws IOException {
        var mesh = readJson("/assets/blacklight/models/claw_blender.json");
        for (String side : new String[]{"R", "L"}) {
            double[] palm = clawPartCenter(mesh, "Palm mass / Hand_" + side);
            double[] thumb = clawPartCenter(mesh, "Opposed thumb muscle / Hand_" + side);
            double[] finger = clawPartCenter(mesh, "Knuckle root 1 / Hand_" + side);
            double[] back = clawPartCenter(mesh, "Palm dorsal spur / Hand_" + side);
            for (int i=0;i<3;i++) {thumb[i]-=palm[i];finger[i]-=palm[i];back[i]=palm[i]-back[i];}
            // A right hand has thumb x fingers pointing into its palmar surface;
            // a left hand has the opposite chirality. Read the actual mesh faces.
            double chirality=(thumb[1]*finger[2]-thumb[2]*finger[1])*back[0]
                    +(thumb[2]*finger[0]-thumb[0]*finger[2])*back[1]
                    +(thumb[0]*finger[1]-thumb[1]*finger[0])*back[2];
            assertTrue((side.equals("R") ? chirality : -chirality) > 0,
                    side + " claw thumb is on the wrong anatomical side");
        }
    }

    @Test
    void morphAndGrabActionsKeepTheActualClawMeshClearOfTheHeadThroughoutEachClip() throws IOException {
        var mesh=readJson("/assets/blacklight/models/claw_blender.json").getAsJsonObject("joints");
        for(String name:new String[]{"action/transform_claws","action/consume","action/grab_reach","action/grab_hold","action/grab_throw"}) {
            var clip=readAnimation("biped/"+name+".json");
            for(var time:tracks(clip).get("Head").getAsJsonArray("time")) {
                var pose=worldPose(clip,time.getAsDouble());var head=pose.get("Head");
                for(String side:new String[]{"R","L"}) {
                    var hand=pose.get("Hand_"+side);
                    for(var face:mesh.getAsJsonArray("Hand_"+side)) for(var vertex:face.getAsJsonObject().getAsJsonArray("p")) {
                        var v=vertex.getAsJsonArray();var world=point(hand,v.get(0).getAsDouble(),v.get(1).getAsDouble(),v.get(2).getAsDouble());
                        double[] local=new double[3];
                        for(int axis=0;axis<3;axis++)for(int k=0;k<3;k++)local[axis]+=head[k][axis]*(world[k]-head[k][3]);
                        // Minecraft's 8-pixel head occupies +/- .25 around local Y=.25.
                        double dx=Math.max(0,Math.abs(local[0])-.25),dy=Math.max(0,Math.abs(local[1]-.25)-.25),dz=Math.max(0,Math.abs(local[2])-.25);
                        assertTrue(dx*dx+dy*dy+dz*dz>=.06*.06,name+" "+side+" claw approaches head at "+time);
                    }
                }
            }
        }
    }

    @Test
    void exportedGripAnchorsStayInsideTheRenderedPalms() throws IOException {
        var mesh=readJson("/assets/blacklight/models/claw_blender.json");
        for(String side:new String[]{"L","R"}) {
            var center=clawPartCenter(mesh,"Palm mass / Hand_"+side);
            var anchor=com.codex.blacklight.client.animation.WeaponModelAnchors.get(
                    com.codex.blacklight.power.PowerForm.CLAWS,"palm_"+side,new org.joml.Vector3f(Float.NaN));
            assertTrue(anchor.isFinite(),"IK must use a shipped contact landmark");
            assertTrue(anchor.distance(new org.joml.Vector3f((float)center[0],(float)center[1],(float)center[2]))<.05,
                    "grip IK anchor must lie inside the visible palm");
        }
    }

    @Test
    void bladeHoldPlacesContinuousEdgeInTheRightSideBodyAnchor() throws IOException {
        var clip=readAnimation("biped/action/grab_blade_hold.json");
        for(double t=0;t<=2;t+=.05) {
            var blade=point(worldPose(clip,t).get("Hand_R"),-.0636396,.68,-.0636396);
            assertEquals(com.codex.blacklight.combat.GrabPlacement.BLADE_RIGHT,blade[0]*.9375,.04);
            assertEquals(com.codex.blacklight.combat.GrabPlacement.BLADE_FORWARD,blade[1]*.9375,.04);
            assertEquals(com.codex.blacklight.combat.GrabPlacement.BLADE_HEIGHT,blade[2]*.9375,.04);
        }
    }

    @Test
    void consumeStartsFromTheCarryPoseInsteadOfDroppingBothHandsToIdle() throws IOException {
        var held=worldPose(readAnimation("biped/action/grab_hold.json"),0);
        for(String clip:new String[]{"consume","consume_normal","consume_hammer","consume_blade","consume_whip"}) {
            if(clip.equals("consume_blade"))held=worldPose(readAnimation("biped/action/grab_blade_hold.json"),0);
            else held=worldPose(readAnimation("biped/action/grab_hold.json"),0);
            var first=worldPose(readAnimation("biped/action/"+clip+".json"),0);
            for(String joint:new String[]{"Torso","Chest","Hand_L","Hand_R","Head"})
                for(int row=0;row<4;row++)for(int col=0;col<4;col++)
                    assertEquals(held.get(joint)[row][col],first.get(joint)[row][col],.0001,clip+" carry continuity "+joint);
        }
    }

    @Test
    void hammerContactIsFastThenBrieflySettlesBeforeRecovery() throws IOException {
        var clip=readAnimation("biped/action/consume_hammer.json");
        var ready=worldPose(clip,.80).get("Hand_R");
        var contact=worldPose(clip,.90).get("Hand_R");
        var held=worldPose(clip,1.0).get("Hand_R");
        double travel=0,settle=0;
        for(int axis=0;axis<3;axis++) {
            travel+=Math.pow(contact[axis][3]-ready[axis][3],2);
            settle+=Math.pow(held[axis][3]-contact[axis][3],2);
        }
        assertTrue(travel>.25,"hammer must travel decisively into contact in two ticks");
        assertTrue(settle<.0001,"contact needs a readable settle instead of a continuous soft swing");
    }

    @Test
    void newConsumeFormsKeepTheirOwnMeshesClearOfTheHead() throws IOException {
        var failures = new java.util.ArrayList<String>();
        for (var form : Map.of("hammerfists", "hammer", "blade", "blade", "whipfist", "whip").entrySet()) {
            for(String name:form.getKey().equals("blade")?new String[]{"action/consume_blade","action/grab_blade","action/grab_blade_hold","action/grab_blade_throw"}:new String[]{"action/consume_"+form.getValue(),"action/grab_reach","action/grab_hold","action/grab_throw",form.getKey().equals("whipfist")?"action/grab_whip":"action/grab_hold"}) {
            var root = readJson("/assets/blacklight/models/form_"+form.getKey()+".json");
            var mesh=root.getAsJsonObject("joints");
            double min = 100; String closest = "";
            var clip=readAnimation("biped/"+name+".json");
            for(var time:tracks(clip).get("Head").getAsJsonArray("time")) {
                var pose=worldPose(clip,time.getAsDouble());var head=pose.get("Head");
                for(String side:new String[]{"R","L"}) {
                    if (!mesh.has("Hand_"+side)) continue;
                    var hand=pose.get("Hand_"+side);
                    for(var face:mesh.getAsJsonArray("Hand_"+side)) for(var vertex:face.getAsJsonObject().getAsJsonArray("p")) {
                        var v=vertex.getAsJsonArray();
                        double x=v.get(0).getAsDouble(),y=v.get(1).getAsDouble(),z=v.get(2).getAsDouble();
                        // Default BladeShape sizing is baked by ClawArmModel before
                        // joint attachment; measuring the raw export misses its tips.
                        if(!root.has("approvedProportions")&&form.getKey().equals("blade")&&side.equals("R")) {
                            x*=com.codex.blacklight.client.model.BladeShape.WIDTH;
                            z*=com.codex.blacklight.client.model.BladeShape.WIDTH;
                            y=com.codex.blacklight.client.model.BladeShape.length((float)y);
                        }
                        var world=point(hand,x,y,z);
                        double[] local=new double[3];
                        for(int axis=0;axis<3;axis++)for(int k=0;k<3;k++)local[axis]+=head[k][axis]*(world[k]-head[k][3]);
                        // Minecraft's 8-pixel head occupies +/- .25 around local Y=.25.
                        double dx=Math.max(0,Math.abs(local[0])-.25),dy=Math.max(0,Math.abs(local[1]-.25)-.25),dz=Math.max(0,Math.abs(local[2])-.25);
                        double d = Math.sqrt(dx*dx+dy*dy+dz*dz);
                        if (d < min) { min=d; closest=name+" "+side+" at "+time; }
                    }
                }
            }
            if (min < .06) failures.add(closest+" clearance="+min);
            }
        }
        assertTrue(failures.isEmpty(), String.join("; ", failures));
    }

    @Test
    void sizedBladeKeepsHeadClearanceBetweenGrabKeys() throws IOException {
        var root=readJson("/assets/blacklight/models/form_blade.json");
        var mesh=root.getAsJsonObject("joints");
        for(String name:new String[]{"grab_blade","grab_blade_throw"}) {
            var clip=readAnimation("biped/action/"+name+".json");
            var headTimes=tracks(clip).get("Head").getAsJsonArray("time");
            double minimum=100,closestTime=0;
            for(int sample=0;sample<=Math.round(headTimes.get(headTimes.size()-1).getAsDouble()*200);sample++) {
                double time=sample/200D;
                var pose=interpolatedWorldPose(clip,time);var head=pose.get("Head");var hand=pose.get("Hand_R");
                for(var face:mesh.getAsJsonArray("Hand_R"))for(var vertex:face.getAsJsonObject().getAsJsonArray("p")) {
                    var v=vertex.getAsJsonArray();
                    boolean approved=root.has("approvedProportions");
                    var world=point(hand,v.get(0).getAsDouble()*(approved?1:com.codex.blacklight.client.model.BladeShape.WIDTH),
                            approved?v.get(1).getAsDouble():com.codex.blacklight.client.model.BladeShape.length(v.get(1).getAsFloat()),
                            v.get(2).getAsDouble()*(approved?1:com.codex.blacklight.client.model.BladeShape.WIDTH));
                    double[] local=new double[3];
                    for(int axis=0;axis<3;axis++)for(int k=0;k<3;k++)local[axis]+=head[k][axis]*(world[k]-head[k][3]);
                    double dx=Math.max(0,Math.abs(local[0])-.25),dy=Math.max(0,Math.abs(local[1]-.25)-.25),dz=Math.max(0,Math.abs(local[2])-.25);
                    double distance=Math.sqrt(dx*dx+dy*dy+dz*dz);
                    if(distance<minimum){minimum=distance;closestTime=time;}
                }
            }
            assertTrue(minimum>=.06,name+" interpolated clearance="+minimum+" at "+closestTime);
        }
    }

    private static Map<String,double[][]> interpolatedWorldPose(JsonObject clip,double time)throws IOException {
        var sampled=clip.deepCopy();
        for(var item:sampled.getAsJsonArray("animation")) {
            var track=item.getAsJsonObject();var times=track.getAsJsonArray("time");var frames=track.getAsJsonArray("transform");
            int upper=0;while(upper<times.size()-1&&times.get(upper).getAsDouble()<time)upper++;
            int lower=Math.max(0,upper-1);double start=times.get(lower).getAsDouble(),end=times.get(upper).getAsDouble();
            double t=end==start?0:com.codex.blacklight.util.MathCompat.clamp((time-start)/(end-start),0,1);
            var a=frames.get(lower).getAsJsonObject();var b=frames.get(upper).getAsJsonObject();var frame=new JsonObject();
            for(String channel:new String[]{"loc","sca"}) {
                var values=new com.google.gson.JsonArray();
                for(int axis=0;axis<3;axis++)values.add(a.getAsJsonArray(channel).get(axis).getAsDouble()*(1-t)+b.getAsJsonArray(channel).get(axis).getAsDouble()*t);
                frame.add(channel,values);
            }
            double[] qa=new double[4],qb=new double[4];double la=0,lb=0;
            for(int axis=0;axis<4;axis++){qa[axis]=a.getAsJsonArray("rot").get(axis).getAsDouble();qb[axis]=b.getAsJsonArray("rot").get(axis).getAsDouble();la+=qa[axis]*qa[axis];lb+=qb[axis]*qb[axis];}
            double dot=0;for(int axis=0;axis<4;axis++){qa[axis]/=Math.sqrt(la);qb[axis]/=Math.sqrt(lb);dot+=qa[axis]*qb[axis];}
            if(dot<0){dot=-dot;for(int axis=0;axis<4;axis++)qb[axis]=-qb[axis];}
            double wa=1-t,wb=t;
            if(dot<.9995){double angle=Math.acos(com.codex.blacklight.util.MathCompat.clamp(dot,-1,1));wa=Math.sin((1-t)*angle)/Math.sin(angle);wb=Math.sin(t*angle)/Math.sin(angle);}
            double length=0;double[] q=new double[4];for(int axis=0;axis<4;axis++){q[axis]=qa[axis]*wa+qb[axis]*wb;length+=q[axis]*q[axis];}
            var rotation=new com.google.gson.JsonArray();for(double value:q)rotation.add(value/Math.sqrt(length));frame.add("rot",rotation);
            var sampledTimes=new com.google.gson.JsonArray();sampledTimes.add(time);var sampledFrames=new com.google.gson.JsonArray();sampledFrames.add(frame);
            track.add("time",sampledTimes);track.add("transform",sampledFrames);
        }
        return worldPose(sampled,time);
    }

    private static double[] clawPartCenter(JsonObject mesh, String name) {
        // Export keeps one part table; left triangles mirror the right ranges.
        String side=name.substring(name.length()-1);
        var part=mesh.getAsJsonObject("parts").getAsJsonObject(name.substring(0,name.length()-1)+"R");
        assertNotNull(part,name);
        var faces=mesh.getAsJsonObject("joints").getAsJsonArray("Hand_"+side);
        int start=part.get("start").getAsInt(),count=part.get("count").getAsInt();
        double[] sum=new double[3];
        for(int i=start;i<start+count;i++)for(var p:faces.get(i).getAsJsonObject().getAsJsonArray("p"))
            for(int axis=0;axis<3;axis++)sum[axis]+=p.getAsJsonArray().get(axis).getAsDouble()/(count*3);
        return sum;
    }

    // Independent forward kinematics on the shipped skeleton. Assertions use world
    // positions rather than key indices or the sign of one quaternion component.
    private static Map<String,double[][]> worldPose(JsonObject clip,double time) throws IOException {
        Map<String,double[][]> result=new HashMap<>();
        var skeleton=readJson("/assets/blacklight/animation/player_bind.json");
        for(var root:skeleton.getAsJsonArray("hierarchy"))
            walkPose(root.getAsJsonObject(),identity(),tracks(clip),time,result);
        return result;
    }
    private static void walkPose(JsonObject bone,double[][] parent,Map<String,JsonObject> tracks,double time,Map<String,double[][]> out) {
        String name=bone.get("name").getAsString();double[][] rest=new double[4][4];
        var flat=bone.getAsJsonArray("transform");
        for(int i=0;i<16;i++)rest[i/4][i%4]=flat.get(i).getAsDouble();
        var tr=tracks.get(name);var times=tr.getAsJsonArray("time");int index=0;
        for(int i=1;i<times.size();i++)if(Math.abs(times.get(i).getAsDouble()-time)<Math.abs(times.get(index).getAsDouble()-time))index=i;
        var f=tr.getAsJsonArray("transform").get(index).getAsJsonObject();var q=f.getAsJsonArray("rot");
        // Independent rest-plus-local forward kinematics keeps the exported
        // quaternion convention identical to the native runtime.
        double w=q.get(0).getAsDouble(),x=q.get(1).getAsDouble(),y=q.get(2).getAsDouble(),z=q.get(3).getAsDouble();
        double[][] local={{1-2*(y*y+z*z),2*(x*y-z*w),2*(x*z+y*w),0},
            {2*(x*y+z*w),1-2*(x*x+z*z),2*(y*z-x*w),0},
            {2*(x*z-y*w),2*(y*z+x*w),1-2*(x*x+y*y),0},{0,0,0,1}};
        for(int i=0;i<3;i++) {
            local[i][3]=f.getAsJsonArray("loc").get(i).getAsDouble();
            for(int j=0;j<3;j++) local[i][j]*=f.getAsJsonArray("sca").get(j).getAsDouble();
        }
        double[][] world=multiply(multiply(parent,rest),local);out.put(name,world);
        for(var child:bone.getAsJsonArray("children"))walkPose(child.getAsJsonObject(),world,tracks,time,out);
    }
    private static double[][] identity(){return new double[][]{{1,0,0,0},{0,1,0,0},{0,0,1,0},{0,0,0,1}};}
    private static double[][] multiply(double[][] a,double[][] b){
        double[][] c=new double[4][4];for(int i=0;i<4;i++)for(int j=0;j<4;j++)for(int k=0;k<4;k++)c[i][j]+=a[i][k]*b[k][j];return c;
    }
    private static double[] point(double[][] m,double x,double y,double z){
        return new double[]{m[0][0]*x+m[0][1]*y+m[0][2]*z+m[0][3],m[1][0]*x+m[1][1]*y+m[1][2]*z+m[1][3],m[2][0]*x+m[2][1]*y+m[2][2]*z+m[2][3]};
    }

    private static JsonObject readAnimation(String path) throws IOException {
        return readJson("/assets/blacklight/native_animations/" + path);
    }

    private static JsonObject readJson(String path) throws IOException {
        var stream = MvpResourceTest.class.getResourceAsStream(path);
        assertNotNull(stream, path);
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static Map<String, JsonObject> tracks(JsonObject animation) {
        Map<String, JsonObject> tracks = new HashMap<>();
        for (JsonElement element : animation.getAsJsonArray("animation")) {
            JsonObject track = element.getAsJsonObject();
            tracks.put(track.get("name").getAsString(), track);
            assertEquals(track.getAsJsonArray("time").size(),
                    track.getAsJsonArray("transform").size(),
                    track.get("name").getAsString() + " keyframe count");
        }
        return tracks;
    }

    private static void assertResource(String path) {
        assertNotNull(MvpResourceTest.class.getResource(
                "/assets/blacklight/native_animations/" + path), path);
    }
}
