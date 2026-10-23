package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akythera.core.animation.driver.ModifyBoneDriver;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akytheralabs.gui.widget.script.node.ModifyBoneNodeData;
import com.yesman.akytheralabs.gui.widget.script.node.NodeData;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

public final class ModifyBoneNodeWidget extends AbstractPoseControlNodeWidget {
    private final ModifyBoneDriver.Space space;

    public ModifyBoneNodeWidget(Font font, ModifyBoneDriver.Space space, int graphX, int graphY) {
        super(font, title(space), graphX, graphY);

        this.space = space;

        addInput(new Pin("Bone", NodeColors.forType(String.class), false, String.class));
        addInput(new Pin("Rotation", NodeColors.forType(Vec3.class), false, Vec3.class));
        addInput(new Pin("Replace", NodeColors.forType(float.class), false, float.class));

        relayout();
    }

    public static Component title(ModifyBoneDriver.Space space) {
        return Component.literal("Modify Bone (" + ParseUtils.camelToSpacedPascal(space.getSerializedName()) + ")");
    }

    @Override
    public NodeData toData() {
        return new ModifyBoneNodeData(space);
    }
}
