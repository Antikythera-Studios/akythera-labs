package com.yesman.akytheralabs.gui.widget.viewer;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewer;
import com.yesman.akythera.client.api.particle.AkytheraMeshParticle;
import com.yesman.akythera.client.api.particle.AkytheraMeshParticleGroup;
import com.yesman.akythera.client.api.particle.AkytheraParticle;
import com.yesman.akythera.client.api.particle.AkytheraParticleSystem;
import com.yesman.akythera.client.api.particle.AkytheraSpriteParticle;
import com.yesman.akythera.client.api.particle.AkytheraTrailParticle;
import com.yesman.akythera.client.api.particle.ParticleHost;
import com.yesman.akythera.client.core.renderer.RenderUtils;
import com.yesman.akythera.client.core.renderer.TrailRenderer;
import com.yesman.akythera.core.blueprint.particle.ParticleBlueprint;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.gui.widget.PreviewTextureCache;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/// The particles a [Model3DViewer] plays with no level: they tick and draw here, not in the particle engine. Physics
/// collides with the `y = 0` floor only, and a particle is tinted gray by its brightness ([#brightnessTint]).
///
/// A texture or sprite in the open project is drawn from its file, since the game holds only what the loaded resource
/// packs carry.
public final class PreviewParticles implements ParticleHost {
    /// The share of a particle's color drawn at brightness 0. Brightness 1 draws all of it.
    private static final float DARKEST = 128.0F / 255.0F;

    /// The pieces emitters name, or null for an unknown one.
    private final Function<ResourceKey<ParticleBlueprint>, @Nullable ParticleBlueprint> pieces;

    private final List<Particle> particles = new ArrayList<>();
    private final PreviewTextureCache projectTextures = new PreviewTextureCache();

    public PreviewParticles(final Function<ResourceKey<ParticleBlueprint>, @Nullable ParticleBlueprint> pieces) {
        this.pieces = pieces;
    }

    /// Plays `system`, which ticks with the particles it spawns. Build it with this as its host.
    public void addSystem(final AkytheraParticleSystem system) {
        particles.add(system);
    }

    /// Stops ticking `system` without removing it, so the particles it spawned stay as they are.
    public void dropSystem(final AkytheraParticleSystem system) {
        particles.remove(system);
    }

    /// Removes the particles `test` matches.
    public void removeParticles(final Predicate<AkytheraParticle> test) {
        for (Particle particle : particles) {
            if (particle instanceof AkytheraParticle akytheraParticle && test.test(akytheraParticle)) akytheraParticle.remove();
        }

        particles.removeIf(particle -> !particle.isAlive());
    }

    /// Ticks every particle once. A particle spawned during the pass ticks on the next one, as in the particle engine.
    public void tick() {
        for (Particle particle : List.copyOf(particles)) particle.tick();

        particles.removeIf(particle -> !particle.isAlive());
    }

    /// Frame-updates the particles' animators by `frameTime` seconds.
    public void frameUpdate(final float frameTime, final float partialTick) {
        AkytheraMeshParticleGroup.frameUpdate(particles, frameTime, partialTick);
    }

    public boolean isEmpty() {
        return particles.isEmpty();
    }

    public void clear() {
        particles.clear();
    }

    /// Frees the project textures read so far.
    public void releaseTextures() {
        projectTextures.releaseAll();
    }

    /// Draws every particle at `partialTick` between its last two ticks, and answers where each stands.
    public Drawn draw(final Model3DViewer model3DViewer, final PoseStack poseStack, final ByteBufferBuilder byteBuffer, final float partialTick) {
        Vector3f right = model3DViewer.getCameraRight();
        Vector3f up = model3DViewer.getCameraUp();
        Vector3f eye = model3DViewer.getCameraPosition();
        Vector3f backward = new Vector3f(right).cross(up);
        List<Sprite> sprites = new ArrayList<>();
        List<Vec3> bottoms = new ArrayList<>();
        List<AABB> boxes = new ArrayList<>();
        boolean trailQueued = false;

        for (Particle particle : particles) {
            if (!(particle instanceof AkytheraParticle akytheraParticle)) continue;

            int tint = brightnessTint(akytheraParticle.state().getBrightness());

            if (particle instanceof AkytheraSpriteParticle sprite) {
                AkytheraSpriteParticle.Frame frame = sprite.frame(partialTick);

                sprites.add(new Sprite(frame, tint));
                bottoms.add(frame.position());
                continue;
            }

            bottoms.add(akytheraParticle.getPosition());
            boxes.add(particle.getBoundingBox());

            if (particle instanceof AkytheraMeshParticle mesh) {
                mesh.drawImmediate(poseStack, byteBuffer, partialTick, tint, this::projectTexture, eye);
            } else if (particle instanceof AkytheraTrailParticle trail) {
                TrailRenderer.Trail extracted = trail.extractTrail(Vec3.ZERO, partialTick, tint, this::projectTexture);

                if (extracted != null) {
                    TrailRenderer.getInstance().submit(extracted);
                    trailQueued = true;
                }
            }
        }

        if (trailQueued) TrailRenderer.getInstance().render();

        // Farthest first along the view axis, so translucent quads blend over those behind
        sprites.sort(Comparator.comparingDouble(sprite -> backward.dot(
            (float)sprite.frame().position().x, (float)sprite.frame().position().y, (float)sprite.frame().position().z
        )));

        for (Sprite sprite : sprites) {
            drawSprite(sprite, right, up, poseStack.last(), byteBuffer);
        }

        return new Drawn(sprites, bottoms, boxes);
    }

