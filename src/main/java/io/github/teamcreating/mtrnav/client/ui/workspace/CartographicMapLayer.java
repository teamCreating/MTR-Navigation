package io.github.teamcreating.mtrnav.client.ui.workspace;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.teamcreating.mtrnav.compat.MinecraftGui;
import io.github.teamcreating.mtrnav.core.Model;
import io.github.teamcreating.mtrnav.core.ui.TopologyProjection;
import io.github.teamcreating.mtrnav.core.ui.UiRect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import java.util.List;

/**
 * Generates and caches a dark-mode smartphone-navigation cartographic base map (Apple Maps / Amap style).
 * Samples real loaded Minecraft chunk terrain + hillshading in live worlds, and blends seamlessly
 * with topographic contours and station urban district shading for unloaded areas or test fixtures.
 */
public final class CartographicMapLayer {
    private static final int TEX_W = 256;
    private static final int TEX_H = 192;
    private static final ResourceLocation TEX_ID = ResourceLocation.fromNamespaceAndPath("mtrnav", "dynamic/nav_map_backdrop");

    private DynamicTexture texture;
    private boolean registered;
    private String lastKey = "";
    private long lastRefreshNanos;
    private boolean hasLiveTerrain;
    private boolean terrainEnabled = true;

    public boolean hasLiveTerrain() { return hasLiveTerrain; }
    public boolean terrainEnabled() { return terrainEnabled; }
    public void toggleTerrain() {
        terrainEnabled = !terrainEnabled;
        lastKey = "";
    }

    public void render(GuiGraphics g, UiRect rect, TopologyProjection projection, Model.Catalog catalog) {
        if (rect.width() <= 4 || rect.height() <= 4) return;
        ensureTexture();

        long now = System.nanoTime();
        String dim = catalog == null ? "" : catalog.dimension();
        boolean fixture = NewUiFixtures.enabled();
        String key = rect.width() + "x" + rect.height()
                + "|" + Math.round(projection.panX() * 2.0)
                + "|" + Math.round(projection.panY() * 2.0)
                + "|" + Math.round(projection.zoom() * 100.0)
                + "|" + dim + "|" + fixture + "|" + terrainEnabled;

        boolean periodicLiveRefresh = !fixture && terrainEnabled && (now - lastRefreshNanos) > 1_500_000_000L;
        if (!key.equals(lastKey) || periodicLiveRefresh) {
            rebuild(rect, projection, catalog, fixture);
            lastKey = key;
            lastRefreshNanos = now;
        }

        MinecraftGui.blitFull(g, TEX_ID, rect.x() + 1, rect.y() + 1, rect.width() - 2, rect.height() - 2);
    }

    private void ensureTexture() {
        if (texture == null) {
            texture = new DynamicTexture(TEX_W, TEX_H, false);
        }
        if (!registered) {
            Minecraft.getInstance().getTextureManager().register(TEX_ID, texture);
            registered = true;
        }
    }

    private void rebuild(UiRect rect, TopologyProjection proj, Model.Catalog catalog, boolean fixture) {
        NativeImage img = texture.getPixels();
        if (img == null) return;

        ClientLevel level = (!fixture && terrainEnabled) ? Minecraft.getInstance().level : null;
        List<Model.Station> stations = catalog == null ? List.of() : catalog.stations();
        double[] stX = new double[stations.size()];
        double[] stZ = new double[stations.size()];
        for (int i = 0; i < stations.size(); i++) {
            stX[i] = stations.get(i).x();
            stZ[i] = stations.get(i).z();
        }

        double stepBlocks = Math.max(1.0, 1.0 / Math.max(1e-4, proj.pixelsPerBlock()) * (rect.width() / (double) TEX_W));
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int liveCount = 0;
        boolean[] waterMask = new boolean[TEX_W * TEX_H];
        int[] argbBuf = new int[TEX_W * TEX_H];

        for (int py = 0; py < TEX_H; py++) {
            double sy = rect.y() + 1.0 + (py + 0.5) * (rect.height() - 2.0) / TEX_H;
            double wz = proj.screenToWorldZ(sy);
            int bz = (int) Math.floor(wz);

            for (int px = 0; px < TEX_W; px++) {
                double sx = rect.x() + 1.0 + (px + 0.5) * (rect.width() - 2.0) / TEX_W;
                double wx = proj.screenToWorldX(sx);
                int bx = (int) Math.floor(wx);
                int idx = py * TEX_W + px;

                int pixel = 0;
                if (level != null) {
                    LevelChunk chunk = level.getChunkSource().getChunkNow(bx >> 4, bz >> 4);
                    if (chunk != null) {
                        pixel = sampleLiveBlock(level, chunk, bx, bz, (int) Math.max(1, Math.round(stepBlocks)), pos, waterMask, idx);
                        liveCount++;
                    }
                }
                if (pixel == 0) {
                    pixel = sampleCartographicBase(wx, wz, stX, stZ, fixture, stepBlocks, waterMask, idx);
                }
                argbBuf[idx] = pixel;
            }
        }

        // Crisp dark-mode coastline edge accent wherever water meets land
        for (int py = 1; py < TEX_H - 1; py++) {
            int row = py * TEX_W;
            for (int px = 1; px < TEX_W - 1; px++) {
                int idx = row + px;
                if (waterMask[idx]) {
                    boolean shore = !waterMask[idx - 1] || !waterMask[idx + 1]
                            || !waterMask[idx - TEX_W] || !waterMask[idx + TEX_W];
                    if (shore) argbBuf[idx] = 0xFF1F4772;
                }
            }
        }

        for (int py = 0; py < TEX_H; py++) {
            int row = py * TEX_W;
            for (int px = 0; px < TEX_W; px++) {
                MinecraftGui.setPixel(img, px, py, argbBuf[row + px]);
            }
        }
        hasLiveTerrain = liveCount > 64;
        texture.upload();
    }

