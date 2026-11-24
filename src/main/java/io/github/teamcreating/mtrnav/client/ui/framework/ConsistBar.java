package io.github.teamcreating.mtrnav.client.ui.framework;

import io.github.teamcreating.mtrnav.client.ui.theme.UiDraw;
import io.github.teamcreating.mtrnav.client.ui.theme.UiTokens;
import io.github.teamcreating.mtrnav.core.Model;
import net.minecraft.client.gui.GuiGraphics;

import static io.github.teamcreating.mtrnav.client.NavigationClient.tr;

/**
 * Visual train consist strip rendering miniature car blocks + car count + live/planned source pill.
 */
public final class ConsistBar {
    private ConsistBar() {}

    public static void render(GuiGraphics g, Model.Consist consist, int routeColor, int x, int y, int maxWidth) {
        if (consist == null || maxWidth < 24) return;
        int count = consist.count();
        int cursor = x;
        int maxCarsDrawn = Math.min(count, 8);
        if (count > 0) {
            for (int i = 0; i < maxCarsDrawn; i++) {
                boolean cab = i == 0 || i == maxCarsDrawn - 1;
                int cw = cab ? 10 : 8;
                if (cursor + cw > x + maxWidth - 36) break;
                int fill = UiTokens.withAlpha(routeColor, cab ? 220 : 165);
                UiDraw.roundedRect(g, cursor, y + 1, cw, 6, cab ? 2f : 1f, fill);
                cursor += cw + 2;
            }
            if (count > maxCarsDrawn && cursor + 18 < x + maxWidth) {
                UiDraw.text(g, "+" + (count - maxCarsDrawn), cursor, y, 18, UiTokens.TEXT_MUTED);
                cursor += 16;
            }
            String countLabel = tr("mtrnav.vehicle_cars", count);
            int lw = UiDraw.font().width(countLabel);
            if (cursor + lw + 6 <= x + maxWidth) {
                UiDraw.text(g, countLabel, cursor + 2, y, lw + 2, UiTokens.TEXT_SECONDARY);
                cursor += lw + 8;
            }
        } else {
            String unknown = tr("mtrnav.vehicle_unknown_short");
            UiDraw.text(g, unknown, cursor, y, maxWidth, UiTokens.TEXT_MUTED);
            return;
        }
        String src = switch (consist.source()) {
            case ACTUAL -> tr("mtrnav.stock_source.actual");
            case PLANNED -> tr("mtrnav.stock_source.planned");
            default -> "";
        };
        if (!src.isEmpty() && cursor + 24 < x + maxWidth) {
            int color = consist.source() == Model.StockSource.ACTUAL ? UiTokens.STATUS_LIVE : UiTokens.TEXT_MUTED;
            UiDraw.text(g, "· " + src, cursor, y, x + maxWidth - cursor, color);
        }
    }
}
