/*
 * Copyright (C) 2014-2019 Luciano Vernaschi (luciano at cromoteca.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.cromoteca.bfts.testutil;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simple utility to execute SQL queries from files or resources.
 *
 * @author Luciano Vernaschi (luciano at cromoteca.com)
 */
public class SQLScript {
  private static final String VARIABLE_NAME = "[A-Za-z0-9_-]+";
  private static final Pattern VARIABLE_DECLARATION
      = Pattern.compile(VARIABLE_NAME);
  private static final Pattern VARIABLE_USE = Pattern.compile("\\$\\{\\s*("
      + VARIABLE_NAME + ")\\s*\\}");
  private static final String VARIABLE_SEPARATOR = "::";
  private final SQLScriptVars variables;

  public SQLScript() {
    variables = new SQLScriptVars();
  }

  public SQLScriptVars run(Connection connection, URL... resources)
      throws IOException, SQLScriptException, SQLException {
    for (URL resource : resources) {
      try (Reader reader = new InputStreamReader(resource.openStream())) {
        run(connection, reader);
      }
    }

    return variables;
  }

  public SQLScriptVars run(Connection connection, Reader... readers)
      throws IOException, SQLScriptException, SQLException {
    int n;
    char[] cbuf = new char[512];

    for (Reader reader : readers) {
      StringBuilder sb = new StringBuilder();

      while ((n = reader.read(cbuf)) >= 0) {
        sb.append(cbuf, 0, n);
      }

      String script = sb.toString();
      run(connection, script);
    }

    return variables;
  }

  public SQLScriptVars run(Connection connection, String... scripts)
      throws SQLScriptException, SQLException {
    for (String script : scripts) {
      try (Statement stmt = connection.createStatement()) {
        runScript(stmt, script);
      }

      if (!connection.getAutoCommit()) {
        connection.commit();
      }
    }

    return variables;
  }

  public SQLScriptVars getVariables() {
    return variables;
  }

  public void clearVariables() {
    variables.clear();
  }

  private void runScript(Statement stmt, String script)
      throws SQLException, SQLScriptException {
    script = script.replaceAll("\r", "")
        .replaceAll("-->(.+)\n", "$1" + VARIABLE_SEPARATOR)
        .replaceAll("--.*\n", "");
    String[] queries = script.split(";");

    for (String query : queries) {
      query = query.trim() + ";";

      if (query.length() > 1) {
        StringBuffer sbuf = new StringBuffer();
        Matcher m = VARIABLE_USE.matcher(query);

        while (m.find()) {
          String variableName = m.group(1);

          if (!variables.containsKey(variableName)) {
            throw new SQLScriptException("Variable " + variableName
                + " is not defined");
          }

          Object o = variables.get(variableName);

          if (o == null) {
            o = "NULL";
            // TODO: find a date format that works on most databases
            // } else if (o instanceof Date) {
            //   o = '\'' + DATE_FORMAT.format((Date) o) + '\'';
          } else if (o instanceof CharSequence) {
            o = '\'' + o.toString() + '\'';
          }

          m.appendReplacement(sbuf, o.toString());
        }

        m.appendTail(sbuf);
        query = sbuf.toString();
        String[] assignment = query.split(VARIABLE_SEPARATOR);

        if (assignment.length == 2) {
          String variableName = assignment[0].trim();

          if (!VARIABLE_DECLARATION.matcher(variableName).matches()) {
            throw new SQLScriptException(variableName
                + " is not a valid variable name");
          }

          query = assignment[1].trim();
          ResultSet rs;

          try {
            rs = stmt.executeQuery(query);
          } catch (SQLException ex) {
            throw new SQLScriptException("Query \"" + query + "\" is invalid",
                ex);
          }

          variables.put(variableName, null);

          if (rs != null) {
            List<Map<String, Object>> resultSet = new ArrayList<>();
            ResultSetMetaData metaData = rs.getMetaData();
            int n = metaData.getColumnCount();

            while (rs.next()) {
              Map<String, Object> row = new HashMap<>();

              for (int i = 1; i <= n; i++) {
                row.put(metaData.getColumnName(i), rs.getObject(i));
              }

              resultSet.add(row);
            }

            if (n == 1 && resultSet.size() == 1) {
              variables.put(variableName,
                  resultSet.get(0).values().iterator().next());
            } else if (!resultSet.isEmpty()) {
              variables.put(variableName, resultSet);
            }
          }
        } else {
          try {
            stmt.execute(query);
          } catch (SQLException ex) {
            throw new SQLScriptException("Query \"" + query + "\" is invalid",
                ex);
          }
        }
      }
    }
  }
}
