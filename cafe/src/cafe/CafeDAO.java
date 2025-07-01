package cafe;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Data Access Object for cafe management operations
 */
public class CafeDAO {

    private static final Logger logger = Logger.getLogger(CafeDAO.class.getName());

    // Employee operations
    public List<String> getAllEmployees() {
        List<String> employees = new ArrayList<>();
        String query = "SELECT Nama_Pegawai FROM Pegawai";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                employees.add(rs.getString("Nama_Pegawai"));
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to fetch employees", e);
        }

        return employees;
    }

    // Menu operations
    public List<String> getAllMenuItems() {
        List<String> menuItems = new ArrayList<>();
      //   String query = "SELECT Nama_Menu + ' - Rp' + CAST(Harga AS VARCHAR(20)) as DisplayName FROM Menu WHERE Status_Menu = 1";
        String query = "SELECT Nama_Menu + ' - Rp' + CAST(Harga AS VARCHAR(20)) as DisplayName FROM Menu";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                menuItems.add(rs.getString("DisplayName"));
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to fetch menu items", e);
        }

        return menuItems;
    }

    // Table operations
    public List<String> getAvailableTables() {
        List<String> tables = new ArrayList<>();
        String query = "SELECT Nomor_Meja, Kapasitas FROM Meja ORDER BY Nomor_Meja";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                String tableInfo = "Meja " + rs.getInt("Nomor_Meja") + " (Kapasitas: " + rs.getInt("Kapasitas") + ")";
                tables.add(tableInfo);
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to fetch tables", e);
        }

        return tables;
    }

    // Payment methods
    public List<String> getPaymentMethods() {
        List<String> methods = new ArrayList<>();
        methods.add("Cash");
        methods.add("Debit Card");
        methods.add("Credit Card");
        methods.add("E-Wallet");
        return methods;
    }

    // Order operations
    public boolean insertOrder(String customerName, String employeeName, String tableInfo, 
                              String orderDetails, String paymentMethod, List<String> selectedMenuItems) {
        
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false); // Start transaction

            // Get employee ID
            int employeeId = getEmployeeId(employeeName);
            
            // Extract table number from table info
            int tableNumber = extractTableNumber(tableInfo);

            // Insert main order
            String insertOrderQuery = """
                INSERT INTO Pesanan (ID_Pegawai, Nomor_Meja, Nama_Pemesan, Tanggal_Pesanan, Metode_Pembayaran) 
                VALUES (?, ?, ?, ?, ?)
            """;

            int orderId;
            try (PreparedStatement stmt = conn.prepareStatement(insertOrderQuery, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setInt(1, employeeId);
                stmt.setInt(2, tableNumber);
                stmt.setString(3, customerName);
                stmt.setDate(4, Date.valueOf(LocalDate.now()));
                stmt.setString(5, paymentMethod);

                int rowsAffected = stmt.executeUpdate();
                
                if (rowsAffected == 0) {
                    throw new SQLException("Creating order failed, no rows affected");
                }

                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        orderId = generatedKeys.getInt(1);
                    } else {
                        throw new SQLException("Creating order failed, no ID obtained");
                    }
                }
            }

            // Insert order details
            for (String menuItem : selectedMenuItems) {
                insertOrderDetail(conn, orderId, menuItem, orderDetails);
            }

            conn.commit(); // Commit transaction
            logger.info("Order inserted successfully with ID: " + orderId);
            return true;

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to insert order", e);
            if (conn != null) {
                try {
                    conn.rollback(); // Rollback on error
                } catch (SQLException ex) {
                    logger.log(Level.SEVERE, "Failed to rollback transaction", ex);
                }
            }
            return false;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true); // Reset auto-commit
                } catch (SQLException e) {
                    logger.log(Level.SEVERE, "Failed to reset auto-commit", e);
                }
            }
        }
    }

    private void insertOrderDetail(Connection conn, int orderId, String menuItem, String notes) throws SQLException {
        // Extract menu ID from display name
        int menuId = getMenuIdFromDisplayName(menuItem);
        int price = getMenuPrice(menuId);

        String insertDetailQuery = """
            INSERT INTO Detail_Pesanan (ID_Pesanan, ID_Menu, Catatan, Subtotal) 
            VALUES (?, ?, ?, ?)
        """;

        try (PreparedStatement stmt = conn.prepareStatement(insertDetailQuery)) {
            stmt.setInt(1, orderId);
            stmt.setInt(2, menuId);
            stmt.setString(3, notes);
            stmt.setInt(4, price);

            stmt.executeUpdate();
        }
    }

    // Get all orders for display
    public Object[][] getAllOrders() {
        List<Object[]> orders = new ArrayList<>();
        String query = """
            SELECT p.ID_Pesanan, p.Nama_Pemesan, p.Nomor_Meja, 
                   p.Metode_Pembayaran,
                   STRING_AGG(m.Nama_Menu, ', ') as Menu_Items,
                   SUM(dp.Subtotal) as Total_Harga
            FROM Pesanan p
            LEFT JOIN Detail_Pesanan dp ON p.ID_Pesanan = dp.ID_Pesanan
            LEFT JOIN Menu m ON dp.ID_Menu = m.ID_Menu
            GROUP BY p.ID_Pesanan, p.Nama_Pemesan, p.Nomor_Meja, p.Metode_Pembayaran
            ORDER BY p.ID_Pesanan DESC
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                Object[] row = {
                    rs.getString("Nama_Pemesan"),
                    "Meja " + rs.getInt("Nomor_Meja"),
                    rs.getString("Menu_Items"),
                    rs.getString("Metode_Pembayaran"),
                    "Rp " + rs.getInt("Total_Harga")
                };
                orders.add(row);
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to fetch orders", e);
        }

        return orders.toArray(new Object[0][]);
    }

    // Delete order (for "Layani" - serve order)
    public boolean deleteOrder(int orderId) {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            // Delete order details first
            String deleteDetailsQuery = "DELETE FROM Detail_Pesanan WHERE ID_Pesanan = ?";
            try (PreparedStatement stmt = conn.prepareStatement(deleteDetailsQuery)) {
                stmt.setInt(1, orderId);
                stmt.executeUpdate();
            }

            // Delete main order
            String deleteOrderQuery = "DELETE FROM Pesanan WHERE ID_Pesanan = ?";
            try (PreparedStatement stmt = conn.prepareStatement(deleteOrderQuery)) {
                stmt.setInt(1, orderId);
                int rowsAffected = stmt.executeUpdate();
                
                if (rowsAffected > 0) {
                    conn.commit();
                    logger.info("Order served/deleted successfully");
                    return true;
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to serve/delete order", e);
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    logger.log(Level.SEVERE, "Failed to rollback transaction", ex);
                }
            }
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException e) {
                    logger.log(Level.SEVERE, "Failed to reset auto-commit", e);
                }
            }
        }

        return false;
    }

    // Delete order by customer details (for serving orders without ID column)
    public boolean deleteOrderByDetails(String customerName, String tableInfo, String menuItems) {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            // Extract table number from table info (e.g., "Meja 1" -> 1)
            int tableNumber = extractTableNumber(tableInfo);

            // Find the order ID based on customer name and table
            String findOrderQuery = """
                SELECT TOP 1 p.ID_Pesanan 
                FROM Pesanan p 
                WHERE p.Nama_Pemesan = ? AND p.Nomor_Meja = ?
                ORDER BY p.ID_Pesanan DESC
            """;
            
            int orderId = -1;
            try (PreparedStatement stmt = conn.prepareStatement(findOrderQuery)) {
                stmt.setString(1, customerName);
                stmt.setInt(2, tableNumber);
                ResultSet rs = stmt.executeQuery();
                
                if (rs.next()) {
                    orderId = rs.getInt("ID_Pesanan");
                } else {
                    logger.warning("Order not found for customer: " + customerName + " at " + tableInfo);
                    return false;
                }
            }

            // Delete order details first
            String deleteDetailsQuery = "DELETE FROM Detail_Pesanan WHERE ID_Pesanan = ?";
            try (PreparedStatement stmt = conn.prepareStatement(deleteDetailsQuery)) {
                stmt.setInt(1, orderId);
                stmt.executeUpdate();
            }

            // Delete main order
            String deleteOrderQuery = "DELETE FROM Pesanan WHERE ID_Pesanan = ?";
            try (PreparedStatement stmt = conn.prepareStatement(deleteOrderQuery)) {
                stmt.setInt(1, orderId);
                int rowsAffected = stmt.executeUpdate();
                
                if (rowsAffected > 0) {
                    conn.commit();
                    logger.info("Order served/deleted successfully for: " + customerName);
                    return true;
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to serve/delete order by details", e);
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    logger.log(Level.SEVERE, "Failed to rollback transaction", ex);
                }
            }
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException e) {
                    logger.log(Level.SEVERE, "Failed to reset auto-commit", e);
                }
            }
        }

        return false;
    }

    // Helper methods
    private int getEmployeeId(String employeeName) throws SQLException {
        String query = "SELECT ID_Pegawai FROM Pegawai WHERE Nama_Pegawai = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, employeeName);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getInt("ID_Pegawai");
            }
        }

        throw new SQLException("Employee not found: " + employeeName);
    }

    private int extractTableNumber(String tableInfo) {
        try {
            // Extract number from "Meja X (Kapasitas: Y)" format
            String[] parts = tableInfo.split(" ");
            if (parts.length >= 2) {
                return Integer.parseInt(parts[1]);
            }
        } catch (NumberFormatException e) {
            logger.log(Level.WARNING, "Failed to parse table number from: " + tableInfo, e);
        }

        return 1; // Default to table 1 if parsing fails
    }

    private int getMenuIdFromDisplayName(String displayName) throws SQLException {
        // Extract menu name from "MenuName - RpPrice" format
        String menuName = displayName.split(" - Rp")[0];
        
        String query = "SELECT ID_Menu FROM Menu WHERE Nama_Menu = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, menuName);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getInt("ID_Menu");
            }
        }

        throw new SQLException("Menu not found: " + menuName);
    }

    private int getMenuPrice(int menuId) throws SQLException {
        String query = "SELECT Harga FROM Menu WHERE ID_Menu = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, menuId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getInt("Harga");
            }
        }

        throw new SQLException("Menu price not found for ID: " + menuId);
    }

    // Initialize sample data
    public void initializeSampleData() {
        try (Connection conn = DatabaseConnection.getConnection()) {
            // First, create tables if they don't exist
            createDatabaseSchema(conn);
            
            // Then insert sample data
            insertSeedEmployees(conn);
            insertSeedMenuItems(conn);
            insertSeedTables(conn);
            
            logger.info("Sample data initialized successfully");
            
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to initialize sample data", e);
        }
    }

    /**
     * Create database schema programmatically
     */
    private void createDatabaseSchema(Connection conn) throws SQLException {
        logger.info("Creating database schema...");
        
        // Create Meja table first (referenced by Pesanan)
        String createMejaTable = """
            IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[Meja]') AND type in (N'U'))
            BEGIN
                CREATE TABLE Meja(
                    Nomor_Meja INT PRIMARY KEY IDENTITY(1,1),
                    Kapasitas INT NOT NULL
                );
            END
        """;
        
        // Create Pegawai table
        String createPegawaiTable = """
            IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[Pegawai]') AND type in (N'U'))
            BEGIN
                CREATE TABLE Pegawai(
                    ID_Pegawai INT PRIMARY KEY IDENTITY(101,1),
                    Nama_Pegawai VARCHAR(50) NOT NULL,
                    Tanggal_Lahir DATE NOT NULL,
                    No_Telpon VARCHAR(20) NOT NULL,
                    Alamat VARCHAR(200) NOT NULL,
                    Gaji INT NOT NULL,
                    Umur AS DATEDIFF(YEAR, Tanggal_Lahir, GETDATE())
                );
            END
        """;
        
        // Create Menu table
        String createMenuTable = """
            IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[Menu]') AND type in (N'U'))
            BEGIN
                CREATE TABLE Menu(
                    ID_Menu INT PRIMARY KEY IDENTITY(200,1),
                    Nama_Menu VARCHAR(50) NOT NULL,
                    Kategori VARCHAR(50) NOT NULL,
                    Harga INT NOT NULL,
                    Deskripsi VARCHAR(100),
                    Status_Menu BIT NOT NULL
                );
            END
        """;
        
        // Create Pesanan table
        String createPesananTable = """
            IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[Pesanan]') AND type in (N'U'))
            BEGIN
                CREATE TABLE Pesanan(
                    ID_Pesanan INT PRIMARY KEY IDENTITY (301, 1),
                    ID_Pegawai INT FOREIGN KEY REFERENCES Pegawai(ID_Pegawai),
                    Nomor_Meja INT FOREIGN KEY REFERENCES Meja(Nomor_Meja),
                    Nama_Pemesan VARCHAR(40) NOT NULL,
                    Tanggal_Pesanan DATE NOT NULL,
                    Metode_Pembayaran VARCHAR(20) NOT NULL
                );
            END
        """;
        
        // Create Detail_Pesanan table
        String createDetailPesananTable = """
            IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[Detail_Pesanan]') AND type in (N'U'))
            BEGIN
                CREATE TABLE Detail_Pesanan(
                    ID_Detail INT PRIMARY KEY IDENTITY(401,1),
                    ID_Pesanan INT FOREIGN KEY REFERENCES Pesanan(ID_Pesanan),
                    ID_Menu INT FOREIGN KEY REFERENCES Menu(ID_Menu),
                    Catatan VARCHAR(100),
                    Subtotal INT NOT NULL
                );
            END
        """;
        
        // Create view for table reservation status
        String createStatusView = """
            IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[StatusReservasiMeja]') AND type in (N'V'))
            BEGIN
                EXEC('CREATE VIEW StatusReservasiMeja AS
                SELECT 
                    m.Nomor_Meja,
                    m.Kapasitas,
                    CASE 
                        WHEN EXISTS (
                            SELECT 1 
                            FROM Pesanan p 
                            WHERE p.Nomor_Meja = m.Nomor_Meja
                        )
                        THEN ''Terpakai''
                        ELSE ''Tersedia''
                    END AS Status_Reservasi
                FROM Meja m');
            END
        """;
        
        // Execute table creation statements
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(createMejaTable);
            logger.info("Meja table created/verified");
            
            stmt.executeUpdate(createPegawaiTable);
            logger.info("Pegawai table created/verified");
            
            stmt.executeUpdate(createMenuTable);
            logger.info("Menu table created/verified");
            
            stmt.executeUpdate(createPesananTable);
            logger.info("Pesanan table created/verified");
            
            stmt.executeUpdate(createDetailPesananTable);
            logger.info("Detail_Pesanan table created/verified");
            
            stmt.executeUpdate(createStatusView);
            logger.info("StatusReservasiMeja view created/verified");
        }
        
        logger.info("Database schema creation completed");
    }

    // Comprehensive seeding functionality
    public boolean seedDatabase() {
        try (Connection conn = DatabaseConnection.getConnection()) {
            logger.info("Starting comprehensive database seeding...");
            
            // First, create tables if they don't exist
            createDatabaseSchema(conn);
            
            // Clear existing data first
            clearAllData(conn);
            
            // Insert fresh seed data
            insertSeedEmployees(conn);
            insertSeedMenuItems(conn);
            insertSeedTables(conn);
            insertSampleOrders(conn);
            
            logger.info("Database seeding completed successfully");
            return true;
            
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to seed database", e);
            return false;
        }
    }

    private void clearAllData(Connection conn) throws SQLException {
        logger.info("Clearing existing data...");
        
        // Check if tables exist before trying to delete from them
        try (Statement stmt = conn.createStatement()) {
            // Delete in correct order due to foreign key constraints
            if (tableExists(conn, "Detail_Pesanan")) {
                stmt.executeUpdate("DELETE FROM Detail_Pesanan");
            }
            if (tableExists(conn, "Pesanan")) {
                stmt.executeUpdate("DELETE FROM Pesanan");
            }
            if (tableExists(conn, "Menu")) {
                stmt.executeUpdate("DELETE FROM Menu");
            }
            if (tableExists(conn, "Pegawai")) {
                stmt.executeUpdate("DELETE FROM Pegawai");
            }
            if (tableExists(conn, "Meja")) {
                stmt.executeUpdate("DELETE FROM Meja");
            }
            
            // Reset identity seeds if tables exist
            if (tableExists(conn, "Meja")) {
                stmt.executeUpdate("DBCC CHECKIDENT ('Meja', RESEED, 0)");
            }
            if (tableExists(conn, "Pegawai")) {
                stmt.executeUpdate("DBCC CHECKIDENT ('Pegawai', RESEED, 100)");
            }
            if (tableExists(conn, "Menu")) {
                stmt.executeUpdate("DBCC CHECKIDENT ('Menu', RESEED, 199)");
            }
            if (tableExists(conn, "Pesanan")) {
                stmt.executeUpdate("DBCC CHECKIDENT ('Pesanan', RESEED, 300)");
            }
            if (tableExists(conn, "Detail_Pesanan")) {
                stmt.executeUpdate("DBCC CHECKIDENT ('Detail_Pesanan', RESEED, 400)");
            }
        }
    }

    /**
     * Check if a table exists in the database
     */
    private boolean tableExists(Connection conn, String tableName) throws SQLException {
        String query = "SELECT COUNT(*) FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[" + tableName + "]') AND type in (N'U')";
        
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        }
        
        return false;
    }

    private void insertSeedEmployees(Connection conn) throws SQLException {
        String query = "INSERT INTO Pegawai (Nama_Pegawai, Tanggal_Lahir, No_Telpon, Alamat, Gaji) VALUES (?, ?, ?, ?, ?)";

        String[][] employees = {
            {"Ari Wibowo", "1990-05-12", "081234567890", "Jl. Melati No.1", "5000000"},
            {"Siti Aminah", "1995-09-20", "081345678901", "Jl. Kenanga No.2", "4500000"},
            {"Rudi Hartono", "1988-12-01", "081456789012", "Jl. Anggrek No.3", "5500000"},
            {"Bagas Pratama", "2000-03-15", "081567890123", "Jl. Mawar No.123", "5000000"},
            {"Siti Rahma", "1995-07-22", "081678901234", "Jl. Melati No.45", "4500000"}
        };

        for (String[] emp : employees) {
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, emp[0]); // Name
                stmt.setDate(2, Date.valueOf(emp[1])); // Birth date
                stmt.setString(3, emp[2]); // Phone
                stmt.setString(4, emp[3]); // Address
                stmt.setInt(5, Integer.parseInt(emp[4])); // Salary
                stmt.executeUpdate();
            }
        }
        logger.info("Inserted seed employees");
    }

    private void insertSeedMenuItems(Connection conn) throws SQLException {
        String query = "INSERT INTO Menu (Nama_Menu, Kategori, Harga, Deskripsi, Status_Menu) VALUES (?, ?, ?, ?, ?)";

        String[][] menuItems = {
            {"Nasi Goreng Spesial", "Makanan", "25000", "Nasi goreng dengan ayam dan telur", "1"},
            {"Es Teh Manis", "Minuman", "8000", "Teh manis dingin", "1"},
            {"Kopi Hitam", "Minuman", "10000", "Kopi hitam tanpa gula", "1"},
            {"Mie Ayam", "Makanan", "20000", "Mie ayam dengan pangsit", "1"},
            {"Espresso", "Minuman", "13000", "Espresso", "1"},
            {"Matcha", "Minuman", "18000", "Matcha dengan susu murni creamy", "1"},
            {"Coffee Latte", "Minuman", "20000", "Espresso dan susu creamy", "1"},
            {"Cappuccino", "Minuman", "22000", "Espresso dan foam", "1"},
            {"Americano", "Minuman", "18000", "Espresso dengan air mineral", "1"},
            {"Pancake", "Makanan", "25000", "Pancake dengan syrup", "1"},
            {"Hot Chocolate", "Minuman", "18000", "Coklat manis hangat", "1"},
            {"Gado-Gado", "Makanan", "22000", "Gado-gado dengan bumbu kacang", "1"},
            {"Sate Ayam", "Makanan", "28000", "Sate ayam dengan bumbu kacang", "1"},
            {"Juice Jeruk", "Minuman", "15000", "Jus jeruk segar", "1"},
            {"Fried Rice", "Makanan", "23000", "Nasi goreng ala western", "1"}
        };

        for (String[] item : menuItems) {
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, item[0]); // Name
                stmt.setString(2, item[1]); // Category
                stmt.setInt(3, Integer.parseInt(item[2])); // Price
                stmt.setString(4, item[3]); // Description
                stmt.setBoolean(5, Boolean.parseBoolean(item[4])); // Status
                stmt.executeUpdate();
            }
        }
        logger.info("Inserted seed menu items");
    }

    private void insertSeedTables(Connection conn) throws SQLException {
        String query = "INSERT INTO Meja (Kapasitas) VALUES (?)";

        int[] capacities = {2, 4, 6, 8, 3, 4, 3, 2, 6, 4};

        for (int capacity : capacities) {
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setInt(1, capacity);
                stmt.executeUpdate();
            }
        }
        logger.info("Inserted seed tables");
    }

    private void insertSampleOrders(Connection conn) throws SQLException {
        // Insert some sample orders for demonstration
        String orderQuery = "INSERT INTO Pesanan (ID_Pegawai, Nomor_Meja, Nama_Pemesan, Tanggal_Pesanan, Metode_Pembayaran) VALUES (?, ?, ?, ?, ?)";
        String detailQuery = "INSERT INTO Detail_Pesanan (ID_Pesanan, ID_Menu, Catatan, Subtotal) VALUES (?, ?, ?, ?)";

        // Sample order 1
        try (PreparedStatement stmt = conn.prepareStatement(orderQuery, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, 101); // First employee
            stmt.setInt(2, 1); // Table 1
            stmt.setString(3, "John Doe");
            stmt.setDate(4, new Date(System.currentTimeMillis()));
            stmt.setString(5, "Cash");
            stmt.executeUpdate();

            ResultSet rs = stmt.getGeneratedKeys();
            if (rs.next()) {
                int orderId = rs.getInt(1);
                
                // Add order details
                try (PreparedStatement detailStmt = conn.prepareStatement(detailQuery)) {
                    detailStmt.setInt(1, orderId);
                    detailStmt.setInt(2, 200); // First menu item
                    detailStmt.setString(3, "Extra pedas");
                    detailStmt.setInt(4, 25000);
                    detailStmt.executeUpdate();

                    detailStmt.setInt(1, orderId);
                    detailStmt.setInt(2, 201); // Second menu item
                    detailStmt.setString(3, "");
                    detailStmt.setInt(4, 8000);
                    detailStmt.executeUpdate();
                }
            }
        }

        // Sample order 2
        try (PreparedStatement stmt = conn.prepareStatement(orderQuery, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, 102); // Second employee
            stmt.setInt(2, 3); // Table 3
            stmt.setString(3, "Jane Smith");
            stmt.setDate(4, new Date(System.currentTimeMillis()));
            stmt.setString(5, "Credit Card");
            stmt.executeUpdate();

            ResultSet rs = stmt.getGeneratedKeys();
            if (rs.next()) {
                int orderId = rs.getInt(1);
                
                // Add order details
                try (PreparedStatement detailStmt = conn.prepareStatement(detailQuery)) {
                    detailStmt.setInt(1, orderId);
                    detailStmt.setInt(2, 206); // Coffee Latte
                    detailStmt.setString(3, "Extra shot");
                    detailStmt.setInt(4, 20000);
                    detailStmt.executeUpdate();

                    detailStmt.setInt(1, orderId);
                    detailStmt.setInt(2, 209); // Pancake
                    detailStmt.setString(3, "");
                    detailStmt.setInt(4, 25000);
                    detailStmt.executeUpdate();
                }
            }
        }

        logger.info("Inserted sample orders");
    }

    /**
     * Safely clear all data from database
     */
    public boolean clearDatabaseSafely() {
        try (Connection conn = DatabaseConnection.getConnection()) {
            clearAllData(conn);
            logger.info("Database cleared successfully");
            return true;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to clear database", e);
            return false;
        }
    }
}
