package com.yesman.akytheralabs.akythera.entitypatch.component;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.component.TransformInheritPolicy;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.client.api.component.impl.MeshComponent;
import com.yesman.akythera.client.api.component.impl.MeshComponentBlueprint;
import com.yesman.akythera.client.api.mesh.MeshManager;
import com.yesman.akythera.client.core.asset.mesh.Mesh;
import com.yesman.akytheralabs.NodeTreeSourceRenderer;
import com.yesman.akytheralabs.ScriptNodeSourceCompiler;
import com.yesman.akytheralabs.akythera.animation.AnimatorScriptHolder;
import com.yesman.akytheralabs.akythera.animation.ConditionGraphCompiler;
import com.yesman.akytheralabs.akythera.asset.EditorAssetAccessors;
import com.yesman.akytheralabs.dataholder.DataHolder;
import com.yesman.akytheralabs.dataholder.EditorReference;
import com.yesman.akytheralabs.dataholder.TransformHolder;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.editor.EditorNodeGraph;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.script.ScriptGraphData;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/// Editor holder for an `akythera:mesh` ([MeshComponent]) — a mesh drawn wherever its host places it.
///
/// Attachable under a [TransformSocketHolder], where it rides that socket, and under an
/// [ArmatureComponentHolder], where it is the entity's own model. Several may hang off one armature.
/// Not a [RootAttachableComponent]. Its [#localTransform] is in its host's space, or in its armature's for a
/// skeletal mesh.
///
/// [#renderTypeGetter] is a raw [ScriptGraphData] canvas edited in a center tab, stored the way
/// [AnimatorScriptHolder] stores its canvases. Its result root takes a [RenderType], and the owning
/// patch is what the canvas answers for `self` — a read of the [MeshComponent#OWNER_PARAM] local, which
/// fills every unwired owner slot.
public final class MeshComponentHolder extends ComponentDataHolder<MeshComponentBlueprint> implements TransformComponentHolder {
    public static final MapCodec<MeshComponentHolder> EDITOR_CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
            EditorAssetAccessors.MESH_CODEC.optionalFieldOf("mesh")
                .forGetter(h -> Optional.of(h.mesh)),
            ScriptGraphData.CODEC.optionalFieldOf("render_type_getter", ScriptGraphData.EMPTY)
                .forGetter(h -> h.renderTypeGetter),
            TransformHolder.CODEC.optionalFieldOf("transform")
                .forGetter(h -> Optional.of(h.localTransform)),
            TransformInheritPolicy.CODEC.optionalFieldOf("transform_inherit_policy", TransformInheritPolicy.LOC_ROT_SCA)
                .forGetter(TransformComponentHolder::getTransformInheritPolicy)
        ).apply(instance, (mesh, renderTypeGetter, transform, transformInheritPolicy) -> new MeshComponentHolder(
            mesh.orElseGet(() -> EditorAssetAccessors.mesh(DataHolder.EMPTY_REFERENCE)),
            renderTypeGetter,
            transform.orElseGet(TransformHolder::identity),
            transformInheritPolicy))
    );

    /// The mesh drawn. The [DataHolder#EMPTY_REFERENCE] placeholder displays as `<none>` and lowers to
    /// no mesh at all, which draws nothing.
    @EditorEditable(label = LangKeys.GUI_ENTITY_PATCH_SCREEN_ROW_MESH, order = 0)
    private AssetAccessor<? extends Mesh<?>> mesh;

    @EditorEditable(label = LangKeys.GUI_ENTITY_PATCH_SCREEN_ROW_RENDER_TYPE_GETTER, order = 1)
    @EditorNodeGraph(resultType = RenderType.class)
    private ScriptGraphData renderTypeGetter;

    /// The elements of its host's space, or of its armature's for a skeletal mesh, the mesh takes on.
    @EditorEditable(label = LangKeys.GUI_ENTITY_PATCH_SCREEN_ROW_TRANSFORM_INHERIT_POLICY, order = 2)
    private TransformInheritPolicy transformInheritPolicy;

    /// Where the mesh sits. A nested editor object, rendered as location / rotation / scale rows under one
    /// "Local Transform" heading.
    @EditorEditable(label = LangKeys.GUI_ENTITY_PATCH_SCREEN_SECTION_LOCAL_TRANSFORM, order = 3)
    private TransformHolder localTransform;

    public MeshComponentHolder(
        AssetAccessor<? extends Mesh<?>> mesh,
        ScriptGraphData renderTypeGetter,
        TransformHolder localTransform,
        TransformInheritPolicy transformInheritPolicy
    ) {
        this.mesh = mesh;
        this.renderTypeGetter = renderTypeGetter;
        this.localTransform = localTransform;
        this.transformInheritPolicy = transformInheritPolicy;
    }

    /// A freshly attached mesh component: nothing picked, so it draws nothing.
    public static MeshComponentHolder createDefault() {
        return new MeshComponentHolder(
            EditorAssetAccessors.mesh(DataHolder.EMPTY_REFERENCE),
            ScriptGraphData.EMPTY,
            TransformHolder.identity(),
            TransformInheritPolicy.LOC_ROT_SCA
        );
    }

    /// The authored mesh, or null while the row is on the `<none>` placeholder. The trimmed id is what
    /// the accessor was issued for — [MeshManager#DIRECTORY] is not part of an authored id.
    public @Nullable AssetAccessor<? extends Mesh<?>> getMesh() {
        Identifier id = mesh.identifier();
        Identifier trimmed = Identifier.fromNamespaceAndPath(
            id.getNamespace(), id.getPath().replace(MeshManager.DIRECTORY, ""));

        return trimmed.equals(DataHolder.EMPTY_REFERENCE) ? null : mesh;
    }

    public void setMesh(AssetAccessor<? extends Mesh<?>> mesh) {
        this.mesh = mesh;
    }

    public ScriptGraphData getRenderTypeGetter() {
        return renderTypeGetter;
    }

    public void setRenderTypeGetter(ScriptGraphData renderTypeGetter) {
        this.renderTypeGetter = renderTypeGetter;
    }

    @Override
    public TransformHolder getLocalTransform() {
        return localTransform;
    }

    public void setLocalTransform(TransformHolder localTransform) {
        this.localTransform = localTransform;
    }

    @Override
    public TransformInheritPolicy getTransformInheritPolicy() {
        return transformInheritPolicy;
    }

    public void setTransformInheritPolicy(TransformInheritPolicy transformInheritPolicy) {
        this.transformInheritPolicy = transformInheritPolicy;
    }

    @Override
    public void readFrom(MeshComponentBlueprint object) {
        mesh = object.mesh() == null
            ? EditorAssetAccessors.mesh(DataHolder.EMPTY_REFERENCE)
            : object.mesh();

        // Runtime PureNode trees cannot be decompiled back into editor canvases, so an imported
        // component opens with an empty canvas.
        renderTypeGetter = ScriptGraphData.EMPTY;
        localTransform.readFrom(object.transform());
        transformInheritPolicy = object.transformInheritPolicy();
    }

    @Override
    public MeshComponentBlueprint makeObject() {
        return new MeshComponentBlueprint(getMesh(), compileRenderType(), localTransform.makeObject(), transformInheritPolicy, isEnabled());
    }

    /// The canvas lowered to its headless tree, or null while nothing is wired into the result. The
    /// owner type fills unwired owner slots; the canvas' own owner pin is a context read.
    private @Nullable PureNode compileRenderType() {
        return ConditionGraphCompiler.compileOptional(renderTypeGetter, EntityPatch.class, MeshComponent.OWNER_PARAM);
    }

    @Override
    public Class<? extends PatchComponent> generateEventually() {
        return MeshComponent.class;
    }

    /// The mesh when it is a project asset, plus the assets the canvas' nodes bind.
    @Override
    public Stream<EditorReference> referencedReferences() {
        return Stream.concat(
            mesh instanceof EditorReference ref ? Stream.of(ref) : Stream.empty(),
            renderTypeGetter.referencedReferences());
    }

    @Override
    public Stream<ScriptGraphData> canvases() {
        return Stream.of(renderTypeGetter);
    }

    @Override
    public void mapCanvases(final UnaryOperator<ScriptGraphData> mapper) {
        renderTypeGetter = mapper.apply(renderTypeGetter);
    }

    /// The first canvas node the decode guard could not resolve, if any.
    @Override
    public @Nullable Exception getNestedFailureException() {
        return renderTypeGetter.firstFailure();
    }

    // ***************************************************************
    // Source export (File > Export > As Source Code)
    // ***************************************************************

    @Override
    public String sourceTemplate() {
        return "components/mesh.ftl";
    }

    /// Tokens for `components/mesh.ftl`: the mesh behind a `has_mesh` flag the fragment turns into a
    /// null argument, and the compiled render type behind a flag of its own, both as a node tree from
    /// [NodeTreeSourceRenderer#pure] and as a lambda from [ScriptNodeSourceCompiler#compilePureLambda].
    @Override
    public Map<String, Object> getDTO() {
        AssetAccessor<? extends Mesh<?>> picked = getMesh();
        PureNode renderType = compileRenderType();

        Map<String, Object> dto = new HashMap<>();
        dto.put("has_mesh", picked != null);
        dto.put("mesh", picked == null ? "null" : NodeTreeSourceRenderer.meshAccessor(picked));
        dto.put("has_render_type_getter", renderType != null);
        dto.put("render_type_getter", renderType == null ? "null" : NodeTreeSourceRenderer.pure(renderType, 5));
        dto.put("render_type_lambda", renderType == null ? "null"
            : ScriptNodeSourceCompiler.compilePureLambda(renderType, MeshComponent.OWNER_PARAM, EntityPatch.class));
        dto.put("transform", localTransform.sourceTokens());
        dto.put("transform_inherit_policy", transformInheritPolicy.name());

        return dto;
    }
}
