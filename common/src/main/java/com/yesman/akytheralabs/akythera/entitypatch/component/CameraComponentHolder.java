package com.yesman.akytheralabs.akythera.entitypatch.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.component.TransformInheritPolicy;
import com.yesman.akythera.client.api.camera.CameraComponent;
import com.yesman.akythera.client.api.camera.CameraComponentBlueprint;
import com.yesman.akytheralabs.dataholder.TransformHolder;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;

import java.util.Map;
import java.util.Optional;

/// Editor holder for an `akythera:camera` ([CameraComponentBlueprint]) — the local player's third-person view.
///
/// Its [#localTransform] location places the camera in its parent's space: the arm from a [SpringArmComponentHolder]'s
/// pivot, or the patch's model space at the root.
public final class CameraComponentHolder extends ComponentDataHolder<CameraComponentBlueprint>
    implements RootAttachableComponent, TransformComponentHolder
{
    public static final MapCodec<CameraComponentHolder> EDITOR_CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
            TransformHolder.CODEC.optionalFieldOf("transform").forGetter(h -> Optional.of(h.localTransform)),
            Codec.BOOL.optionalFieldOf("match_player_yaw_to_camera", true).forGetter(h -> h.matchPlayerYawToCamera),
            Codec.BOOL.optionalFieldOf("collide_with_terrain", true).forGetter(h -> h.collideWithTerrain),
            Codec.DOUBLE.optionalFieldOf("probe_radius", 0.1D).forGetter(h -> h.probeRadius)
        ).apply(instance, (transform, matchPlayerYawToCamera, collideWithTerrain, probeRadius) -> {
            CameraComponentHolder h = new CameraComponentHolder();
            h.localTransform = transform.orElseGet(TransformHolder::identity);
            h.matchPlayerYawToCamera = matchPlayerYawToCamera;
            h.collideWithTerrain = collideWithTerrain;
            h.probeRadius = probeRadius;
            return h;
        })
    );

    /// Whether the player turns with the camera, or only while moving.
    @EditorEditable(label = LangKeys.GUI_ENTITY_PATCH_SCREEN_ROW_MATCH_PLAYER_YAW_TO_CAMERA, order = 0)
    public boolean matchPlayerYawToCamera = true;

    /// Whether the camera's arm shrinks where blocks are in the way.
    @EditorEditable(label = LangKeys.GUI_ENTITY_PATCH_SCREEN_ROW_COLLIDE_WITH_TERRAIN, order = 1)
    private boolean collideWithTerrain = true;

    /// How far the camera keeps off blocks, in blocks.
    @EditorEditable(label = LangKeys.GUI_ENTITY_PATCH_SCREEN_ROW_PROBE_RADIUS, order = 2, min = 0.0F)
    private double probeRadius = 0.1D;

    /// Where the camera sits in third person. Left at the origin, vanilla places it.
    @EditorEditable(label = LangKeys.GUI_ENTITY_PATCH_SCREEN_SECTION_LOCAL_TRANSFORM, order = 3)
    private TransformHolder localTransform = TransformHolder.identity();

    public static CameraComponentHolder createDefault() {
        return new CameraComponentHolder();
    }

    @Override
    public TransformHolder getLocalTransform() {
        return localTransform;
    }

    @Override
    public TransformInheritPolicy getTransformInheritPolicy() {
        return TransformInheritPolicy.LOC_ROT_SCA;
    }

    @Override
    public void readFrom(CameraComponentBlueprint object) {
        localTransform.readFrom(object.transform());
        matchPlayerYawToCamera = object.matchPlayerYawToCamera();
        collideWithTerrain = object.collideWithTerrain();
        probeRadius = object.probeRadius();
    }

    @Override
    public CameraComponentBlueprint makeObject() {
        return new CameraComponentBlueprint(localTransform.makeObject(), matchPlayerYawToCamera, collideWithTerrain, probeRadius, isEnabled());
    }

    @Override
    public Class<? extends PatchComponent> generateEventually() {
        return CameraComponent.class;
    }

    // ***************************************************************
    // Source export (File > Export > As Source Code)
    // ***************************************************************

    @Override
    public String sourceTemplate() {
        return "components/camera.ftl";
    }

    @Override
    public Map<String, Object> getDTO() {
        return Map.of(
            "transform", localTransform.sourceTokens(),
            "match_player_yaw_to_camera", matchPlayerYawToCamera,
            "collide_with_terrain", collideWithTerrain,
            "probe_radius", probeRadius
        );
    }
}
