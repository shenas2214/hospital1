package hospital.service;

import hospital.Database;
import hospital.model.Patient;

import java.sql.*;

public class PatientService {

    public Patient register(String name, String phone) throws SQLException {
        String sql = "INSERT INTO patients(name, phone) VALUES (?, ?)";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, phone);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return new Patient(rs.getInt(1), name, phone);
            }
        }
    }
}
