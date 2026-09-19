import java.sql.*;
import org.flywaydb.core.Flyway;

/** Run locally with the admin connection; the deployed app never needs migration privileges. */
class ProvisionUploadRuntime {
    public static void main(String[] args) throws Exception {
        String url=System.getenv("SENTINELPAY_VERIFY_URL"), user=System.getenv("SENTINELPAY_VERIFY_USER"), password=System.getenv("SENTINELPAY_VERIFY_PASSWORD");
        String runtimePassword=System.getenv("SENTINELPAY_RUNTIME_PASSWORD");
        if(runtimePassword==null||!runtimePassword.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Invalid generated credential");
        Flyway.configure().dataSource(url,user,password).locations("filesystem:backend/src/main/resources/db/uploads")
            .defaultSchema("sentinelpay_private").schemas("sentinelpay_private").load().migrate();
        try(var c=DriverManager.getConnection(url,user,password);var s=c.createStatement()) {
            c.setAutoCommit(false);
            boolean exists;
            try(var r=s.executeQuery("SELECT 1 FROM pg_roles WHERE rolname='sentinelpay_runtime'")){exists=r.next();}
            if(!exists)s.execute("CREATE ROLE sentinelpay_runtime LOGIN PASSWORD '"+runtimePassword+"' NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS");
            s.execute("GRANT CONNECT ON DATABASE postgres TO sentinelpay_runtime");
            s.execute("GRANT USAGE ON SCHEMA sentinelpay_private TO sentinelpay_runtime");
            s.execute("GRANT SELECT, INSERT, DELETE ON sentinelpay_private.upload, sentinelpay_private.upload_event TO sentinelpay_runtime");
            s.execute("GRANT SELECT, INSERT, UPDATE ON sentinelpay_private.request_limit TO sentinelpay_runtime");
            c.commit();
        }
        try(var c=DriverManager.getConnection(url,System.getenv("SENTINELPAY_RUNTIME_USER"),runtimePassword);var s=c.createStatement()){
            try(var r=s.executeQuery("SELECT rolsuper,rolcreatedb,rolcreaterole,rolbypassrls FROM pg_roles WHERE rolname=current_user")){
                if(!r.next()||r.getBoolean(1)||r.getBoolean(2)||r.getBoolean(3)||r.getBoolean(4))throw new IllegalStateException("Runtime role is too privileged");
            }
            try(var r=s.executeQuery("SELECT has_schema_privilege(current_user,'sentinelpay_private','CREATE'), has_table_privilege(current_user,'sentinelpay_private.upload','SELECT'), has_schema_privilege(current_user,'auth','USAGE')")){
                if(!r.next()||r.getBoolean(1)||!r.getBoolean(2)||r.getBoolean(3))throw new IllegalStateException("Runtime grants are incorrect");
            }
            try(var r=s.executeQuery("SELECT count(*) FROM pg_class t JOIN pg_namespace n ON n.oid=t.relnamespace WHERE n.nspname='sentinelpay_private' AND t.relname IN ('upload','upload_event') AND t.relrowsecurity AND t.relforcerowsecurity AND t.relowner<>(SELECT oid FROM pg_roles WHERE rolname=current_user)")){
                if(!r.next()||r.getInt(1)!=2)throw new IllegalStateException("Upload row security is not enforced");
            }
            for(String table:new String[]{"upload","upload_event"}) {
                try(var r=s.executeQuery("SELECT count(*) FROM sentinelpay_private."+table)) {
                    if(!r.next()||r.getLong(1)!=0)throw new IllegalStateException("Missing owner context exposed rows");
                }
            }
        }
        System.out.println("Migrations applied; restricted runtime login and grants verified. No credentials printed.");
    }
}

