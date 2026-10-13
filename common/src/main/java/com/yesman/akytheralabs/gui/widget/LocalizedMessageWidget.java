package com.yesman.akytheralabs.gui.widget;

import com.yesman.akythera.api.data.serializable.LocalizedMessage;
import com.yesman.akythera.client.api.gui.AkytheraButton;
import com.yesman.akythera.client.api.gui.TextArea;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.common.DataBoundWidget;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.gui.widget.main.LocalizedMessageSelectModal;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/// Edits a [LocalizedMessage] in a [TextArea] filling the widget. The italic `L` button picking a project lang
/// key through [LocalizedMessageSelectModal] is [#getSelectButton], placed by whoever lays the widget out.
///
/// The typed text is translatable while it is a key of the open project's localization, and
/// handwritten otherwise. A translatable message shows in blue, with the `L` button lit, and reads as
/// its project text in the game's selected language until the area is focused, which shows the key.
public final class LocalizedMessageWidget extends AbstractContainerWidget implements AnchoredWidget, DataBoundWidget<LocalizedMessage> {
    private static final int TRANSLATABLE_COLOR = 0xFF8FC7FF;

    private final TextArea textArea;
    private final AkytheraButton selectButton;

    private LocalizedMessage value = LocalizedMessage.EMPTY;

    /// Called when an edit ends: the text area losing focus, or a key picked.
    private @Nullable BiConsumer<LocalizedMessage, LocalizedMessage> onCommitted;

