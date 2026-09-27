import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.FontFactory;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.PdfWriter;

public class AttestationGenerator {

    private final Etudiant etudiant;
    private final Institution institution;
    private final String reference;
    private final String numero;
    private final Date dateEmission;
    private final String option;
    private final String typeDoc;
    private final String classe;
    private final String anneeAcademique;

    // Constructeur complet
    public AttestationGenerator(Etudiant etudiant, Institution institution, String typeDoc,
                                String reference, String numero, Date dateEmission,
                                String option, String classe, String anneeAcademique) {
        this.etudiant = etudiant;
        this.institution = ensureInstitutionComplete(institution);
        this.typeDoc = (typeDoc != null && !typeDoc.isBlank()) ? typeDoc : "Attestation de scolarité";
        this.reference = (reference != null && !reference.isBlank()) ? reference : "REF-0001";
        this.numero = (numero != null && !numero.isBlank()) ? numero : "NUM-0001";
        this.dateEmission = dateEmission != null ? dateEmission : new Date();
        this.option = option != null ? option : "";
        this.classe = classe != null ? classe : (etudiant != null ? etudiant.getClasse() : "");
        this.anneeAcademique = anneeAcademique != null ? anneeAcademique : (etudiant != null ? etudiant.getAnneeAcademique() : "");

        System.out.println("📌 AttestationGenerator - Institution: " + this.institution.getNomInstitution());
        System.out.println("   Catégorie: '" + this.institution.getCategorieInstitution() + "'");
        System.out.println("   Adresse: '" + this.institution.getAdresseInstitution() + "'");
        System.out.println("   Tél primaire: '" + this.institution.getTelephonePrimaireInstitution() + "'");
        System.out.println("   Email primaire: '" + this.institution.getMailPrimaire() + "'");
    }

    // Constructeur avec option
    public AttestationGenerator(Etudiant etudiant, Institution institution, String typeDoc,
                                String reference, String numero, Date dateEmission, String option) {
        this(etudiant, institution, typeDoc, reference, numero, dateEmission,
             option, etudiant != null ? etudiant.getClasse() : "",
             etudiant != null ? etudiant.getAnneeAcademique() : "");
    }

    // Constructeur sans option
    public AttestationGenerator(Etudiant etudiant, Institution institution, String typeDoc,
                                String reference, String numero, Date dateEmission) {
        this(etudiant, institution, typeDoc, reference, numero, dateEmission, "");
    }

