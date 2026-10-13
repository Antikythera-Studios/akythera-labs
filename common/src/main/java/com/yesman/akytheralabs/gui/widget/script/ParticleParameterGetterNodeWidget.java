package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.client.api.particle.module.ParticleSystemParameterNode;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.script.node.NodeData;
import com.yesman.akytheralabs.gui.widget.script.node.ParticleParameterGetterNodeData;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/// Widget for a [ParticleSystemParameterNode]: a pure node that reads a parameter of the particle system. One
/// output, colored and typed by the parameter and labeled with its name.
public final class ParticleParameterGetterNodeWidget extends AbstractNodeWidget {
    private final String parameter;
    private final @Nullable ScriptType parameterType;

    public ParticleParameterGetterNodeWidget(Font font, String parameter, @Nullable ScriptType parameterType, int graphX, int graphY) {
        super(font, Component.translatable(LangKeys.GUI_PARTICLE_SYSTEM_SCREEN_NODE_GET_PARAMETER, parameter), graphX, graphY);

        this.parameter = parameter;
        this.parameterType = parameterType;

        setTitleColor(NodeColors.titleTint(VariableNodeWidget.colorType(parameterType)));
        addOutput(new Pin(
            parameter, NodeColors.forType(VariableNodeWidget.colorType(parameterType)), false,
            VariableNodeWidget.pinType(parameterType), VariableNodeWidget.pinGenericType(parameterType)
        ));
        relayout();
    }

    @Override
    public NodeData toData() {
        return new ParticleParameterGetterNodeData(parameter, Optional.ofNullable(parameterType));
    }
}
