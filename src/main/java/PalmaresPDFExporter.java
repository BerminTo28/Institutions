import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Chunk;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.FontFactory;
import com.itextpdf.text.Image;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;

public class PalmaresPDFExporter {

    public static byte[] export(List<PalmaresEntry> entries, Institution institution,
                                String annee, String session, String classe) throws DocumentException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4.rotate(), 20, 20, 20, 20);
        PdfWriter.getInstance(document, baos);
        document.open();

        // --- LOGO ---
        String logoPath = resolveLogoPath(institution.getLogoInstitution());
        if (logoPath != null) {
            try {
                Image logo = Image.getInstance(logoPath);
                logo.scaleToFit(60, 60); // plus petit pour économiser de l'espace
                logo.setAlignment(Element.ALIGN_CENTER);
                document.add(logo);
            } catch (DocumentException | IOException e) { /* ignorer */ }
        }

        // --- En-tête institution (compact) ---
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, BaseColor.DARK_GRAY);
        Paragraph nom = new Paragraph(institution.getNomInstitution(), titleFont);
        nom.setAlignment(Element.ALIGN_CENTER);
        nom.setSpacingAfter(2);
        document.add(nom);

        if (institution.getSigleInstitution() != null && !institution.getSigleInstitution().isEmpty()) {
            Paragraph sigle = new Paragraph(institution.getSigleInstitution(),
                    FontFactory.getFont(FontFactory.HELVETICA, 12, BaseColor.DARK_GRAY));
            sigle.setAlignment(Element.ALIGN_CENTER);
            sigle.setSpacingAfter(2);
            document.add(sigle);
        }

        if (institution.getDeviseInstitution() != null && !institution.getDeviseInstitution().isEmpty()) {
            Paragraph devise = new Paragraph(institution.getDeviseInstitution(),
                    FontFactory.getFont(FontFactory.HELVETICA, 10, Font.ITALIC, BaseColor.GRAY));
            devise.setAlignment(Element.ALIGN_CENTER);
            devise.setSpacingAfter(4);
            document.add(devise);
        }

        // --- Ligne d'info (classe, période, année) avec espacement ---
        Paragraph infos = new Paragraph();
        infos.setAlignment(Element.ALIGN_CENTER);
        infos.setSpacingAfter(6);
        Font infoFont = FontFactory.getFont(FontFactory.HELVETICA, 11, BaseColor.DARK_GRAY);
        Chunk c1 = new Chunk("Classe : ", infoFont);
        Chunk c1b = new Chunk(classe, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, BaseColor.BLACK));
        Chunk sep = new Chunk("   |   ", infoFont);
        Chunk c2 = new Chunk("Période : ", infoFont);
        Chunk c2b = new Chunk(session, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, BaseColor.BLACK));
        Chunk sep2 = new Chunk("   |   ", infoFont);
        Chunk c3 = new Chunk("Année : ", infoFont);
        Chunk c3b = new Chunk(annee, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, BaseColor.BLACK));
        infos.add(c1);
        infos.add(c1b);
        infos.add(sep);
        infos.add(c2);
        infos.add(c2b);
        infos.add(sep2);
        infos.add(c3);
        infos.add(c3b);
        document.add(infos);

        // --- Titre du palmarès ---
        Paragraph titleDoc = new Paragraph("PALMARÈS DES ÉTUDIANTS",
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, new BaseColor(30, 64, 175)));
        titleDoc.setAlignment(Element.ALIGN_CENTER);
        titleDoc.setSpacingAfter(8);
        document.add(titleDoc);

        // --- Tableau ---
        Set<String> matiereSet = new LinkedHashSet<>();
        for (PalmaresEntry entry : entries) {
            for (PalmaresEntry.MatiereNote mn : entry.getMatieres()) {
                matiereSet.add(mn.getNom());
            }
        }
        List<String> matiereNames = new ArrayList<>(matiereSet);
        int totalColonnes = 6 + matiereNames.size();

        PdfPTable table = new PdfPTable(totalColonnes);
        table.setWidthPercentage(100);
        table.setSpacingBefore(5);
        table.setSpacingAfter(5);
        table.setHorizontalAlignment(Element.ALIGN_CENTER);

        // En-têtes
        BaseColor headerBg = new BaseColor(30, 64, 175);
        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, BaseColor.WHITE);
        for (String col : new String[]{"Rang", "Matricule", "Nom", "Prénom"}) {
            table.addCell(createHeaderCell(col, headerFont, headerBg));
        }
        for (String m : matiereNames) {
            table.addCell(createHeaderCell(m, headerFont, headerBg));
        }
        table.addCell(createHeaderCell("Moyenne", headerFont, headerBg));
        table.addCell(createHeaderCell("Mention", headerFont, headerBg));

        // Corps
        BaseColor evenRowBg = new BaseColor(240, 248, 255);
        BaseColor oddRowBg = BaseColor.WHITE;
        Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 8);

        for (int i = 0; i < entries.size(); i++) {
            PalmaresEntry entry = entries.get(i);
            BaseColor rowBg = (i % 2 == 0) ? evenRowBg : oddRowBg;

            // Rang
            PdfPCell cell = new PdfPCell(new Phrase(String.valueOf(entry.getRang()), normalFont));
            cell.setBackgroundColor(rowBg);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setPadding(2);
            table.addCell(cell);

            // Matricule
            cell = new PdfPCell(new Phrase(entry.getMatricule() != null ? entry.getMatricule() : "", normalFont));
            cell.setBackgroundColor(rowBg);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setPadding(2);
            table.addCell(cell);

            // Nom
            cell = new PdfPCell(new Phrase(entry.getNom(), normalFont));
            cell.setBackgroundColor(rowBg);
            cell.setPadding(2);
            table.addCell(cell);

            // Prénom
            cell = new PdfPCell(new Phrase(entry.getPrenom(), normalFont));
            cell.setBackgroundColor(rowBg);
            cell.setPadding(2);
            table.addCell(cell);

            // Matières
            Map<String, Double> noteMap = new HashMap<>();
            for (PalmaresEntry.MatiereNote mn : entry.getMatieres()) {
                noteMap.put(mn.getNom(), mn.getNote());
            }
            for (String m : matiereNames) {
                Double note = noteMap.get(m);
                String val = (note != null && note >= 0) ? String.format("%.2f", note) : "-";
                cell = new PdfPCell(new Phrase(val, normalFont));
                cell.setBackgroundColor(rowBg);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                cell.setPadding(2);
                table.addCell(cell);
            }

            // Moyenne
            cell = new PdfPCell(new Phrase(String.format("%.2f", entry.getMoyenne()), normalFont));
            cell.setBackgroundColor(rowBg);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setPadding(2);
            table.addCell(cell);

            // Mention
            String mention = entry.getMention();
            BaseColor mentionColor;
            if (null == mention) mentionColor = BaseColor.BLACK;
            else mentionColor = switch (mention) {
                case "Admis(e)" -> new BaseColor(0, 128, 0);
                case "Maintenu(e)" -> new BaseColor(255, 0, 0);
                default -> BaseColor.BLACK;
            };
            Font mentionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, mentionColor);
            cell = new PdfPCell(new Phrase(mention, mentionFont));
            cell.setBackgroundColor(rowBg);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setPadding(2);
            table.addCell(cell);
        }

        document.add(table);

        // Pied de page
        Paragraph footer = new Paragraph("Généré le " + new java.util.Date(),
                FontFactory.getFont(FontFactory.HELVETICA, 8, BaseColor.GRAY));
        footer.setAlignment(Element.ALIGN_CENTER);
        footer.setSpacingBefore(6);
        document.add(footer);

        document.close();
        return baos.toByteArray();
    }

    private static PdfPCell createHeaderCell(String text, Font font, BaseColor bg) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(bg);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(3);
        return cell;
    }

    private static String resolveLogoPath(String original) {
        if (original == null || original.trim().isEmpty()) return null;
        String clean = original.trim();
        List<String> candidates = new ArrayList<>();
        candidates.add(clean);
        if (clean.startsWith("/")) candidates.add("." + clean);
        String userDir = System.getProperty("user.dir");
        if (!userDir.endsWith("/") && !userDir.endsWith("\\")) userDir += "/";
        if (clean.startsWith("/")) candidates.add(userDir + clean.substring(1));
        else candidates.add(userDir + clean);
        for (String path : candidates) {
            File f = new File(path);
            if (f.exists() && f.isFile()) return path;
        }
        return null;
    }
}