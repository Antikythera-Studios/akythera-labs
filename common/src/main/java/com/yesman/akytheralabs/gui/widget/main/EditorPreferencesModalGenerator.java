package com.yesman.akytheralabs.gui.widget.main;

import com.yesman.akythera.client.gui.widget.AnchoredButton;
import com.yesman.akythera.client.gui.widget.ClampedNumberBox;
import com.yesman.akythera.client.gui.widget.common.AnchoredWidget;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.config.AkytheraLabsConfig;
import com.yesman.akytheralabs.editor.keybind.EditorKeyMapping;
import com.yesman.akytheralabs.editor.keybind.EditorKeyMappings;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.WidgetContainerModal;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;

public final class EditorPreferencesModalGenerator {
    private static final int POPUP_W = 320;
    private static final int MIN_SCALE = 1;
    private static final int MAX_SCALE = 6;

    private EditorPreferencesModalGenerator() {
    }

    public static void open(Font font) {
        AnchoredButton saveBtn = AnchoredButton.buttonBuilder(font, Component.translatable(LangKeys.GUI_SAVE), _ -> {
            AkytheraLabsConfig.saveConfigValues();
            AkytheraEditorLayoutManager.getInstance().closeModal();
        }).build();

        AnchoredButton cancelBtn = AnchoredButton.buttonBuilder(font, Component.translatable(LangKeys.GUI_CANCEL), _ -> {
            AkytheraLabsConfig.revertToSpecValues();
            AkytheraEditorLayoutManager.getInstance().rescaleScreens(AkytheraLabsConfig.getEditorGuiScale());
            AkytheraEditorLayoutManager.getInstance().closeModal();
        }).build();

        int btnW = WidgetContainerModal.evenButtonWidth(POPUP_W, 2);

        WidgetContainerModal.Builder builder = WidgetContainerModal.builder(font, Component.translatable(LangKeys.GUI_MAIN_SCREEN_WIDGET_EDITOR_PREFERENCES), POPUP_W)
            .saveHistory()
            .space(10) // Top space
            .widgetContainerRow(SectionHeaderRow.ROW_H, new SectionHeaderRow(font, Component.translatable(LangKeys.GUI_EDITOR_PREFERENCES_SECTION_GRAPHICS)))
            .widgetContainerRow(SectionDividerRow.ROW_H, new SectionDividerRow())
            .widgetContainerRow(GuiScaleRow.ROW_H, new GuiScaleRow(font))
            .widgetContainerRow(SectionHeaderRow.ROW_H, new SectionHeaderRow(font, Component.translatable(LangKeys.GUI_EDITOR_PREFERENCES_SECTION_KEY_MAPPINGS)))
            .widgetContainerRow(SectionDividerRow.ROW_H, new SectionDividerRow());

        for (EditorKeyMapping mapping : EditorKeyMappings.all()) {
            builder.widgetContainerRow(KeyMappingRow.ROW_H, new KeyMappingRow(font, mapping));
        }

        builder
            .widgetContainerRow(ResetAllRow.ROW_H, new ResetAllRow(font))
            .space(10) // Bottom space
            .footerButton(cancelBtn, btnW)
            .footerButton(saveBtn, btnW);

        AkytheraEditorLayoutManager.getInstance().openModalWidgetAtCenter(builder.build());
    }

    private static final class GuiScaleRow extends WidgetContainerModal.WidgetsContainerRow {
        private static final int ROW_H = 34;
        private static final int BOX_W = 80;
        private static final int BOX_H = 20;

        private final Font font;

        GuiScaleRow(Font font) {
            this.font = font;

            ClampedNumberBox<Integer> scaleBox = new ClampedNumberBox<>(
                font,
                AnchoredWidget.pixel(0), AnchoredWidget.pixel(BOX_W),
                AnchoredWidget.pixel(0), AnchoredWidget.pixel(BOX_H),
                HorizontalAnchorType.RIGHT_WIDTH,
                VerticalAnchorType.CENTER_HEIGHT,
                AkytheraLabsConfig::getEditorGuiScale,
                v -> {
                    AkytheraLabsConfig.setEditorGuiScale(v);
                    AkytheraEditorLayoutManager.getInstance().rescaleScreens(v);
                },
                Component.empty(),
                true,
                MIN_SCALE, MAX_SCALE, 1, -1,
                Integer::parseInt, String::valueOf,
                Integer::compareTo, Integer::sum
            );

            scaleBox.setValue(AkytheraLabsConfig.getEditorGuiScale());
            addWidget(scaleBox);
        }

