import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class MultipartParser {

    public static class MultipartData {
        private final Map<String, String> fields = new HashMap<>();
        private String fileName;
        private byte[] fileContent;
        private String fileFieldName;

        public Map<String, String> getFields() { return fields; }
        public String getFileName() { return fileName; }
        public byte[] getFileContent() { return fileContent; }
        public String getFileFieldName() { return fileFieldName; }

        public void addField(String name, String value) {
            fields.put(name, value);
        }

        public void setFile(String fieldName, String fileName, byte[] content) {
            this.fileFieldName = fieldName;
            this.fileName = fileName;
            this.fileContent = content;
        }

        public boolean hasFile() {
            return fileContent != null && fileContent.length > 0;
        }
    }

    public static MultipartData parse(InputStream inputStream, String contentType) throws IOException {
        MultipartData data = new MultipartData();

        String boundary = extractBoundary(contentType);
        if (boundary == null) {
            throw new IOException("Boundary introuvable dans Content-Type");
        }

        // Lire tout le corps
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = inputStream.read(buf)) != -1) {
            buffer.write(buf, 0, n);
        }
        byte[] bodyBytes = buffer.toByteArray();
        String bodyStr = new String(bodyBytes, StandardCharsets.ISO_8859_1);

        String boundaryString = "--" + boundary;
        int start = 0;

        while (true) {
            int partStart = bodyStr.indexOf(boundaryString, start);
            if (partStart == -1) break;

            int partEnd = bodyStr.indexOf(boundaryString, partStart + boundaryString.length());
            if (partEnd == -1) {
                // Dernière partie
                partEnd = bodyStr.indexOf(boundaryString + "--", partStart + boundaryString.length());
                if (partEnd == -1) break;
            }

            String part = bodyStr.substring(partStart + boundaryString.length(), partEnd);

            // Supprimer les \r\n au début et à la fin
            if (part.startsWith("\r\n")) part = part.substring(2);
            if (part.endsWith("\r\n")) part = part.substring(0, part.length() - 2);

            if (part.isEmpty()) {
                start = partEnd;
                continue;
            }

            // Séparer les en-têtes du contenu
            int separator = part.indexOf("\r\n\r\n");
            if (separator == -1) {
                separator = part.indexOf("\n\n");
            }
            if (separator == -1) {
                start = partEnd;
                continue;
            }

            String headers = part.substring(0, separator);
            String content = part.substring(separator + 4); // +4 pour \r\n\r\n

            // Extraire le nom et le nom de fichier des en-têtes
            String name = null;
            String filename = null;
            for (String line : headers.split("\r\n")) {
                if (line.startsWith("Content-Disposition:")) {
                    name = extractValue(line, "name");
                    filename = extractValue(line, "filename");
                    break;
                }
            }

            if (name == null) {
                start = partEnd;
                continue;
            }

            // Supprimer le \r\n final du contenu
            if (content.endsWith("\r\n")) {
                content = content.substring(0, content.length() - 2);
            }

            if (filename != null && !filename.isEmpty()) {
                // C'est un fichier
                data.setFile(name, filename, content.getBytes(StandardCharsets.ISO_8859_1));
            } else {
                data.addField(name, content.trim());
            }

            start = partEnd;
        }

        return data;
    }

    private static String extractBoundary(String contentType) {
        if (contentType == null) return null;
        int idx = contentType.indexOf("boundary=");
        if (idx == -1) return null;
        String boundary = contentType.substring(idx + 9);
        if (boundary.startsWith("\"") && boundary.endsWith("\"")) {
            boundary = boundary.substring(1, boundary.length() - 1);
        }
        return boundary;
    }

    private static String extractValue(String disposition, String key) {
        int idx = disposition.indexOf(key + "=");
        if (idx == -1) return null;
        int start = idx + key.length() + 1;
        if (start >= disposition.length()) return null;
        char quote = disposition.charAt(start);
        if (quote == '"' || quote == '\'') {
            int end = disposition.indexOf(quote, start + 1);
            if (end != -1) {
                return disposition.substring(start + 1, end);
            }
        } else {
            int end = disposition.indexOf(';', start);
            if (end == -1) end = disposition.length();
            return disposition.substring(start, end).trim();
        }
        return null;
    }
}