import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;

public class StatistiquesUtils {

    public static String getInstitutionId(HttpExchange exchange) {
        try {
            String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
            System.out.println("🔍 [STATS] Cookie reçu: " + cookieHeader);
            
            if (cookieHeader == null) {
                System.out.println("❌ [STATS] Aucun cookie trouvé");
                return null;
            }

            String sessionId = null;
            for (String cookie : cookieHeader.split(";")) {
                String[] parts = cookie.trim().split("=", 2);
                if (parts.length == 2) {
                    String name = parts[0].trim();
                    if ("SESSION_ID".equalsIgnoreCase(name) || "JSESSIONID".equalsIgnoreCase(name)) {
                        sessionId = parts[1].trim();
                        System.out.println("✅ [STATS] Session ID trouvé: " + sessionId);
                        break;
                    }
                }
            }

            if (sessionId == null) {
                System.out.println("❌ [STATS] Aucun SESSION_ID trouvé");
                return null;
            }

            HttpSession session = SessionManager.getSession(sessionId);
            if (session == null) {
                System.out.println("❌ [STATS] Session introuvable: " + sessionId);
                return null;
            }

            System.out.println("📦 [STATS] Attributs de la session: " + session.getAttributes());

            String instId = (String) session.getAttribute("institutionId");
            if (instId == null || instId.isBlank()) {
                instId = (String) session.getAttribute("institution_id");
                System.out.println("📌 [STATS] institution_id trouvé: " + instId);
            } else {
                System.out.println("📌 [STATS] institutionId trouvé: " + instId);
            }
            
            if (instId == null) {
                System.out.println("❌ [STATS] Aucun attribut institutionId ou institution_id trouvé");
            }
            
            return instId;
        } catch (Exception e) {
            System.err.println("❌ [STATS] Erreur: " + e.getMessage());
            return null;
        }
    }

    public static Map<String, String> parseQuery(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isBlank()) return params;
        for (String pair : query.split("&")) {
            int idx = pair.indexOf("=");
            if (idx > 0) {
                String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                String value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                params.put(key, value);
            }
        }
        return params;
    }

    public static void sendJson(HttpExchange exchange, Map<String, Object> data) throws IOException {
        sendJson(exchange, data, 200);
    }

    public static void sendJson(HttpExchange exchange, Map<String, Object> data, int statusCode) throws IOException {
        String json = mapToJson(data);
        byte[] response = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(statusCode, response.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }

    public static String mapToJson(Map<String, Object> map) {
        if (map == null) return "null";
        StringBuilder sb = new StringBuilder("{");
        int count = 0;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (count > 0) sb.append(",");
            sb.append("\"").append(escapeJson(e.getKey())).append("\":");
            Object v = e.getValue();
            sb.append(valueToJson(v));
            count++;
        }
        sb.append("}");
        return sb.toString();
    }

    public static String listToJson(List<?> list) {
        if (list == null) return "null";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(valueToJson(list.get(i)));
        }
        sb.append("]");
        return sb.toString();
    }

    public static String arrayToJson(Object[] arr) {
        if (arr == null) return "null";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(valueToJson(arr[i]));
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Convertit une valeur en JSON de manière sécurisée
     * Évite les casts non vérifiés en utilisant une approche par type
     */
    private static String valueToJson(Object v) {
        if (v == null) {
            return "null";
        }
        
        // Gestion des types primitifs et courants
        if (v instanceof String string) {
            return "\"" + escapeJson(string) + "\"";
        }
        if (v instanceof Number || v instanceof Boolean) {
            return v.toString();
        }
        
        // Gestion des collections et tableaux avec vérification de type
        if (v instanceof Map) {
            // ✅ Cast sécurisé : on vérifie d'abord le type
            Map<?, ?> map = (Map<?, ?>) v;
            // On construit une nouvelle Map avec des clés String
            Map<String, Object> safeMap = new HashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = entry.getKey() != null ? entry.getKey().toString() : "null";
                safeMap.put(key, entry.getValue());
            }
            return mapToJson(safeMap);
        }
        
        if (v instanceof List) {
            return listToJson((List<?>) v);
        }
        
        if (v instanceof Object[] objects) {
            return arrayToJson(objects);
        }
        
        // Fallback pour les types non reconnus
        return "null";
    }

    public static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append("\\u").append(String.format("%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}