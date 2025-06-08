import React, { useState, useEffect } from "react";
import { createRoot } from "react-dom/client";

function App() {
  const [storages, setStorages] = useState([]);

  // Expose a global callback for Java to invoke
  useEffect(() => {
    // Fetch storages on mount
    const result = window.invoke('list');
    try {
      setStorages(JSON.parse(result));
    } catch {
      setStorages([]);
    }
  }, []);

  return (
      <div>
        <strong>Storages:</strong>
        <pre>{JSON.stringify(storages, null, 2)}</pre>
      </div>
  );
}

const root = createRoot(document.getElementById("root"));
root.render(<App />);