    // ============================================================
    // RECHARGEMENT DE L'INSTITUTION SI INCOMPLÈTE
    // ============================================================
    private Institution ensureInstitutionComplete(Institution inst) {
        if (inst == null) {
            throw new IllegalArgumentException("L'institution ne peut pas être nulle.");
        }

        boolean isIncomplete = (inst.getAdresseInstitution() == null || inst.getAdresseInstitution().isBlank()) ||
                               (inst.getTelephonePrimaireInstitution() == null || inst.getTelephonePrimaireInstitution().isBlank()) ||
                               (inst.getMailPrimaire() == null || inst.getMailPrimaire().isBlank());

        if (!isIncomplete) {
            return inst;
        }

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            InstitutionData data = new InstitutionData(conn);
            Institution reloaded = data.readByIdOrInstitutionId(inst.getInstitutionId());
            if (reloaded != null) {
                System.out.println("✅ Institution rechargée depuis la base pour " + inst.getInstitutionId());
                if (reloaded.getLogoInstitution() == null || reloaded.getLogoInstitution().isBlank()) {
                    reloaded.setLogoInstitution(inst.getLogoInstitution());
                }
                return reloaded;
            } else {
                System.err.println("⚠️ Impossible de recharger l'institution, utilisation de l'original.");
                return inst;
            }
        } catch (SQLException e) {
            System.err.println("❌ Erreur lors du rechargement de l'institution: " + e.getMessage());
            return inst;
        }
    }

    // ============================================================
    // DÉTERMINATION DU TYPE D'EN-TÊTE
    // ============================================================
    private boolean isPublic() {
        String categorie = institution.getCategorieInstitution();
        if (categorie == null) return false;
        return categorie.trim().toLowerCase().contains("public") ||
               categorie.trim().toLowerCase().contains("publique");
    }

    // ============================================================
    // DESSIN DE L'ATTESTATION
    // ============================================================
    public void drawDocument(Graphics2D g2) {
        g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY);
        
        float pageWidth = 595f;
        float pageHeight = 842f;

        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, (int) pageWidth, (int) pageHeight);

        float y;

        // ===== EN-TÊTE =====
        if (isPublic()) {
            y = DocumentPublicHeader.draw(g2, institution, pageWidth, pageHeight,
                                          reference, numero, dateEmission);
        } else {
            y = DocumentPrivateHeader.draw(g2, institution, pageWidth, pageHeight,
                                           dateEmission);
        }

        // ===== TITRE =====
        y = drawDocumentTitle(g2, y + 30);

        // ===== CORPS =====
        drawBody(g2, y + 25, pageWidth);

        // ===== PIED DE PAGE =====
        DocumentFooter.draw(g2, institution, reference, numero);
    }

    // ============================================================
    // TITRE
    // ============================================================
    private float drawDocumentTitle(Graphics2D g2, float y) {
        String title = typeDoc.toUpperCase();
        g2.setFont(new Font("Times New Roman", Font.BOLD, 22));
        g2.setColor(new Color(0, 51, 102));
        FontMetrics fm = g2.getFontMetrics();
        int titleWidth = fm.stringWidth(title);
        float pageWidth = 595f;
        int x = (int) ((pageWidth - titleWidth) / 2);
        g2.drawString(title, x, y);
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawLine(x, (int) y + 5, x + titleWidth, (int) y + 5);
        return y + fm.getHeight() + 20;
    }

    // ============================================================
    // CORPS
    // ============================================================
    private void drawBody(Graphics2D g2, float y, float pageWidth) {
        g2.setColor(Color.BLACK);
        Font bodyFont = new Font("Times New Roman", Font.PLAIN, 14);
        g2.setFont(bodyFont);
        float margin = 50f;
        float maxWidth = pageWidth - 2 * margin;
        float lineSpacing = 6f;

        String sexeVal = (etudiant.getSexe() != null) ? etudiant.getSexe().trim().toUpperCase() : "M";
        boolean isMasc = sexeVal.equals("M") || sexeVal.equals("G") || sexeVal.equals("MASCULIN") || sexeVal.equals("GARÇON");
        String civilite = isMasc ? "Monsieur" : "Madame";
        String accord = isMasc ? "inscrit" : "inscrite";

        String sigle = (institution.getSigleInstitution() != null) ? institution.getSigleInstitution() : "CTPEA";
        String nomComplet = etudiant.getNom().toUpperCase() + " " + etudiant.getPrenom();
        String optionFinale = (option != null && !option.isBlank()) ? "Option " + option : "";
        String classeEtudiant = (classe != null && !classe.isBlank()) ? classe : "N/A";
        String anneeAcademiqueStr = (anneeAcademique != null && !anneeAcademique.isBlank()) ? anneeAcademique : "exercice";

        String contenuPrincipal = "Le Secrétariat Général du " + institution.getNomInstitution() + " (" +
                sigle + "), atteste, par la présente, que "
                + civilite + " " + nomComplet + ", est " + accord + " au programme régulier du Centre "
                + "conduisant au Diplôme d'Études Supérieures en Économie Appliquée ou en Planification "
                + "ou en Économie Appliquée : " + optionFinale + ", " + civilite + " " + nomComplet + " est en "
                + classeEtudiant + " pour l'exercice académique " + anneeAcademiqueStr + ". ";

        y = drawJustifiedParagraph(g2, contenuPrincipal, margin, y, maxWidth, bodyFont, lineSpacing);

        String phraseFinale = "En foi de quoi, la présente attestation lui est délivrée pour servir et valoir ce que de droit.";
        y = drawJustifiedParagraph(g2, phraseFinale, margin, y, maxWidth, bodyFont, lineSpacing);

        y += 20;

        String dateStr = new SimpleDateFormat("dd MMMM yyyy", Locale.FRENCH).format(dateEmission);
        g2.setFont(new Font("Times New Roman", Font.PLAIN, 14));
        g2.drawString("Fait à Port-au-Prince, le " + dateStr, margin, y);
        y += 40;

        g2.setFont(new Font("Times New Roman", Font.BOLD, 14));
        String sigTitle = "Le Secrétariat Général du " + sigle;
        FontMetrics fmSig = g2.getFontMetrics();
        int xSig = (int) (pageWidth - margin - fmSig.stringWidth(sigTitle));
        g2.drawString(sigTitle, xSig, y);
    }

    // ============================================================
    // PARAGRAPHE JUSTIFIÉ
    // ============================================================
    private float drawJustifiedParagraph(Graphics2D g2, String text, float x, float y,
                                         float maxWidth, Font font, float lineSpacing) {
        g2.setFont(font);
        FontMetrics fm = g2.getFontMetrics();
        String[] words = text.split("\\s+");
        java.util.List<String> lines = new java.util.ArrayList<>();
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            String testLine = currentLine.length() == 0 ? word : currentLine + " " + word;
            if (fm.stringWidth(testLine) > maxWidth) {
                lines.add(currentLine.toString());
                currentLine = new StringBuilder(word);
            } else {
                currentLine = new StringBuilder(testLine);
            }
        }
        if (!currentLine.toString().isEmpty()) {
            lines.add(currentLine.toString());
        }

        float currentY = y;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            currentY += fm.getAscent();
            boolean isLastLine = (i == lines.size() - 1);
            if (isLastLine || !line.contains(" ")) {
                g2.drawString(line, x, currentY);
            } else {
                String[] lineWords = line.split(" ");
                int wordsWidth = 0;
                for (String w : lineWords) wordsWidth += fm.stringWidth(w);
                float justifiedSpace = (maxWidth - wordsWidth) / (lineWords.length - 1);
                float currentX = x;
                for (int j = 0; j < lineWords.length; j++) {
                    g2.drawString(lineWords[j], currentX, currentY);
                    currentX += fm.stringWidth(lineWords[j]);
                    if (j < lineWords.length - 1) currentX += justifiedSpace;
                }
            }
            currentY += fm.getDescent() + fm.getLeading() + lineSpacing;
        }
        return currentY;
    }

    // ============================================================
    // GÉNÉRATION PDF (Adapté pour iText 5)
    // ============================================================
    public static String genererPDF(Etudiant etudiant, Institution institution,
                                    String type, String reference, String numero,
                                    Date dateEmission, String option, String classe, String anneeAcademique) {
        try {
            String fileName = "attestation_" + System.currentTimeMillis() + ".pdf";
            File dir = new File("attestations");
            if (!dir.exists()) dir.mkdirs();
            File output = new File(dir, fileName);

            Document document = new Document(PageSize.A4, 50, 50, 50, 50);
            PdfWriter.getInstance(document, new java.io.FileOutputStream(output));
            document.open();

            Paragraph titre = new Paragraph(type.toUpperCase());
            titre.setAlignment(Element.ALIGN_CENTER);
            titre.setFont(FontFactory.getFont(FontFactory.TIMES_BOLD, 22, com.itextpdf.text.BaseColor.BLUE));
            document.add(titre);

            document.add(new Paragraph("\n"));

            StringBuilder body = new StringBuilder();
            String sexeVal = (etudiant.getSexe() != null) ? etudiant.getSexe().trim().toUpperCase() : "M";
            boolean isMasc = sexeVal.equals("M") || sexeVal.equals("G") || sexeVal.equals("MASCULIN") || sexeVal.equals("GARÇON");
            String civilite = isMasc ? "Monsieur" : "Madame";
            String accord = isMasc ? "inscrit" : "inscrite";
            String nomComplet = etudiant.getNom().toUpperCase() + " " + etudiant.getPrenom();
            String optionFinale = (option != null && !option.isBlank()) ? "Option " + option : "";
            String classeEtudiant = (classe != null && !classe.isBlank()) ? classe : "N/A";
            String anneeStr = (anneeAcademique != null && !anneeAcademique.isBlank()) ? anneeAcademique : "exercice";
            String sigle = (institution.getSigleInstitution() != null) ? institution.getSigleInstitution() : "CTPEA";

            body.append("Le Secrétariat Général du ").append(institution.getNomInstitution())
                .append(" (").append(sigle).append("), atteste, par la présente, que ")
                .append(civilite).append(" ").append(nomComplet).append(", est ")
                .append(accord).append(" au programme régulier du Centre ")
                .append("conduisant au Diplôme d'Études Supérieures en Économie Appliquée ou en Planification ")
                .append("ou en Économie Appliquée : ").append(optionFinale).append(", ")
                .append(civilite).append(" ").append(nomComplet).append(" est en ")
                .append(classeEtudiant).append(" pour l'exercice académique ").append(anneeStr).append(". ");

            Paragraph pBody = new Paragraph(body.toString(), FontFactory.getFont(FontFactory.TIMES_ROMAN, 14));
            document.add(pBody);
            document.add(new Paragraph("\n"));

            Paragraph pFin = new Paragraph("En foi de quoi, la présente attestation lui est délivrée pour servir et valoir ce que de droit.", FontFactory.getFont(FontFactory.TIMES_ROMAN, 14));
            document.add(pFin);
            
            document.add(new Paragraph("\n\n"));
            String dateStr = new SimpleDateFormat("dd MMMM yyyy", Locale.FRENCH).format(dateEmission != null ? dateEmission : new Date());
            Paragraph pDate = new Paragraph("Fait à Port-au-Prince, le " + dateStr, FontFactory.getFont(FontFactory.TIMES_ROMAN, 14));
            document.add(pDate);
            
            document.add(new Paragraph("\n\n\n"));
            Paragraph pSig = new Paragraph("Le Secrétariat Général du " + sigle, FontFactory.getFont(FontFactory.TIMES_BOLD, 14));
            pSig.setAlignment(Element.ALIGN_RIGHT);
            document.add(pSig);

            document.close();
            return fileName;
        } catch (IOException | DocumentException e) {
            throw new RuntimeException("Erreur génération PDF pour " + etudiant.getNom(), e);
        }
    }

    // ============================================================
    // GÉNÉRATION D'IMAGE (APERÇU HAUTE DÉFINITION - CORRIGÉ)
    // ============================================================
    public static BufferedImage generatePreviewImage(Etudiant etudiant, Institution institution,
                                                     String type, String reference, String numero,
                                                     Date dateEmission, String option,
                                                     String classe, String anneeAcademique) {
        AttestationGenerator generator = new AttestationGenerator(
                etudiant, institution, type, reference, numero, dateEmission,
                option, classe, anneeAcademique
        );

        // Facteur de zoom (2.0) pour doubler la résolution et éliminer le flou[cite: 4]
        double scale = 2.0;
        int width = (int) (595 * scale);
        int height = (int) (842 * scale);

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = image.createGraphics();
        
        // Application de l'échelle globale
        g2.scale(scale, scale);
        
        // Hints de qualité maximale
        g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(java.awt.RenderingHints.KEY_FRACTIONALMETRICS, java.awt.RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g2.setRenderingHint(java.awt.RenderingHints.KEY_COLOR_RENDERING, java.awt.RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        g2.setRenderingHint(java.awt.RenderingHints.KEY_DITHERING, java.awt.RenderingHints.VALUE_DITHER_ENABLE);
        
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, 595, 842);
        generator.drawDocument(g2);
        g2.dispose();
        return image;
    }
}