        @Override
        protected void extractRowContent(WidgetContainerModal popup, GuiGraphicsExtractor g, ScreenRectangle rowRectangle, int mouseX, int mouseY, float a) {
            int textY = rowRectangle.top() + font.lineHeight / 2;

            g.text(font, Component.translatable(LangKeys.GUI_MAIN_SCREEN_WIDGET_EDITOR_PREFERENCES_GUI_SCALE),
                rowRectangle.left(), textY, 0xFFAAAAAA, false);
        }
    }

    private static final class SectionHeaderRow extends WidgetContainerModal.WidgetsContainerRow {
        private static final int ROW_H = 16;

        private final Font font;
        private final Component title;

        SectionHeaderRow(Font font, Component title) {
            this.font = font;
            this.title = title;
        }

        @Override
        protected void extractRowContent(WidgetContainerModal popup, GuiGraphicsExtractor g, ScreenRectangle rowRectangle, int mouseX, int mouseY, float a) {
            int textY = rowRectangle.top() + (rowRectangle.height() - font.lineHeight) / 2 + 1;

            g.text(font, title, rowRectangle.left(), textY, 0xFFEEEEEE, false);
        }
    }

    private static final class SectionDividerRow extends WidgetContainerModal.WidgetsContainerRow {
        private static final int ROW_H = 4;

        @Override
        protected void extractRowContent(WidgetContainerModal popup, GuiGraphicsExtractor g, ScreenRectangle rowRectangle, int mouseX, int mouseY, float a) {
            int sepY = rowRectangle.top() + rowRectangle.height() / 2;

            g.fill(rowRectangle.left(), sepY, rowRectangle.right(), sepY + 1, 0xFF3A3A3A);
        }
    }

    private static final class KeyMappingRow extends WidgetContainerModal.WidgetsContainerRow {
        private static final int ROW_H = 22;
        private static final int HOLDER_W = 110;
        private static final int HOLDER_H = 16;

        private final Font font;
        private final EditorKeyMapping mapping;

        KeyMappingRow(Font font, EditorKeyMapping mapping) {
            this.font = font;
            this.mapping = mapping;

            KeyMappingHolder holder = new KeyMappingHolder(
                font,
                mapping,
                AnchoredWidget.pixel(0), AnchoredWidget.pixel(HOLDER_W),
                AnchoredWidget.pixel(0), AnchoredWidget.pixel(HOLDER_H),
                HorizontalAnchorType.RIGHT_WIDTH,
                VerticalAnchorType.CENTER_HEIGHT
            );

            addWidget(holder);
        }

        @Override
        protected void extractRowContent(WidgetContainerModal popup, GuiGraphicsExtractor g, ScreenRectangle rowRectangle, int mouseX, int mouseY, float a) {
            int textY = rowRectangle.top() + (rowRectangle.height() - font.lineHeight) / 2 + 1;

            g.text(font, mapping.getName(), rowRectangle.left(), textY, 0xFFAAAAAA, false);
        }
    }

    private static final class ResetAllRow extends WidgetContainerModal.WidgetsContainerRow {
        private static final int ROW_H = 22;
        private static final int BTN_W = 60;
        private static final int BTN_H = 16;

        private final Font font;

        ResetAllRow(Font font) {
            this.font = font;

            AnchoredButton resetBtn = AnchoredButton.buttonBuilder(font,
                Component.translatable(LangKeys.GUI_EDITOR_PREFERENCES_RESET),
                _ -> EditorKeyMappings.resetAll()
            )
                .xUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(BTN_W))
                .yUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(BTN_H))
                .horizontalAnchorType(AnchoredWidget.HorizontalAnchorType.RIGHT_WIDTH)
                .verticalAnchorType(AnchoredWidget.VerticalAnchorType.CENTER_HEIGHT)
                .build();

            addWidget(resetBtn);
        }

        @Override
        protected void extractRowContent(WidgetContainerModal popup, GuiGraphicsExtractor g, ScreenRectangle rowRectangle, int mouseX, int mouseY, float a) {
            Component label = Component.translatable(LangKeys.GUI_EDITOR_PREFERENCES_RESET_ALL_KEYS);
            int textY = rowRectangle.top() + (rowRectangle.height() - font.lineHeight) / 2 + 1;

            g.text(font, label, rowRectangle.left(), textY, 0xFFAAAAAA, false);
        }
    }
}