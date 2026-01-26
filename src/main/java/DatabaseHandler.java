import java.sql.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.JTable;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class DatabaseHandler {
    private static final String URL = "jdbc:sqlite:library_db.db";

    public static Connection connect() throws SQLException {
        return DriverManager.getConnection(URL);
    }

    public static void initializeDatabase() {
        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS users ("
                    + "u_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "username TEXT UNIQUE,"
                    + "password TEXT,"
                    + "program TEXT);"); 

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
                    + "due_date TEXT,"
                    + "borrower_id INTEGER);");
            
            // Migration handling (in case columns were missing from previous versions)
            try { stmt.execute("ALTER TABLE books ADD COLUMN borrower_id INTEGER;"); } catch (SQLException e) {}
            try { stmt.execute("ALTER TABLE books RENAME COLUMN year TO year_published;"); } catch (SQLException e) {}
            try { stmt.execute("ALTER TABLE users ADD COLUMN program TEXT;"); } catch (SQLException e) {}
            try { stmt.execute("ALTER TABLE books ADD COLUMN due_date TEXT;"); } catch (SQLException e) {}
            
        } catch (SQLException e) {
            System.out.println("DB Init Error: " + e.getMessage());
        }
    }

    private static void loadFilteredTable(JTable table, String sql, String keyword, boolean isPersonal) {
        String[] columns = {"Book ID", "Book Name", "Author", "Genre", "Year Published", "Status", "Days Remaining"};
        DefaultTableModel model = new DefaultTableModel(columns, 0);
        
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            int paramIndex = 1;
            if (isPersonal) { pstmt.setInt(paramIndex++, userSession.currentUserId); }
            
            String searchPattern = "%" + keyword + "%";
            int placeholders = (int) sql.chars().filter(ch -> ch == '?').count();
            int searchParams = isPersonal ? (placeholders - 1) : placeholders;
            
            for (int i = 0; i < searchParams; i++) { pstmt.setString(paramIndex++, searchPattern); }
            
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                String dueDateStr = rs.getString("due_date");
                String daysDisplay = "N/A"; 

                if (dueDateStr != null && !dueDateStr.isEmpty()) {
                    try {
                        LocalDate today = LocalDate.now();
                        LocalDate dueDate = LocalDate.parse(dueDateStr); // Expects yyyy-MM-dd
                        long diff = ChronoUnit.DAYS.between(today, dueDate);
                        
                        if (diff < 0) {
                            daysDisplay = "OVERDUE (" + Math.abs(diff) + " days)";
                        } else if (diff == 0) {
                            daysDisplay = "DUE TODAY";
                        } else {
                            daysDisplay = diff + " days left";
                        }
                    } catch (Exception e) {
                        daysDisplay = dueDateStr; 
                    }
                }

                model.addRow(new Object[]{
                    rs.getString("id"), rs.getString("name"), rs.getString("author"),
                    rs.getString("genre"), rs.getInt("year_published"), rs.getString("status"),
                    daysDisplay 
                });
            }
            table.setModel(model);
        } catch (SQLException e) { System.out.println("Filter Error: " + e.getMessage()); }
    }

    public static void searchAndLoadTable(JTable table, String keyword) {
        String sql = "SELECT * FROM books WHERE (name LIKE ? OR author LIKE ? OR id LIKE ? OR genre LIKE ? OR CAST(year_published AS TEXT) LIKE ?)";
        loadFilteredTable(table, sql, keyword, false);
    }

    public static void loadAvailableBooks(JTable table, String keyword) {
        String sql = "SELECT * FROM books WHERE status = 'Available' AND (name LIKE ? OR author LIKE ? OR id LIKE ? OR genre LIKE ? OR CAST(year_published AS TEXT) LIKE ?)";
        loadFilteredTable(table, sql, keyword, false);
    }

    public static void loadBorrowedBooks(JTable table, String keyword) {
        String sql = "SELECT * FROM books WHERE status = 'Borrowed' AND borrower_id = ? AND (name LIKE ? OR author LIKE ? OR id LIKE ? OR genre LIKE ? OR CAST(year_published AS TEXT) LIKE ?)";
        loadFilteredTable(table, sql, keyword, true);
    }

    public static void loadSortedTable(JTable table, int sortColumnIndex) {
        String[] columns = {"Book ID", "Book Name", "Author", "Genre", "Year Published", "Status", "Days Remaining"};
        String sql = "SELECT * FROM books WHERE status = 'Available'";
        
        try (Connection conn = connect(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            List<Object[]> dataList = new ArrayList<>();
            while (rs.next()) {
                dataList.add(new Object[]{
                    rs.getString("id"), 
                    rs.getString("name"), 
                    rs.getString("author"), 
                    rs.getString("genre"), 
                    rs.getInt("year_published"), 
                    rs.getString("status"),
                    "Available" 
                });
            }
            
            Object[][] data = dataList.toArray(new Object[0][]);
            if (data.length > 0) heapSort(data, sortColumnIndex);
            table.setModel(new DefaultTableModel(data, columns));
        } catch (SQLException e) { 
            System.out.println("Table Error: " + e.getMessage()); 
        }
    }
    
    public static void addBook(String id, String name, String author, String genre, int year) {
        String sql = "INSERT INTO books(id, name, author, genre, year_published, status) VALUES(?,?,?,?,?,'Available')";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, id); pstmt.setString(2, name); pstmt.setString(3, author);
            pstmt.setString(4, genre); pstmt.setInt(5, year);
            pstmt.executeUpdate();
        } catch (SQLException e) { System.out.println("Add Error: " + e.getMessage()); }
    }

    public static void borrowBook(String bookId, String bName, String program, String bDate, String dDate) {
        String sql = "UPDATE books SET status = 'Borrowed', borrower_name = ?, program = ?, borrow_date = ?, due_date = ?, borrower_id = ? WHERE id = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, bName);
            pstmt.setString(2, program);
            pstmt.setString(3, bDate);
            pstmt.setString(4, dDate);
            pstmt.setInt(5, userSession.currentUserId);
            pstmt.setString(6, bookId);
            pstmt.executeUpdate();
        } catch (SQLException e) { System.out.println("Borrow Error: " + e.getMessage()); }
    }

    public static void returnBook(String bookId) {
        String sql = "UPDATE books SET status = 'Available', borrower_name = NULL, program = NULL, borrow_date = NULL, due_date = NULL, borrower_id = NULL WHERE id = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, bookId); pstmt.executeUpdate();
        } catch (SQLException e) { System.out.println("Return Error: " + e.getMessage()); }
    }

    public static void removeBook(String bookId) {
        String sql = "DELETE FROM books WHERE id = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, bookId); pstmt.executeUpdate();
        } catch (SQLException e) { System.out.println("Remove Error: " + e.getMessage()); }
    }


    public static boolean registerUser(String username, String password, String program) {
        String sql = "INSERT INTO users(username, password, program) VALUES(?, ?, ?)";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, password);
            pstmt.setString(3, program);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) { return false; }
    }

    public static boolean verifyLogin(String user, String pass) {
        String sql = "SELECT * FROM users WHERE username = ? AND password = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, user);
            pstmt.setString(2, pass);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                userSession.currentUserId = rs.getInt("u_id");
                userSession.currentUsername = rs.getString("username");
                userSession.currentUserProgram = rs.getString("program");
                return true;
            }
        } catch (SQLException e) { System.out.println("Login Error: " + e.getMessage()); }
        return false;
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
}