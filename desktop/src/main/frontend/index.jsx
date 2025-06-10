import { Grid, GridColumn } from "@vaadin/react-components";
import React, { useEffect, useState } from "react";
import { createRoot } from "react-dom/client";

// Mock Java bridge for browser development
if (typeof window.invoke !== "function") {
  window.invoke = (method, params) => {
    if (method === "list") {
      return JSON.stringify({
        localStorages: [
          { name: "Mock Local", path: "/tmp/mock", port: 1234 }
        ],
        connectedStorages: [
          { name: "Mock Connected", path: "/mnt/mock", encryption: "none" }
        ]
      });
    }
    return "{}";
  };
}

function App() {
  // Redefine console methods to use window.logToJava if it's a function
  if (typeof window.logToJava === 'function') {
    console.log = function (...args) {
      window.logToJava('LOG', ...args);
    };
    console.error = function (...args) {
      window.logToJava('ERROR', ...args);
    };
    console.warn = function (...args) {
      window.logToJava('WARN', ...args);
    };
  }

  console.log('React app started', React.version);

  const [storages, setStorages] = useState({ localStorages: [], connectedStorages: [] });

  useEffect(() => {
    // Fetch storages on mount
    const result = window.invoke('list');
    try {
      setStorages(JSON.parse(result));
    } catch {
      setStorages({ localStorages: [], connectedStorages: [] });
    }
  }, []);

  return (
    <div>
      <div className="table-container">
        {storages.localStorages && storages.localStorages.length === 0 && (
          <p>No local storages</p>
        )}
        {storages.localStorages && storages.localStorages.map((s, i) => (
          <Grid items={storages.localStorages} allRowsVisible>
            <GridColumn path="name" />
            <GridColumn path="path" />
            <GridColumn path="port" />
          </Grid>
        ))}
      </div>
      <div className="table-container">
        {storages.connectedStorages && storages.connectedStorages.length === 0 && (
          <p>No connected storages</p>
        )}
        {storages.connectedStorages && storages.connectedStorages.map((s, i) => (
          <Grid items={storages.connectedStorages} allRowsVisible>
            <GridColumn path="name" />
            <GridColumn path="path" />
            <GridColumn path="encryption" />
          </Grid>
        ))}
      </div>
    </div>
  );
}

const root = createRoot(document.getElementById("root"));
root.render(<App />);
