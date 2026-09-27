// BulletinFooter.java
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

public final class BulletinFooter {

    private BulletinFooter() {}

    public static void draw(Graphics2D g2, Institution institution, String reference, String numero) {
        DocumentFooter.draw(g2, institution, reference, numero);
    }

    public static void draw(Graphics2D g2, Institution institution, 
        String reference, String numero, BufferedImage qrCodeImage) {
        DocumentFooter.draw(g2, institution, reference, numero);
    }
}