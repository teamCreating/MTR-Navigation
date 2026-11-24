package io.github.teamcreating.mtrnav.client.ui.theme;

/**
 * Semantic design tokens for the modern Transit Signage + PIDS UI.
 * Color is reserved for MTR lines, live statuses, and primary actions.
 */
public final class UiTokens {
    private UiTokens() {}

    // Surfaces
    public static final int SCRIM             = 0x76080B10;
    public static final int SHELL_BG          = 0xE812161E;
    public static final int RAIL_BG           = 0xED0D1016;
    public static final int SURFACE           = 0xFF181E28;
    public static final int SURFACE_HOVER     = 0xFF202836;
    public static final int SURFACE_ELEVATED  = 0xFF242D3D;
    public static final int SURFACE_SELECTED  = 0xFF1D2D42;
    public static final int INSET             = 0xFF0E1218;
    public static final int DRAWER_BG         = 0xF5141922;
    public static final int HUD_BG            = 0x940B0F16;
    public static final int HUD_HEADER        = 0x36324156;
    public static final int HUD_BORDER        = 0x6E627896;

    // Borders
    public static final int BORDER_SUBTLE     = 0xFF263040;
    public static final int BORDER_DEFAULT    = 0xFF334155;
    public static final int BORDER_FOCUS      = 0xFF38BDF8;
    public static final int BORDER_SELECTED   = 0xFF58B8FF;

    // Typography
    public static final int TEXT_PRIMARY      = 0xFFF3F6FA;
    public static final int TEXT_SECONDARY    = 0xFFA7B4C6;
    public static final int TEXT_MUTED        = 0xFF708096;
    public static final int TEXT_DISABLED     = 0xFF525F72;

    // Accents & Status
    public static final int PRIMARY           = 0xFF10B981;
    public static final int PRIMARY_HOVER     = 0xFF34D399;
    public static final int PRIMARY_PRESSED   = 0xFF059669;
    public static final int PRIMARY_INK       = 0xFF061B14;

    public static final int ACCENT_SKY        = 0xFF38BDF8;
    public static final int STATUS_LIVE       = 0xFF34D399;
    public static final int STATUS_WARN       = 0xFFFBBF24;
    public static final int STATUS_DANGER     = 0xFFF87171;

    // Geometry constants (Minecraft GUI units)
    public static final int RADIUS_SM         = 4;
    public static final int RADIUS_MD         = 6;
    public static final int RADIUS_LG         = 9;
    public static final int GAP_XS            = 4;
    public static final int GAP_SM            = 6;
    public static final int GAP_MD            = 10;
    public static final int CONTROL_H         = 20;
    public static final int CONTROL_SM        = 18;
    public static final int RAIL_WIDE         = 104;
    public static final int RAIL_COMPACT      = 34;

    public static int withAlpha(int argb, int alpha) {
        return ((Math.clamp(alpha, 0, 255)) << 24) | (argb & 0x00FFFFFF);
    }

    public static int mulAlpha(int argb, double factor) {
        int a = (argb >>> 24) & 0xFF;
        return withAlpha(argb, (int) Math.round(a * Math.clamp(factor, 0.0, 1.0)));
    }

    public static int contrastInk(int bgArgb) {
        double r = linear(((bgArgb >>> 16) & 0xFF) / 255.0);
        double g = linear(((bgArgb >>> 8) & 0xFF) / 255.0);
        double b = linear((bgArgb & 0xFF) / 255.0);
        double lum = 0.2126 * r + 0.7152 * g + 0.0722 * b;
        return lum > 0.22 ? 0xFF0E131A : 0xFFF8FAFC;
    }

    private static double linear(double c) {
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }
}
