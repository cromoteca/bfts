import "./style.css";
import React, { useState, useEffect } from "react";
import { createRoot } from "react-dom/client";

function App() {
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
        <table>
          <caption>Local Storages</caption>
          <thead>
            <tr>
              <th>Name</th>
              <th>Path</th>
              <th>Port</th>
            </tr>
          </thead>
          <tbody>
            {storages.localStorages && storages.localStorages.length === 0 && (
              <tr><td colSpan={3} style={{textAlign: 'center'}}>No local storages</td></tr>
            )}
            {storages.localStorages && storages.localStorages.map((s, i) => (
              <tr key={i}>
                <td>{s.name}</td>
                <td>{s.path}</td>
                <td>{s.port}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div className="table-container">
        <table>
          <caption>Connected Storages</caption>
          <thead>
            <tr>
              <th>Name</th>
              <th>Path</th>
              <th>Encryption</th>
            </tr>
          </thead>
          <tbody>
            {storages.connectedStorages && storages.connectedStorages.length === 0 && (
              <tr><td colSpan={3} style={{textAlign: 'center'}}>No connected storages</td></tr>
            )}
            {storages.connectedStorages && storages.connectedStorages.map((s, i) => (
              <tr key={i}>
                <td>{s.name}</td>
                <td>{s.path}</td>
                <td>{s.encryption}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

const root = createRoot(document.getElementById("root"));
root.render(<App />);
