import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class UploadHandler implements HttpHandler {
    private final Path basePath;

    public UploadHandler(String basePath) {
        this.basePath = Paths.get(basePath);
        System.out.println("📁 UploadHandler basePath: " + this.basePath.toAbsolutePath());
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        System.out.println("📂 Requête upload : " + path);

        // Retirer le préfixe "/uploads" du chemin
        String relative = path;
        if (relative.startsWith("/uploads/")) {
            relative = relative.substring(9); // "/uploads/".length() = 9
        } else if (relative.startsWith("/uploads")) {
            relative = relative.substring(8); // "/uploads".length() = 8
        } else {
            // Si le chemin ne commence pas par /uploads, on ne sert pas
            send404(exchange);
            return;
        }

        // Nettoyer le chemin (sécurité)
        relative = relative.replaceAll("^/|/$", "");
        relative = relative.replace("..", "").replace("//", "/");
        if (relative.isEmpty()) {
            send404(exchange);
            return;
        }

        Path filePath = basePath.resolve(relative);
        System.out.println("📂 Fichier cherché : " + filePath.toAbsolutePath());

        if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
            System.out.println("❌ Fichier non trouvé");
            send404(exchange);
            return;
        }

        String mimeType = getMimeType(filePath.toString());
        exchange.getResponseHeaders().set("Content-Type", mimeType);
        exchange.sendResponseHeaders(200, Files.size(filePath));

        try (OutputStream os = exchange.getResponseBody()) {
            Files.copy(filePath, os);
        }
    }

    private String getMimeType(String path) {
        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".jpg") || path.endsWith(".jpeg")) return "image/jpeg";
        if (path.endsWith(".gif")) return "image/gif";
        if (path.endsWith(".svg")) return "image/svg+xml";
        if (path.endsWith(".webp")) return "image/webp";
        if (path.endsWith(".ico")) return "image/x-icon";
        if (path.endsWith(".css")) return "text/css";
        if (path.endsWith(".js")) return "application/javascript";
        if (path.endsWith(".html") || path.endsWith(".htm")) return "text/html";
        if (path.endsWith(".pdf")) return "application/pdf";
        return "application/octet-stream";
    }

    private void send404(HttpExchange exchange) throws IOException {
        String msg = "<h1>404 - Fichier non trouvé</h1>";
        byte[] data = msg.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(404, data.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(data);
        }
    }
}