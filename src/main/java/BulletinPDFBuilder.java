import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.itextpdf.awt.PdfGraphics2D;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfTemplate;
import com.itextpdf.text.pdf.PdfWriter;

public class BulletinPDFBuilder {

    // ==================== GESTION DU DOSSIER DE TÉLÉCHARGEMENT ====================

    private static File getDownloadDirectory() {
        String userHome = System.getProperty("user.home");
        String os = System.getProperty("os.name").toLowerCase();
        String downloadsPath;

        if (os.contains("win")) {
            downloadsPath = userHome + "\\Downloads";
            File telechargements = new File(userHome + "\\Téléchargements");
            if (telechargements.exists() && telechargements.isDirectory()) {
                downloadsPath = userHome + "\\Téléchargements";
            }
        } else if (os.contains("mac")) {
            downloadsPath = userHome + "/Downloads";
        } else {
            downloadsPath = userHome + "/Téléchargements";
            File telechargements = new File(downloadsPath);
            if (!telechargements.exists()) {
                downloadsPath = userHome + "/Downloads";
            }
        }

        File dir = new File(downloadsPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    private static File ensureDirectory(File dir) {
        if (dir == null) {
            dir = getDownloadDirectory();
        } else if (dir.isFile()) {
            File parent = dir.getParentFile();
            if (parent != null) {
                dir = parent;
            } else {
                dir = getDownloadDirectory();
            }
        }
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    // ==================== CONSTRUCTION SIMPLE (UNE SESSION) ====================

    public File build(BulletinData data, File dossierSortie) {
        dossierSortie = ensureDirectory(dossierSortie);

        String nomFichier = String.format("BULLETIN_%s_%s_%s.pdf",
                data.getAnnee().replace("/", "-"),
                data.getSession().replace(" ", "_"),
                data.getEtudiant().getNumeroIdentifiantEtudiant());
        File fichier = new File(dossierSortie, nomFichier);

        Document document = new Document(PageSize.A4, 30, 30, 25, 25);
        try {
            PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream(fichier));
            document.open();

            String reference = (data.getReference() != null && !data.getReference().isEmpty())
                    ? data.getReference() : data.getInstitution().getSigleInstitution() + "/Rel...";
            String numero = (data.getNumero() != null && !data.getNumero().isEmpty())
                    ? data.getNumero() : "137........";

            List<String> sessions = Collections.singletonList(data.getSession());
            Map<String, String> sessionLabels = buildSessionLabels(sessions);
            double moyenneAnnuelle = data.getMoyenneGenerale();

            List<Integer> rangsParSession = Collections.singletonList(data.getRang());

            Printable printable = new BulletinPrintable(data, reference, numero,
                    sessions, sessionLabels, moyenneAnnuelle, rangsParSession);

            PdfContentByte cb = writer.getDirectContent();
            PdfTemplate template = cb.createTemplate(PageSize.A4.getWidth(), PageSize.A4.getHeight());
            Graphics2D g2 = new PdfGraphics2D(cb, PageSize.A4.getWidth(), PageSize.A4.getHeight());

            int result = printable.print(g2, null, 0);
            if (result != Printable.PAGE_EXISTS) {
                System.err.println("⚠️ Erreur lors de l'impression du bulletin");
            }

            g2.dispose();
            cb.addTemplate(template, 0, 0);

            document.close();
            return fichier;
        } catch (DocumentException | PrinterException | FileNotFoundException e) {
            System.err.println("❌ Erreur lors de la génération du bulletin: " + e.getMessage());
            return null;
        }
    }

    // ==================== CONSTRUCTION MULTI-SESSIONS ====================

    public File buildMultiSession(List<BulletinData> datas, File dossierSortie,
                                  Etudiant etudiant, Institution institution,
                                  double moyenneAnnuelle,
                                  List<Integer> rangsParSession) {
        if (datas == null || datas.isEmpty()) {
            return null;
        }

        dossierSortie = ensureDirectory(dossierSortie);

        String reference = null;
        String numero = null;
        for (BulletinData d : datas) {
            String ref = d.getReference();
            String num = d.getNumero();
            if (reference == null && ref != null && !ref.isEmpty()) {
                reference = ref;
            }
            if (numero == null && num != null && !num.isEmpty()) {
                numero = num;
            }
        }
        if (reference == null || reference.isEmpty()) {
            reference = institution.getSigleInstitution() + "/Rel...";
        }
        if (numero == null || numero.isEmpty()) {
            numero = "137........";
        }

        String nomFichier = String.format("BULLETIN_MULTI_%s.pdf",
                etudiant.getNumeroIdentifiantEtudiant());
        File fichier = new File(dossierSortie, nomFichier);

        Document document = new Document(PageSize.A4, 30, 30, 25, 25);
        try {
            PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream(fichier));
            document.open();

            Set<String> sessionsSet = new LinkedHashSet<>();
            for (BulletinData d : datas) {
                sessionsSet.add(d.getSession());
            }
            List<String> sessions = new ArrayList<>(sessionsSet);
            Map<String, String> sessionLabels = buildSessionLabels(sessions);

            List<BulletinReleveMatiere> mergedMatieres = mergeMatieresBySession(datas, sessions);

            Printable printable = new MultiSessionPrintable(
                    institution, reference, numero,
                    etudiant.getNom(), etudiant.getPrenom(), etudiant.getClasse(),
                    datas.get(0).getAnnee(),
                    mergedMatieres, sessions, sessionLabels,
                    moyenneAnnuelle, rangsParSession);

            PdfContentByte cb = writer.getDirectContent();
            PdfTemplate template = cb.createTemplate(PageSize.A4.getWidth(), PageSize.A4.getHeight());
            Graphics2D g2 = new PdfGraphics2D(cb, PageSize.A4.getWidth(), PageSize.A4.getHeight());

            int result = printable.print(g2, null, 0);
            if (result != Printable.PAGE_EXISTS) {
                System.err.println("⚠️ Erreur lors de l'impression du bulletin multi-session");
            }

            g2.dispose();
            cb.addTemplate(template, 0, 0);

            document.close();
            return fichier;
        } catch (DocumentException | PrinterException | FileNotFoundException e) {
            System.err.println("❌ Erreur lors de la génération du bulletin multi-session: " + e.getMessage());
            return null;
        }
    }

    // ==================== FUSION DES MATIÈRES POUR MULTI-SESSIONS ====================

    public List<BulletinReleveMatiere> mergeMatieresBySession(List<BulletinData> datas, List<String> sessions) {
        Map<String, BulletinReleveMatiere> map = new LinkedHashMap<>();

        for (BulletinData data : datas) {
            String sessKey = data.getSession();
            for (BulletinReleveMatiere rm : data.getReleves()) {
                String code = rm.getCodeCours();
                BulletinReleveMatiere master = map.get(code);
                if (master == null) {
                    master = new BulletinReleveMatiere(
                            rm.getAnneeAcademique(),
                            rm.getCodeCours(),
                            rm.getIntituleMatiere(),
                            rm.getCoefficient(),
                            rm.getNoteMaximale()
                    );
                    map.put(code, master);
                }
                double note = rm.getNoteBrute(data.getSession());
                if (note >= 0) {
                    master.ajouterNote(sessKey, note);
                }
            }
        }

        for (BulletinReleveMatiere rm : map.values()) {
            for (String sess : sessions) {
                if (rm.getNoteBrute(sess) < 0) {
                    rm.ajouterNote(sess, -1.0);
                }
            }
        }

        return new ArrayList<>(map.values());
    }

    // ==================== CLASSES INTERNES STATIQUES ====================

    private static class BulletinPrintable implements Printable {
        private final BulletinData data;
        private final String reference;
        private final String numero;
        private final List<String> sessions;
        private final Map<String, String> sessionLabels;
        private final double moyenneAnnuelle;
        private final List<Integer> rangsParSession;

        public BulletinPrintable(BulletinData data, String reference, String numero,
                                 List<String> sessions, Map<String, String> sessionLabels,
                                 double moyenneAnnuelle, List<Integer> rangsParSession) {
            this.data = data;
            this.reference = reference;
            this.numero = numero;
            this.sessions = sessions;
            this.sessionLabels = sessionLabels;
            this.moyenneAnnuelle = moyenneAnnuelle;
            this.rangsParSession = rangsParSession;
        }

        @Override
        public int print(Graphics g, PageFormat pf, int pageIndex) throws PrinterException {
            if (pageIndex > 0) {
                return NO_SUCH_PAGE;
            }
            Graphics2D g2 = (Graphics2D) g;

            Institution institution = data.getInstitution();

           // Remplacez l'appel existant dans la classe BulletinPrintable
float yAfterHeader = BulletinHeader.draw(g2,
        institution,
        reference,
        numero,
        data.getEtudiant().getNom(),
        data.getEtudiant().getPrenom(),
        data.getAnnee(),
        data.getEtudiant().getClasse(),
        null, // <--- La date d'émission (null pour utiliser la date actuelle)
        true);

            double moyenneSession = BulletinGenerator.calculerMoyenneGenerale(data.getReleves(), data.getSession());
            List<Double> moyennesParSession = Collections.singletonList(moyenneSession);

            String categorie = institution != null ? institution.getCategorieInstitution() : "";
            boolean isPublic = "Publique".equalsIgnoreCase(categorie);

            if (isPublic) {
                BulletinBody.draw(g2, data.getReleves(), sessions, sessionLabels,
                        yAfterHeader, data.getRang(),
                        institution != null ? institution.getSigleInstitution() : "",
                        institution != null ? institution.getResponsableInstitution() : "",
                        institution != null ? institution.getPosteResponsableInstitution() : "",
                        moyenneAnnuelle, moyennesParSession, rangsParSession);
            } else {
                BulletinBodyPrivate.draw(g2, data.getReleves(), sessions, sessionLabels,
                        yAfterHeader, data.getRang(),
                        institution != null ? institution.getSigleInstitution() : "",
                        institution != null ? institution.getResponsableInstitution() : "",
                        institution != null ? institution.getPosteResponsableInstitution() : "",
                        moyenneAnnuelle, moyennesParSession, rangsParSession);
            }

            BulletinFooter.draw(g2, institution, reference, numero);
            return PAGE_EXISTS;
        }
    }

    private static class MultiSessionPrintable implements Printable {
        private final Institution institution;
        private final String reference;
        private final String numero;
        private final String nom;
        private final String prenom;
        private final String classe;
        private final String annee;
        private final List<BulletinReleveMatiere> matieres;
        private final List<String> sessions;
        private final Map<String, String> sessionLabels;
        private final double moyenneAnnuelle;
        private final List<Integer> rangsParSession;

        public MultiSessionPrintable(Institution institution, String reference, String numero,
                                     String nom, String prenom, String classe, String annee,
                                     List<BulletinReleveMatiere> matieres, List<String> sessions,
                                     Map<String, String> sessionLabels,
                                     double moyenneAnnuelle, List<Integer> rangsParSession) {
            this.institution = institution;
            this.reference = reference;
            this.numero = numero;
            this.nom = nom;
            this.prenom = prenom;
            this.classe = classe;
            this.annee = annee;
            this.matieres = matieres;
            this.sessions = sessions;
            this.sessionLabels = sessionLabels;
            this.moyenneAnnuelle = moyenneAnnuelle;
            this.rangsParSession = rangsParSession;
        }

        @Override
        public int print(Graphics g, PageFormat pf, int pageIndex) throws PrinterException {
            if (pageIndex > 0) {
                return NO_SUCH_PAGE;
            }
            Graphics2D g2 = (Graphics2D) g;

            // Remplacez l'appel existant dans la classe MultiSessionPrintable
float yAfterHeader = BulletinHeader.draw(g2, 
        institution, 
        reference, 
        numero,
        nom, 
        prenom, 
        annee, 
        classe, 
        null, // <--- La date d'émission (null pour utiliser la date actuelle)
        true);

            List<Double> moyennesParSession = new ArrayList<>();
            for (String sess : sessions) {
                double moy = BulletinGenerator.calculerMoyenneGenerale(matieres, sess);
                moyennesParSession.add(moy);
            }

            String categorie = institution != null ? institution.getCategorieInstitution() : "";
            boolean isPublic = "Publique".equalsIgnoreCase(categorie);

int rangFallback = 0;
if (rangsParSession != null && !rangsParSession.isEmpty()) {
    Integer first = rangsParSession.get(0);
    rangFallback = (first != null) ? first : 0;
}

            if (isPublic) {
                BulletinBody.draw(g2, matieres, sessions, sessionLabels,
                        yAfterHeader, rangFallback,
                        institution != null ? institution.getSigleInstitution() : "",
                        institution != null ? institution.getResponsableInstitution() : "",
                        institution != null ? institution.getPosteResponsableInstitution() : "",
                        moyenneAnnuelle, moyennesParSession, rangsParSession);
            } else {
                BulletinBodyPrivate.draw(g2, matieres, sessions, sessionLabels,
                        yAfterHeader, rangFallback,
                        institution != null ? institution.getSigleInstitution() : "",
                        institution != null ? institution.getResponsableInstitution() : "",
                        institution != null ? institution.getPosteResponsableInstitution() : "",
                        moyenneAnnuelle, moyennesParSession, rangsParSession);
            }

            BulletinFooter.draw(g2, institution, reference, numero);
            return PAGE_EXISTS;
        }
    }

    // ==================== CONSTRUCTION DES LIBELLÉS DE SESSIONS ====================

    private Map<String, String> buildSessionLabels(List<String> sessions) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String s : sessions) {
            if (s == null || s.isEmpty()) {
                continue;
            }
            String label = s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
            map.put(s, label);
        }
        return map;
    }

    // ==================== GÉNÉRATION EN MÉMOIRE (byte[]) ====================

    public byte[] buildToBytes(BulletinData data) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 30, 30, 25, 25);
            PdfWriter writer = PdfWriter.getInstance(document, baos);
            document.open();

            String reference = (data.getReference() != null && !data.getReference().isEmpty())
                    ? data.getReference() : data.getInstitution().getSigleInstitution() + "/Rel...";
            String numero = (data.getNumero() != null && !data.getNumero().isEmpty())
                    ? data.getNumero() : "137........";

            List<String> sessions = Collections.singletonList(data.getSession());
            Map<String, String> sessionLabels = buildSessionLabels(sessions);
            double moyenneAnnuelle = data.getMoyenneGenerale();

            List<Integer> rangsParSession = Collections.singletonList(data.getRang());

            Printable printable = new BulletinPrintable(data, reference, numero,
                    sessions, sessionLabels, moyenneAnnuelle, rangsParSession);

            PdfContentByte cb = writer.getDirectContent();
            PdfTemplate template = cb.createTemplate(PageSize.A4.getWidth(), PageSize.A4.getHeight());
            Graphics2D g2 = new PdfGraphics2D(cb, PageSize.A4.getWidth(), PageSize.A4.getHeight());

            int result = printable.print(g2, null, 0);
            if (result != Printable.PAGE_EXISTS) {
                System.err.println("⚠️ Erreur lors de l'impression du bulletin");
            }

            g2.dispose();
            cb.addTemplate(template, 0, 0);

            document.close();
            return baos.toByteArray();
        } catch (DocumentException | PrinterException | IOException e) {
            System.err.println("❌ Erreur génération bulletin: " + e.getMessage());
            return null;
        }
    }
}