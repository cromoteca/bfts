import React, { useCallback, useEffect, useRef, useState } from "react";
import { useNotification } from "../NotificationContext.jsx";

const DEFAULT_CONTROL_PORT = 8615;

export default function Configuration() {
  const notify = useNotification();
  const [controlInfo, setControlInfo] = useState({
    port: 0,
    configuredPort: DEFAULT_CONTROL_PORT,
    owner: true,
    storages: [],
  });
  const [portInput, setPortInput] = useState(String(DEFAULT_CONTROL_PORT));
  const portEditingRef = useRef(false);

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
        const configuredPort = Number(parsed.configuredPort ?? parsed.port ?? DEFAULT_CONTROL_PORT);
        const effectivePort = Number(parsed.port ?? configuredPort);
        const owner = Boolean(parsed.owner);
        const storages = Array.isArray(parsed.storages) ? parsed.storages : [];

        setControlInfo({
          port: effectivePort,
          configuredPort,
          owner,
          storages,
        });

        if (!portEditingRef.current) {
          setPortInput(String(configuredPort));
        }
      }
    } catch (err) {
      console.error("Failed to fetch control status", err);
    }
  }, []);

  useEffect(() => {
    fetchControlStatus();
    const interval = setInterval(fetchControlStatus, 5000);
    return () => clearInterval(interval);
  }, [fetchControlStatus]);

  const handlePortInputChange = (event) => {
    setPortInput(event.target.value);
  };

  const handlePortInputFocus = () => {
    portEditingRef.current = true;
  };

  const handlePortInputBlur = () => {
    portEditingRef.current = false;
  };

  const handleApplyControlPort = () => {
    const value = Number.parseInt(portInput, 10);
    if (!Number.isInteger(value) || value < 1 || value > 65535) {
      if (notify) {
        notify("Control port must be between 1 and 65535", "error");
      }
      return;
    }

    if (typeof window.invoke !== "function") {
      return;
    }

    try {
      window.invoke("controlPort", [value]);
      if (notify) {
        notify(`Control port set to ${value}`, "success");
      }
    } catch (err) {
      console.error("Failed to set control port", err);
      if (notify) {
        notify("Failed to apply control port", "error");
      }
    } finally {
      fetchControlStatus();
    }
  };

  const ownerDescription = controlInfo.owner
    ? "This instance owns the control port."
    : `Commands are forwarded to another instance on port ${controlInfo.port}.`;

  return (
    <div>
      <h1>Configuration</h1>
      <section style={{ marginBottom: 24 }}>
        <h2>Control Port</h2>
        <p className="center-muted" style={{ marginBottom: 16 }}>
          {ownerDescription}
        </p>
        <div className="flex-center-gap" style={{ flexWrap: "wrap", gap: "0.75rem" }}>
          <label style={{ display: "flex", alignItems: "center", gap: "0.5rem" }}>
            <span>Port</span>
            <input
              type="number"
              min={1}
              max={65535}
              className="input-small"
              value={portInput}
              onChange={handlePortInputChange}
              onFocus={handlePortInputFocus}
              onBlur={handlePortInputBlur}
            />
          </label>
          <button type="button" className="btn-small" onClick={handleApplyControlPort}>
            Apply
          </button>
        </div>
      </section>
    </div>
  );
}
