import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Utilitaire JSON basé sur Gson
 */
public class JsonUtil {

    private static final Gson gson = new GsonBuilder()
            .setDateFormat("yyyy-MM-dd'T'HH:mm:ss")
            .registerTypeAdapter(LocalDateTime.class, (com.google.gson.JsonSerializer<LocalDateTime>) (src, typeOfSrc, context) ->
                    context.serialize(src.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)))
            .registerTypeAdapter(LocalDateTime.class, (com.google.gson.JsonDeserializer<LocalDateTime>) (json, typeOfT, context) ->
                    LocalDateTime.parse(json.getAsString(), DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            .create();

    /**
     * Convertit un objet en JSON
     */
    public static String toJson(Object obj) {
        return gson.toJson(obj);
    }

    /**
     * Convertit un JSON en objet d'une classe donnée
     */
    public static <T> T fromJson(String json, Class<T> clazz) throws JsonSyntaxException {
        return gson.fromJson(json, clazz);
    }

    /**
     * Convertit un JSON en objet d'un type générique (ex: List<CoursEnLigne>)
     */
    public static <T> T fromJson(String json, Type type) throws JsonSyntaxException {
        return gson.fromJson(json, type);
    }

    /**
     * Convertit un JSON en Map<String, Object>
     */
    public static Map<String, Object> fromJsonToMap(String json) throws JsonSyntaxException {
        Type type = new TypeToken<Map<String, Object>>() {}.getType();
        return gson.fromJson(json, type);
    }

    /**
     * Parse une query string (ex: "param1=value1&param2=value2") en Map
     */
    public static Map<String, String> parseQuery(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isBlank()) return params;
        for (String pair : query.split("&")) {
            int idx = pair.indexOf("=");
            if (idx > 0) {
                try {
                    String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8.name());
                    String value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8.name());
                    params.put(key, value);
                } catch (UnsupportedEncodingException e) {
                    // ignore
                }
            }
        }
        return params;
    }

    /**
     * Convertit une Map en JSON
     */
    public static String mapToJson(Map<?, ?> map) {
        return gson.toJson(map);
    }
}