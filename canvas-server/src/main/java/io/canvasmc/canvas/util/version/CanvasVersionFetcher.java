package io.canvasmc.canvas.util.version;

import com.destroystokyo.paper.util.VersionFetcher;
import io.papermc.paper.ServerBuildInfo;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.jspecify.annotations.NullMarked;

import static net.kyori.adventure.text.Component.newline;
import static net.kyori.adventure.text.Component.text;

/**
 * Filter's version banner, replacing {@link com.destroystokyo.paper.PaperVersionFetcher}: the brand and the Minecraft
 * version framed by two bars a little short of the chat box's default width, with nothing to hover or click.
 */
@NullMarked
public class CanvasVersionFetcher implements VersionFetcher {

    private static final TextColor BLOSSOM = TextColor.color(0xF3C2D8);
    private static final String BAR = "▬".repeat(70);
    private static final long CACHE_TIME = TimeUnit.DAYS.toMillis(365);

    @Override
    public long getCacheTime() {
        return CACHE_TIME;
    }

    @Override
    public Component getVersionMessage() {
        return Component.empty();
    }

    @Override
    public Component getFullOutMessage() {
        final ServerBuildInfo buildInfo = ServerBuildInfo.buildInfo();

        return Component.textOfChildren(
            text(BAR, NamedTextColor.DARK_GRAY),
            newline(),
            text("  ✿ ", BLOSSOM),
            text(buildInfo.brandName(), NamedTextColor.WHITE),
            text("  ·  ", NamedTextColor.DARK_GRAY),
            text("Minecraft ", NamedTextColor.GRAY),
            text(buildInfo.minecraftVersionName(), NamedTextColor.WHITE),
            newline(),
            text("  Magnolia's own server software, tuned region", NamedTextColor.GRAY),
            newline(),
            text("  by region by the team that runs the server.", NamedTextColor.GRAY),
            newline(),
            text(BAR, NamedTextColor.DARK_GRAY)
        );
    }
}
