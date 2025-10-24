import React, { useEffect, useRef, useState } from "react";

const LOG_POLL_INTERVAL = 2000;
const MAX_LOG_ENTRIES = 600;

export default function Dashboard() {
  const [logs, setLogs] = useState([]);
  const lastIdRef = useRef(0);
  const logContainerRef = useRef(null);

  useEffect(() => {
    let active = true;

    const fetchLogs = () => {
      if (typeof window.invoke !== "function") {
        return;
      }

      try {
        const response = window.invoke("logs", [lastIdRef.current]);
        if (!response) {
          return;
        }

        const parsed = JSON.parse(response);
        if (!parsed || parsed.exception || parsed.available === false) {
          return;
        }

        if (Array.isArray(parsed.entries) && parsed.entries.length > 0) {
          const entries = parsed.entries.map(entry => ({
            id: entry.id,
            stream: entry.stream,
            timestamp: entry.timestamp,
            message: entry.message,
          }));

          if (active) {
            setLogs(prev => {
              const merged = [...prev, ...entries];
              if (merged.length > MAX_LOG_ENTRIES) {
                return merged.slice(-MAX_LOG_ENTRIES);
              }
              return merged;
            });
          }
        }

        if (typeof parsed.lastId === "number") {
          lastIdRef.current = parsed.lastId;
        }
      } catch (err) {
        console.error("Failed to fetch logs", err);
      }
    };

    fetchLogs();
    const interval = setInterval(fetchLogs, LOG_POLL_INTERVAL);

    return () => {
      active = false;
      clearInterval(interval);
    };
  }, []);

  useEffect(() => {
    if (logContainerRef.current) {
      logContainerRef.current.scrollTop = logContainerRef.current.scrollHeight;
    }
  }, [logs]);

  const handleStartBackup = () => {
    if (typeof window.invoke !== "function") {
      return;
    }
    try {
      window.invoke("start");
    } catch (err) {
      console.error("Failed to start backup", err);
    }
  };

  const handleStopBackup = () => {
    if (typeof window.invoke !== "function") {
      return;
    }

    try {
      window.invoke("stop");
    } catch (err) {
      console.error("Failed to stop backup", err);
    }
  };

  const handleClearLogs = () => {
    setLogs([]);
  };

  const formatTimestamp = (value) => {
    if (!value) return "";
    try {
      return new Date(value).toLocaleTimeString();
    } catch {
      return "";
    }
  };

  return (
    <>
      <h1>Welcome to BFTS</h1>
      <p>Your backups are safe and robust with BFTS. Use the navigation above to manage your backups and storage devices.</p>
      <div className="flex-center-gap" style={{ marginBottom: 16 }}>
        <button type="button" className="btn" onClick={handleStartBackup}>
          Start Backup
        </button>
        <button type="button" className="btn" onClick={handleStopBackup}>
          Stop Backup
        </button>
        <button type="button" className="btn btn-secondary" onClick={handleClearLogs}>
          Clear Log
        </button>
      </div>
      <section>
        <h2>Activity Log</h2>
        <div
          ref={logContainerRef}
          style={{
            border: "1px solid #ccc",
            borderRadius: 4,
            background: "#111",
            color: "#f1f1f1",
            padding: "0.75rem",
            minHeight: 200,
            maxHeight: 360,
            overflowY: "auto",
            fontFamily: "monospace",
            fontSize: "0.85rem",
          }}
        >
          {logs.length === 0 ? (
            <div className="center-muted">No log messages yet.</div>
          ) : (
            logs.map(entry => (
              <div
                key={entry.id}
                style={{
                  display: "flex",
                  gap: "0.75rem",
                  padding: "0.1rem 0",
                  color: entry.stream === "stderr" ? "#ff9c9c" : "#d0f0ff",
                }}
              >
                <span style={{ opacity: 0.6 }}>
                  {formatTimestamp(entry.timestamp)}
                </span>
                <span>{entry.message}</span>
              </div>
            ))
          )}
        </div>
      </section>
    </>
  );
}
