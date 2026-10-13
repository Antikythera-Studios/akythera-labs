package com.yesman.akytheralabs.gui.widget.viewer;

import com.yesman.akythera.api.data.serializable.LocalizedMessage;
import com.yesman.akythera.client.api.gui.AkytheraButton;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewer;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.AkytheraLabsButtonTheme;
import com.yesman.akytheralabs.gui.widget.main.EntityPreviewModelMappingModal;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.NonNull;

import java.util.List;

/// The card an editor previewer shows in place of a model when the entity type it was asked for has
/// no mesh bound: the reason, over a row reading `Open` with a button opening
/// [EntityPreviewModelMappingModal] beside it.
public final class PreviewMappingWarningCard extends AbstractWidget implements AnchoredWidget {
    private static final int CARD_W = 230;
    private static final int PADDING = 10;
    private static final int LINE_H = 12;
    private static final int BUTTON_H = 16;
    /// Space each side of the button label, and between `Open` and the button.
    private static final int BUTTON_INSET = 6;

    private static final int BACKGROUND_COLOR = 0xFF2A2A2A;
    private static final int BORDER_COLOR = 0xFF3A3A3A;
    private static final int TEXT_COLOR = 0xFFCCCCCC;

    /// Replaces whatever card `previewer` holds with one naming `entityTypeId`.
    public static void raise(Model3DViewer previewer, Font font, Identifier entityTypeId) {
        clear(previewer);
        previewer.addOverlayWidget(new PreviewMappingWarningCard(font, entityTypeId));
    }

    /// Takes down `previewer`'s card, if it has one.
    public static void clear(Model3DViewer previewer) {
        previewer.removeOverlayWidgets(PreviewMappingWarningCard.class);
    }

    private final Font font;
    private final List<FormattedCharSequence> lines;

    /// The plain `Open` ahead of the button, drawn as text.
    private final Component openPrefix;
    private final int openPrefixWidth;

    private final AkytheraButton openButton;
    private final int buttonWidth;

    private PreviewMappingWarningCard(Font font, Identifier entityTypeId) {
        super(0, 0, CARD_W, 0, Component.empty());

        this.font = font;
        this.lines = font.split(
            Component.translatable(LangKeys.GUI_PREVIEW_MAPPING_UNDEFINED, entityTypeId.toString()),
            CARD_W - PADDING * 2
        );

        this.openPrefix = Component.translatable(LangKeys.GUI_PREVIEW_MAPPING_OPEN_PREFIX);
        this.openPrefixWidth = font.width(openPrefix);

        LocalizedMessage buttonLabel = LocalizedMessage.translatable(LangKeys.GUI_PREVIEW_MAPPING_OPEN_BUTTON);
        this.buttonWidth = font.width(buttonLabel.toComponent()) + BUTTON_INSET * 2;

        this.openButton = AkytheraButton
            .buttonBuilder(font, buttonLabel, () -> EntityPreviewModelMappingModal.open(font))
            .xUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(buttonWidth))
            .yUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(BUTTON_H))
            .horizontalAnchorType(HorizontalAnchorType.LEFT_WIDTH)
            .verticalAnchorType(VerticalAnchorType.TOP_HEIGHT)
            .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
            .build();

        setHeight(PADDING * 2 + lines.size() * LINE_H + BUTTON_H + PADDING / 2);
        this.y2 = AnchoredWidget.pixel(getHeight());
    }

    /// Left edge of the `Open` + button group, which is centered on the card as one unit.
    private int actionRowLeft() {
        return getX() + (getWidth() - (openPrefixWidth + BUTTON_INSET + buttonWidth)) / 2;
    }

    /// Seats the button on the card's own rectangle. Runs on every relocate, which the viewer drives.
    private void layoutChildren() {
        openButton.setX(actionRowLeft() + openPrefixWidth + BUTTON_INSET);
        openButton.setY(getBottom() - PADDING - BUTTON_H);
        openButton.setWidth(buttonWidth);
        openButton.setHeight(BUTTON_H);
    }

    @Override
    public void relocate(@NonNull ScreenRectangle screenRectangle) {
        AnchoredWidget.super.relocate(screenRectangle);
        layoutChildren();
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (openButton.isMouseOver(event.x(), event.y())) {
            return openButton.mouseClicked(event, doubleClick);
        }

        // The card is opaque to the camera: a drag started on it would spin the view behind it.
        return isMouseOver(event.x(), event.y());
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        g.fill(getX(), getY(), getRight(), getBottom(), BACKGROUND_COLOR);
        g.outline(getX(), getY(), getWidth(), getHeight(), BORDER_COLOR);

        int textY = getY() + PADDING;

        for (FormattedCharSequence line : lines) {
            g.text(font, line, getX() + PADDING, textY, TEXT_COLOR, false);
            textY += LINE_H;
        }

        g.text(
            font, openPrefix,
            actionRowLeft(), openButton.getY() + (BUTTON_H - font.lineHeight) / 2,
            TEXT_COLOR, false
        );

        openButton.extractRenderState(g, mouseX, mouseY, a);
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput narrationElementOutput) {
    }

    // ***************************************************************
    // [AnchoredWidget] implementations
    // ***************************************************************
    private Unit x1 = AnchoredWidget.pixel(0);
    private Unit x2 = AnchoredWidget.pixel(CARD_W);
    private Unit y1 = AnchoredWidget.pixel(0);
    private Unit y2 = AnchoredWidget.pixel(0);
    private HorizontalAnchorType horizontalAnchorType = HorizontalAnchorType.CENTER_WIDTH;
    private VerticalAnchorType verticalAnchorType = VerticalAnchorType.CENTER_HEIGHT;

    @Override public Unit getX1() { return x1; }
    @Override public Unit getX2() { return x2; }
    @Override public Unit getY1() { return y1; }
    @Override public Unit getY2() { return y2; }
    @Override public void setX1(Unit unit) { x1 = unit; }
    @Override public void setX2(Unit unit) { x2 = unit; }
    @Override public void setY1(Unit unit) { y1 = unit; }
    @Override public void setY2(Unit unit) { y2 = unit; }
    @Override public HorizontalAnchorType getHorizontalAnchorType() { return horizontalAnchorType; }
    @Override public VerticalAnchorType getVerticalAnchorType() { return verticalAnchorType; }
    @Override public void setHorizontalAnchorType(HorizontalAnchorType horizontalAnchorType) { this.horizontalAnchorType = horizontalAnchorType; }
    @Override public void setVerticalAnchorType(VerticalAnchorType verticalAnchorType) { this.verticalAnchorType = verticalAnchorType; }
}
