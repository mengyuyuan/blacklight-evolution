#version 150
uniform sampler2D DiffuseSampler;
uniform vec2 InSize;
uniform float Burst;
uniform float Retract;
uniform float Flight;
uniform float CentreX;
uniform float CentreY;
in vec2 texCoord;
out vec4 fragColor;
void main() {
    vec2 centre=vec2(CentreX,CentreY);
    float aspect=InSize.x/max(InSize.y,1.0);
    vec2 axis=texCoord-centre;
    vec2 radial=axis*vec2(aspect,1.0);
    float radius=length(radial);
    float strength=clamp(Burst+Retract+Flight*0.55,0.0,1.0);
    // Positive blast pushes the lens out; recovery draws it back into the
    // actor. The recovered source envelopes drive this original shader.
    float direction=Burst-Retract*1.6;
    float ring=exp(-pow((radius-0.24-0.10*Burst)/0.15,2.0));
    float displacement=(direction*0.024*ring+Flight*0.007*radius);
    vec2 warped=clamp(texCoord+axis*displacement,vec2(0.001),vec2(0.999));
    vec4 colour=texture(DiffuseSampler,warped);
    vec3 sum=colour.rgb;float total=1.0;
    // Radial scene streaks, no bright overlay, tint or HUD modification.
    float blur=(Burst*0.025+Retract*0.012+Flight*0.014)*smoothstep(0.03,0.45,radius);
    for(int i=1;i<=6;i++) {
        float offset=(float(i)/6.0-0.5)*blur;
        vec2 uv=clamp(warped+axis*offset,vec2(0.001),vec2(0.999));
        float weight=1.0-float(i)*0.08;
        sum+=texture(DiffuseSampler,uv).rgb*weight;total+=weight;
    }
    // This pass replaces the whole world RGB. Fog clears use alpha zero;
    // preserving it loses the blue background in the final alpha-blended blit.
    fragColor=vec4(mix(colour.rgb,sum/total,strength),1.0);
}
