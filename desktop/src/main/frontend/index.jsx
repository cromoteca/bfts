import React, { useState, useEffect } from "react";
import { createRoot } from "react-dom/client";

function App() {
  const [pickedDir, setPickedDir] = useState("");
  const [storages, setStorages] = useState([]);

  const openDirectoryPicker = () => {
    window.logToJava("Opening directory picker...");

    if (typeof window.openDirectoryPicker === "function") {
      window.openDirectoryPicker();
    } else {
      alert("Java interop not available.");
    }
  };

  // Expose a global callback for Java to invoke
  useEffect(() => {
    window.showPickedDirectory = (path) => {
      window.logToJava(`Picked directory: ${path}`);
      setPickedDir(path ? `Picked directory: ${path}` : "No directory selected");
      // If a directory was picked, list its contents and log them
      if (path && typeof window.list === 'function') {
        try {
          const result = window.list(path);
          window.logToJava(`Contents of ${path}: ${result}`);
        } catch (e) {
          window.logToJava('Error: ' + e);
        }
      }
    };

    // Fetch storages on mount
    if (typeof window.invoke === 'function') {
      const result = window.invoke('list');
      try {
        setStorages(JSON.parse(result));
      } catch {
        setStorages([]);
      }
    }
  }, []);

  return (
    <div style={{ fontFamily: "Arial, sans-serif", margin: "2em" }}>
      <h1 style={{ color: "#2c3e50" }}>Welcome to the SWT Embedded Browser!</h1>
      <p style={{ color: "#34495e" }}>
        This is a custom HTML page loaded from the application's resources.
      </p>
      <p style={{ color: "#34495e" }}>
        You can modify this file to display any content you like.
      </p>
      <button
        onClick={openDirectoryPicker}
        style={{
          marginTop: "1em",
          padding: "0.5em 1em",
          fontSize: "1em",
        }}
      >
        Open Directory Picker
      </button>
      <div
        style={{
          marginTop: "1em",
          color: "#2980b9",
          fontWeight: "bold",
        }}
      >
        {pickedDir}
      </div>
      <div style={{ marginTop: "2em" }}>
        <strong>Storages:</strong>
        <pre>{JSON.stringify(storages, null, 2)}</pre>
      </div>
    </div>
  );
}

const root = createRoot(document.getElementById("root"));
root.render(<App />);
