package com.yesman.akytheralabs.akythera.entitypatch.component;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.client.api.component.impl.EntityModelComponent;
import com.yesman.akythera.client.api.component.impl.MeshGetter;
import com.yesman.akythera.client.core.asset.mesh.SkeletalMesh;
import com.yesman.akythera.compat.CompatibleMod;
import com.yesman.akythera.platform.ModPlatformProvider;
import com.yesman.akytheralabs.NodeTreeSourceRenderer;
import com.yesman.akytheralabs.akythera.animation.ConditionGraphCompiler;
import com.yesman.akytheralabs.dataholder.EditorReference;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.editor.EditorNodeGraph;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.script.ScriptGraphData;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/// Editor holder for an `akythera:entity_model` ([EntityModelComponent]) — the [SkeletalMesh] a patched
/// entity is drawn with, and which renderer draws it.
///
/// A [RootAttachableComponent], attached directly on the patch. It hosts no children.
///
/// [#meshGetter] is a raw [ScriptGraphData] canvas edited in a center tab, stored the way
/// [com.yesman.akytheralabs.akythera.animation.AnimatorScriptHolder] stores its canvases. Its
/// [MeshGetter#OWNER_PARAM] entry pin carries the owning patch, typed by the patch's parent, so a
/// player-parented patch can pick its mesh off the skin's model type; its result root takes an
/// [AssetAccessor].
public final class EntityModelComponentHolder extends ComponentDataHolder<EntityModelComponent>
    implements RootAttachableComponent
{
    public static final MapCodec<EntityModelComponentHolder> EDITOR_CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
            ScriptGraphData.CODEC.optionalFieldOf("mesh_getter", ScriptGraphData.EMPTY)
                .forGetter(h -> h.meshGetter),
            StringRepresentable.fromEnum(EntityModelComponent.Renderer::values).fieldOf("renderer")
                .forGetter(EntityModelComponentHolder::getRenderer)
        ).apply(instance, EntityModelComponentHolder::new)
    );

    /// Yields the mesh to skin. An empty canvas leaves the entity on its vanilla model.
    @EditorEditable(label = LangKeys.GUI_ENTITY_PATCH_SCREEN_ROW_MESH_GETTER, order = 0)
    @EditorNodeGraph(
        contextParams = MeshGetter.OWNER_PARAM,
        contextParamTypes = EntityPatch.class,
        resultType = AssetAccessor.class
    )
    private ScriptGraphData meshGetter;

    /// Which renderer draws the mesh, picked from [#rendererCandidates].
    @EditorEditable(
        label = LangKeys.GUI_ENTITY_PATCH_SCREEN_ROW_RENDERER,
        order = 1,
        candidates = "rendererCandidates"
    )
    private EntityModelComponent.Renderer renderer;

    public EntityModelComponentHolder(ScriptGraphData meshGetter, EntityModelComponent.Renderer renderer) {
        this.meshGetter = meshGetter;
        this.renderer = renderer;
    }

    /// A freshly added entity model component, with an empty canvas that draws nothing.
    public static EntityModelComponentHolder createDefault() {
        return new EntityModelComponentHolder(ScriptGraphData.EMPTY, EntityModelComponent.Renderer.DEFAULT);
    }

    /// [#renderer]'s [EditorEditable#candidates] source: every [EntityModelComponent.Renderer], minus
    /// [EntityModelComponent.Renderer#PLAYER_ANIMATION_LIBRARY] while PAL is not installed. An
    /// already-stored value still displays.
    private List<EntityModelComponent.Renderer> rendererCandidates() {
        boolean palLoaded = ModPlatformProvider.get()
            .isModLoaded(CompatibleMod.PLAYER_ANIMATION_LIBRARY.getModId());

        return Arrays.stream(EntityModelComponent.Renderer.values())
            .filter(candidate -> palLoaded || candidate != EntityModelComponent.Renderer.PLAYER_ANIMATION_LIBRARY)
            .toList();
    }

    public ScriptGraphData getMeshGetter() {
        return meshGetter;
    }

    public EntityModelComponent.Renderer getRenderer() {
        return renderer;
    }

    public void setMeshGetter(ScriptGraphData meshGetter) {
        this.meshGetter = meshGetter;
    }

    public void setRenderer(EntityModelComponent.Renderer renderer) {
        this.renderer = renderer;
    }

    @Override
    public void readFrom(EntityModelComponent object) {
        // Runtime PureNode trees cannot be decompiled back into editor canvases, so an imported
        // component opens with an empty canvas.
        meshGetter = ScriptGraphData.EMPTY;
        renderer = object.renderer();
    }

    @Override
    public EntityModelComponent makeObject() {
        return new EntityModelComponent(new MeshGetter(compileGetter(), null), renderer);
    }

    /// The canvas lowered to its headless tree, or null while nothing is wired into the result. The
    /// owner type fills unwired owner slots; the canvas' own owner pin is a context read.
    private @Nullable PureNode compileGetter() {
        return ConditionGraphCompiler.compileOptional(meshGetter, EntityPatch.class, MeshGetter.OWNER_PARAM);
    }

    /// An [EntityModelComponent] is its own blueprint — it builds itself.
    @Override
    public Class<? extends PatchComponent> generateEventually() {
        return EntityModelComponent.class;
    }

    /// The mesh assets this canvas' nodes bind.
    @Override
    public Stream<EditorReference> referencedReferences() {
        return meshGetter.referencedReferences();
    }

    /// The first canvas node the decode guard could not resolve, if any.
    @Override
    public @Nullable Exception getNestedFailureException() {
        return meshGetter.firstFailure();
    }

    /// ***************************************************************
    /// Source export (File > Export > As Source Code)
    /// ***************************************************************

    @Override
    public String sourceTemplate() {
        return "components/entity_model.ftl";
    }

    /// Tokens for `components/entity_model.ftl`: the compiled getter tree from
    /// [NodeTreeSourceRenderer#pure] behind a `has_mesh_getter` flag the fragment turns into an
    /// unbound getter, plus the renderer's enum constant name.
    @Override
    public Map<String, Object> getDTO() {
        PureNode getter = compileGetter();

        Map<String, Object> dto = new HashMap<>();
        dto.put("has_mesh_getter", getter != null);
        dto.put("mesh_getter", getter == null ? "null" : NodeTreeSourceRenderer.pure(getter, 5));
        dto.put("renderer", renderer.name());

        return dto;
    }
}
