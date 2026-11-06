package com.yesman.akytheralabs.gui.widget.main;

import com.yesman.akythera.api.data.serializable.LocalizedMessage;
import com.yesman.akythera.client.api.gui.AkytheraButton;
import com.yesman.akythera.client.api.gui.CheckBox;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akytheralabs.config.AkytheraLabsConfig;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.AkytheraLabsButtonTheme;
import com.yesman.akytheralabs.gui.widget.EditorEditBox;
import com.yesman.akytheralabs.gui.widget.WidgetContainerModal;
import com.yesman.akytheralabs.mcp.AkytheraMcpServer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.URI;

/// `Tools > Open MCP`: starts and stops [AkytheraMcpServer] on a URL the user types.
public final class McpServerModalGenerator {
    private static final int POPUP_W = 340;
    private static final int CHECK_ROW_H = 16;
    private static final int CHECK_H = 10;

    private McpServerModalGenerator() {}

    public static void open(final Font font) {
        UrlRow urlRow = new UrlRow(font);
        StatusRow statusRow = new StatusRow(font);
        int btnW = WidgetContainerModal.evenButtonWidth(POPUP_W, 3);

        CheckBox autoStartBox = new CheckBox(
            font,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(POPUP_W),
            AnchoredWidget.pixel((CHECK_ROW_H - CHECK_H) / 2), AnchoredWidget.pixel(CHECK_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            AkytheraLabsConfig::isMcpAutoStart,
            checked -> {
                AkytheraLabsConfig.setMcpAutoStart(checked);
                AkytheraLabsConfig.saveConfigValues();
            },
            Component.translatable(LangKeys.GUI_MCP_MODAL_AUTO_START),
            true
        );
        autoStartBox.setWidgetValue(AkytheraLabsConfig.isMcpAutoStart());

        AkytheraButton closeBtn = AkytheraButton.buttonBuilder(font, LocalizedMessage.translatable(LangKeys.GUI_CLOSE), () -> ModalManager.getInstance().closeModal())
            .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
            .build();

        AkytheraButton stopBtn = AkytheraButton.buttonBuilder(font, LocalizedMessage.translatable(LangKeys.GUI_MCP_MODAL_STOP), () -> {
                AkytheraMcpServer.getInstance().stop();
                statusRow.failure = null;
            })
            .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
            .build();

        AkytheraButton startBtn = AkytheraButton.buttonBuilder(font, LocalizedMessage.translatable(LangKeys.GUI_MCP_MODAL_START), () -> {
                String url = urlRow.urlBox.getText().trim();

                try {
                    AkytheraMcpServer.getInstance().start(url);
                    AkytheraLabsConfig.setMcpServerUrl(url);
                    AkytheraLabsConfig.saveConfigValues();
                    statusRow.failure = null;
                    urlRow.urlBox.unmark();
                } catch (IOException | IllegalArgumentException e) {
                    statusRow.failure = e.getMessage();
                    urlRow.urlBox.mark();
                }
            })
            .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
            .build();

        ModalManager.getInstance().openModalWidgetAtCenter(
            WidgetContainerModal.builder(font, Component.translatable(LangKeys.GUI_MCP_MODAL_TITLE), POPUP_W)
                .textLines(Component.translatable(LangKeys.GUI_MCP_MODAL_WARNING).withStyle(ChatFormatting.GOLD))
                .widgetContainerRow(UrlRow.ROW_H, urlRow)
                .singleWidgetRow(CHECK_ROW_H, autoStartBox)
                .widgetContainerRow(StatusRow.ROW_H, statusRow)
                .space(10) // Bottom space
                .footerButton(closeBtn, btnW)
                .footerButton(stopBtn, btnW)
                .footerButton(startBtn, btnW)
                .build()
        );
    }

    private static final class UrlRow extends WidgetContainerModal.WidgetsContainerRow {
        private static final int ROW_H = 26;
        private static final int BOX_W = 220;
        private static final int BOX_H = 18;

        private final Font font;
        private final EditorEditBox<String> urlBox;

        UrlRow(final Font font) {
            this.font = font;
            this.urlBox = EditorEditBox.filteredStringBox(
                font,
                _ -> true,
                Component.literal(AkytheraMcpServer.DEFAULT_URL),
                HorizontalAnchorType.RIGHT_WIDTH,
                VerticalAnchorType.CENTER_HEIGHT
            );

            urlBox.setX1(AnchoredWidget.pixel(0));
            urlBox.setX2(AnchoredWidget.pixel(BOX_W));
            urlBox.setY1(AnchoredWidget.pixel(0));
            urlBox.setY2(AnchoredWidget.pixel(BOX_H));

            URI running = AkytheraMcpServer.getInstance().getUri();
            urlBox.setValue(running == null ? AkytheraLabsConfig.getMcpServerUrl() : running.toString());
            addWidget(urlBox);
        }

        @Override
        protected void extractRowContent(WidgetContainerModal popup, GuiGraphicsExtractor g, ScreenRectangle rowRectangle, int mouseX, int mouseY, float a) {
            int textY = rowRectangle.top() + (rowRectangle.height() - font.lineHeight) / 2 + 1;

            g.text(font, Component.translatable(LangKeys.GUI_MCP_MODAL_URL), rowRectangle.left(), textY, 0xFFAAAAAA, false);
        }
    }

    private static final class StatusRow extends WidgetContainerModal.WidgetsContainerRow {
        private static final int ROW_H = 18;

        private final Font font;

        /// Why the last start failed, cleared by the next start or stop.
        private @Nullable String failure;

        StatusRow(final Font font) {
            this.font = font;
        }

        @Override
        protected void extractRowContent(WidgetContainerModal popup, GuiGraphicsExtractor g, ScreenRectangle rowRectangle, int mouseX, int mouseY, float a) {
            URI running = AkytheraMcpServer.getInstance().getUri();
            int textY = rowRectangle.top() + (rowRectangle.height() - font.lineHeight) / 2 + 1;

            if (failure != null) {
                g.text(font, Component.translatable(LangKeys.GUI_MCP_MODAL_STATUS_FAILED, failure), rowRectangle.left(), textY, 0xFFFF6060, false);
            } else if (running != null) {
                g.text(font, Component.translatable(LangKeys.GUI_MCP_MODAL_STATUS_RUNNING, running.toString()), rowRectangle.left(), textY, 0xFF60FF60, false);
            } else {
                g.text(font, Component.translatable(LangKeys.GUI_MCP_MODAL_STATUS_STOPPED), rowRectangle.left(), textY, 0xFFAAAAAA, false);
            }
        }
    }
}
