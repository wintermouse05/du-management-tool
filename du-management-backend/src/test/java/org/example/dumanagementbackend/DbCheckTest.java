package org.example.dumanagementbackend;

import org.junit.jupiter.api.Test;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class DbCheckTest {

    @Test
    public void test() {
        try {
            Connection conn = DriverManager.getConnection("jdbc:postgresql://localhost:5432/du_management", "postgres", "postgres");
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT column_name, data_type, is_nullable FROM information_schema.columns WHERE table_name = 'menu_items'");
            System.out.println("========== MENU_ITEMS COLUMNS ==========");
            while (rs.next()) {
                System.out.println(rs.getString(1) + " | " + rs.getString(2) + " | " + rs.getString(3));
            }
            System.out.println("========================================");
            
            ResultSet rs2 = stmt.executeQuery("SELECT COUNT(*) FROM menu_items");
            if (rs2.next()) {
                System.out.println("Menu items count: " + rs2.getInt(1));
            }
            conn.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
