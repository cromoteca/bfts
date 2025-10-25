import React, { useEffect, useState } from "react";
import { useNotification } from "../NotificationContext.jsx";

const PRIORITY_LEVELS = [
  { value: 0, label: "Disabled", className: "priority-disabled" },
  { value: 5, label: "Low", className: "priority-low" },
  { value: 10, label: "Normal", className: "priority-normal" },
  { value: 15, label: "High", className: "priority-high" },
];

const safeParse = (payload) => {
  if (payload === undefined || payload === null) {
    return null;
  }

  try {
    return JSON.parse(payload);
  } catch {
    return null;
  }
};

const sanitizeName = (rawValue) => {
  if (!rawValue) {
    return "";
  }

  const replaced = rawValue
      .replace(/[^A-Za-z0-9_-]+/g, "-")
      .replace(/--+/g, "-")
      .replace(/__+/g, "_");
  const trimmed = replaced
      .replace(/^[^A-Za-z0-9]+/, "")
      .replace(/[^A-Za-z0-9]+$/, "");
  return trimmed;
};

export default function Directories() {
  const notify = useNotification();
  const [storages, setStorages] = useState([]);
  const [sourcesByStorage, setSourcesByStorage] = useState({});
  const [forms, setForms] = useState({});
  const [loading, setLoading] = useState(false);

  const getSources = (storageName) => sourcesByStorage[storageName] || [];

  const deriveNameFromPath = (storageName, path) => {
    if (!path) {
      return "";
    }

    const normalized = path.replace(/\\+/g, "/");
    const segments = normalized.split("/").filter(Boolean);
    const lastSegment = segments.length > 0 ? segments[segments.length - 1] : "";
    const sanitized = sanitizeName(lastSegment);

    if (sanitized) {
      return sanitized;
    }

    const fallbackIndex = getSources(storageName).length + 1;
    return `source${fallbackIndex}`;
  };

  const handleError = (message) => {
    if (notify) {
      notify(message, "error");
    }
  };

  const loadSourcesForStorage = (storageName) => {
    try {
      const raw = window.invoke("sources", [storageName]);
      const parsed = safeParse(raw);

      if (parsed && parsed.exception) {
        handleError(`Unable to load directories for ${storageName}: ${parsed.message || parsed.exception}`);
        return [];
      }

      if (Array.isArray(parsed)) {
        return parsed;
      }
    } catch (err) {
      handleError(`Unable to load directories for ${storageName}`);
    }

    return [];
  };

  const refreshStorage = (storageName) => {
    const updated = loadSourcesForStorage(storageName);
    setSourcesByStorage(prev => ({
      ...prev,
      [storageName]: updated,
    }));
  };

  const reloadAll = () => {
    setLoading(true);
    try {
      const raw = window.invoke("list");
      const parsed = safeParse(raw);

      if (!parsed) {
        setStorages([]);
        setSourcesByStorage({});
        return;
      }

      if (parsed.exception) {
        handleError(parsed.message || "Unable to fetch storages");
        setStorages([]);
        setSourcesByStorage({});
        return;
      }

      const connected = Array.isArray(parsed.connectedStorages)
        ? parsed.connectedStorages
        : [];
      setStorages(connected);

      const nextSources = {};
      const nextForms = {};

      connected.forEach(storage => {
        nextSources[storage.name] = loadSourcesForStorage(storage.name);
        nextForms[storage.name] = { name: "", path: "", selectedPriority: 10 };
      });

      setSourcesByStorage(nextSources);
      setForms(nextForms);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    reloadAll();
  }, []);

  const handleFormChange = (storageName, field, value) => {
    setForms(prev => ({
      ...prev,
      [storageName]: {
        ...(prev[storageName] || { name: "", path: "", selectedPriority: 10 }),
        [field]: value,
      },
    }));
  };

  const handlePickDirectory = async (storageName) => {
    if (typeof window.openDirectoryPicker !== "function") {
      return;
    }

    const dir = await window.openDirectoryPicker();

    if (!dir) {
      return;
    }

    setForms(prev => {
      const current = prev[storageName] || { name: "", path: "" };
      const name = current.name && current.name.trim().length > 0
        ? current.name
        : deriveNameFromPath(storageName, dir);

      return {
        ...prev,
        [storageName]: {
          name,
          path: dir,
          selectedPriority: current.selectedPriority ?? 10,
        },
      };
    });
  };

  const handleAddDirectory = (storageName) => {
    const form = forms[storageName] || { name: "", path: "" };
    const path = form.path.trim();

    if (!path) {
      handleError("Select a directory path before adding.");
      return;
    }

    const providedName = sanitizeName(form.name.trim());
    const finalName = providedName || deriveNameFromPath(storageName, path);

    if (!finalName) {
      handleError("Unable to determine a valid name for the selected directory.");
      return;
    }

    const selectedPriority = Number.isInteger(form.selectedPriority)
      ? form.selectedPriority
      : 10;

    const addRawResult = window.invoke("add", [storageName, finalName, path]);
    const addParsedResult = safeParse(addRawResult);

    if (addParsedResult && typeof addParsedResult === "object" && addParsedResult.exception) {
      handleError(addParsedResult.message || addParsedResult.exception);
      return;
    }

    const addMessage = typeof addParsedResult === "string" ? addParsedResult : addRawResult;

    if (notify) {
      notify(addMessage);
    }

    if (selectedPriority !== 10) {
      const priorityRawResult = window.invoke("priority", [storageName, finalName, selectedPriority]);
      const priorityParsedResult = safeParse(priorityRawResult);

      if (priorityParsedResult && typeof priorityParsedResult === "object" && priorityParsedResult.exception) {
        handleError(priorityParsedResult.message || priorityParsedResult.exception);
      } else if (notify) {
        const level = PRIORITY_LEVELS.find(item => item.value === selectedPriority);
        const label = level ? level.label.toLowerCase() : selectedPriority;
        notify(`Priority for ${finalName} set to ${label}`);
      }
    }

    refreshStorage(storageName);
    setForms(prev => ({
      ...prev,
      [storageName]: { name: "", path: "", selectedPriority: 10 },
    }));
  };

  const handlePriorityChange = (storageName, source, level) => {
    if (!source.name) {
      handleError("Cannot change priority of a source without a name.");
      return;
    }

    const rawResult = window.invoke("priority", [storageName, source.name, level.value]);
    const parsedResult = safeParse(rawResult);

    if (parsedResult && typeof parsedResult === "object" && parsedResult.exception) {
      handleError(parsedResult.message || parsedResult.exception);
      return;
    }

    if (notify) {
      notify(`Priority for ${source.name} set to ${level.label.toLowerCase()}`);
    }

    refreshStorage(storageName);
  };

  return (
    <div>
      <h1>Directories</h1>
      {loading ? (
        <p className="center-muted">Loading connected storages…</p>
      ) : storages.length === 0 ? (
        <p className="center-muted">No connected storages found.</p>
      ) : (
        storages.map(storage => {
          const sources = getSources(storage.name);
          const form = forms[storage.name] || { name: "", path: "" };

          return (
            <div key={storage.name}>
              <h2>{storage.name}</h2>
              <table className="storages-table">
                <thead>
                  <tr>
                    <th>Name</th>
                    <th>Path</th>
                    <th>Priority</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {sources.length === 0 ? (
                    <tr>
                      <td colSpan={4} className="center-muted">
                        No directories configured for this storage.
                      </td>
                    </tr>
                  ) : (
                    sources.map((source, idx) => (
                      <tr key={`${storage.name}-${idx}`}>
                        <td>{source.name || "—"}</td>
                        <td>{source.path}</td>
                        <td>
                          <div className="priority-buttons">
                            {PRIORITY_LEVELS.map(level => {
                              const active = source.priority === level.value;
                              return (
                                <button
                                  key={level.value}
                                  type="button"
                                  className={`priority-btn ${level.className} ${active ? "active" : ""}`}
                                  onClick={() => handlePriorityChange(storage.name, source, level)}
                                  title={active ? `Priority already ${level.label.toLowerCase()}` : `Set priority to ${level.label.toLowerCase()}`}
                                >
                                  {level.label}
                                </button>
                              );
                            })}
                          </div>
                          {source.priority !== null && !PRIORITY_LEVELS.some(level => level.value === source.priority) && (
                            <div className="priority-warning">
                              Current priority: {source.priority}
                            </div>
                          )}
                        </td>
                        <td />
                      </tr>
                    ))
                  )}
                  <tr>
                    <td>
                      <input
                        type="text"
                        value={form.name}
                        onChange={e => handleFormChange(storage.name, "name", e.target.value)}
                        placeholder="Name (optional)"
                        className="input-medium"
                      />
                    </td>
                    <td>
                      <div className="flex-center-gap">
                        <input
                          type="text"
                          value={form.path}
                          onChange={e => handleFormChange(storage.name, "path", e.target.value)}
                          placeholder="Directory path"
                          className="input-large"
                        />
                        <button
                          type="button"
                          className="btn-small"
                          onClick={() => handlePickDirectory(storage.name)}
                        >
                          Browse
                        </button>
                      </div>
                    </td>
                    <td>
                      <div className="priority-buttons">
                        {PRIORITY_LEVELS.map(level => {
                          const active = form.selectedPriority === level.value;
                          return (
                            <button
                              key={level.value}
                              type="button"
                              className={`priority-btn ${level.className} ${active ? "active" : ""}`}
                              onClick={() => handleFormChange(storage.name, "selectedPriority", level.value)}
                            >
                              {level.label}
                            </button>
                          );
                        })}
                      </div>
                    </td>
                    <td>
                      <button
                        type="button"
                        className="btn-small"
                        disabled={!form.path.trim()}
                        onClick={() => handleAddDirectory(storage.name)}
                      >
                        Add
                      </button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          );
        })
      )}
    </div>
  );
}
