package com.example.inventorymanagement;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Scanner;

@Component
public class InventoryJdbcRunner implements CommandLineRunner {

    @Value("${spring.datasource.url}")
    private String url;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    @Override
    public void run(String... args) {
        try (Connection con = DriverManager.getConnection(url, username, password);
             Scanner sc = new Scanner(System.in)) {

            System.out.println("MySQL Connected Successfully!");

            while (true) {
                System.out.println("\n===== INVENTORY MANAGEMENT =====");
                System.out.println("1. Insert Product");
                System.out.println("2. Show Low-Stock Products by Category");
                System.out.println("3. Add Stock");
                System.out.println("4. Search Product and Update Price");
                System.out.println("5. Total Inventory Value");
                System.out.println("6. Delete Product if Quantity is Zero");
                System.out.println("7. Exit");
                System.out.print("Enter choice: ");

                String choiceInput = sc.nextLine().trim();
                int choice;
                try {
                    choice = Integer.parseInt(choiceInput);
                } catch (NumberFormatException e) {
                    System.out.println("Please enter a number from 1 to 7.");
                    continue;
                }

                try {
                    switch (choice) {
                        case 1 -> insertProduct(con, sc);
                        case 2 -> showLowStock(con, sc);
                        case 3 -> addStock(con, sc);
                        case 4 -> updatePrice(con, sc);
                        case 5 -> showInventoryValue(con);
                        case 6 -> deleteProduct(con, sc);
                        case 7 -> {
                            System.out.println("Exiting Inventory Management.");
                            return;
                        }
                        default -> System.out.println("Invalid choice. Enter 1-7.");
                    }
                } catch (NumberFormatException e) {
                    System.out.println("Invalid numeric input. Please try the operation again.");
                } catch (SQLException e) {
                    System.out.println("Database operation failed: " + e.getMessage());
                }
            }
        } catch (SQLException e) {
            System.out.println("Database connection failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private int readInt(Scanner sc, String prompt) {
        System.out.print(prompt);
        return Integer.parseInt(sc.nextLine().trim());
    }

    private double readDouble(Scanner sc, String prompt) {
        System.out.print(prompt);
        return Double.parseDouble(sc.nextLine().trim());
    }

    private void insertProduct(Connection con, Scanner sc) throws SQLException {
        int id = readInt(sc, "Product ID: ");

        System.out.print("Product Name: ");
        String name = sc.nextLine().trim();

        System.out.print("Category: ");
        String category = sc.nextLine().trim();

        int quantity = readInt(sc, "Quantity: ");
        double price = readDouble(sc, "Price: ");

        if (quantity < 0 || price < 0) {
            System.out.println("Quantity and price cannot be negative.");
            return;
        }

        String sql = """
                INSERT INTO Product (product_id, product_name, category, quantity, price)
                VALUES (?, ?, ?, ?, ?)
                """;

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setString(2, name);
            ps.setString(3, category);
            ps.setInt(4, quantity);
            ps.setDouble(5, price);
            ps.executeUpdate();
            System.out.println("Product inserted successfully!");
        } catch (SQLIntegrityConstraintViolationException e) {
            System.out.println("Duplicate Product ID! Please use a different ID.");
        }
    }

    private void showLowStock(Connection con, Scanner sc) throws SQLException {
        System.out.print("Enter category: ");
        String category = sc.nextLine().trim();

        String sql = """
                SELECT product_id, product_name, category, quantity, price
                FROM Product
                WHERE category = ? AND quantity < 20
                ORDER BY price DESC
                """;

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, category);
            try (ResultSet rs = ps.executeQuery()) {
                boolean found = false;
                while (rs.next()) {
                    found = true;
                    printProduct(rs);
                }
                if (!found) {
                    System.out.println("No matching low-stock products found.");
                }
            }
        }
    }

    private void addStock(Connection con, Scanner sc) throws SQLException {
        int id = readInt(sc, "Enter Product ID: ");
        int amount = readInt(sc, "Quantity to add: ");

        if (amount <= 0) {
            System.out.println("Quantity to add must be greater than zero.");
            return;
        }

        String sql = "UPDATE Product SET quantity = quantity + ? WHERE product_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, amount);
            ps.setInt(2, id);
            int rows = ps.executeUpdate();
            System.out.println(rows > 0 ? "Stock updated successfully!" : "Product ID not found.");
        }
    }

    private void updatePrice(Connection con, Scanner sc) throws SQLException {
        int id = readInt(sc, "Enter Product ID: ");
        String searchSql = "SELECT * FROM Product WHERE product_id = ?";

        try (PreparedStatement ps = con.prepareStatement(searchSql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    System.out.println("Product not found.");
                    return;
                }
                System.out.println("Current product details:");
                printProduct(rs);
            }
        }

        double newPrice = readDouble(sc, "Enter new price: ");
        if (newPrice <= 0) {
            System.out.println("Price must be greater than zero.");
            return;
        }

        String updateSql = "UPDATE Product SET price = ? WHERE product_id = ?";
        try (PreparedStatement ps = con.prepareStatement(updateSql)) {
            ps.setDouble(1, newPrice);
            ps.setInt(2, id);
            ps.executeUpdate();
        }

        System.out.println("Updated product details:");
        try (PreparedStatement ps = con.prepareStatement(searchSql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    printProduct(rs);
                }
            }
        }
    }

    private void showInventoryValue(Connection con) throws SQLException {
        String sql = "SELECT COALESCE(SUM(quantity * price), 0) AS total_value FROM Product";
        try (PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                System.out.printf("Total Inventory Value: %.2f%n", rs.getDouble("total_value"));
            }
        }
    }

    private void deleteProduct(Connection con, Scanner sc) throws SQLException {
        int id = readInt(sc, "Enter Product ID to delete: ");
        String sql = "DELETE FROM Product WHERE product_id = ? AND quantity = 0";

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            int rows = ps.executeUpdate();
            System.out.println(rows > 0
                    ? "Product deleted successfully!"
                    : "Product not found or quantity is not zero.");
        }
    }

    private void printProduct(ResultSet rs) throws SQLException {
        System.out.printf(
                "ID: %d | Name: %s | Category: %s | Quantity: %d | Price: %.2f%n",
                rs.getInt("product_id"),
                rs.getString("product_name"),
                rs.getString("category"),
                rs.getInt("quantity"),
                rs.getDouble("price")
        );
    }
}

