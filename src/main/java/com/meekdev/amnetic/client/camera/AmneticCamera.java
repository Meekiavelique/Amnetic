package com.meekdev.amnetic.client.camera;

import com.meekdev.amnetic.client.post.internal.CameraState;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

public final class AmneticCamera {

    private AmneticCamera() {}

    public static boolean isReady() {
        return CameraState.valid() && mcCamera() != null;
    }

    public static Vec3 position() {
        Camera c = mcCamera();
        return c != null ? c.position() : Vec3.ZERO;
    }

    public static Vec3 forward() {
        Camera c = mcCamera();
        if (c == null) return new Vec3(0, 0, -1);
        var f = c.forwardVector();
        return new Vec3(f.x(), f.y(), f.z());
    }

    public static Vec3 up() {
        Camera c = mcCamera();
        if (c == null) return new Vec3(0, 1, 0);
        var u = c.upVector();
        return new Vec3(u.x(), u.y(), u.z());
    }

    public static Vec3 right() {
        Camera c = mcCamera();
        if (c == null) return new Vec3(1, 0, 0);
        var l = c.leftVector();
        return new Vec3(-l.x(), -l.y(), -l.z());
    }

    public static float yaw() {
        Camera c = mcCamera();
        return c != null ? c.yRot() : 0f;
    }

    public static float pitch() {
        Camera c = mcCamera();
        return c != null ? c.xRot() : 0f;
    }

    public static float fov() {
        Camera c = mcCamera();
        return c != null ? c.getFov() : 70f;
    }

    public static float near() {
        return Camera.PROJECTION_Z_NEAR;
    }

    public static float far() {
        return CameraState.depthFar();
    }

    public static Matrix4f projection() {
        return CameraState.projection(new Matrix4f());
    }

    public static Matrix4f view() {
        return CameraState.viewRotation(new Matrix4f());
    }

    public static Matrix4f viewProjection() {
        return projection().mul(CameraState.viewRotation(new Matrix4f()));
    }

    public static Matrix4f inverseViewProjection() {
        return CameraState.inverseViewProjection(new Matrix4f());
    }

    public static Vector2f worldToScreen(Vec3 world) {
        Vec3 cam = position();
        Vector4f clip = new Vector4f(
                (float) (world.x - cam.x),
                (float) (world.y - cam.y),
                (float) (world.z - cam.z), 1f);
        viewProjection().transform(clip);
        if (clip.w <= 1.0e-4f) return null; // behind / on the camera plane

        float ndcX = clip.x / clip.w;
        float ndcY = clip.y / clip.w;
        Minecraft mc = Minecraft.getInstance();
        float w = mc.getWindow().getGuiScaledWidth();
        float h = mc.getWindow().getGuiScaledHeight();
        return new Vector2f((ndcX * 0.5f + 0.5f) * w, (1f - (ndcY * 0.5f + 0.5f)) * h);
    }

    public static Ray screenToRay(double screenX, double screenY) {
        Minecraft mc = Minecraft.getInstance();
        float w = mc.getWindow().getGuiScaledWidth();
        float h = mc.getWindow().getGuiScaledHeight();
        float ndcX = (float) (screenX / w) * 2f - 1f;
        float ndcY = 1f - (float) (screenY / h) * 2f;

        Matrix4f inv = inverseViewProjection();
        Vector4f near = inv.transform(new Vector4f(ndcX, ndcY, -1f, 1f));
        Vector4f far = inv.transform(new Vector4f(ndcX, ndcY, 1f, 1f));
        near.div(near.w);
        far.div(far.w);
        Vec3 dir = new Vec3(far.x - near.x, far.y - near.y, far.z - near.z);
        return new Ray(position(), dir);
    }

    public static Vector3f worldToNdc(Vec3 world) {
        Vec3 cam = position();
        Vector4f clip = viewProjection().transform(new Vector4f(
                (float) (world.x - cam.x), (float) (world.y - cam.y), (float) (world.z - cam.z), 1f));
        return new Vector3f(clip.x / clip.w, clip.y / clip.w, clip.z / clip.w);
    }

    public static Vec3 ndcToWorld(float ndcX, float ndcY, float ndcZ) {
        Vector4f p = inverseViewProjection().transform(new Vector4f(ndcX, ndcY, ndcZ, 1f));
        Vec3 cam = position();
        return new Vec3(cam.x + p.x / p.w, cam.y + p.y / p.w, cam.z + p.z / p.w);
    }

    public static boolean isVisible(Vec3 point) {
        Frustum f = frustum();
        return f != null && f.pointInFrustum(point.x, point.y, point.z);
    }

    public static boolean isVisible(AABB box) {
        Frustum f = frustum();
        return f != null && f.isVisible(box);
    }

    public static boolean isVisible(Vec3 center, double radius) {
        return isVisible(new AABB(
                center.x - radius, center.y - radius, center.z - radius,
                center.x + radius, center.y + radius, center.z + radius));
    }

    public static double distanceTo(Vec3 point) {
        return position().distanceTo(point);
    }

    public static double distanceSquaredTo(Vec3 point) {
        return position().distanceToSqr(point);
    }

    public static Vec3 directionTo(Vec3 point) {
        return point.subtract(position()).normalize();
    }

    public static float angleTo(Vec3 point) {
        double dot = forward().dot(directionTo(point));
        return (float) Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dot))));
    }

    public static boolean isLookingAt(Vec3 point, float toleranceDegrees) {
        return angleTo(point) <= toleranceDegrees;
    }

    public static HitResult pick(double maxDistance) {
        return raycast(position(), forward(), maxDistance);
    }

    public static HitResult pickFromScreen(double screenX, double screenY, double maxDistance) {
        Ray ray = screenToRay(screenX, screenY);
        return raycast(ray.origin(), ray.direction(), maxDistance);
    }

    private static HitResult raycast(Vec3 start, Vec3 dir, double maxDistance) {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        Entity camEntity = mc.getCameraEntity();
        if (level == null) return null;

        Vec3 end = start.add(dir.scale(maxDistance));
        CollisionContext collision = camEntity != null
                ? CollisionContext.of(camEntity) : CollisionContext.empty();
        BlockHitResult block = level.clip(new ClipContext(
                start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, collision));

        double reach = block.getType() == HitResult.Type.MISS
                ? maxDistance : start.distanceTo(block.getLocation());
        Vec3 entityEnd = start.add(dir.scale(reach));
        AABB searchBox = new AABB(start, entityEnd).inflate(1.0);
        EntityHitResult entity = camEntity == null ? null
                : ProjectileUtil.getEntityHitResult(
                        level, camEntity, start, entityEnd, searchBox,
                        e -> !e.isSpectator() && e.isPickable(), 0.0f);

        if (entity != null && (block.getType() == HitResult.Type.MISS
                || start.distanceToSqr(entity.getLocation()) < start.distanceToSqr(block.getLocation()))) {
            return entity;
        }
        return block;
    }

    private static Camera mcCamera() {
        Minecraft mc = Minecraft.getInstance();
        return mc.gameRenderer != null ? mc.gameRenderer.getMainCamera() : null;
    }

    private static Frustum frustum() {
        Camera c = mcCamera();
        return c != null ? c.getCullFrustum() : null;
    }
}
