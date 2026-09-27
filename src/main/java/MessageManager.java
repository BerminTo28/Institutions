import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MessageManager {

    private static final Logger LOGGER = Logger.getLogger(MessageManager.class.getName());
    private final MessageData messageData;

    public MessageManager(Connection connection) {
        this.messageData = new MessageData(connection);
    }

    // ============================================================
    // ENVOI
    // ============================================================

    /**
     * ✅ Envoie un message (création).
     *    L'ID est généré automatiquement en UUID par MessageData.create().
     */
    public boolean envoyerMessage(Message message) throws SQLException {
        if (message == null) {
            LOGGER.warning("envoyerMessage() : message nul");
            return false;
        }
        return messageData.create(message);
    }

    // ============================================================
    // MARQUER COMME LU
    // ============================================================

    /**
     * ✅ Marque un message comme lu.
     *    @param id UUID du message (String)
     */
    public boolean marquerCommeLu(String id, String institutionId) {
        if (id == null || id.isBlank()) {
            LOGGER.warning("marquerCommeLu() : id nul ou vide");
            return false;
        }
        try {
            Message m = messageData.findById(id, institutionId);
            if (m == null) return false;
            m.setLu(true);
            return messageData.update(m);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur marquage lu " + id, e);
            return false;
        }
    }

    // ============================================================
    // SUPPRESSION LOGIQUE
    // ============================================================

    /**
     * ✅ Supprime logiquement un message (côté expéditeur ou destinataire).
     *    @param id UUID du message (String)
     */
    public boolean supprimerMessage(String id, String institutionId, boolean pourExpediteur) throws SQLException {
        if (id == null || id.isBlank()) {
            LOGGER.warning("supprimerMessage() : id nul ou vide");
            return false;
        }
        return messageData.deleteLogique(id, institutionId, pourExpediteur);
    }

    // ============================================================
    // LECTURE
    // ============================================================

    public List<Message> getReçus(String destinataireId, String destinataireType,
                                   String institutionId) {
        try {
            return messageData.getReçus(destinataireId, destinataireType, institutionId);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur récupération reçus", e);
            return List.of();
        }
    }

    public List<Message> getEnvoyés(String expediteurId, String expediteurType,
                                     String institutionId) {
        try {
            return messageData.getEnvoyés(expediteurId, expediteurType, institutionId);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur récupération envoyés", e);
            return List.of();
        }
    }

    /**
     * ✅ Récupère un message par son UUID.
     *    @param id UUID du message (String)
     */
    public Message getMessage(String id, String institutionId) {
        if (id == null || id.isBlank()) return null;
        try {
            return messageData.findById(id, institutionId);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur récupération message " + id, e);
            return null;
        }
    }

    public int countNonLus(String destinataireId, String destinataireType,
                            String institutionId) {
        try {
            return messageData.countNonLus(destinataireId, destinataireType, institutionId);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur comptage non lus", e);
            return 0;
        }
    }

    // ============================================================
    // CONVERSATION (bonus)
    // ============================================================

    /**
     * ✅ Récupère les messages d'une conversation (fil).
     *    @param reponseA UUID du message parent (String)
     */
    public List<Message> getConversation(String reponseA, String institutionId) {
        if (reponseA == null || reponseA.isBlank()) return List.of();
        try {
            return messageData.getConversation(reponseA, institutionId);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur récupération conversation " + reponseA, e);
            return List.of();
        }
    }
}