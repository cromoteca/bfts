package com.cromoteca.bfts.storage;

import java.util.ArrayList;
import java.util.List;

/** Simple tabular result for queries returning columns and row values. */
public class TabularQueryResult {

  private List<String> columns;
  private List<List<Object>> rows;

  public TabularQueryResult() {
    // For serialization
  }

  public TabularQueryResult(List<String> columns, List<List<Object>> rows) {
    this.columns = new ArrayList<>(columns);
    List<List<Object>> copy = new ArrayList<>(rows.size());
    for (List<Object> row : rows) {
      copy.add(new ArrayList<>(row));
    }
    this.rows = copy;
  }

  public List<String> getColumns() {
    return columns;
  }

  public List<List<Object>> getRows() {
    return rows;
  }
}
