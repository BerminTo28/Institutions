import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class BulletinBody {

    private static final float MARGIN = 50f;
    private static final float PAGE_WIDTH = 595f;
    private static final float TABLE_WIDTH = PAGE_WIDTH - 2 * MARGIN;
    private static final float ROW_HEIGHT = 16f;

    private static final Font HEADER_FONT = new Font("Times New Roman", Font.BOLD, 9);
    private static final Font BODY_FONT = new Font("Times New Roman", Font.PLAIN, 8);
    private static final Font TOTAL_FONT = new Font("Times New Roman", Font.BOLD, 9);
    private static final Font NOTA_FONT = new Font("Times New Roman", Font.ITALIC, 8);
    private static final Font MENTION_FONT = new Font("Times New Roman", Font.BOLD, 9);

    private static final Color HEADER_BG = new Color(220, 235, 250, 210);
    private static final Color TOTAL_BG = new Color(245, 245, 245, 210);
    private static final Color MENTION_BG = new Color(255, 248, 220, 210);
    private static final Color BORDER_COLOR = new Color(180, 180, 180);

    public static float draw(Graphics2D g2,
                             List<BulletinReleveMatiere> matieres,
                             List<String> sessions,
                             Map<String, String> sessionLabels,
                             float startY,
                             int rang,
                             String sigle,
                             String nomResponsable,
                             String fonctionResponsable,
                             double moyenneAnnuelle,
                             List<Double> moyennesParSession,
                             List<Integer> rangsParSession) {

        // Sécurité sessions / labels
        if (sessions == null || sessions.isEmpty()) {
            sessions = Collections.singletonList("Session");
            if (sessionLabels == null || sessionLabels.isEmpty()) {
                sessionLabels = new LinkedHashMap<>();
                sessionLabels.put("Session", "Notes");
            } else if (!sessionLabels.containsKey("Session")) {
                sessionLabels.put("Session", "Notes");
            }
        }
        if (sessionLabels == null || sessionLabels.isEmpty()) {
            sessionLabels = new LinkedHashMap<>();
            for (String s : sessions) {
                sessionLabels.put(s, s);
            }
        }

        int nbSessions = sessions.size();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

        // Largeurs des colonnes
        final float MIN_MATIERE = 80f;
        final float MAX_MATIERE = 200f;
        final float NOTE_MAX_WIDTH = 50f;
        final float COEF_WIDTH = 40f;
        final float PADDING_SESSION = 12f;

        g2.setFont(HEADER_FONT);
        FontMetrics fmHeader = g2.getFontMetrics();
        float[] sessionMinWidths = new float[nbSessions];
        for (int i = 0; i < nbSessions; i++) {
            String label = sessionLabels.getOrDefault(sessions.get(i), sessions.get(i));
            float textWidth = fmHeader.stringWidth(label);
            sessionMinWidths[i] = textWidth + 2 * PADDING_SESSION;
        }

        g2.setFont(BODY_FONT);
        FontMetrics fmBody = g2.getFontMetrics();
        float maxMatiereWidth = fmBody.stringWidth("Matières");
        if (matieres != null && !matieres.isEmpty()) {
            for (BulletinReleveMatiere mat : matieres) {
                float w = fmBody.stringWidth(mat.getIntituleMatiere());
                if (w > maxMatiereWidth) {
                    maxMatiereWidth = w;
                }
            }
        }

        float[] sessionWidths = new float[nbSessions];
        float totalSessionWidth = 0f;
        for (int i = 0; i < nbSessions; i++) {
            sessionWidths[i] = sessionMinWidths[i];
            totalSessionWidth += sessionWidths[i];
        }

        float usedWidth = totalSessionWidth + NOTE_MAX_WIDTH + COEF_WIDTH;
        float availableForMatiere = TABLE_WIDTH - usedWidth;
        float matiereWidth = Math.max(MIN_MATIERE, Math.min(MAX_MATIERE, availableForMatiere));

        float[] colWidths = new float[3 + nbSessions];
        colWidths[0] = matiereWidth;
        colWidths[1] = NOTE_MAX_WIDTH;
        colWidths[2] = COEF_WIDTH;
        System.arraycopy(sessionWidths, 0, colWidths, 3, nbSessions);

        float totalTableWidth = 0f;
        for (float w : colWidths) {
            totalTableWidth += w;
        }
        float startX = (PAGE_WIDTH - totalTableWidth) / 2f;

        // Ajustement police si beaucoup de sessions
        Font tableFont = BODY_FONT;
        float avgSessionWidth = 0;
        for (int i = 0; i < nbSessions; i++) {
            avgSessionWidth += sessionWidths[i];
        }
        avgSessionWidth /= nbSessions;
        if (avgSessionWidth < 45 && nbSessions >= 2) {
            tableFont = new Font("Times New Roman", Font.PLAIN, 7);
        } else if (avgSessionWidth < 35 && nbSessions >= 3) {
            tableFont = new Font("Times New Roman", Font.PLAIN, 6);
        }
        g2.setFont(tableFont);

        float y = startY;
        float x;

        // --- EN-TÊTE ---
        g2.setFont(HEADER_FONT);
        x = startX;
        drawCell(g2, x, y, colWidths[0], ROW_HEIGHT, "Matières", true, false, HEADER_BG);
        x += colWidths[0];
        drawCell(g2, x, y, colWidths[1], ROW_HEIGHT, "Note Max", true, false, HEADER_BG);
        x += colWidths[1];
        drawCell(g2, x, y, colWidths[2], ROW_HEIGHT, "Coef.", true, false, HEADER_BG);
        x += colWidths[2];
        for (int i = 0; i < nbSessions; i++) {
            String label = sessionLabels.getOrDefault(sessions.get(i), sessions.get(i));
            drawCell(g2, x, y, colWidths[3 + i], ROW_HEIGHT, label, true, false, HEADER_BG);
            x += colWidths[3 + i];
        }
        y += ROW_HEIGHT;

        // --- CORPS ---
        g2.setFont(tableFont);
        if (matieres == null || matieres.isEmpty()) {
            float totalWidth = 0;
            for (float w : colWidths) {
                totalWidth += w;
            }
            g2.setFont(new Font("Times New Roman", Font.ITALIC, 10));
            String message = "Aucune matière n'a été trouvée pour cette classe, année et session.";
            drawCell(g2, startX, y, totalWidth, ROW_HEIGHT * 2, message, false, false, null);
            y += ROW_HEIGHT * 2;
            g2.setFont(tableFont);
        } else {
            for (BulletinReleveMatiere mat : matieres) {
                x = startX;
                drawCell(g2, x, y, colWidths[0], ROW_HEIGHT, mat.getIntituleMatiere(), false, false, null);
                x += colWidths[0];
                drawCell(g2, x, y, colWidths[1], ROW_HEIGHT, formatDouble(mat.getNoteMaximale()), false, true, null);
                x += colWidths[1];
                drawCell(g2, x, y, colWidths[2], ROW_HEIGHT, formatDouble(mat.getCoefficient()), false, true, null);
                x += colWidths[2];
                for (int i = 0; i < nbSessions; i++) {
                    double note = mat.getNoteBrute(sessions.get(i));
                    drawCell(g2, x, y, colWidths[3 + i], ROW_HEIGHT, formatNote(note), false, true, null);
                    x += colWidths[3 + i];
                }
                y += ROW_HEIGHT;
            }
        }

        // --- TOTAL ---
        g2.setFont(TOTAL_FONT);
        x = startX;
        drawCell(g2, x, y, colWidths[0], ROW_HEIGHT, "TOTAL", false, false, TOTAL_BG);
        x += colWidths[0];
        double totalNoteMaxPondere = 0.0;
        if (matieres != null) {
            for (BulletinReleveMatiere mat : matieres) {
                totalNoteMaxPondere += mat.getNoteMaximale() * mat.getCoefficient();
            }
        }
        drawCell(g2, x, y, colWidths[1], ROW_HEIGHT, formatDouble(totalNoteMaxPondere), false, true, TOTAL_BG);
        x += colWidths[1];
        drawCell(g2, x, y, colWidths[2], ROW_HEIGHT, "", false, true, TOTAL_BG);
        x += colWidths[2];
        for (int i = 0; i < nbSessions; i++) {
            double sommePonderee = 0.0;
            String session = sessions.get(i);
            if (matieres != null) {
                for (BulletinReleveMatiere mat : matieres) {
                    double note = mat.getNoteBrute(session);
                    if (note >= 0) {
                        sommePonderee += note * mat.getCoefficient();
                    }
                }
            }
            drawCell(g2, x, y, colWidths[3 + i], ROW_HEIGHT, formatDouble(sommePonderee), false, true, TOTAL_BG);
            x += colWidths[3 + i];
        }
        y += ROW_HEIGHT;

        // --- MOYENNE par session ---
        g2.setFont(TOTAL_FONT);
        x = startX;
        drawCell(g2, x, y, colWidths[0], ROW_HEIGHT, "MOYENNE", false, false, TOTAL_BG);
        x += colWidths[0];
        drawCell(g2, x, y, colWidths[1], ROW_HEIGHT, formatDouble(10.0), false, true, TOTAL_BG);
        x += colWidths[1];
        drawCell(g2, x, y, colWidths[2], ROW_HEIGHT, "", false, true, TOTAL_BG);
        x += colWidths[2];
        for (int i = 0; i < nbSessions; i++) {
            double moy = 0.0;
            if (moyennesParSession != null && i < moyennesParSession.size()) {
                Double value = moyennesParSession.get(i);
                if (value != null) {
                    moy = value;
                }
            }
            drawCell(g2, x, y, colWidths[3 + i], ROW_HEIGHT, formatDouble(moy), false, true, TOTAL_BG);
            x += colWidths[3 + i];
        }
        y += ROW_HEIGHT;

        // --- PLACE (Rang par session) ---
        float largeurIntitule = colWidths[0] + colWidths[1] + colWidths[2];
        g2.setFont(TOTAL_FONT);
        x = startX;
        drawCell(g2, x, y, largeurIntitule, ROW_HEIGHT, "PLACE", false, false, TOTAL_BG);
        x += largeurIntitule;

        if (nbSessions > 0) {
            for (int i = 0; i < nbSessions; i++) {
                String texte = "";
                if (rangsParSession != null && i < rangsParSession.size()) {
                    int r = rangsParSession.get(i);
                    if (r > 0) texte = String.valueOf(r);
                }
                drawCell(g2, x, y, colWidths[3 + i], ROW_HEIGHT, texte, false, true, TOTAL_BG);
                x += colWidths[3 + i];
            }
        }
        y += ROW_HEIGHT;

        // --- MOYENNE ANNUELLE (valeur dans la dernière colonne) ---
        g2.setFont(TOTAL_FONT);
        x = startX;
        drawCell(g2, x, y, largeurIntitule, ROW_HEIGHT, "MOYENNE ANNUELLE", false, false, TOTAL_BG);
        x += largeurIntitule;

        if (nbSessions > 0) {
            // Remplir les colonnes avant la dernière avec des cellules vides (fond coloré)
            for (int i = 0; i < nbSessions - 1; i++) {
                drawCell(g2, x, y, colWidths[3 + i], ROW_HEIGHT, "", false, true, TOTAL_BG);
                x += colWidths[3 + i];
            }
            // Dernière colonne : la moyenne annuelle
            drawCell(g2, x, y, colWidths[3 + nbSessions - 1], ROW_HEIGHT, formatDouble(moyenneAnnuelle), false, true, TOTAL_BG);
        }
        y += ROW_HEIGHT;

        // --- MENTION (valeur dans la dernière colonne) ---
        g2.setFont(MENTION_FONT);
        x = startX;
        drawCell(g2, x, y, largeurIntitule, ROW_HEIGHT, "MENTION", false, false, MENTION_BG);
        x += largeurIntitule;

        if (nbSessions > 0) {
            // Remplir les colonnes avant la dernière avec des cellules vides (fond coloré)
            for (int i = 0; i < nbSessions - 1; i++) {
                drawCell(g2, x, y, colWidths[3 + i], ROW_HEIGHT, "", false, true, MENTION_BG);
                x += colWidths[3 + i];
            }
            // Dernière colonne : la mention
            String mention = getMention(moyenneAnnuelle);
            drawCell(g2, x, y, colWidths[3 + nbSessions - 1], ROW_HEIGHT, mention, false, true, MENTION_BG);
        }
        y += ROW_HEIGHT;

        // --- NOTA BENE & SIGNATURES ---
        y += 100;
        g2.setFont(NOTA_FONT);
        g2.setColor(Color.BLACK);
        String line1 = "NB: Pour être admis en année supérieure ou pour obtenir le diplôme d'Etudes Supérieures du deuxième";
        String line2 = "cycle, l'étudiant doit obtenir une moyenne annuelle de 60% sans qu'aucune de ses notes annuelles soit";
        String line3 = "inférieure à 50%.";
        g2.drawString(line1, MARGIN, y);
        y += 15;
        g2.drawString(line2, MARGIN, y);
        y += 15;
        g2.drawString(line3, MARGIN, y);
        y += 15;

        g2.setColor(Color.DARK_GRAY);
        g2.setStroke(new BasicStroke(0.6f));

        y += 30;
        g2.setFont(new Font("Times New Roman", Font.PLAIN, 9));
        g2.setColor(Color.BLACK);
        String gauche1 = "Le secrétariat du ";
        String droite1 = "Vue et Approuvé par :";
        drawLeftRight(g2, gauche1, droite1, MARGIN, PAGE_WIDTH - MARGIN, y);
        y += 20;
        String sigleValue = (sigle != null) ? sigle : "";
        String nomValue = (nomResponsable != null) ? nomResponsable : "";
        String fonctionValue = (fonctionResponsable != null) ? " (" + fonctionResponsable + ")" : "";
        String gauche2 = sigleValue;
        String droite2 = nomValue + fonctionValue;
        drawLeftRight(g2, gauche2, droite2, MARGIN, PAGE_WIDTH - MARGIN, y);
        y += 20;
        y += 10;
        return y;
    }

    private static String getMention(double moyenne) {
        if (moyenne >= 5) {
            return "Admis(e)";
        }
        return "Maintenu(e)";
    }

    private static void drawLeftRight(Graphics2D g2, String leftText, String rightText,
                                      float leftX, float rightX, float y) {
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(leftText, leftX, y);
        int rightWidth = fm.stringWidth(rightText);
        float rightPos = rightX - rightWidth;
        g2.drawString(rightText, rightPos, y);
    }

    private static void drawCell(Graphics2D g2, float x, float y, float width, float height,
                                 String text, boolean isHeader, boolean alignRight, Color bgColor) {
        if (bgColor != null) {
            g2.setColor(bgColor);
            g2.fillRect((int) x, (int) y, (int) width, (int) height);
        }
        g2.setColor(BORDER_COLOR);
        g2.setStroke(new BasicStroke(0.4f));
        g2.drawRect((int) x, (int) y, (int) width, (int) height);

        if (text == null || text.isEmpty()) {
            return;
        }

        Font currentFont = isHeader ? HEADER_FONT : g2.getFont();
        g2.setFont(currentFont);
        g2.setColor(Color.BLACK);
        FontMetrics fm = g2.getFontMetrics();
        float textY = y + (height - fm.getHeight()) / 2f + fm.getAscent();
        float textX;
        if (isHeader) {
            float textWidth = fm.stringWidth(text);
            textX = x + (width - textWidth) / 2f;
        } else {
            if (alignRight) {
                float textWidth = fm.stringWidth(text);
                textX = x + width - textWidth - 6f;
            } else {
                textX = x + 6f;
            }
        }
        g2.drawString(text, textX, textY);
    }

    private static String formatNote(double note) {
        return (note < 0) ? "-" : String.format(Locale.US, "%.2f", note);
    }

    private static String formatDouble(double val) {
        return String.format(Locale.US, "%.2f", val);
    }
}