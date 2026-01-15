import java.sql.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.JTable;

public class DatabaseHandler {
    private static final String URL = "jdbc:sqlite:library_db.db";

    public static Connection connect() throws SQLException {
        return DriverManager.getConnection(URL);
    }

    public static void initializeDatabase() {
        // Updated table to store borrower info and date
        String sql = "CREATE TABLE IF NOT EXISTS books ("
                + "id INTEGER PRIMARY KEY,"
                + "name TEXT NOT NULL,"
                + "author TEXT,"
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

    public static void addBook(int id, String name, String author, int year) {
        String sql = "INSERT INTO books(id, name, author, year, status) VALUES(?,?,?,?,'Available')";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            pstmt.setString(2, name);
            pstmt.setString(3, author);
            pstmt.setInt(4, year);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Add Error: " + e.getMessage());
        }
    }

    public static void searchAndLoadTable(JTable table, String keyword) {
        String sql = "SELECT * FROM books WHERE name LIKE ? OR author LIKE ? OR id LIKE ?";
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
            System.out.println("Search Error: " + e.getMessage());
        }
    }

    // NEW BORROW LOGIC: Saves Name, Program, and Date
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

    // NEW RETURN LOGIC: Clears borrower info
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