    public LocalizedMessageWidget(
        Font font,
        Unit x1, Unit x2, Unit y1, Unit y2,
        HorizontalAnchorType horizontalAnchor, VerticalAnchorType verticalAnchor,
        Callable<LocalizedMessage> dataProvider,
        Consumer<LocalizedMessage> onValueChanged
    ) {
        super(0, 0, 0, 0, Component.empty(), AbstractScrollArea.defaultSettings(0));

        this.x1 = x1;
        this.x2 = x2;
        this.y1 = y1;
        this.y2 = y2;
        this.horizontalAnchorType = horizontalAnchor;
        this.verticalAnchorType = verticalAnchor;
        this.dataProvider = dataProvider;
        this.onValueChanged = onValueChanged;

        this.textArea = new EditorTextArea(
            font,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            HorizontalAnchorType.LEFT_WIDTH,
            VerticalAnchorType.TOP_HEIGHT,
            () -> "",
            text -> {
                showValue(messageOf(text));
                onValueChanged.accept(value);
            },
            Component.empty()
        );
        this.textArea.setOnCommitted((oldText, newText) -> {
            if (onCommitted != null) onCommitted.accept(messageOf(oldText), messageOf(newText));
        });
        this.textArea.setFormatter(text -> {
            AkytheraProject project = AkytheraProject.getCurrentProjectOrNull();
            if (!value.isTranslatable() || project == null) return text;

            String translated = project.getLocalization().resolve(text, Minecraft.getInstance().getLanguageManager().getSelected());

            return translated.isEmpty() ? text : translated;
        });

        this.selectButton = AkytheraButton.buttonBuilder(font, LocalizedMessage.handwritten("L"), () -> openSelect(font))
            .xUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(0))
            .yUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(0))
            .horizontalAnchorType(HorizontalAnchorType.LEFT_WIDTH)
            .verticalAnchorType(VerticalAnchorType.TOP_HEIGHT)
            .theme(AkytheraLabsButtonTheme.AKYTHERA_EXPLORER_BUTTON)
            .isHighlighted(widget -> value.isTranslatable() || widget.isHoveredOrFocused())
            .build();
        this.selectButton.setMessage(Component.literal("L").withStyle(ChatFormatting.ITALIC));
    }

    /// `text` as a translatable message while the open project has it as a key, else as a handwritten one.
    private static LocalizedMessage messageOf(String text) {
        AkytheraProject project = AkytheraProject.getCurrentProjectOrNull();

        return project != null && project.getLocalization().contains(text)
            ? LocalizedMessage.translatable(text)
            : LocalizedMessage.handwritten(text);
    }

    /// The italic `L` button picking a project lang key, lit while the message is translatable. Laid out
    /// by the caller, not by this widget.
    public AkytheraButton getSelectButton() {
        return selectButton;
    }

    /// Opens the key picker. Without an open project there are no keys to pick, and nothing opens.
    private void openSelect(Font font) {
        if (AkytheraProject.getCurrentProjectOrNull() == null) return;

        ModalManager modals = ModalManager.getInstance();

        modals.openModalWidgetAtCenter(new LocalizedMessageSelectModal(font, key -> {
            modals.closeModal();

            LocalizedMessage old = value;

            setValue(LocalizedMessage.translatable(key));
            onValueChanged.accept(value);

            if (onCommitted != null) onCommitted.accept(old, value);
        }));
    }

    public void setOnCommitted(@Nullable BiConsumer<LocalizedMessage, LocalizedMessage> onCommitted) {
        this.onCommitted = onCommitted;
    }

    /// Shows `value` without calling the change callback. Null reads as [LocalizedMessage#EMPTY].
    public void setValue(@Nullable LocalizedMessage value) {
        showValue(Objects.requireNonNullElse(value, LocalizedMessage.EMPTY));
        textArea.setValue(this.value.text());
    }

    private void showValue(LocalizedMessage value) {
        this.value = value;
        textArea.setTextColor(value.isTranslatable() ? TRANSLATABLE_COLOR : null);
    }

    public LocalizedMessage getValue() {
        return value;
    }

    /// Lays the text area over this widget's current bounds.
    private void layoutChildren() {
        textArea.setX(getX());
        textArea.setY(getY());
        textArea.setWidth(getWidth());
        textArea.setHeight(getHeight());
    }

    @Override
    public void setX(int x) {
        super.setX(x);
        layoutChildren();
    }

    @Override
    public void setY(int y) {
        super.setY(y);
        layoutChildren();
    }

    @Override
    public void relocate(ScreenRectangle screenRectangle) {
        AnchoredWidget.super.relocate(screenRectangle);
        layoutChildren();
    }

    @Override
    protected int contentHeight() {
        return getHeight();
    }

    @Override
    public @NonNull List<? extends GuiEventListener> children() {
        return List.of(textArea);
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (!active || !visible) return false;

        for (GuiEventListener child : children()) {
            if (child.mouseClicked(event, doubleClick)) {
                setFocused(child);

                if (event.button() == 0) setDragging(true);

                return true;
            }
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return textArea.isMouseOver(mouseX, mouseY) && textArea.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void setFocused(@Nullable GuiEventListener focused) {
        // Re-focusing the already-focused child clears the focus in vanilla's implementation.
        if (getFocused() == focused) return;

        super.setFocused(focused);
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);

        if (getFocused() != null && !focused) {
            getFocused().setFocused(false);
            setFocused(null);
        }
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        textArea.extractRenderState(graphics, mouseX, mouseY, a);
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput narrationElementOutput) {
    }

    // ***************************************************************
    // [AnchoredWidget] implementations
    // ***************************************************************
    private Unit x1;
    private Unit x2;
    private Unit y1;
    private Unit y2;
    private HorizontalAnchorType horizontalAnchorType;
    private VerticalAnchorType verticalAnchorType;

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

    // *****************************************************************
    // [DataBoundWidget] implementations
    // *****************************************************************
    private final Callable<LocalizedMessage> dataProvider;
    private final Consumer<LocalizedMessage> onValueChanged;

    @Override
    public Callable<LocalizedMessage> getDataProvider() {
        return dataProvider;
    }

    @Override
    public Consumer<LocalizedMessage> valueChangeCallback() {
        return onValueChanged;
    }

    @Override
    public Consumer<LocalizedMessage> valueSetter() {
        return this::setValue;
    }

    @Override
    public void reset() {
        setValue(LocalizedMessage.EMPTY);
    }
}
