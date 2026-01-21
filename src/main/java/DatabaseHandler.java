import java.sql.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.JTable;
import java.util.ArrayList;
import java.util.List;

public class DatabaseHandler {
    private static final String URL = "jdbc:sqlite:library_db.db";

    public static Connection connect() throws SQLException {
        return DriverManager.getConnection(URL);
    }

    public static void initializeDatabase() {
        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            // 1. Users Table
            stmt.execute("CREATE TABLE IF NOT EXISTS users ("
                    + "u_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "username TEXT UNIQUE,"
                    + "password TEXT);");

            // 2. Books Table - Note 'year_published' is the official name
            stmt.execute("CREATE TABLE IF NOT EXISTS books ("
                    + "id TEXT PRIMARY KEY," 
                    + "name TEXT NOT NULL,"
                    + "author TEXT,"
                    + "genre TEXT," 
                    + "year_published INTEGER," 
                    + "status TEXT DEFAULT 'Available',"
                    + "borrower_name TEXT,"
                    + "program TEXT,"
                    + "borrow_date TEXT,"
                    + "borrower_id INTEGER);");
            
            // Safety: Try to migrate old columns if they exist
            try { stmt.execute("ALTER TABLE books ADD COLUMN borrower_id INTEGER;"); } catch (SQLException e) {}
            try { stmt.execute("ALTER TABLE books RENAME COLUMN year TO year_published;"); } catch (SQLException e) {}
            
        } catch (SQLException e) {
            System.out.println("DB Init Error: " + e.getMessage());
        }
    }

    // --- Helper for Loading Tables ---
    private static void loadFilteredTable(JTable table, String sql, String keyword, boolean isPersonal) {
        String[] columns = {"Book ID", "Book Name", "Author", "Genre", "Year Published", "Status"};
        DefaultTableModel model = new DefaultTableModel(columns, 0);

        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            int paramIndex = 1;
            if (isPersonal) {
                // Ensure UserSession is accessible
                pstmt.setInt(paramIndex++, userSession.currentUserId);
            }
            
            // Count how many '?' placeholders are in the SQL string
            int placeholders = (int) sql.chars().filter(ch -> ch == '?').count();
            int searchParams = isPersonal ? (placeholders - 1) : placeholders;

            String searchPattern = "%" + keyword + "%";
            for (int i = 0; i < searchParams; i++) {
                pstmt.setString(paramIndex++, searchPattern);
            }
            
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                model.addRow(new Object[]{
                    rs.getString("id"),
                    rs.getString("name"),
                    rs.getString("author"),
                    rs.getString("genre"), 
                    rs.getInt("year_published"), // Use 'year_published'
                    rs.getString("status")
                });
            }
            table.setModel(model);
        } catch (SQLException e) {
            System.out.println("Filter Error: " + e.getMessage());
        }
    }

    // --- Public Methods for UI ---

    public static void searchAndLoadTable(JTable table, String keyword) {
        // Updated to search through ALL columns including genre and year_published
        String sql = "SELECT * FROM books WHERE (name LIKE ? OR author LIKE ? OR id LIKE ? OR genre LIKE ? OR year_published LIKE ?)";
        loadFilteredTable(table, sql, keyword, false);
    }

    public static void loadAvailableBooks(JTable table, String keyword) {
        String sql = "SELECT * FROM books WHERE status = 'Available' AND (name LIKE ? OR author LIKE ? OR id LIKE ? OR genre LIKE ? OR year_published LIKE ?)";
        loadFilteredTable(table, sql, keyword, false);
    }

    public static void loadBorrowedBooks(JTable table, String keyword) {
        String sql = "SELECT * FROM books WHERE status = 'Borrowed' AND borrower_id = ? AND (name LIKE ? OR author LIKE ? OR id LIKE ? OR genre LIKE ? OR year_published LIKE ?)";
        loadFilteredTable(table, sql, keyword, true);
    }

    public static void addBook(String id, String name, String author, String genre, int year) {
        String sql = "INSERT INTO books(id, name, author, genre, year_published, status) VALUES(?,?,?,?,?,'Available')";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, id);
            pstmt.setString(2, name);
            pstmt.setString(3, author);
            pstmt.setString(4, genre);
            pstmt.setInt(5, year);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Add Error: " + e.getMessage());
        }
    }

    public static void loadSortedTable(JTable table, int sortColumnIndex) {
        String sql = "SELECT * FROM books";
        String[] columns = {"Book ID", "Book Name", "Author", "Genre", "Year Published", "Status"};
        try (Connection conn = connect(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            List<Object[]> dataList = new ArrayList<>();
            while (rs.next()) {
                dataList.add(new Object[]{
                    rs.getString("id"), rs.getString("name"), rs.getString("author"), 
                    rs.getString("genre"), rs.getInt("year_published"), rs.getString("status")
                });
            }
            Object[][] data = dataList.toArray(new Object[0][]);
            if (data.length > 0) heapSort(data, sortColumnIndex);
            table.setModel(new DefaultTableModel(data, columns));
        } catch (SQLException e) {
            System.out.println("Sort Error: " + e.getMessage());
        }
    }
    
    // ... (borrowBook, returnBook, removeBook, and register/login logic from before)
    
    public static void borrowBook(String bookId, String bName, String program, String bDate) {
        String sql = "UPDATE books SET status = 'Borrowed', borrower_name = ?, program = ?, borrow_date = ?, borrower_id = ? WHERE id = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, bName);
            pstmt.setString(2, program);
            pstmt.setString(3, bDate);
            pstmt.setInt(4, userSession.currentUserId);
            pstmt.setString(5, bookId);
            pstmt.executeUpdate();
        } catch (SQLException e) { System.out.println("Borrow Error: " + e.getMessage()); }
    }

    public static void returnBook(String bookId) {
        String sql = "UPDATE books SET status = 'Available', borrower_name = NULL, program = NULL, borrow_date = NULL, borrower_id = NULL WHERE id = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, bookId);
            pstmt.executeUpdate();
        } catch (SQLException e) { System.out.println("Return Error: " + e.getMessage()); }
    }

    public static void removeBook(String bookId) {
        String sql = "DELETE FROM books WHERE id = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, bookId);
            pstmt.executeUpdate();
        } catch (SQLException e) { System.out.println("Remove Error: " + e.getMessage()); }
    }

    public static void heapSort(Object[][] data, int column) {
        int n = data.length;
        for (int i = n / 2 - 1; i >= 0; i--) heapify(data, n, i, column);
        for (int i = n - 1; i > 0; i--) {
            Object[] temp = data[0]; data[0] = data[i]; data[i] = temp;
            heapify(data, i, 0, column);
        }
    }

    private static void heapify(Object[][] data, int n, int i, int column) {
        int largest = i, l = 2 * i + 1, r = 2 * i + 2;
        if (l < n && compare(data[l][column], data[largest][column]) > 0) largest = l;
        if (r < n && compare(data[r][column], data[largest][column]) > 0) largest = r;
        if (largest != i) {
            Object[] swap = data[i]; data[i] = data[largest]; data[largest] = swap;
            heapify(data, n, largest, column);
        }
    }

    private static int compare(Object o1, Object o2) {
        if (o1 == null) return -1;
        if (o2 == null) return 1;
        if (o1 instanceof Integer && o2 instanceof Integer) return ((Integer) o1).compareTo((Integer) o2);
        return o1.toString().compareToIgnoreCase(o2.toString());
    }

    public static boolean registerUser(String username, String password) {
        String sql = "INSERT INTO users(username, password) VALUES(?, ?)";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username); pstmt.setString(2, password);
            pstmt.executeUpdate(); return true;
        } catch (SQLException e) { return false; }
    }

    public static boolean verifyLogin(String user, String pass) {
        String sql = "SELECT u_id FROM users WHERE username = ? AND password = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, user); pstmt.setString(2, pass);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                userSession.currentUserId = rs.getInt("u_id");
                userSession.currentUsername = user;
                return true;
            }
        } catch (SQLException e) { System.out.println("Login Error: " + e.getMessage()); }
        return false;
    }
}