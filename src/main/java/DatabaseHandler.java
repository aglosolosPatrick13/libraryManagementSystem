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
        // Updated to include 'genre' column
        String sql = "CREATE TABLE IF NOT EXISTS books ("
                + "id INTEGER PRIMARY KEY,"
                + "name TEXT NOT NULL,"
                + "author TEXT,"
                + "genre TEXT," // Added Genre column
                + "year INTEGER,"
                + "status TEXT DEFAULT 'Available',"
                + "borrower_name TEXT,"
                + "program TEXT,"
                + "borrow_date TEXT);";
        
        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            System.out.println("DB Init Error: " + e.getMessage());
        }
    }

    // --- NEW FILTERING METHODS FOR YOUR PAGES ---

    /**
     * For Borrow Page: Filters by Status = 'Available'
     */
    public static void loadAvailableBooks(JTable table, String keyword) {
        String sql = "SELECT * FROM books WHERE status = 'Available' AND (name LIKE ? OR author LIKE ? OR id LIKE ?)";
        loadFilteredTable(table, sql, keyword);
    }

    /**
     * For Return Page: Filters by Status = 'Borrowed'
     */
    public static void loadBorrowedBooks(JTable table, String keyword) {
        String sql = "SELECT * FROM books WHERE status = 'Borrowed' AND (name LIKE ? OR author LIKE ? OR id LIKE ?)";
        loadFilteredTable(table, sql, keyword);
    }

    /**
     * Helper method to handle the table loading logic for filtered views
     */
    private static void loadFilteredTable(JTable table, String sql, String keyword) {
        String[] columns = {"Book ID", "Book Name", "Author", "Year", "Status"};
        DefaultTableModel model = new DefaultTableModel(columns, 0);

        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, "%" + keyword + "%");
            pstmt.setString(2, "%" + keyword + "%");
            pstmt.setString(3, "%" + keyword + "%");
            
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                model.addRow(new Object[]{
                    rs.getInt("id"),
                    rs.getString("name"),
                    rs.getString("author"),
                    rs.getInt("year"),
                    rs.getString("status")
                });
            }
            table.setModel(model);
        } catch (SQLException e) {
            System.out.println("Filter Error: " + e.getMessage());
        }
    }

    // --- UPDATED ADD BOOK (Includes Genre) ---

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

    // --- HEAPSORT CORE LOGIC ---
    
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

    public static void loadSortedTable(JTable table, int sortColumnIndex) {
        String sql = "SELECT * FROM books";
        String[] columns = {"Book ID", "Book Name", "Author", "Year", "Status"};
        try (Connection conn = connect(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            List<Object[]> dataList = new ArrayList<>();
            while (rs.next()) {
                dataList.add(new Object[]{rs.getInt("id"), rs.getString("name"), rs.getString("author"), rs.getInt("year"), rs.getString("status")});
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
}