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
/// Its [#localTransform] location places the camera in the patch's model space, the space the preview shows it in.
public final class CameraComponentHolder extends ComponentDataHolder<CameraComponentBlueprint>
    implements RootAttachableComponent, TransformComponentHolder
{
    public static final MapCodec<CameraComponentHolder> EDITOR_CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
            TransformHolder.CODEC.optionalFieldOf("transform").forGetter(h -> Optional.of(h.localTransform)),
            Codec.BOOL.optionalFieldOf("match_player_yaw_to_camera", true).forGetter(h -> h.matchPlayerYawToCamera)
        ).apply(instance, (transform, matchPlayerYawToCamera) -> {
            CameraComponentHolder h = new CameraComponentHolder();
            h.localTransform = transform.orElseGet(TransformHolder::identity);
            h.matchPlayerYawToCamera = matchPlayerYawToCamera;
            return h;
        })
    );

    /// Whether the player turns with the camera, or only while moving.
    @EditorEditable(label = LangKeys.GUI_ENTITY_PATCH_SCREEN_ROW_MATCH_PLAYER_YAW_TO_CAMERA, order = 0)
    public boolean matchPlayerYawToCamera = true;

    /// Where the camera sits in third person. Left at the origin, vanilla places it.
    @EditorEditable(label = LangKeys.GUI_ENTITY_PATCH_SCREEN_SECTION_LOCAL_TRANSFORM, order = 1)
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
    }

    @Override
    public CameraComponentBlueprint makeObject() {
        return new CameraComponentBlueprint(localTransform.makeObject(), matchPlayerYawToCamera, isEnabled());
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
        return Map.of("transform", localTransform.sourceTokens(), "match_player_yaw_to_camera", matchPlayerYawToCamera);
    }
}
