
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;

public class EtudiantRootHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String html = """
            <!DOCTYPE html>
            <html>
            <head><meta charset="UTF-8"><title>Portail Étudiant</title>
            <style>
                body{font-family:sans-serif;background:#f0f4f8;display:flex;justify-content:center;align-items:center;height:100vh;margin:0;}
                .card{background:white;padding:40px;border-radius:20px;box-shadow:0 8px 30px rgba(0,0,0,0.08);text-align:center;max-width:500px;}
                h1{color:#1e40af;font-size:32px;}
                .btn{display:inline-block;padding:12px 30px;background:#1e40af;color:white;text-decoration:none;border-radius:8px;margin:10px;}
                .btn:hover{background:#1e3a8a;}
            </style>
            </head>
            <body>
                <div class="card">
                    <h1>🎓 Portail Étudiant</h1>
                    <p>Espace dédié aux étudiants</p>
                    <a href="/dashboard" class="btn">📊 Tableau de bord</a>
                    <a href="/inscription" class="btn">📝 Inscription</a>
                </div>
            </body>
            </html>
        """;
        ResponseUtil.sendHtml(exchange, 200, html);
    }
}