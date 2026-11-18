package com.yesman.akytheralabs.akythera.animation.notify.impl;

import com.yesman.akythera.api.animation.notify.impl.BlockSlamNotify;
import com.yesman.akythera.api.world.BlockSlam;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewer;
import com.yesman.akythera.core.util.LogicalSide;
import com.yesman.akytheralabs.akythera.animation.notify.AnimationNotifyHolder;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

/// Editor holder for a [BlockSlamNotify].
public final class BlockSlamNotifyHolder extends AnimationNotifyHolder<BlockSlamNotify> {
    @EditorEditable(label = LangKeys.GUI_NOTIFY_TRACKS_FIELD_RADIUS, order = 0, min = (float)BlockSlam.MIN_RADIUS)
    private double radius = 3.0D;

    /// Offset from the owner's feet: +x left, +z forward.
    @EditorEditable(label = LangKeys.GUI_NOTIFY_TRACKS_FIELD_OFFSET, order = 1)
    private Vec3 offset = Vec3.ZERO;

    @Override
    public void readFrom(final BlockSlamNotify object) {
        radius = object.radius();
        offset = object.offset();
    }

    @Override
    public BlockSlamNotify makeObject() {
        return new BlockSlamNotify(radius, offset);
    }

    @Override
    public LogicalSide logicalSide() {
        return LogicalSide.CLIENT; // BlockSlamNotify hard-codes it
    }

    @Override
    public String notifyName() {
        return BlockSlamNotify.class.getSimpleName();
    }

    @Override
    public void simulate(final Model3DViewer previewer) {
    }

    @Override
    public String sourceTemplate() {
        return "notify/block_slam.ftl";
    }

    @Override
    public Map<String, Object> getDTO() {
        return Map.of("radius", radius, "offsetX", offset.x, "offsetY", offset.y, "offsetZ", offset.z);
    }
}
