#version 150
uniform sampler2D DiffuseSampler;
uniform vec2 InSize;
uniform float Burst;
uniform float Retract;
uniform float Flight;
uniform float Rush;
uniform float FlightTime;
uniform float CentreX;
uniform float CentreY;
uniform float FlowX;
uniform float FlowY;
uniform float FlightAxis;
in vec2 texCoord;
out vec4 fragColor;
void main() {
    vec2 centre=vec2(CentreX,CentreY);
    float aspect=InSize.x/max(InSize.y,1.0);
    vec2 axis=texCoord-centre;
    vec2 radial=axis*vec2(aspect,1.0);
    float radius=length(radial);
    float strength=clamp(max(Burst+Retract,Flight),0.0,1.0);
    // Positive blast pushes the lens out; recovery draws it back into the
    // actor. The recovered source envelopes drive this original shader.
    float direction=Burst-Retract*1.6;
    float ring=exp(-pow((radius-0.24-0.10*Burst)/0.15,2.0));
    float peripheral=smoothstep(0.14,0.65,radius);
    float displacement=(direction*0.024*ring+Flight*(0.012+0.014*Rush)*radius*peripheral);
    vec2 warped=clamp(texCoord+axis*displacement,vec2(0.001),vec2(0.999));
    vec4 colour=texture(DiffuseSampler,warped);
    vec3 sum=colour.rgb;float total=1.0;
    // Radial scene streaks, no bright overlay, tint or HUD modification.
    vec2 blur=axis*(Burst*0.025+Retract*0.012)*smoothstep(0.03,0.45,radius);
    // Looking along the drop yields radial flow; looking across it yields
    // upward world flow. Keep the aim/actor centre legible in either view.
    blur+=(axis*FlightAxis*0.055+vec2(FlowX,FlowY)*0.040)*Flight*peripheral*(1.0+1.4*Rush);
    for(int i=1;i<=6;i++) {
        float offset=float(i)/6.0-0.5;
        vec2 uv=clamp(warped+blur*offset,vec2(0.001),vec2(0.999));
        float weight=1.0-float(i)*0.08;
        sum+=texture(DiffuseSampler,uv).rgb*weight;total+=weight;
    }
    // This pass replaces the whole world RGB. Fog clears use alpha zero;
    // preserving it loses the blue background in the final alpha-blended blit.
    vec3 result=mix(colour.rgb,sum/total,strength);
    // Air-pressure streaks remain visible against empty high-altitude sky.
    // Direction follows the projected world airflow, not a fixed HUD overlay.
    vec2 flow=normalize(vec2(FlowX,FlowY)+vec2(0.00001));
    float across=dot(radial,vec2(-flow.y,flow.x));
    float along=dot(radial,flow);
    float angle=atan(radial.y,radial.x);
    float radialLane=(angle+3.14159265)*10.185916;
    float linearLane=(across+2.0)*42.0;
    float axial=smoothstep(0.35,0.85,FlightAxis);
    float lane=mix(linearLane,radialLane,axial);
    float seed=fract(sin(floor(lane)*127.1)*43758.5453);
    float stripe=pow(max(0.0,1.0-abs(fract(lane)-0.5)*2.0),14.0);
    float travel=mix(along*1.8,radius*2.4,axial);
    float phase=fract(travel-FlightTime*(1.2+1.8*Rush)+seed*7.0);
    float streak=smoothstep(0.02,0.15,phase)*(1.0-smoothstep(0.35+0.2*Rush,0.85,phase));
    float pressure=stripe*streak*peripheral*Flight*(0.025+0.10*Rush);
    result=mix(result,vec3(0.79,0.87,0.91),pressure);
    fragColor=vec4(result,1.0);
}
