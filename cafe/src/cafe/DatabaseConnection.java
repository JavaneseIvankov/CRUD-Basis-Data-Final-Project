package cafe;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Database connection utility for the Cafe Management System
 */
public class DatabaseConnection {

    private static final Logger logger = Logger.getLogger(DatabaseConnection.class.getName());

    // Database connection parameters - matching docker-compose.yml
    private static final String SERVER = "localhost";
    private static final String PORT = "1433";
    private static final String DATABASE = "master";
    private static final String USERNAME = "sa";
    private static final String PASSWORD = "YourStrong!Passw0rd";

    // Connection URL
    private static final String URL = String.format(
            "jdbc:sqlserver://%s:%s;databaseName=%s;trustServerCertificate=true;encrypt=false",
            SERVER, PORT, DATABASE);

    /**
     * Get a new database connection (not singleton)
     * Each call returns a fresh connection that should be closed after use
     * 
     * @return Connection object
     */
    public static Connection getConnection() {
        try {
            // Load SQL Server JDBC driver
            Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");

            // Create and return a new connection each time
            Connection connection = DriverManager.getConnection(URL, USERNAME, PASSWORD);
            logger.info("Database connection established successfully");
            return connection;
            
        } catch (ClassNotFoundException e) {
            logger.log(Level.SEVERE, "SQL Server JDBC Driver not found", e);
            throw new RuntimeException("Database driver not found", e);
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to connect to database", e);
            throw new RuntimeException("Failed to connect to database", e);
        }
    }

    /**
     * Close a specific database connection
     * @param connection The connection to close
     */
    public static void closeConnection(Connection connection) {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                logger.info("Database connection closed successfully");
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to close database connection", e);
        }
    }

    /**
     * Test database connection
     * 
     * @return true if connection is successful
     */
    public static boolean testConnection() {
        try (Connection conn = getConnection()) {
            return conn != null && !conn.isClosed();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Database connection test failed", e);
            return false;
        }
    }
}
