import React, { useEffect, useState } from "react";
import { createRoot } from "react-dom/client";
import { initializeMockBridge } from "./mock";

// import '@vaadin/vaadin-lumo-styles/all-imports.js';

initializeMockBridge();

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
    <>
      <nav>
        <a href="#">Dashboard</a>
        <a href="#">Backups</a>
        <a href="#">Storages</a>
        <a href="#">Settings</a>
      </nav>
      <main>
        <h1>Welcome to BFTS</h1>
        <p>Your backups are safe and robust with BFTS. Use the navigation above to manage your backups and storage devices.</p>
        <button className="btn">Start Backup</button>
      </main>
      <footer>
        &copy; {new Date().getFullYear()} BFTS. All rights reserved.
      </footer>
    </>
  );
}

const root = createRoot(document.getElementById("root"));
root.render(<App />);
