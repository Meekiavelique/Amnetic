#version 330 core

in vec4 vColor;
in vec3 vPos;

out vec4 FragColor;

void main() {
    if (vColor.a < 0.001) discard;

    // instanced geometry has no normals, so the face normal is the derivative of the position,
    // which is constant across a triangle and is what keeps this flat rather than smooth
    vec3 n = normalize(cross(dFdx(vPos), dFdy(vPos)));
    // vPos is relative to the camera, so the outward face is the one pointing back at it
    if (dot(n, vPos) > 0.0) {
        n = -n;
    }

    float face = abs(n.y) > max(abs(n.x), abs(n.z))
            ? (n.y > 0.0 ? 1.0 : 0.5)
            : (abs(n.z) > abs(n.x) ? 0.8 : 0.6);

    FragColor = vec4(vColor.rgb * face, vColor.a);
}
