// Dans GestionnaireUtils.java
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GestionnaireUtils {

    private GestionnaireUtils() {}

    public static java.sql.Date convertirDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }
        
        // Format 1: dd-MMM-yy (ex: 15-Jan-24)
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MMM-yy", Locale.FRENCH);
        sdf.setLenient(false);
        try {
            java.util.Date d = sdf.parse(dateStr);
            return new java.sql.Date(d.getTime());
        } catch (ParseException e) {
            // Format 2: dd/MM/yyyy (ex: 15/01/2024)
            SimpleDateFormat sdf2 = new SimpleDateFormat("dd/MM/yyyy");
            try {
                java.util.Date d = sdf2.parse(dateStr);
                return new java.sql.Date(d.getTime());
            } catch (ParseException ex) {
                // Format 3: yyyy-MM-dd (ex: 2024-01-15)
                SimpleDateFormat sdf3 = new SimpleDateFormat("yyyy-MM-dd");
                try {
                    java.util.Date d = sdf3.parse(dateStr);
                    return new java.sql.Date(d.getTime());
                } catch (ParseException ex3) {
                    Logger.getLogger(GestionnaireUtils.class.getName())
                          .log(Level.SEVERE, "Date non convertible : " + dateStr, ex3);
                    return null;
                }
            }
        }
    }
}