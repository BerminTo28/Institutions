public class UICommunication {

    public static String renderPage(String institutionId) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>");
        sb.append("<html lang=\"fr\">");
        sb.append("<head>");
        sb.append("<meta charset=\"UTF-8\">");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
        sb.append("<title>Communication</title>");
        sb.append("<link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css\">");
        sb.append("<link href=\"https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700;800&display=swap\" rel=\"stylesheet\">");
        sb.append("<style>");
        sb.append("""
            * { margin:0; padding:0; box-sizing:border-box; }
            html, body { width: 100%; min-height: 100vh; background: #FAFBFB; font-family: 'Inter', sans-serif; }
            body { padding: 20px; }
            
            .container { width: 100%; max-width: 100%; margin: 0 auto; }
            
            /* Menu de navigation fluide et étiré */
            .nav-menu { display: flex; gap: 8px; background: white; border-radius: 12px; padding: 8px; margin-bottom: 20px; border: 1px solid #DCE1E6; flex-wrap: wrap; width: 100%; }
            .nav-btn { flex: 1 1 180px; text-align: center; padding: 12px 20px; background: none; border: none; cursor: pointer; font-weight: 600; border-radius: 8px; transition: all 0.2s ease-in-out; text-decoration: none; color: #333; font-size: 14px; }
            .nav-btn.active { background: #3B82F6; color: white; }
            .nav-btn:hover:not(.active) { background: #EFF6FF; color: #1D4ED8; }

            .header { background: white; color: #1E293B; padding: 24px; border-radius: 16px; text-align: center; margin-bottom: 20px; border: 1px solid #DCE1E6; box-shadow: 0 2px 8px rgba(0,0,0,0.06); width: 100%; }
            .header h1 { font-size: 22px; font-weight: 700; color: #1E40AF; display: flex; align-items: center; justify-content: center; gap: 10px; }
            .header h1 i { color: #3B82F6; }
            .header p { color: #64748B; font-size: 14px; margin-top: 6px; }

            .card-wrapper { background: white; border-radius: 16px; padding: 24px; margin-bottom: 20px; border: 1px solid #DCE1E6; box-shadow: 0 2px 8px rgba(0,0,0,0.06); width: 100%; }
            
            .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 20px; }
            .card { background: #FAFBFB; border-radius: 12px; padding: 30px 20px; text-align: center; cursor: pointer; transition: all 0.2s ease-in-out; border: 1px solid #DCE1E6; text-decoration: none; color: inherit; display: flex; flex-direction: column; align-items: center; justify-content: center; }
            .card:hover { transform: translateY(-4px); box-shadow: 0 6px 16px rgba(0,0,0,0.08); border-color: #3B82F6; background: #FFF; }
            .card i { font-size: 40px; margin-bottom: 15px; }
            .card .title { font-weight: 700; font-size: 16px; color: #1E293B; margin-top: 8px; }
            .card .desc { font-size: 13px; color: #64748B; margin-top: 4px; }
            
            .card.email i { color: #3B82F6; }
            .card.sms i { color: #8B5CF6; }
            .card.appel i { color: #EF4444; }
            .card.whatsapp i { color: #10B981; }
            
            .back { display: inline-flex; align-items: center; gap: 8px; text-align: center; margin-top: 10px; color: #64748B; text-decoration: none; font-weight: 600; font-size: 14px; background: white; padding: 10px 20px; border-radius: 8px; border: 1px solid #DCE1E6; transition: all 0.2s; }
            .back:hover { background: #F1F5F9; color: #0F172A; }
        """);
        sb.append("</style>");
        sb.append("</head>");
        sb.append("<body>");
        sb.append("<div class=\"container\">");

        // Menu de navigation fluide et étiré
        sb.append("<div class=\"nav-menu\">");
        sb.append("<a href=\"/dashboard\" class=\"nav-btn\"><i class=\"fas fa-home\"></i> Tableau de bord</a>");
        sb.append("<a href=\"/admin/communication\" class=\"nav-btn active\"><i class=\"fas fa-comments\"></i> Communication</a>");
        sb.append("</div>");

        sb.append("<div class=\"header\">");
        sb.append("<h1><i class=\"fas fa-comments\"></i> Communication</h1>");
        sb.append("<p>Sélectionnez un canal pour communiquer avec vos étudiants</p>");
        sb.append("</div>");

        sb.append("<div class=\"card-wrapper\">");
        sb.append("<div class=\"grid\">");
        
        sb.append("<a href=\"/admin/communication/email\" class=\"card email\">");
        sb.append("<i class=\"fas fa-envelope\"></i>");
        sb.append("<div class=\"title\">Email</div>");
        sb.append("<div class=\"desc\">Envoyer un email</div>");
        sb.append("</a>");

        sb.append("<a href=\"/admin/communication/sms\" class=\"card sms\">");
        sb.append("<i class=\"fas fa-sms\"></i>");
        sb.append("<div class=\"title\">SMS</div>");
        sb.append("<div class=\"desc\">Envoyer un SMS</div>");
        sb.append("</a>");

        sb.append("<a href=\"/admin/communication/appel\" class=\"card appel\">");
        sb.append("<i class=\"fas fa-phone\"></i>");
        sb.append("<div class=\"title\">Appel</div>");
        sb.append("<div class=\"desc\">Passer un appel</div>");
        sb.append("</a>");

        sb.append("<a href=\"/admin/communication/whatsapp\" class=\"card whatsapp\">");
        sb.append("<i class=\"fab fa-whatsapp\"></i>");
        sb.append("<div class=\"title\">WhatsApp</div>");
        sb.append("<div class=\"desc\">Envoyer via WhatsApp</div>");
        sb.append("</a>");

        sb.append("</div>");
        sb.append("</div>");

        sb.append("<a href=\"/dashboard\" class=\"back\"><i class=\"fas fa-arrow-left\"></i> Retour au tableau de bord</a>");

        sb.append("</div>");
        sb.append("</body>");
        sb.append("</html>");
        
        return sb.toString();
    }
}
