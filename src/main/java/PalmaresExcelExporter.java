import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.util.IOUtils;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFPicture;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class PalmaresExcelExporter {

    public static byte[] export(List<PalmaresEntry> entries, Institution institution,
                                String annee, String session, String classe) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Palmares");
            
            // --- Centrage horizontal et vertical sur la page d'impression ---
            sheet.setHorizontallyCenter(true);
            sheet.setVerticallyCenter(true);

            int currentRow ;

            // --- Styles ---
            CellStyle titleStyle = createTitleStyle(workbook);
            CellStyle subtitleStyle = createSubtitleStyle(workbook);
            CellStyle italicStyle = createItalicStyle(workbook);
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle evenRowStyle = createEvenRowStyle(workbook);
            CellStyle oddRowStyle = createOddRowStyle(workbook);
            CellStyle mentionAdmisStyle = createMentionStyle(workbook, IndexedColors.GREEN);
            CellStyle mentionMaintenuStyle = createMentionStyle(workbook, IndexedColors.RED);
            CellStyle numberStyle = createNumberStyle(workbook);

            // --- Gestion du LOGO ---
            String logoPath = resolveLogoPath(institution.getLogoInstitution());
            boolean hasLogo = false;
            if (logoPath != null) {
                try (FileInputStream fis = new FileInputStream(logoPath)) {
                    byte[] pictureData = IOUtils.toByteArray(fis);
                    int pictureIdx = workbook.addPicture(pictureData, Workbook.PICTURE_TYPE_PNG);
                    XSSFDrawing drawing = (XSSFDrawing) sheet.createDrawingPatriarch();
                    // Position : colonnes 0 à 2, lignes 0 à 2 (mais on va ajuster la hauteur)
                    XSSFClientAnchor anchor = new XSSFClientAnchor();
                    anchor.setCol1(0);
                    anchor.setRow1(0);
                    anchor.setCol2(2);
                    anchor.setRow2(2);
                    XSSFPicture pic = drawing.createPicture(anchor, pictureIdx);
                    // Redimensionnement proportionnel sans dépasser la zone
                    pic.resize(1.0); // mise à l'échelle automatique
                    hasLogo = true;
                } catch (Exception e) {
                    // Ignoré
                }
            }

            // On décale le début pour laisser de la place au logo (si présent)
            int offset = hasLogo ? 3 : 0; // on laisse 3 lignes pour le logo
            currentRow = offset;

            // --- En-tête texte (compact) ---
            // Ligne 1 : Nom institution
            Row row0 = sheet.createRow(currentRow++);
            Cell cell0 = row0.createCell(0);
            cell0.setCellValue(institution.getNomInstitution());
            cell0.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(currentRow-1, currentRow-1, 0, 10));
            row0.setHeightInPoints(22);

            // Ligne 2 : Sigle (si présent)
            if (institution.getSigleInstitution() != null && !institution.getSigleInstitution().isEmpty()) {
                Row rowSigle = sheet.createRow(currentRow++);
                Cell cellSigle = rowSigle.createCell(0);
                cellSigle.setCellValue(institution.getSigleInstitution());
                cellSigle.setCellStyle(subtitleStyle);
                sheet.addMergedRegion(new CellRangeAddress(currentRow-1, currentRow-1, 0, 10));
                rowSigle.setHeightInPoints(18);
            }

            // Ligne 3 : Devise (si présente)
            if (institution.getDeviseInstitution() != null && !institution.getDeviseInstitution().isEmpty()) {
                Row rowDevise = sheet.createRow(currentRow++);
                Cell cellDevise = rowDevise.createCell(0);
                cellDevise.setCellValue(institution.getDeviseInstitution());
                cellDevise.setCellStyle(italicStyle);
                sheet.addMergedRegion(new CellRangeAddress(currentRow-1, currentRow-1, 0, 10));
                rowDevise.setHeightInPoints(16);
            }

            // --- Ligne d'info : Classe | Période | Année (sur une seule ligne, espacée) ---
            Row rowInfos = sheet.createRow(currentRow++);
            Cell cellInfos = rowInfos.createCell(0);
            cellInfos.setCellValue("Classe : " + classe + "   |   Période : " + session + "   |   Année : " + annee);
            cellInfos.setCellStyle(subtitleStyle);
            sheet.addMergedRegion(new CellRangeAddress(currentRow-1, currentRow-1, 0, 10));
            rowInfos.setHeightInPoints(18);

            // Ligne vide légère
            currentRow++;
            // Titre Palmarès
            Row rowTitle = sheet.createRow(currentRow++);
            Cell cellTitle = rowTitle.createCell(0);
            cellTitle.setCellValue("PALMARÈS DES ÉTUDIANTS AVEC DÉTAIL DES NOTES");
            cellTitle.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(currentRow-1, currentRow-1, 0, 10));
            rowTitle.setHeightInPoints(20);

            currentRow++; // ligne vide avant tableau

            // --- Déterminer les matières ---
            Set<String> matiereSet = new LinkedHashSet<>();
            for (PalmaresEntry entry : entries) {
                for (PalmaresEntry.MatiereNote mn : entry.getMatieres()) {
                    matiereSet.add(mn.getNom());
                }
            }
            List<String> matiereNames = new ArrayList<>(matiereSet);

            // En-têtes du tableau
            Row headerRow = sheet.createRow(currentRow++);
            int colIdx = 0;
            String[] fixedHeaders = {"Rang", "Matricule", "Nom", "Prénom"};
            for (String h : fixedHeaders) {
                Cell cell = headerRow.createCell(colIdx++);
                cell.setCellValue(h);
                cell.setCellStyle(headerStyle);
            }
            for (String m : matiereNames) {
                Cell cell = headerRow.createCell(colIdx++);
                cell.setCellValue(m);
                cell.setCellStyle(headerStyle);
            }
            Cell cellMoy = headerRow.createCell(colIdx++);
            cellMoy.setCellValue("Moyenne");
            cellMoy.setCellStyle(headerStyle);
            Cell cellMen = headerRow.createCell(colIdx++);
            cellMen.setCellValue("Mention");
            cellMen.setCellStyle(headerStyle);
            headerRow.setHeightInPoints(20);

            // Données
            for (int i = 0; i < entries.size(); i++) {
                PalmaresEntry entry = entries.get(i);
                Row row = sheet.createRow(currentRow++);
                boolean isEven = (i % 2 == 0);
                CellStyle rowStyle = isEven ? evenRowStyle : oddRowStyle;
                row.setHeightInPoints(16);

                int c = 0;
                Cell cell;

                cell = row.createCell(c++);
                cell.setCellValue(entry.getRang());
                cell.setCellStyle(rowStyle);

                cell = row.createCell(c++);
                cell.setCellValue(entry.getMatricule() != null ? entry.getMatricule() : "");
                cell.setCellStyle(rowStyle);

                cell = row.createCell(c++);
                cell.setCellValue(entry.getNom());
                cell.setCellStyle(rowStyle);

                cell = row.createCell(c++);
                cell.setCellValue(entry.getPrenom());
                cell.setCellStyle(rowStyle);

                Map<String, Double> noteMap = new HashMap<>();
                for (PalmaresEntry.MatiereNote mn : entry.getMatieres()) {
                    noteMap.put(mn.getNom(), mn.getNote());
                }
                for (String m : matiereNames) {
                    Double note = noteMap.get(m);
                    cell = row.createCell(c++);
                    if (note != null && note >= 0) {
                        cell.setCellValue(note);
                        cell.setCellStyle(numberStyle);
                    } else {
                        cell.setCellValue("-");
                        cell.setCellStyle(rowStyle);
                    }
                }

                cell = row.createCell(c++);
                cell.setCellValue(entry.getMoyenne());
                cell.setCellStyle(numberStyle);

                cell = row.createCell(c++);
                String mention = entry.getMention();
                cell.setCellValue(mention);
                if (null == mention) {
                    cell.setCellStyle(rowStyle);
                } else switch (mention) {
                    case "Admis(e)" -> cell.setCellStyle(mentionAdmisStyle);
                    case "Maintenu(e)" -> cell.setCellStyle(mentionMaintenuStyle);
                    default -> cell.setCellStyle(rowStyle);
                }
            }

            // Ajuster les largeurs
            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(baos);
        }
        return baos.toByteArray();
    }

    // ----- Styles -----
    private static CellStyle createTitleStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 16);
        font.setColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private static CellStyle createSubtitleStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setFontHeightInPoints((short) 12);
        font.setColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private static CellStyle createItalicStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setItalic(true);
        font.setFontHeightInPoints((short) 10);
        font.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private static CellStyle createHeaderStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private static CellStyle createEvenRowStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private static CellStyle createOddRowStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setFillForegroundColor(IndexedColors.WHITE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private static CellStyle createMentionStyle(Workbook wb, IndexedColors color) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setColor(color.getIndex());
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setFillForegroundColor(IndexedColors.WHITE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private static CellStyle createNumberStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setDataFormat(wb.createDataFormat().getFormat("0.00"));
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
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