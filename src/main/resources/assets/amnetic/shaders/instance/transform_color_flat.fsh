#version 330 core

in vec4 vColor;
in vec3 vPos;

out vec4 FragColor;

void main() {
    if (vColor.a < 0.001) discard;

    vec3 n = normalize(cross(dFdx(vPos), dFdy(vPos)));
    if (dot(n, vPos) > 0.0) {
        n = -n;
    }

    float face = abs(n.y) > max(abs(n.x), abs(n.z))
            ? (n.y > 0.0 ? 1.0 : 0.5)
            : (abs(n.z) > abs(n.x) ? 0.8 : 0.6);

    FragColor = vec4(vColor.rgb * face, vColor.a);
}
