package dnss.tools.dnt.processor;

import dnss.tools.dnt.DNT;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public abstract class AbstractProcessor implements Runnable {
    private final Connection conn;
    private final File file;
    private Map<String, Types> fields;

    public AbstractProcessor(Connection conn, File file) {
        this.conn = conn;
        this.file = file;
    }

    public File getFile() {
        return file;
    }

    public void recreateTable(Map<String, Types> fields) throws SQLException {
        try (Statement stmt = conn.createStatement()){
            String table = getName();

            // first drop table
            String query = "DROP TABLE IF EXISTS " + table;
            if (DNT.isVerbose()) {
                System.out.println(query);
            }

            stmt.executeUpdate(query);

            // recreate table
            query = "CREATE TABLE " + table + "(";
            Iterator<Map.Entry<String, Types>> iterator = fields.entrySet().iterator();
            boolean isFirst = true;
            while (iterator.hasNext()) {
                Map.Entry<String, Types> entry = iterator.next();

                query += entry.getKey() + " " + entry.getValue();
                if (isFirst) {
                    query += " PRIMARY KEY";
                }

                isFirst = false;
                if (iterator.hasNext()) {
                    query += ",";
                }
            }
            query += ")";

            if (DNT.isVerbose()) {
                System.out.println(query);
            }

            stmt.executeUpdate(query);
            this.fields = fields; // since this was all okay, we'll keep it for inserting later
            stmt.close();
        }
    }


    public void insert(List<Object> list) throws SQLException {
        String query = "INSERT INTO " + getName() + " VALUES(";

        for (int i = 0; i < list.size(); i++) {
            query += "?";
            if (i < list.size() - 1) {
                query += ",";
            }
        }
        query += ")";

        PreparedStatement stmt = conn.prepareStatement(query);
        try {
            Iterator<Types> iterator = fields.values().iterator();
            for (int i = 0, j = 1; iterator.hasNext(); i++, j++) {
                Types type = iterator.next();
                Object obj = list.get(i);
                switch (type) {
                    case STRING:
                        if (obj instanceof String) {
                            String str = (String)obj;
                            stmt.setString(j, str.length() == 0 ? null : str);
                        } else {
                            byte[] bytes = (byte[])obj;
                            stmt.setBytes(j, bytes);
                        }
                        break;
                    case INTEGER:
                        stmt.setInt(j, (Integer)obj);
                        break;
                    case BOOLEAN:
                        stmt.setBoolean(j, (Boolean)obj);
                        break;
                    case FLOAT:
                        stmt.setFloat(j, (Float)obj);
                        break;
                }
            }

            if (DNT.isVerbose()) {
                System.out.println(stmt.toString());
            }

            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println(stmt.toString());
            throw e;
        } finally {
            stmt.close();
        }
    }

    public String getName() {
        return file.getName();
    }

    abstract void parse() throws Exception;

    @Override
    public void run() {
        try {
            parse();
            System.out.println("Created " + getName() + " from " + file.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("There was an error when parsing " + file.getAbsolutePath());
            e.printStackTrace();
        }
    }

    public enum Types {
        STRING  ("TEXT"),
        BOOLEAN ("BOOLEAN"),
        INTEGER ("INTEGER"),
        FLOAT   ("REAL");

        public String fieldType;
        Types(String fieldType) {
            this.fieldType = fieldType;
        }

        public static Types getType(int b) {
            switch (b) {
                case 1: return STRING;
                case 2: return BOOLEAN;
                case 3: return INTEGER;
                case 4:case 5: return FLOAT;
                default: throw new RuntimeException("Cannot resolve type " + b);
            }
        }

        @Override
        public String toString() {
            return fieldType;
        }
    }
}
