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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SQLScriptVars extends HashMap<String, Object> {
  public String getString(String variableName) {
    return (String) get(variableName);
  }

  public int getInt(String variableName) {
    return (Integer) get(variableName);
  }

  public double getDouble(String variableName) {
    return (Double) get(variableName);
  }

  public boolean getBoolean(String variableName) {
    return (Boolean) get(variableName);
  }

  public List<Map<String, Object>> getResultSet(String variableName) {
    return (List<Map<String, Object>>) get(variableName);
  }
}
