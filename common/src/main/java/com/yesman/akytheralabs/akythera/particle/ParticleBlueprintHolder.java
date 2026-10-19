package com.yesman.akytheralabs.akythera.particle;

import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.data.serializable.RGBA;
import com.yesman.akythera.api.asset.armature.ArmatureManager;
import com.yesman.akythera.api.component.TransformInheritPolicy;
import com.yesman.akythera.client.api.mesh.MeshManager;
import com.yesman.akythera.client.core.asset.mesh.Mesh;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.blueprint.ArmatureComponentBlueprint;
import com.yesman.akythera.core.blueprint.particle.ParticleBlueprint;
import com.yesman.akythera.core.blueprint.particle.ParticleProperties;
import com.yesman.akythera.core.blueprint.particle.SkeletalMeshParticleBlueprint;
import com.yesman.akythera.core.blueprint.particle.SpriteParticleBlueprint;
import com.yesman.akythera.core.blueprint.particle.StaticMeshParticleBlueprint;
import com.yesman.akythera.core.blueprint.particle.TrailParticleBlueprint;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.akythera.asset.EditorAssetAccessors;
import com.yesman.akytheralabs.akythera.entitypatch.component.AnimatorScriptRef;
import com.yesman.akytheralabs.ParticleSourceWriter;
import com.yesman.akytheralabs.dataholder.AbstractDataHolder;
import com.yesman.akytheralabs.dataholder.DTOProvider;
import com.yesman.akytheralabs.dataholder.DataHolder;
import com.yesman.akytheralabs.dataholder.TransformHolder;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.viewer.ParticlePreviewComponent;
import com.yesman.akytheralabs.gui.widget.viewer.ParticleTransformComponent;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/// Modifiable data holder for a [ParticleBlueprint] piece of any [Type]. The fields are the editor's Details panel,
/// each type showing its own; the blueprint is rebuilt from them on [#makeObject].
public final class ParticleBlueprintHolder extends AbstractDataHolder<ParticleBlueprint> implements DTOProvider {
    private static final float DEFAULT_SIZE = 0.2F;
    private static final RGBA WHITE = new RGBA(1.0F, 1.0F, 1.0F);
    private static final float DEFAULT_LENGTH = 1.0F;
    private static final int DEFAULT_INTERPOLATION = 4;
    private static final int DEFAULT_TRAIL_LIFETIME = 6;

