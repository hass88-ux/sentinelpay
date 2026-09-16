import java.sql.DriverManager;
import java.sql.SQLException;

/** Read-only connection probe. Credentials are inherited from the setup wrapper, never command arguments. */
class VerifySupabase {
    public static void main(String[] args) {
        DriverManager.setLoginTimeout(15);
        try (var connection = DriverManager.getConnection(System.getenv("SENTINELPAY_VERIFY_URL"),
                System.getenv("SENTINELPAY_VERIFY_USER"), System.getenv("SENTINELPAY_VERIFY_PASSWORD"));
                var statement = connection.createStatement()) {
            connection.setReadOnly(true);
            statement.setQueryTimeout(10);
            try (var result = statement.executeQuery("SELECT 1")) {
                if (!result.next() || result.getInt(1) != 1) throw new SQLException("Unexpected result");
            }
            System.out.println("Supabase database connection verified. No database data was changed.");
        } catch (SQLException error) {
            // Provider exception messages may include connection details. Only print a safe diagnosis.
            boolean certificate = false;
            for (Throwable cause = error; cause != null; cause = cause.getCause()) {
                if (cause instanceof javax.net.ssl.SSLException
                        || cause instanceof java.security.cert.CertificateException) certificate = true;
            }
            System.err.println(certificate
                    ? "Certificate verification failed. Download the database CA from this project's SSL Configuration."
                    : "Database connection failed. Check the pooler hostname, database password, and project availability.");
            System.exit(1);
        }
    }
}
