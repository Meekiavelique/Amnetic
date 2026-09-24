
vec4 sssTint = texelFetch(MaterialTexels, materialId * 3 + 0);
vec4 sssShape = texelFetch(MaterialTexels, materialId * 3 + 1);
float sssPower = texelFetch(MaterialTexels, materialId * 3 + 2).x;

float sssThickness = sssShape.y;
vec3 sssSigmaT = 1.0 / max(sssTint.rgb, vec3(1e-3));
vec3 sssTransmit = exp(-sssSigmaT * sssThickness) * (1.0 - exp(-sssThickness));

vec3 n = normalize(s.normal);
vec3 v = normalize(-s.fragPos);
vec3 scatter = vec3(0.0);

for (int li = 0; li < LightCount; li++) {
    vec3 lpos = texelFetch(LightTexels, li * 8 + 0).xyz;
    float lrange = texelFetch(LightTexels, li * 8 + 0).w;
    vec3 lcol = texelFetch(LightTexels, li * 8 + 1).rgb * texelFetch(LightTexels, li * 8 + 1).w;
    vec3 ldir = texelFetch(LightTexels, li * 8 + 2).xyz;
    int ltype = int(texelFetch(LightTexels, li * 8 + 2).w + 0.5);
    float lcosIn = texelFetch(LightTexels, li * 8 + 3).x;
    float lcosOut = texelFetch(LightTexels, li * 8 + 3).y;
    int lcurve = int(texelFetch(LightTexels, li * 8 + 3).z + 0.5);
    float lparam = texelFetch(LightTexels, li * 8 + 3).w;

    vec3 l;
    float atten;
    if (ltype == 2) {
        l = -normalize(ldir);
        atten = 1.0;
    } else {
        vec3 delta = lpos - s.fragPos;
        float dist = length(delta);
        l = delta / max(dist, 1e-4);
        atten = falloff(dist, lrange, lcurve, lparam);
        if (ltype == 1) {
            float cone = dot(-l, normalize(ldir));
            atten *= smoothstep(lcosOut, lcosIn, cone);
        }
    }
    if (atten <= 0.0) {
        continue;
    }

    float backlit = pow(clamp(dot(v, -l), 0.0, 1.0), max(sssPower, 1e-2));

    float facing = clamp(-dot(n, l) * 0.5 + 0.5, 0.0, 1.0);

    scatter += lcol * atten * backlit * facing * sssTransmit;
}

return s.radiance + s.albedo * sssTint.rgb * scatter * sssTint.a;
