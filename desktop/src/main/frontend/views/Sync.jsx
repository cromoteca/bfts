import React, { useEffect, useState } from "react";
import { useNotification } from "../NotificationContext.jsx";

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

export default function Sync() {
  const notify = useNotification();
  const [syncGroups, setSyncGroups] = useState([]);
  const [loading, setLoading] = useState(false);

  const handleError = (message) => {
    if (notify) {
      notify(message, "error");
    }
  };

  const loadSyncGroups = () => {
    setLoading(true);
    try {
      const raw = window.invoke("syncList");
      const parsed = safeParse(raw);

      if (parsed && parsed.exception) {
        handleError(parsed.message || parsed.exception);
        setSyncGroups([]);
        return;
      }

      if (Array.isArray(parsed)) {
        setSyncGroups(parsed);
      }
    } catch (err) {
      handleError("Unable to load sync groups");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadSyncGroups();
  }, []);

  const handleSyncChange = (dirName, storageName, field, value) => {
    try {
      // Find current settings
      const group = syncGroups.find(g => g.dirName === dirName);
      const occurrence = group?.occurrences.find(o => o.storageName === storageName);
      const syncSource = field === "syncSource" ? value : occurrence?.syncSource || false;
      const syncTarget = field === "syncTarget" ? value : occurrence?.syncTarget || false;

      const raw = window.invoke("syncSet", [storageName, dirName, syncSource, syncTarget]);
      const parsed = safeParse(raw);

      if (parsed && typeof parsed === "object" && parsed.exception) {
        handleError(parsed.message || parsed.exception);
        return;
      }

      if (notify) {
        const direction = syncSource && syncTarget
          ? "two-way"
          : syncSource
            ? "outgoing"
            : syncTarget
              ? "incoming"
              : "disabled";
        notify(`Sync for ${dirName} on ${storageName} set to ${direction}`);
      }

      loadSyncGroups();
    } catch (err) {
      handleError(`Failed to update sync settings: ${err.message}`);
    }
  };

  const getSyncDescription = (occurrences) => {
    const sourceCount = occurrences.filter(o => o.syncSource).length;
    const targetCount = occurrences.filter(o => o.syncTarget).length;

    if (sourceCount === 0 && targetCount === 0) {
      return "No synchronization configured";
    }

    const parts = [];
    if (sourceCount > 0) {
      parts.push(`${sourceCount} source(s)`);
    }
    if (targetCount > 0) {
      parts.push(`${targetCount} target(s)`);
    }

    return `Sync: ${parts.join(", ")}`;
  };

  const getSyncStatusClass = (occurrence, allOccurrences) => {
    const hasPartner = allOccurrences.some(o =>
      o.storageName !== occurrence.storageName &&
      ((occurrence.syncSource && o.syncTarget) || (occurrence.syncTarget && o.syncSource))
    );

    if (occurrence.syncSource && occurrence.syncTarget) {
      return "sync-two-way";
    } else if (occurrence.syncSource) {
      return hasPartner ? "sync-active-source" : "sync-pending";
    } else if (occurrence.syncTarget) {
      return hasPartner ? "sync-active-target" : "sync-pending";
    }
    return "sync-disabled";
  };

  return (
    <div>
      <h1>Synchronization</h1>
      <p className="center-muted">
        Configure synchronization between directories with the same name across different storages.
      </p>

      {loading ? (
        <p className="center-muted">Loading sync configuration…</p>
      ) : syncGroups.length === 0 ? (
        <p className="center-muted">
          No directories found in multiple storages. Add directories with the same name to different storages to enable synchronization.
        </p>
      ) : (
        syncGroups.map(group => (
          <div key={group.dirName} className="sync-group">
            <h2>
              {group.dirName}
              <span className="sync-group-status">
                {getSyncDescription(group.occurrences)}
              </span>
            </h2>
            <table className="storages-table">
              <thead>
                <tr>
                  <th>Storage</th>
                  <th>Path</th>
                  <th>Direction</th>
                  <th>Sync Source</th>
                  <th>Sync Target</th>
                </tr>
              </thead>
              <tbody>
                {group.occurrences.map(occurrence => {
                  const statusClass = getSyncStatusClass(occurrence, group.occurrences);
                  let directionLabel = "—";
                  if (occurrence.syncSource && occurrence.syncTarget) {
                    directionLabel = "Two-way";
                  } else if (occurrence.syncSource) {
                    directionLabel = "Outgoing →";
                  } else if (occurrence.syncTarget) {
                    directionLabel = "← Incoming";
                  }

                  return (
                    <tr key={`${group.dirName}-${occurrence.storageName}`} className={statusClass}>
                      <td>{occurrence.storageName}</td>
                      <td>{occurrence.path}</td>
                      <td className="sync-direction">{directionLabel}</td>
                      <td>
                        <label className="sync-checkbox">
                          <input
                            type="checkbox"
                            checked={occurrence.syncSource}
                            onChange={e => handleSyncChange(group.dirName, occurrence.storageName, "syncSource", e.target.checked)}
                          />
                          <span>Push changes</span>
                        </label>
                      </td>
                      <td>
                        <label className="sync-checkbox">
                          <input
                            type="checkbox"
                            checked={occurrence.syncTarget}
                            onChange={e => handleSyncChange(group.dirName, occurrence.storageName, "syncTarget", e.target.checked)}
                          />
                          <span>Pull changes</span>
                        </label>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        ))
      )}
    </div>
  );
}
