import java.sql.Connection;
import java.sql.SQLException;

/**
 * Abstraction pour fournir des connexions DB.
 * Les handlers ne captent JAMAIS une connexion — ils l'obtiennent
 * à la demande via cette interface, ce qui est compatible avec un pool.
 */
@FunctionalInterface
public interface ConnectionProvider {

    /**
     * Retourne une connexion. L'appelant DOIT la fermer (try-with-resources).
     */
    Connection getConnection() throws SQLException;

    /**
     * Helper : exécute une action avec une connexion auto-fermée.
     */
    default <T> T withConnection(SqlFunction<Connection, T> action) throws SQLException {
        try (Connection conn = getConnection()) {
            return action.apply(conn);
        }
    }

    @FunctionalInterface
    interface SqlFunction<A, R> {
        R apply(A a) throws SQLException;
    }
}