import java.io.IOException;

import com.sun.net.httpserver.HttpExchange;

public final class SessionUtils {
    
    private SessionUtils() {}
    
    public static String getInstitutionId(HttpExchange exchange) {
        HttpSession session = getSession(exchange);
        if (session == null) return null;
        
        String instId = (String) session.getAttribute("institutionId");
        if (instId == null || instId.isBlank()) {
            instId = (String) session.getAttribute("institution_id");
        }
        return instId;
    }
    
    public static String getUserId(HttpExchange exchange) {
        HttpSession session = getSession(exchange);
        if (session == null) return null;
        return (String) session.getAttribute("userId");
    }

    public static String getRole(HttpExchange exchange) {
        HttpSession session = getSession(exchange);
        if (session == null) return null;
        return (String) session.getAttribute("role");
    }

    public static HttpSession getSession(HttpExchange exchange) {
        try {
            String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
            if (cookieHeader == null) {
                return null;
            }
            
            String sessionId = extractSessionId(cookieHeader);
            if (sessionId == null) {
                return null;
            }
            
            return SessionManager.getSession(sessionId);
        } catch (Exception e) {
            return null;
        }
    }
    
    public static String getSessionIdFromExchange(HttpExchange exchange) {
        try {
            String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
            if (cookieHeader == null) return null;
            return extractSessionId(cookieHeader);
        } catch (Exception e) {
            return null;
        }
    }
    
    private static String extractSessionId(String cookieHeader) {
        for (String cookie : cookieHeader.split(";")) {
            String[] parts = cookie.trim().split("=", 2);
            if (parts.length == 2) {
                String name = parts[0].trim();
                if ("SESSION_ID".equalsIgnoreCase(name) || "JSESSIONID".equalsIgnoreCase(name)) {
                    return parts[1].trim();
                }
            }
        }
        return null;
    }
    
    public static boolean isAuthenticated(HttpExchange exchange) {
        return getInstitutionId(exchange) != null;
    }
    
    public static boolean requireAuthentication(HttpExchange exchange) throws IOException {
        if (!isAuthenticated(exchange)) {
            exchange.getResponseHeaders().set("Location", "/login");
            exchange.sendResponseHeaders(302, -1);
            return true;
        }
        return false;
    }
}