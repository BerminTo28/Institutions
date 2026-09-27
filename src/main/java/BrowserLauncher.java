import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.logging.Logger;

public final class BrowserLauncher {

    private static final Logger LOG = Logger.getLogger(BrowserLauncher.class.getName());

    private BrowserLauncher() { }

    public static void open(String url) {
        try {
            if (Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
                return;
            }
        } catch (IOException | URISyntaxException e) {
            LOG.fine(() -> "Desktop.browse indisponible : " + e.getMessage());
        }
        System.out.println("🔗 Ouvrez votre navigateur sur : " + url);
    }
}