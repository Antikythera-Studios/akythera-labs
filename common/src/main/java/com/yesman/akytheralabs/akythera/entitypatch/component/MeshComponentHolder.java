package com.yesman.akytheralabs.akythera.entitypatch.component;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.client.api.component.impl.MeshComponent;
import com.yesman.akythera.client.api.component.impl.MeshComponentBlueprint;
import com.yesman.akythera.client.api.component.impl.MeshGetter;
import com.yesman.akytheralabs.NodeTreeSourceRenderer;
import com.yesman.akytheralabs.akythera.animation.AnimatorScriptHolder;
import com.yesman.akytheralabs.akythera.animation.ConditionGraphCompiler;
import com.yesman.akytheralabs.dataholder.EditorReference;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.editor.EditorNodeGraph;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.script.ScriptGraphData;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/// Editor holder for an `akythera:mesh` ([MeshComponent]) — a mesh drawn wherever its host places
/// it, chosen by a script.
///
/// Not a [RootAttachableComponent] — the component carries no placement of its own, so it is only
/// reachable through the attach menu of a [BoneSocketHolder].
///
/// [#meshGetter] is a raw [ScriptGraphData] canvas edited in a center tab, stored the way
/// [AnimatorScriptHolder] stores its canvases. Its [MeshGetter#OWNER_PARAM] entry pin carries the owning
/// patch, typed by the patch's parent, so a player-parented patch can read the skin off it; its result root
/// takes an [AssetAccessor].
public final class MeshComponentHolder extends ComponentDataHolder<MeshComponentBlueprint> {
    public static final MapCodec<MeshComponentHolder> EDITOR_CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
            ScriptGraphData.CODEC.optionalFieldOf("mesh_getter", ScriptGraphData.EMPTY)
                .forGetter(h -> h.meshGetter)
        ).apply(instance, MeshComponentHolder::new)
    );

    @EditorEditable(label = LangKeys.GUI_ENTITY_PATCH_SCREEN_ROW_MESH_GETTER, order = 0)
    @EditorNodeGraph(
        contextParams = MeshGetter.OWNER_PARAM,
        contextParamTypes = EntityPatch.class,
        resultType = AssetAccessor.class
    )
    private ScriptGraphData meshGetter;

    public MeshComponentHolder(ScriptGraphData meshGetter) {
        this.meshGetter = meshGetter;
    }

    /// A freshly attached mesh component: an empty canvas, which draws nothing until authored.
    public static MeshComponentHolder createDefault() {
        return new MeshComponentHolder(ScriptGraphData.EMPTY);
    }

    public ScriptGraphData getMeshGetter() {
        return meshGetter;
    }

    public void setMeshGetter(ScriptGraphData meshGetter) {
        this.meshGetter = meshGetter;
    }

    @Override
    public void readFrom(MeshComponentBlueprint object) {
        // Runtime PureNode trees cannot be decompiled back into editor canvases, so an imported
        // component opens with an empty canvas.
        meshGetter = ScriptGraphData.EMPTY;
    }

    @Override
    public MeshComponentBlueprint makeObject() {
        return new MeshComponentBlueprint(compileGetter());
    }

    /// The canvas lowered to its headless tree, or null while nothing is wired into the result. The
    /// owner type fills unwired owner slots; the canvas' own owner pin is a context read.
    private @Nullable PureNode compileGetter() {
        return ConditionGraphCompiler.compileOptional(meshGetter, EntityPatch.class, MeshGetter.OWNER_PARAM);
    }

    @Override
    public Class<? extends PatchComponent> generateEventually() {
        return MeshComponent.class;
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

    // ***************************************************************
    // Source export (File > Export > As Source Code)
    // ***************************************************************

    @Override
    public String sourceTemplate() {
        return "components/mesh.ftl";
    }

    /// Tokens for `components/mesh.ftl`: the compiled getter tree from [NodeTreeSourceRenderer#pure]
    /// behind a `has_mesh_getter` flag the fragment turns into a null argument.
    @Override
    public Map<String, Object> getDTO() {
        PureNode getter = compileGetter();

        Map<String, Object> dto = new HashMap<>();
        dto.put("has_mesh_getter", getter != null);
        dto.put("mesh_getter", getter == null ? "null" : NodeTreeSourceRenderer.pure(getter, 5));

        return dto;
    }
}