    /// Draws one camera-facing quad, from the project's file of its sprite when there is one.
    private void drawSprite(final Sprite drawn, final Vector3f right, final Vector3f up, final PoseStack.Pose pose, final ByteBufferBuilder byteBuffer) {
        AkytheraSpriteParticle.Frame frame = drawn.frame();
        Identifier texture;
        float u0 = 0.0F, u1 = 1.0F, v0 = 0.0F, v1 = 1.0F;
        Identifier spriteId = frame.spriteId();
        Path file = spriteId == null ? null : projectFile(spriteId.withPath(AkytheraProject.PARTICLE_SPRITES_DIRECTORY + spriteId.getPath() + ".png"));

        if (file != null) {
            PreviewTextureCache.Entry entry = projectTextures.get(file);

            if (entry == null) return;

            texture = entry.textureId();
        } else {
            TextureAtlasSprite sprite = frame.sprite();
            texture = sprite.atlasLocation();
            u0 = sprite.getU0();
            u1 = sprite.getU1();
            v0 = sprite.getV0();
            v1 = sprite.getV1();
        }

        float cos = Mth.cos(frame.roll()) * frame.size();
        float sin = Mth.sin(frame.roll()) * frame.size();
        Vector3f quadRight = new Vector3f(right).mul(cos).add(new Vector3f(up).mul(sin));
        Vector3f quadUp = new Vector3f(up).mul(cos).sub(new Vector3f(right).mul(sin));
        Vec3 center = frame.position();
        int color = ARGB.multiply(frame.color().packARGB(), drawn.tint());

        RenderType renderType = AkytheraSpriteParticle.renderType(texture);
        BufferBuilder builder = new BufferBuilder(byteBuffer, renderType.primitiveTopology(), renderType.format());

        vertex(builder, pose, center, quadRight, quadUp, 1.0F, -1.0F, u1, v1, color);
        vertex(builder, pose, center, quadRight, quadUp, 1.0F, 1.0F, u1, v0, color);
        vertex(builder, pose, center, quadRight, quadUp, -1.0F, 1.0F, u0, v0, color);
        vertex(builder, pose, center, quadRight, quadUp, -1.0F, -1.0F, u0, v1, color);

        MeshData meshData = builder.build();

        if (meshData != null) RenderUtils.drawImmediate(renderType, meshData);
    }

    private static void vertex(
        final BufferBuilder builder, final PoseStack.Pose pose, final Vec3 center, final Vector3f right, final Vector3f up,
        final float alongRight, final float alongUp, final float u, final float v, final int color
    ) {
        builder.addVertex(
                pose,
                (float)center.x + right.x * alongRight + up.x * alongUp,
                (float)center.y + right.y * alongRight + up.y * alongUp,
                (float)center.z + right.z * alongRight + up.z * alongUp
            )
            .setUv(u, v)
            .setColor(color)
            .setLight(Model3DViewer.FULL_BRIGHT);
    }

    /// The gray a particle's color is multiplied by: [#DARKEST] at brightness 0, white at 1. ARGB.
    private static int brightnessTint(final float brightness) {
        return ARGB.scaleRGB(-1, Mth.lerp(Mth.clamp(brightness, 0.0F, 1.0F), DARKEST, 1.0F));
    }

    /// `texturePath` drawn from the open project's file when the project has one, else as the loaded resource packs
    /// carry it. The missing texture while the file is still being read.
    private Identifier projectTexture(final Identifier texturePath) {
        Path file = projectFile(texturePath);

        if (file == null) return texturePath;

        PreviewTextureCache.Entry entry = projectTextures.get(file);

        return entry == null ? MissingTextureAtlasSprite.getLocation() : entry.textureId();
    }

    /// The open project's file of `texturePath`, `<namespace>:textures/….png`, or null when the project has none.
    private static @Nullable Path projectFile(final Identifier texturePath) {
        AkytheraProject project = AkytheraProject.getCurrentProjectOrNull();

        if (project == null || !texturePath.getNamespace().equals(project.getProjectName())) return null;

        Path file = project.getProjectRoot().resolve("assets").resolve(texturePath.getPath());

        return Files.isRegularFile(file) ? file : null;
    }

    // ***************************************************************
    // ParticleHost
    // ***************************************************************

    @Override
    public void add(final AkytheraParticle particle) {
        particles.add((Particle)particle);
    }

    @Override
    public @Nullable ParticleBlueprint piece(final ResourceKey<ParticleBlueprint> key) {
        return pieces.apply(key);
    }

    /// Stops a box falling through the `y = 0` floor.
    @Override
    public Vec3 collide(final AABB box, final Vec3 motion) {
        if (motion.y >= 0.0D || box.minY < 0.0D) return motion;

        return new Vec3(motion.x, Math.max(motion.y, -box.minY), motion.z);
    }

    /// What a [#draw] drew.
    ///
    /// @param sprites the sprite particles, farthest first
    /// @param bottoms where each particle stands, its bounding box's bottom center
    /// @param boxes   the bounding boxes of the particles that are no sprites
    public record Drawn(List<Sprite> sprites, List<Vec3> bottoms, List<AABB> boxes) {
        public static final Drawn NOTHING = new Drawn(List.of(), List.of(), List.of());
    }

    /// One sprite particle as drawn in a frame.
    ///
    /// @param tint multiplies its color, ARGB, see [#brightnessTint]
    public record Sprite(AkytheraSpriteParticle.Frame frame, int tint) {
    }
}
