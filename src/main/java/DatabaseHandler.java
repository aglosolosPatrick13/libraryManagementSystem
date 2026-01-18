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

    /**
     * Prints the absolute path of the database to the console 
     * so you can find the exact file to delete if needed.
     */
    public static void printDatabasePath() {
        java.io.File file = new java.io.File("library_db.db");
        System.out.println("DATABASE LOCATION: " + file.getAbsolutePath());
    }

    public static void initializeDatabase() {
        String sql = "CREATE TABLE IF NOT EXISTS books ("
                + "id INTEGER PRIMARY KEY,"
                + "name TEXT NOT NULL,"
                + "author TEXT,"
                + "genre TEXT," 
                + "year INTEGER,"
                + "status TEXT DEFAULT 'Available',"
                + "borrower_name TEXT,"
                + "program TEXT,"
                + "borrow_date TEXT);";
        
        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            
            // FORCED COLUMN ADDITION: This ensures the 'genre' column exists 
            // even if the file was created by an older version of your code.
            try {
                stmt.execute("ALTER TABLE books ADD COLUMN genre TEXT;");
                System.out.println("Database updated: Genre column added.");
            } catch (SQLException e) {
                // Error is ignored because it means the column already exists
            }
        } catch (SQLException e) {
            System.out.println("DB Init Error: " + e.getMessage());
        }
    }

    private static void loadFilteredTable(JTable table, String sql, String keyword) {
        // FIXED: Added "Genre" to the header array (Total 6 columns)
        String[] columns = {"Book ID", "Book Name", "Author", "Genre", "Year", "Status"};
        DefaultTableModel model = new DefaultTableModel(columns, 0);

        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, "%" + keyword + "%");
            pstmt.setString(2, "%" + keyword + "%");
            pstmt.setString(3, "%" + keyword + "%");
            
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                // FIXED: Row data now matches the 6 headers above
                model.addRow(new Object[]{
                    rs.getInt("id"),
                    rs.getString("name"),
                    rs.getString("author"),
                    rs.getString("genre"), 
                    rs.getInt("year"),
                    rs.getString("status")
                });
            }
            table.setModel(model);
        } catch (SQLException e) {
            System.out.println("Filter Error: " + e.getMessage());
        }
    }

    public static void loadAvailableBooks(JTable table, String keyword) {
        String sql = "SELECT * FROM books WHERE status = 'Available' AND (name LIKE ? OR author LIKE ? OR id LIKE ?)";
        loadFilteredTable(table, sql, keyword);
    }

    public static void loadBorrowedBooks(JTable table, String keyword) {
        String sql = "SELECT * FROM books WHERE status = 'Borrowed' AND (name LIKE ? OR author LIKE ? OR id LIKE ?)";
        loadFilteredTable(table, sql, keyword);
    }

    public static void addBook(int id, String name, String author, String genre, int year) {
        String sql = "INSERT INTO books(id, name, author, genre, year, status) VALUES(?,?,?,?,?,'Available')";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
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
        // FIXED: Added "Genre" to header array
        String[] columns = {"Book ID", "Book Name", "Author", "Genre", "Year", "Status"};
        try (Connection conn = connect(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            List<Object[]> dataList = new ArrayList<>();
            while (rs.next()) {
                dataList.add(new Object[]{
                    rs.getInt("id"), 
                    rs.getString("name"), 
                    rs.getString("author"), 
                    rs.getString("genre"), 
                    rs.getInt("year"), 
                    rs.getString("status")
                });
            }
            Object[][] data = dataList.toArray(new Object[0][]);
            if (data.length > 0) heapSort(data, sortColumnIndex);
            table.setModel(new DefaultTableModel(data, columns));
        } catch (SQLException e) {
            System.out.println("Sort Error: " + e.getMessage());
        }
    }

    public static void searchAndLoadTable(JTable table, String keyword) {
        String sql = "SELECT * FROM books WHERE name LIKE ? OR author LIKE ? OR id LIKE ?";
        loadFilteredTable(table, sql, keyword);
    }

    public static void borrowBook(int bookId, String bName, String program, String bDate) {
        String sql = "UPDATE books SET status = 'Borrowed', borrower_name = ?, program = ?, borrow_date = ? WHERE id = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, bName);
            pstmt.setString(2, program);
            pstmt.setString(3, bDate);
            pstmt.setInt(4, bookId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Borrow Error: " + e.getMessage());
        }
    }

    public static void returnBook(int bookId) {
        String sql = "UPDATE books SET status = 'Available', borrower_name = NULL, program = NULL, borrow_date = NULL WHERE id = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, bookId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Return Error: " + e.getMessage());
        }
    }

    public static void removeBook(int bookId) {
        String sql = "DELETE FROM books WHERE id = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, bookId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Remove Error: " + e.getMessage());
        }
    }

    // --- HEAPSORT LOGIC ---
    public static void heapSort(Object[][] data, int column) {
        int n = data.length;
        for (int i = n / 2 - 1; i >= 0; i--) heapify(data, n, i, column);
        for (int i = n - 1; i > 0; i--) {
            Object[] temp = data[0];
            data[0] = data[i];
            data[i] = temp;
            heapify(data, i, 0, column);
        }
    }

    private static void heapify(Object[][] data, int n, int i, int column) {
        int largest = i;
        int l = 2 * i + 1;
        int r = 2 * i + 2;
        if (l < n && compare(data[l][column], data[largest][column]) > 0) largest = l;
        if (r < n && compare(data[r][column], data[largest][column]) > 0) largest = r;
        if (largest != i) {
            Object[] swap = data[i];
            data[i] = data[largest];
            data[largest] = swap;
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