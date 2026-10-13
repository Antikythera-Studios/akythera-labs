package com.yesman.akytheralabs.gui.widget.script.node;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.yesman.akytheralabs.AkytheraLabs;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;

import java.util.function.Function;

/// Registry of every node type a graph canvas can hold, plus the dispatch codec built over it.
///
/// A node type becomes available to the editor by being registered in [#bootstrap] — no enum, no
/// switch. A type is identified by its payload [MapCodec]; [NodeData#mapCodec] is what the dispatch
/// reads, so each type's `CODEC` constant must be the one shared instance (the encode-side lookup is
/// by identity through the mapper's inverse view).
public final class ScriptNodeTypes {
    private static final ExtraCodecs.LateBoundIdMapper<Identifier, MapCodec<? extends NodeData>> ID_MAPPER =
        new ExtraCodecs.LateBoundIdMapper<>();

    private ScriptNodeTypes() {}

    /// `kind` &harr; id. An `akytheralabs`-owned type writes its bare path (`pure_function`), any
    /// other its full id — so built-in rows keep the spelling every existing project file uses.
    private static final Codec<Identifier> KIND_CODEC = Codec.STRING.comapFlatMap(
        ScriptNodeTypes::parseKind,
        id -> id.getNamespace().equals(AkytheraLabs.MODID) ? id.getPath() : id.toString()
    );

    /// The payload dispatch: `kind` picks the type's codec, which contributes its fields **at the
    /// same level** as `kind` — a flat row, matching the on-disk shape.
    public static final MapCodec<NodeData> MAP_CODEC =
        ID_MAPPER.codec(KIND_CODEC).dispatchMap("kind", NodeData::mapCodec, Function.identity());

    /// Registers a node type under `akytheralabs:<path>`. Addons register their own through this.
    public static void register(String path, MapCodec<? extends NodeData> codec) {
        ID_MAPPER.put(AkytheraLabs.identifier(path), codec);
    }

    private static DataResult<Identifier> parseKind(String kind) {
        return DataResult.success(
            kind.indexOf(':') < 0 ? AkytheraLabs.identifier(kind) : Identifier.parse(kind));
    }

    /// Every built-in node type. Called once from each platform's client init.
    public static void bootstrap() {
        register("entry", EntryPointNodeData.CODEC);
        register("entity_patch_entry", MethodEntryNodeData.ENTITY_PATCH_ENTRY_CODEC);
        register("method_entry", MethodEntryNodeData.CODEC);
        register("return", ReturnNodeData.CODEC);
        register("parent_call", ParentCallNodeData.CODEC);
        register("function_call", FunctionCallNodeData.CODEC);
        register("blueprint_field_getter", BlueprintFieldGetterNodeData.CODEC);
        register("blueprint_field_setter", BlueprintFieldSetterNodeData.CODEC);
        register("sequence", SequenceNodeData.CODEC);
        register("branch", BranchNodeData.CODEC);
        register("cast", CastNodeData.CODEC);
        register("flow_function", FlowFunctionNodeData.CODEC);
        register("pure_function", PureFunctionNodeData.CODEC);
        register("literal", LiteralNodeData.CODEC);
        register("self", SelfNodeData.CODEC);
        register("component_getter", ComponentGetterNodeData.CODEC);
        register("variable_getter", VariableGetterNodeData.CODEC);
        register("variable_setter", VariableSetterNodeData.CODEC);
        register("particle_parameter_getter", ParticleParameterGetterNodeData.CODEC);
        register("comparator", ComparatorNodeData.CODEC);
        register("operator", OperatorNodeData.CODEC);
        register("reroute", RerouteNodeData.CODEC);
        register("not", NotNodeData.CODEC);
        register("logical_operation", LogicalOperationNodeData.CODEC);
        register("type_convert", TypeConvertNodeData.CODEC);
        register("mesh_asset", MeshAssetNodeData.CODEC);
        register("output_pose", OutputPoseNodeData.CODEC);
        register("transition_result", TransitionResultNodeData.CODEC);
        register("source_driver", SourceDriverNodeData.CODEC);
        register("anim_sequence_player", AnimSequencePlayerNodeData.CODEC);
        register("blend_space_player", BlendSpacePlayerNodeData.CODEC);
        register("anim_sequence_interface", AnimSequenceInterfaceNodeData.CODEC);
        register("blend_space_interface", BlendSpaceInterfaceNodeData.CODEC);
        register("state_machine", StateMachineNodeData.CODEC);
        register("anim_layer", AnimLayerNodeData.CODEC);
        register("cached_pose", CachedPoseNodeData.CODEC);
        register("cached_pose_getter", CachedPoseGetterNodeData.CODEC);
        register("two_pose_blend", TwoPoseBlendNodeData.CODEC);
        register("additive_blend", AdditiveBlendNodeData.CODEC);
        register("layered_blend_per_bone", LayeredBlendPerBoneNodeData.CODEC);
        register("play_ability_cue", PlayAbilityCueNodeData.CODEC);
        register("play_ability_cue_script", PlayAbilityCueScriptNodeData.CODEC);
    }
}
