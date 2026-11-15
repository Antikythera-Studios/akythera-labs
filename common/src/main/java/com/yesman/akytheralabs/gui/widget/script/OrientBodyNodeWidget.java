package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akythera.core.animation.driver.OrientBodyDriver;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akytheralabs.gui.widget.script.node.NodeData;
import com.yesman.akytheralabs.gui.widget.script.node.OrientBodyNodeData;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

public final class OrientBodyNodeWidget extends AbstractPoseControlNodeWidget {
    private final OrientBodyDriver.PitchSource pitchSource;

    public OrientBodyNodeWidget(Font font, OrientBodyDriver.PitchSource pitchSource, int graphX, int graphY) {
        super(font, title(pitchSource), graphX, graphY);

        this.pitchSource = pitchSource;

        addInput(new Pin("Pivot", NodeColors.forType(String.class), false, String.class));
        addInput(new Pin("Limits", NodeColors.forType(Vec3.class), false, Vec3.class));
        addInput(new Pin("Weight", NodeColors.forType(float.class), false, float.class));

        relayout();
    }

    public static Component title(OrientBodyDriver.PitchSource pitchSource) {
        return Component.literal("Orient Body (" + ParseUtils.camelToSpacedPascal(pitchSource.getSerializedName()) + ")");
    }

    @Override
    public NodeData toData() {
        return new OrientBodyNodeData(pitchSource);
    }
}