    /// The shared, sprite and color fields. Over [RecordCodecBuilder]'s 16 with the rest, so split off [#MESH_AND_TRAIL].
    private static final MapCodec<ParticleBlueprintHolder> SHARED_AND_SPRITE = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                Type.CODEC.optionalFieldOf("type", Type.SPRITE).forGetter(holder -> holder.type),
                Codec.BOOL.optionalFieldOf("has_physics", ParticleProperties.DEFAULT.hasPhysics()).forGetter(holder -> holder.hasPhysics),
                Codec.FLOAT.optionalFieldOf("bb_width", ParticleProperties.DEFAULT.bbWidth()).forGetter(holder -> holder.bbWidth),
                Codec.FLOAT.optionalFieldOf("bb_height", ParticleProperties.DEFAULT.bbHeight()).forGetter(holder -> holder.bbHeight),
                Codec.INT.optionalFieldOf("lifetime", ParticleProperties.DEFAULT.lifetime()).forGetter(holder -> holder.lifetime),
                Codec.FLOAT.optionalFieldOf("gravity", ParticleProperties.DEFAULT.gravity()).forGetter(holder -> holder.gravity),
                Codec.FLOAT.optionalFieldOf("friction", ParticleProperties.DEFAULT.friction()).forGetter(holder -> holder.friction),
                Codec.FLOAT.optionalFieldOf("brightness", ParticleProperties.DEFAULT.brightness()).forGetter(holder -> holder.brightness),
                RGBA.INT_LIST_CODEC.optionalFieldOf("color", WHITE).forGetter(holder -> holder.color),
                Codec.FLOAT.optionalFieldOf("size", DEFAULT_SIZE).forGetter(holder -> holder.size),
                Codec.FLOAT.optionalFieldOf("roll", 0.0F).forGetter(holder -> holder.roll),
                ParticleSprite.CODEC.listOf().optionalFieldOf("sprites", List.of()).forGetter(holder -> holder.sprites)
            ).apply(instance, (type, hasPhysics, bbWidth, bbHeight, lifetime, gravity, friction, brightness, color, size, roll, sprites) -> {
                ParticleBlueprintHolder holder = new ParticleBlueprintHolder();
                holder.type = type;
                holder.hasPhysics = hasPhysics;
                holder.bbWidth = bbWidth;
                holder.bbHeight = bbHeight;
                holder.lifetime = lifetime;
                holder.gravity = gravity;
                holder.friction = friction;
                holder.brightness = brightness;
                holder.color = color;
                holder.size = size;
                holder.roll = roll;
                holder.sprites.clear();
                holder.sprites.addAll(sprites);

                return holder;
            })
    );

    private static final MapCodec<MeshAndTrail> MESH_AND_TRAIL = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                EditorAssetAccessors.MESH_CODEC.optionalFieldOf("mesh").forGetter(MeshAndTrail::mesh),
                Identifier.CODEC.optionalFieldOf("texture").forGetter(MeshAndTrail::texture),
                TransformHolder.CODEC.optionalFieldOf("transform").forGetter(MeshAndTrail::transform),
                EditorAssetAccessors.ARMATURE_CODEC.optionalFieldOf("armature").forGetter(MeshAndTrail::armature),
                AnimatorScriptRef.CODEC.optionalFieldOf("animator_script").forGetter(MeshAndTrail::animatorScript),
                TransformHolder.CODEC.optionalFieldOf("armature_transform").forGetter(MeshAndTrail::armatureTransform),
                Codec.BOOL.optionalFieldOf("copy_attached_pose", false).forGetter(MeshAndTrail::copyAttachedPose),
                Codec.FLOAT.optionalFieldOf("length", DEFAULT_LENGTH).forGetter(MeshAndTrail::length),
                Codec.FLOAT.optionalFieldOf("fade_out", 1.0F).forGetter(MeshAndTrail::fadeOut),
                Codec.INT.optionalFieldOf("interpolation", DEFAULT_INTERPOLATION).forGetter(MeshAndTrail::interpolation),
                Codec.INT.optionalFieldOf("trail_lifetime", DEFAULT_TRAIL_LIFETIME).forGetter(MeshAndTrail::trailLifetime)
            ).apply(instance, MeshAndTrail::new)
    );

    public static final Codec<ParticleBlueprintHolder> EDITOR_CODEC = Codec.mapPair(SHARED_AND_SPRITE, MESH_AND_TRAIL).xmap(
        pair -> {
            ParticleBlueprintHolder holder = pair.getFirst();
            MeshAndTrail rest = pair.getSecond();

            rest.mesh().ifPresent(mesh -> holder.mesh = mesh);
            holder.texture = rest.texture().map(AkytheraProject::resourceTextureOf).orElse(null);
            rest.transform().ifPresent(transform -> holder.transform = transform);
            rest.armature().ifPresent(armature -> holder.armature = armature);
            rest.animatorScript().ifPresent(animatorScript -> holder.animatorScript = animatorScript);
            rest.armatureTransform().ifPresent(armatureTransform -> holder.armatureTransform = armatureTransform);
            holder.copyAttachedPose = rest.copyAttachedPose();
            holder.length = rest.length();
            holder.fadeOut = rest.fadeOut();
            holder.interpolation = rest.interpolation();
            holder.trailLifetime = rest.trailLifetime();

            return holder;
        },
        holder -> Pair.of(holder, new MeshAndTrail(
            Optional.of(holder.mesh),
            Optional.ofNullable(holder.texture).map(ClientAsset.Texture::texturePath),
            Optional.of(holder.transform),
            Optional.of(holder.armature),
            Optional.of(holder.animatorScript),
            Optional.of(holder.armatureTransform),
            holder.copyAttachedPose,
            holder.length,
            holder.fadeOut,
            holder.interpolation,
            holder.trailLifetime
        ))
    ).codec();

    @EditorEditable(label = LangKeys.GUI_PARTICLE_EDITOR_SCREEN_WIDGET_PARTICLE_TYPE, order = 0, rebuildOnWrite = true)
    private Type type = Type.SPRITE;

    // Every type

    @EditorEditable(label = LangKeys.GUI_PARTICLE_EDITOR_SCREEN_WIDGET_HAS_PHYSICS, order = 1)
    private boolean hasPhysics = ParticleProperties.DEFAULT.hasPhysics();

    /// In blocks.
    @EditorEditable(label = LangKeys.GUI_PARTICLE_EDITOR_SCREEN_WIDGET_BB_WIDTH, order = 2, min = 0, viewerFocus = ParticlePreviewComponent.class)
    private float bbWidth = ParticleProperties.DEFAULT.bbWidth();

    /// In blocks.
    @EditorEditable(label = LangKeys.GUI_PARTICLE_EDITOR_SCREEN_WIDGET_BB_HEIGHT, order = 3, min = 0, viewerFocus = ParticlePreviewComponent.class)
    private float bbHeight = ParticleProperties.DEFAULT.bbHeight();

    /// In ticks.
    @EditorEditable(label = LangKeys.GUI_PARTICLE_EDITOR_SCREEN_WIDGET_LIFETIME, order = 4, min = 1, visibleWhen = "hasLifetime")
    private int lifetime = ParticleProperties.DEFAULT.lifetime();

    @EditorEditable(label = LangKeys.GUI_PARTICLE_EDITOR_SCREEN_WIDGET_GRAVITY, order = 5)
    private float gravity = ParticleProperties.DEFAULT.gravity();

    @EditorEditable(label = LangKeys.GUI_PARTICLE_EDITOR_SCREEN_WIDGET_FRICTION, order = 6, min = 0, max = 1)
    private float friction = ParticleProperties.DEFAULT.friction();

    @EditorEditable(label = LangKeys.GUI_PARTICLE_EDITOR_SCREEN_WIDGET_BRIGHTNESS, order = 7, min = 0, max = 1)
    private float brightness = ParticleProperties.DEFAULT.brightness();

    @EditorEditable(label = LangKeys.GUI_PARTICLE_EDITOR_SCREEN_WIDGET_COLOR, order = 8)
    private RGBA color = WHITE;

    // Sprite

    /// The quad's half extent, in blocks.
    @EditorEditable(label = LangKeys.GUI_PARTICLE_EDITOR_SCREEN_WIDGET_SIZE, order = 9, min = 0, visibleWhen = "isSprite")
    private float size = DEFAULT_SIZE;

    /// In degrees.
    @EditorEditable(label = LangKeys.GUI_PARTICLE_EDITOR_SCREEN_WIDGET_ROLL, order = 10, visibleWhen = "isSprite")
    private float roll;

    /// In animation order.
    @EditorEditable(label = LangKeys.GUI_PARTICLE_EDITOR_SCREEN_WIDGET_SPRITES, order = 11, defaultElement = "defaultSprite", visibleWhen = "isSprite")
    private final List<ParticleSprite> sprites = new ArrayList<>(List.of(defaultSprite()));

    // Static and skeletal mesh

    /// The [DataHolder#EMPTY_REFERENCE] placeholder displays as `<none>` and builds no particle.
    @EditorEditable(label = LangKeys.GUI_PARTICLE_EDITOR_SCREEN_WIDGET_MESH, order = 12, visibleWhen = "isMesh")
    private AssetAccessor<? extends Mesh<?>> mesh = EditorAssetAccessors.mesh(DataHolder.EMPTY_REFERENCE);

    /// The model's offset from the emitter, its starting rotation and its starting scale, in the emitter's space.
    @EditorEditable(label = LangKeys.GUI_PARTICLE_EDITOR_SCREEN_WIDGET_TRANSFORM, order = 13, visibleWhen = "isMesh", viewerFocus = ParticleTransformComponent.class)
    private TransformHolder transform = TransformHolder.identity();

    /// The particle's own armature, posed by [#animatorScript]. Left on the `<none>` placeholder, the model is drawn
    /// as authored.
    @EditorEditable(label = LangKeys.GUI_PARTICLE_EDITOR_SCREEN_WIDGET_ARMATURE, order = 14, visibleWhen = "isSkeletal")
    private AssetAccessor<? extends Armature> armature = EditorAssetAccessors.armature(DataHolder.EMPTY_REFERENCE);

    @EditorEditable(label = LangKeys.GUI_PARTICLE_EDITOR_SCREEN_WIDGET_ANIMATOR_SCRIPT, order = 15, visibleWhen = "isSkeletal")
    private AnimatorScriptRef animatorScript = AnimatorScriptRef.none();

    /// Where the skeleton sits in the particle's space.
    @EditorEditable(label = LangKeys.GUI_PARTICLE_EDITOR_SCREEN_WIDGET_ARMATURE_TRANSFORM, order = 16, visibleWhen = "isSkeletal")
    private TransformHolder armatureTransform = TransformHolder.identity();

    @EditorEditable(label = LangKeys.GUI_PARTICLE_EDITOR_SCREEN_WIDGET_COPY_ATTACHED_POSE, order = 17, visibleWhen = "isSkeletal")
    private boolean copyAttachedPose;

    // Mesh and trail

    /// A mesh draws with the texture its metadata names when this is null. A trail needs one.
    @EditorEditable(label = LangKeys.GUI_PARTICLE_EDITOR_SCREEN_WIDGET_TEXTURE, order = 18, visibleWhen = "usesTexture")
    private ClientAsset.@Nullable Texture texture;

    // Trail

    /// In blocks, from the emitter's origin to the trail's far side along its +Z axis.
    @EditorEditable(label = LangKeys.GUI_PARTICLE_EDITOR_SCREEN_WIDGET_LENGTH, order = 19, min = 0, visibleWhen = "isTrail")
    private float length = DEFAULT_LENGTH;

    /// The share of the emitter's time span after which the trail fades out. 1 never fades.
    @EditorEditable(label = LangKeys.GUI_PARTICLE_EDITOR_SCREEN_WIDGET_FADE_OUT, order = 20, min = 0, max = 1, visibleWhen = "isTrail")
    private float fadeOut = 1.0F;

    /// Quads drawn per sampled edge.
    @EditorEditable(label = LangKeys.GUI_PARTICLE_EDITOR_SCREEN_WIDGET_INTERPOLATION, order = 21, min = 1, max = 32, visibleWhen = "isTrail")
    private int interpolation = DEFAULT_INTERPOLATION;

    /// In ticks, how long one sampled edge stays.
    @EditorEditable(label = LangKeys.GUI_PARTICLE_EDITOR_SCREEN_WIDGET_TRAIL_LIFETIME, order = 22, min = 1, visibleWhen = "isTrail")
    private int trailLifetime = DEFAULT_TRAIL_LIFETIME;

    /// The sprite a new particle and a new list element start with, see [EditorEditable#defaultElement].
    static ParticleSprite defaultSprite() {
        return new ParticleSprite(Identifier.withDefaultNamespace("generic_0"));
    }

    // Whether a row shows, see [EditorEditable#visibleWhen]

    boolean isSprite() {
        return type == Type.SPRITE;
    }

    boolean isMesh() {
        return type == Type.STATIC_MESH || type == Type.SKELETAL_MESH;
    }

    boolean isSkeletal() {
        return type == Type.SKELETAL_MESH;
    }

    boolean isTrail() {
        return type == Type.TRAIL;
    }

    /// A trail lives as long as its emitter's time span.
    boolean hasLifetime() {
        return !isTrail();
    }

    boolean usesTexture() {
        return isMesh() || isTrail();
    }

    /// @throws IllegalArgumentException for a sprite particle that borrows a vanilla particle type's sprites, which the
    ///                                  editor can't edit
    @Override
    public void readFrom(final ParticleBlueprint object) {
        ParticleProperties properties = object.properties();

        this.hasPhysics = properties.hasPhysics();
        this.bbWidth = properties.bbWidth();
        this.bbHeight = properties.bbHeight();
        this.lifetime = properties.lifetime();
        this.gravity = properties.gravity();
        this.friction = properties.friction();
        this.brightness = properties.brightness();

        switch (object) {
            case SpriteParticleBlueprint sprite -> {
                List<Identifier> ids = sprite.sprites().left()
                    .orElseThrow(() -> new IllegalArgumentException("The editor can't edit a sprite particle that borrows a vanilla particle type's sprites"));

                this.type = Type.SPRITE;
                this.color = sprite.color();
                this.size = sprite.size();
                this.roll = sprite.roll();
                this.sprites.clear();
                ids.stream().map(ParticleSprite::new).forEach(this.sprites::add);
            }
            case StaticMeshParticleBlueprint staticMesh -> {
                this.type = Type.STATIC_MESH;
                this.color = staticMesh.color();
                this.mesh = staticMesh.mesh();
                this.texture = staticMesh.texture().map(AkytheraProject::resourceTextureOf).orElse(null);
                this.transform = TransformHolder.of(staticMesh.transform());
            }
            case SkeletalMeshParticleBlueprint skeletal -> {
                this.type = Type.SKELETAL_MESH;
                this.color = skeletal.color();
                this.mesh = skeletal.mesh();
                this.texture = skeletal.texture().map(AkytheraProject::resourceTextureOf).orElse(null);
                this.transform = TransformHolder.of(skeletal.transform());
                this.armature = skeletal.armature().<AssetAccessor<? extends Armature>>map(ArmatureComponentBlueprint::armature)
                    .orElseGet(() -> EditorAssetAccessors.armature(DataHolder.EMPTY_REFERENCE));
                this.animatorScript = skeletal.armature().map(blueprint -> AnimatorScriptRef.of(blueprint.scriptFactory())).orElseGet(AnimatorScriptRef::none);
                this.armatureTransform = skeletal.armature().map(blueprint -> TransformHolder.of(blueprint.transform())).orElseGet(TransformHolder::identity);
                this.copyAttachedPose = skeletal.copyAttachedPose();
            }
            case TrailParticleBlueprint trail -> {
                this.type = Type.TRAIL;
                this.color = trail.color();
                this.texture = AkytheraProject.resourceTextureOf(trail.texture());
                this.length = trail.length();
                this.fadeOut = trail.fadeOut();
                this.interpolation = trail.interpolation();
                this.trailLifetime = trail.trailLifetime();
            }
            default -> throw new IllegalArgumentException("The editor can't edit " + object.getClass().getSimpleName());
        }
    }

    /// `piece`: the piece's constructor expression, at the depth the generated provider registers it at.
    ///
    /// @throws IllegalStateException as [#makeObject]
    @Override
    public Map<String, Object> getDTO() {
        return Map.of("piece", ParticleSourceWriter.piece(makeObject(), 3));
    }

    /// @throws IllegalStateException when the [#type]'s required fields are unset: a sprite, a mesh, an animator script
    ///                               for a picked armature, or a trail texture
    @Override
    public ParticleBlueprint makeObject() {
        ParticleProperties properties = new ParticleProperties(hasPhysics, bbWidth, bbHeight, lifetime, gravity, friction, brightness);
        Optional<Identifier> texturePath = Optional.ofNullable(texture).map(ClientAsset.Texture::texturePath);

        return switch (type) {
            case SPRITE -> {
                if (sprites.isEmpty()) throw new IllegalStateException("A sprite particle needs at least one sprite");

                yield new SpriteParticleBlueprint(properties, size, color, roll, Either.left(sprites.stream().map(ParticleSprite::id).toList()));
            }
            case STATIC_MESH -> new StaticMeshParticleBlueprint(properties, pickedMesh(), texturePath, color, transform.makeObject());
            case SKELETAL_MESH -> new SkeletalMeshParticleBlueprint(
                properties, pickedMesh(), armatureBlueprint(), copyAttachedPose, texturePath, color, transform.makeObject()
            );
            case TRAIL -> new TrailParticleBlueprint(
                properties,
                length,
                color,
                fadeOut,
                interpolation,
                trailLifetime,
                texturePath.orElseThrow(() -> new IllegalStateException("A trail particle needs a texture"))
            );
        };
    }

    /// [#mesh], once one is picked.
    ///
    /// @throws IllegalStateException while the row is on the `<none>` placeholder
    private AssetAccessor<? extends Mesh<?>> pickedMesh() {
        if (isUnpicked(mesh, MeshManager.DIRECTORY)) throw new IllegalStateException("A mesh particle needs a mesh");

        return mesh;
    }

    /// The particle's own armature component, or empty while [#armature] is on the `<none>` placeholder. It never roots
    /// motion, and takes on the particle's whole space.
    ///
    /// @throws IllegalStateException for a picked armature without an animator script
    private Optional<ArmatureComponentBlueprint> armatureBlueprint() {
        if (isUnpicked(armature, ArmatureManager.DIRECTORY)) return Optional.empty();

        if (animatorScript instanceof AnimatorScriptRef.DataPackScript dataPack && dataPack.holder().unwrapKey().map(key -> key.identifier().equals(DataHolder.EMPTY_REFERENCE)).orElse(true)) {
            throw new IllegalStateException("A skeletal mesh particle with an armature needs an animator script");
        }

        return Optional.of(new ArmatureComponentBlueprint(
            armature, animatorScript.toFactory(), false, armatureTransform.makeObject(), TransformInheritPolicy.LOC_ROT_SCA, Map.of(), true
        ));
    }

    /// Whether `accessor` is the `<none>` placeholder: [DataHolder#EMPTY_REFERENCE] under its asset `directory`.
    private static boolean isUnpicked(final AssetAccessor<?> accessor, final String directory) {
        Identifier id = accessor.identifier();

        return Identifier.fromNamespaceAndPath(id.getNamespace(), id.getPath().replace(directory, "")).equals(DataHolder.EMPTY_REFERENCE);
    }

    public TransformHolder getTransform() {
        return transform;
    }

    /// In blocks.
    public float getBbWidth() {
        return bbWidth;
    }

    /// In blocks.
    public float getBbHeight() {
        return bbHeight;
    }

    /// Sets the bounding box, in blocks. A negative size becomes zero.
    public void setBoundingBox(final float width, final float height) {
        this.bbWidth = Math.max(width, 0.0F);
        this.bbHeight = Math.max(height, 0.0F);
    }

    public Type getType() {
        return type;
    }

    /// The particle classes the editor builds.
    public enum Type implements StringRepresentable {
        SPRITE, STATIC_MESH, SKELETAL_MESH, TRAIL;

        public static final Codec<Type> CODEC = StringRepresentable.fromEnum(Type::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /// The mesh and trail fields of [#EDITOR_CODEC].
    private record MeshAndTrail(
        Optional<AssetAccessor<? extends Mesh<?>>> mesh,
        Optional<Identifier> texture,
        Optional<TransformHolder> transform,
        Optional<AssetAccessor<? extends Armature>> armature,
        Optional<AnimatorScriptRef> animatorScript,
        Optional<TransformHolder> armatureTransform,
        boolean copyAttachedPose,
        float length,
        float fadeOut,
        int interpolation,
        int trailLifetime
    ) {
    }
}