    private int sampleLiveBlock(ClientLevel level, LevelChunk chunk, int bx, int bz, int delta,
                                BlockPos.MutableBlockPos pos, boolean[] waterMask, int idx) {
        int topY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, bx & 15, bz & 15);
        int minY = level.dimensionType().minY();
        int y = Math.max(minY, topY);
        pos.set(bx, y, bz);
        BlockState state = chunk.getBlockState(pos);
        if (state.isAir() && y > minY) {
            pos.set(bx, y - 1, bz);
            state = chunk.getBlockState(pos);
        }
        MapColor mc = state.getMapColor(level, pos);
        if (mc == MapColor.WATER) {
            waterMask[idx] = true;
            // Subtle shallow/deep ocean variation
            int ocean = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR, bx & 15, bz & 15);
            int depth = Math.clamp(topY - ocean, 0, 18);
            int r = 13 - depth / 4;
            int g = 37 - depth / 2;
            int b = 64 - depth / 2;
            return rgb(r, g, b);
        }

        // Hillshade from NW neighbor if chunk is also loaded
        int shade = 0;
        LevelChunk nwChunk = level.getChunkSource().getChunkNow((bx - delta) >> 4, (bz - delta) >> 4);
        if (nwChunk != null) {
            int nwY = nwChunk.getHeight(Heightmap.Types.WORLD_SURFACE, (bx - delta) & 15, (bz - delta) & 15);
            shade = Math.clamp((topY - nwY) * 2, -12, 12);
        }

        // Grade vanilla/modded map colors into cohesive Apple Maps Dark Mode palette
        int base;
        if (mc == MapColor.GRASS || mc == MapColor.PLANT || mc == MapColor.EMERALD || mc == MapColor.COLOR_GREEN) {
            base = 0xFF172B24; // Dark urban park sage
        } else if (mc == MapColor.SAND || mc == MapColor.TERRACOTTA_YELLOW) {
            base = 0xFF25241E; // Muted coastal sand
        } else if (mc == MapColor.SNOW || mc == MapColor.ICE) {
            base = 0xFF283648; // Cool frosted slate
        } else if (mc == MapColor.NONE) {
            base = 0xFF141C27;
        } else {
            // Desaturate and night-grade architectural blocks (stone, concrete, wood, asphalt, bricks)
            int cr = (mc.col >>> 16) & 0xFF;
            int cg = (mc.col >>> 8) & 0xFF;
            int cb = mc.col & 0xFF;
            int lum = (cr * 30 + cg * 59 + cb * 11) / 100;
            int nr = 20 + ((cr + lum * 2) / 3) * 22 / 255;
            int ng = 27 + ((cg + lum * 2) / 3) * 26 / 255;
            int nb = 38 + ((cb + lum * 2) / 3) * 32 / 255;
            base = rgb(nr, ng, nb);
        }
        return adjustBrightness(base, shade);
    }

    private int sampleCartographicBase(double wx, double wz, double[] stX, double[] stZ,
                                       boolean fixture, double stepBlocks, boolean[] waterMask, int idx) {
        // Compute proximity to nearest station (defines urban neighborhood catchment zone)
        double minDistSq = Double.POSITIVE_INFINITY;
        for (int i = 0; i < stX.length; i++) {
            double dx = wx - stX[i];
            double dz = wz - stZ[i];
            double d2 = dx * dx + dz * dz;
            if (d2 < minDistSq) minDistSq = d2;
        }
        double dist = Math.sqrt(minDistSq);

        // Smooth organic multi-octave elevation field
        double n1 = smoothNoise(wx * 0.0042, wz * 0.0042);
        double n2 = smoothNoise(wx * 0.011 + 17.3, wz * 0.011 - 9.1);
        double elev = n1 * 0.68 + n2 * 0.32;

        if (fixture) {
            // Mosslight Harbour Archipelago: realistic bay/waterways around the 6 coastal stations
            // Keep stations and key corridors on land while surrounding outer harbour & eastern bay are deep water
            double landBias = Math.exp(-(dist * dist) / (155.0 * 155.0)) * 0.72;
            // Harbour channel cutting gently from NE and SW
            double bayCut = Math.exp(-(((wx - 210) * (wx - 210)) / (140.0 * 140.0) + ((wz + 120) * (wz + 120)) / (110.0 * 110.0))) * 0.38
                    + Math.exp(-(((wx - 360) * (wx - 360)) / (160.0 * 160.0) + ((wz - 275) * (wz - 275)) / (115.0 * 115.0))) * 0.42;
            double height = elev * 0.62 + landBias - bayCut;
            if (height < 0.39) {
                waterMask[idx] = true;
                double depth = Math.clamp((0.39 - height) * 3.8, 0.0, 1.0);
                int r = (int) Math.round(15 - depth * 5);
                int g = (int) Math.round(40 - depth * 12);
                int b = (int) Math.round(69 - depth * 15);
                // Subtle marine bathymetry ring
                if (Math.abs((0.39 - height) - 0.11) < 0.008) {
                    r += 3; g += 6; b += 9;
                }
                return rgb(r, g, b);
            }

            // Land: subtle park hills vs urban harbor districts
            boolean parkZone = elev > 0.62 && dist > 38.0;
            int r = parkZone ? 21 : 21;
            int g = parkZone ? 40 : 29;
            int b = parkZone ? 34 : 41;

            // Topographic elevation contour steps
            double band = (height - 0.39) * 9.0;
            int step = (int) Math.floor(band);
            double frac = band - step;
            r += Math.min(8, step * 2);
            g += Math.min(10, step * 2);
            b += Math.min(12, step * 2);
            if (frac < 0.075) {
                r += 4; g += 6; b += 8;
            }

            // Subtle urban district avenue & city-block road grid near stations
            if (dist < 135.0) {
                double urban = 1.0 - dist / 135.0;
                r += (int) (urban * 6);
                g += (int) (urban * 7);
                b += (int) (urban * 10);
                double roadCheck = roadHighlight(wx, wz, stepBlocks);
                if (roadCheck > 0.0) {
                    double k = roadCheck * urban;
                    r += (int) (k * 16);
                    g += (int) (k * 20);
                    b += (int) (k * 26);
                }
            }
            return rgb(r, g, b);
        }

        // Live world unloaded area: honest dark topographic map (never invents water) + urban catchment hints
        int r = 18;
        int g = 25;
        int b = 36;
        double band = elev * 7.0;
        int step = (int) Math.floor(band);
        double frac = band - step;
        r += step;
        g += step + 1;
        b += step * 2;
        if (frac < 0.065) {
            r += 4; g += 6; b += 9;
        }
        if (dist < 130.0) {
            double urban = 1.0 - dist / 130.0;
            r += (int) (urban * 6);
            g += (int) (urban * 7);
            b += (int) (urban * 10);
            double roadCheck = roadHighlight(wx, wz, stepBlocks);
            if (roadCheck > 0.0) {
                double k = roadCheck * urban;
                r += (int) (k * 12);
                g += (int) (k * 15);
                b += (int) (k * 20);
            }
        }
        return rgb(r, g, b);
    }

    private static double roadHighlight(double wx, double wz, double stepBlocks) {
        double majorPeriod = 64.0;
        double minorPeriod = 24.0;
        double majorDist = Math.min(distToGrid(wx, majorPeriod), distToGrid(wz, majorPeriod));
        double minorDist = Math.min(distToGrid(wx, minorPeriod), distToGrid(wz, minorPeriod));
        double majorTol = Math.max(1.8, stepBlocks * 0.75);
        double minorTol = Math.max(1.0, stepBlocks * 0.45);
        if (majorDist < majorTol) return 1.0;
        if (minorDist < minorTol) return 0.42;
        return 0.0;
    }

    private static double distToGrid(double v, double period) {
        double m = ((v % period) + period) % period;
        return Math.min(m, period - m);
    }

    private static double smoothNoise(double x, double y) {
        int xi = (int) Math.floor(x);
        int yi = (int) Math.floor(y);
        double xf = x - xi;
        double yf = y - yi;
        double u = xf * xf * (3.0 - 2.0 * xf);
        double v = yf * yf * (3.0 - 2.0 * yf);
        double a = hash01(xi, yi);
        double b = hash01(xi + 1, yi);
        double c = hash01(xi, yi + 1);
        double d = hash01(xi + 1, yi + 1);
        return (a + (b - a) * u) + ((c + (d - c) * u) - (a + (b - a) * u)) * v;
    }

    private static double hash01(int x, int y) {
        int h = x * 374761393 + y * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0x7FFFFFFF) / (double) 0x7FFFFFFF;
    }

    private static int adjustBrightness(int argb, int delta) {
        int r = Math.clamp(((argb >>> 16) & 0xFF) + delta, 0, 255);
        int g = Math.clamp(((argb >>> 8) & 0xFF) + delta, 0, 255);
        int b = Math.clamp((argb & 0xFF) + delta, 0, 255);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int rgb(int r, int g, int b) {
        return 0xFF000000
                | (Math.clamp(r, 0, 255) << 16)
                | (Math.clamp(g, 0, 255) << 8)
                | Math.clamp(b, 0, 255);
    }
}
