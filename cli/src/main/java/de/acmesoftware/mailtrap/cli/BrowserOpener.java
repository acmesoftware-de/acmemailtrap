package de.acmesoftware.mailtrap.cli;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/** Opens a URL or file in the OS default browser by shelling out — no AWT, so it survives a native image. */
public final class BrowserOpener {

    private BrowserOpener() {
    }

    public static void open(String target) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        List<String> cmd;
        if (os.contains("mac")) {
            cmd = List.of("open", target);
        } else if (os.contains("win")) {
            cmd = List.of("cmd", "/c", "start", "", target);
        } else {
            cmd = List.of("xdg-open", target);
        }
        try {
            new ProcessBuilder(cmd).inheritIO().start();
        } catch (IOException e) {
            throw new CliError("Cannot open the browser (" + cmd.get(0) + "): " + e.getMessage()
                    + " — the target is: " + target);
        }
    }
}
