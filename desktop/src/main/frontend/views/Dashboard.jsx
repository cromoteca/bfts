import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNotification } from "../NotificationContext.jsx";

const LOG_POLL_INTERVAL = 2000;
const MAX_LOG_ENTRIES = 600;
export default function Dashboard() {
  const notify = useNotification();
  const [logs, setLogs] = useState([]);
  const lastIdRef = useRef(0);
  const logContainerRef = useRef(null);
  const [connectedStorages, setConnectedStorages] = useState([]);
  const [controlInfo, setControlInfo] = useState({
    port: 0,
    configuredPort: 0,
    owner: true,
    storages: [],
  });
  const [completing, setCompleting] = useState({});
  const [completingAll, setCompletingAll] = useState(false);

  const fetchControlStatus = useCallback(() => {
    if (typeof window.invoke !== "function") {
      return;
    }

    try {
      const response = window.invoke("controlStatus");
      if (!response) {
        return;
      }

      const parsed = JSON.parse(response);
      if (parsed && !parsed.exception) {
        const configuredPort = Number(parsed.configuredPort ?? parsed.port ?? 0);
        const effectivePort = Number(parsed.port ?? configuredPort);
        const owner = Boolean(parsed.owner);
        const storages = Array.isArray(parsed.storages) ? parsed.storages : [];

        setControlInfo({
          port: effectivePort,
          configuredPort,
          owner,
          storages,
        });
      }
    } catch (err) {
      console.error("Failed to fetch control status", err);
    }
  }, []);

  const fetchStorages = useCallback(() => {
    if (typeof window.invoke !== "function") {
      return;
    }

    try {
      const response = window.invoke("list");
      if (!response) {
        setConnectedStorages([]);
        return;
      }

      const parsed = JSON.parse(response);
      setConnectedStorages(parsed?.connectedStorages ?? []);
    } catch (err) {
      console.error("Failed to fetch storages", err);
      setConnectedStorages([]);
    }
  }, []);

  useEffect(() => {
    fetchControlStatus();
    const interval = setInterval(fetchControlStatus, 5000);
    return () => clearInterval(interval);
  }, [fetchControlStatus]);

  useEffect(() => {
    fetchStorages();
  }, [fetchStorages]);

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

  const statusMaps = useMemo(() => {
    const running = {};
    const completingMap = {};
    (controlInfo.storages || []).forEach(storage => {
      if (storage && storage.name) {
        running[storage.name] = !!storage.running;
        completingMap[storage.name] = !!storage.completing;
      }
    });
    return { running, completing: completingMap };
  }, [controlInfo.storages]);

  const isCompletingAny = completingAll
    || Object.values(completing).some(Boolean)
    || connectedStorages.some(storage => statusMaps.completing[storage.name]);
  const allRunning = connectedStorages.length > 0
    && connectedStorages.every(storage => statusMaps.running[storage.name]);
  const noneRunning = connectedStorages.every(storage => !statusMaps.running[storage.name]);

  const handleStartAll = () => {
    if (typeof window.invoke !== "function") {
      return;
    }
    if (isCompletingAny || allRunning) {
      return;
    }
    try {
      window.invoke("start");
      fetchControlStatus();
    } catch (err) {
      console.error("Failed to start backup", err);
    }
  };

  const handleStopAll = () => {
    if (typeof window.invoke !== "function") {
      return;
    }
    if (isCompletingAny || noneRunning) {
      return;
    }

    try {
      window.invoke("stop");
      fetchControlStatus();
    } catch (err) {
      console.error("Failed to stop backup", err);
    }
  };

  const handleFullBackupAll = () => {
    if (typeof window.invoke !== "function") {
      return;
    }
    if (isCompletingAny || !noneRunning || connectedStorages.length === 0) {
      return;
    }

    setCompletingAll(true);
    try {
      connectedStorages.forEach(storage => {
        window.invoke("complete", [storage.name]);
      });
      if (notify) {
      notify("Full backup started for all storages");
      }
    } catch (err) {
      console.error("Failed to run complete backup for all storages", err);
      if (notify) {
        notify("Failed to run complete backup for all storages", "error");
      }
    } finally {
      setCompletingAll(false);
      fetchControlStatus();
    }
  };

  const handleStartStorage = (name) => {
    if (!name || typeof window.invoke !== "function" || isCompletingAny) {
      return;
    }
    try {
      window.invoke("start", [name]);
      if (notify) {
        notify(`Started backup for ${name}`);
      }
    } catch (err) {
      console.error("Failed to start backup", err);
      if (notify) {
        notify(`Failed to start backup for ${name}`, "error");
      }
    } finally {
      fetchControlStatus();
    }
  };

  const handleStopStorage = (name) => {
    if (!name || typeof window.invoke !== "function" || isCompletingAny) {
      return;
    }

    try {
      window.invoke("stop", [name]);
      if (notify) {
        notify(`Stopped backup for ${name}`);
      }
    } catch (err) {
      console.error("Failed to stop backup", err);
      if (notify) {
        notify(`Failed to stop backup for ${name}`, "error");
      }
    } finally {
      fetchControlStatus();
    }
  };

  const handleFullBackupStorage = (name) => {
    if (!name || typeof window.invoke !== "function" || completingAll) {
      return;
    }

    setCompleting(prev => ({ ...prev, [name]: true }));
    try {
      window.invoke("complete", [name]);
      if (notify) {
      notify(`Full backup started for ${name}`);
      }
    } catch (err) {
      console.error("Failed to run complete backup", err);
      if (notify) {
        notify(`Failed to run complete backup for ${name}`, "error");
      }
    } finally {
      setCompleting(prev => {
        const next = { ...prev };
        delete next[name];
        return next;
      });
      fetchControlStatus();
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

  const ownerDescription = controlInfo.owner
    ? "This instance owns the control port."
    : `Commands are forwarded to another instance on port ${controlInfo.port}.`;

  return (
    <>
      <h1>Welcome to BFTS</h1>
      <p>Your backups are safe and robust with BFTS. Use the navigation above to manage your backups and storage devices.</p>
      <section style={{ marginBottom: 16 }}>
        <h2>Control Status</h2>
        <p className="center-muted">
          {ownerDescription}
        </p>
      </section>
      <div className="flex-center-gap" style={{ marginBottom: 16, flexWrap: "wrap", gap: "0.75rem" }}>
        <button
          type="button"
          className="btn"
          onClick={handleStartAll}
          disabled={isCompletingAny || allRunning}
        >
          Start All
        </button>
        <button
          type="button"
          className="btn"
          onClick={handleStopAll}
          disabled={isCompletingAny || noneRunning}
        >
          Stop All
        </button>
        <button
          type="button"
          className="btn"
          onClick={handleFullBackupAll}
          disabled={isCompletingAny || !noneRunning || connectedStorages.length === 0}
        >
          Full Backup
        </button>
        <button type="button" className="btn btn-secondary" onClick={handleClearLogs}>
          Clear Log
        </button>
      </div>
      <section style={{ marginBottom: 24 }}>
        <h2>Connected Storages</h2>
        <table className="storages-table">
          <thead>
            <tr>
              <th>Name</th>
              <th>Status</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {connectedStorages.length === 0 ? (
              <tr>
                <td className="center-muted" colSpan={3}>
                  No connected storages found.
                </td>
              </tr>
            ) : (
              connectedStorages.map((storage, idx) => {
                const running = !!statusMaps.running[storage.name];
                const completingThis = completingAll
                  || !!completing[storage.name]
                  || !!statusMaps.completing[storage.name];
                let statusLabel = running ? "Running" : "Idle";
                let statusColor = running ? "#2ecc71" : "#e74c3c";

                if (completingThis && !running) {
                  statusLabel = "Full backup running…";
                  statusColor = "#f39c12";
                }

                return (
                  <tr key={`${storage.name}-${idx}`}>
                    <td>{storage.name}</td>
                    <td>
                      <span style={{ color: statusColor, fontWeight: 600 }}>
                        {statusLabel}
                      </span>
                    </td>
                    <td>
                      <div className="flex-center-gap" style={{ flexWrap: "wrap", gap: "0.5rem" }}>
                        <button
                          type="button"
                          className="btn-small"
                          disabled={running || completingThis || isCompletingAny}
                          onClick={() => handleStartStorage(storage.name)}
                        >
                          Start
                        </button>
                        <button
                          type="button"
                          className="btn-small"
                          disabled={!running || completingThis || isCompletingAny}
                          onClick={() => handleStopStorage(storage.name)}
                        >
                          Stop
                        </button>
                        <button
                          type="button"
                          className="btn-small"
                          disabled={running || completingThis || isCompletingAny}
                          onClick={() => handleFullBackupStorage(storage.name)}
                        >
                          Full Backup
                        </button>
                      </div>
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </section>
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
