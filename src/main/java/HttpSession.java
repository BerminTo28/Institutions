import java.util.HashMap;
import java.util.Map;

public class HttpSession {
    private final String id;
    private final Map<String, Object> attributes = new HashMap<>();
    private long creationTime;
    private long lastAccessTime;

    // Constructeur avec ID de session
    public HttpSession(String id) {
        this.id = id;
    }

    // Constructeur sans paramètre (pour compatibilité)
    public HttpSession() {
        this.id = null;
    }

    public String getId() {
        return id;
    }

    public void setAttribute(String name, Object value) {
        attributes.put(name, value);
    }

    public Object getAttribute(String name) {
        return attributes.get(name);
    }

    public void removeAttribute(String name) {
        attributes.remove(name);
    }

    public Map<String, Object> getAttributes() {
        return new HashMap<>(attributes);
    }

    public long getCreationTime() {
        return creationTime;
    }

    public void setCreationTime(long creationTime) {
        this.creationTime = creationTime;
    }

    public long getLastAccessTime() {
        return lastAccessTime;
    }

    public void setLastAccessTime(long lastAccessTime) {
        this.lastAccessTime = lastAccessTime;
    }
}