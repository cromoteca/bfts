import React, { useEffect, useState } from "react";
import { useNotification } from "../NotificationContext.jsx";

const ENCRYPTION_LABELS = {
  NONE: "No encryption",
  DATA: "Data only",
  FULL: "Data and filenames",
};

export default function Storages() {
  const [localStorages, setLocalStorages] = useState([]);
  const [connectedStorages, setConnectedStorages] = useState([]);
  const [newLocalStorage, setNewLocalStorage] = useState({ name: '', path: '', inMemory: false });
  const [newConnectedStorage, setNewConnectedStorage] = useState({ name: '', path: '', encryption: 'NONE' });
  const notify = useNotification();

  useEffect(() => {
    reloadStorages();
  }, []);

  const handlePickDirectory = async (e) => {
    const type = e.currentTarget.getAttribute('data-type');
    if (window.openDirectoryPicker) {
      const dir = await window.openDirectoryPicker();
      if (dir) {
        const dirName = (dir.replace(/\\|\//g, '/').split('/').filter(Boolean).pop()) || '';
        if (type === 'local') {
          setNewLocalStorage(s => ({
            ...s,
            path: dir,
            name: s.name ? s.name : dirName
          }));
        } else if (type === 'connected') {
          setNewConnectedStorage(s => ({
            ...s,
            path: dir,
            name: s.name ? s.name : dirName
          }));
        }
      }
    }
  };

  // Helper to check if a local storage path is already connected
  const isPathConnected = (path) => connectedStorages.some(cs => cs.path === path);

  // Handler to copy local storage path and name to newConnectedStorage
  const handleConnectLocal = (path, name) => {
    setNewConnectedStorage(s => ({ ...s, path, name }));
  };

  // Helper to reload storages list
  const reloadStorages = () => {
    const result = window.invoke('list');
    try {
      const parsed = JSON.parse(result);
      setLocalStorages(parsed.localStorages || []);
      setConnectedStorages(parsed.connectedStorages || []);
    } catch {
      setLocalStorages([]);
      setConnectedStorages([]);
    }
  };

  // Handler for adding a new local storage
  const handleAddLocalStorage = () => {
    if (!newLocalStorage.name.trim() || !newLocalStorage.path.trim()) return;
    const result = window.invoke('init', [newLocalStorage.name, newLocalStorage.path, newLocalStorage.inMemory]);
    if (notify) notify(result);
    setNewLocalStorage({ name: '', path: '', inMemory: false });
    reloadStorages();
  };

  return (
    <div>
      <h1>Storages</h1>
      <h2>Known Local Storages</h2>
      <table className="storages-table">
        <thead>
          <tr>
            <th>Name</th>
            <th>Path</th>
            <th>Port</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {localStorages.length === 0 ? (
            <tr>
              <td colSpan={4} className="center-muted">
                No local storages found.
              </td>
            </tr>
          ) : (
            localStorages.map((storage, idx) => (
              <tr key={idx}>
                <td>{storage.name}</td>
                <td>{storage.path}</td>
                <td>
                  {storage.port !== null ? (
                    storage.port
                  ) : (
                    <div className="flex-center-gap">
                      <input
                        type="number"
                        min={1}
                        max={65535}
                        className="input-small"
                        placeholder="Port"
                      />
                      <button className="btn-small">Publish</button>
                    </div>
                  )}
                </td>
                <td>
                  {!isPathConnected(storage.path) && (
                    <button type="button" className="btn-small" onClick={() => handleConnectLocal(storage.path, storage.name)}>
                      Connect
                    </button>
                  )}
                </td>
              </tr>
            ))
          )}
          <tr>
            <td>
              <input
                type="text"
                value={newLocalStorage.name}
                onChange={e => setNewLocalStorage(s => ({ ...s, name: e.target.value }))}
                placeholder="Name"
                className="input-medium"
              />
            </td>
            <td>
              <div className="flex-center-gap">
                <input
                  type="text"
                  value={newLocalStorage.path}
                  onChange={e => setNewLocalStorage(s => ({ ...s, path: e.target.value }))}
                  placeholder="Path"
                  className="input-large"
                />
                <button type="button" className="btn-small" data-type="local" onClick={handlePickDirectory}>
                  Browse
                </button>
              </div>
            </td>
            <td>
              <label style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                <input
                  type="checkbox"
                  checked={!!newLocalStorage.inMemory}
                  onChange={e => setNewLocalStorage(s => ({ ...s, inMemory: e.target.checked }))}
                />
                In memory
              </label>
            </td>
            <td>
              <button type="button" className="btn-small" onClick={handleAddLocalStorage} disabled={!newLocalStorage.name.trim() || !newLocalStorage.path.trim()}>
                Add
              </button>
            </td>
          </tr>
        </tbody>
      </table>

      <h2>Connected Storages</h2>
      <table className="storages-table">
        <thead>
          <tr>
            <th>Name</th>
            <th>Path</th>
            <th>Encryption</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {connectedStorages.length === 0 ? (
            <tr>
              <td colSpan={4} className="center-muted">
                No connected storages found.
              </td>
            </tr>
          ) : (
            connectedStorages.map((storage, idx) => (
              <tr key={idx}>
                <td>{storage.name}</td>
                <td>{storage.path}</td>
                <td>{ENCRYPTION_LABELS[storage.encryption] || storage.encryption}</td>
                <td />
              </tr>
            ))
          )}
          <tr>
            <td>
              <input
                type="text"
                value={newConnectedStorage.name}
                onChange={e => setNewConnectedStorage(s => ({ ...s, name: e.target.value }))}
                placeholder="Name"
                className="input-medium"
              />
            </td>
            <td>
              <div className="flex-center-gap">
                <input
                  type="text"
                  value={newConnectedStorage.path}
                  onChange={e => setNewConnectedStorage(s => ({ ...s, path: e.target.value }))}
                  placeholder="Path or Host:Port"
                  className="input-large"
                />
                <button type="button" className="btn-small" data-type="connected" onClick={handlePickDirectory}>
                  Browse
                </button>
              </div>
            </td>
            <td>
              <select
                value={newConnectedStorage.encryption}
                onChange={e => setNewConnectedStorage(s => ({ ...s, encryption: e.target.value }))}
                className="select-small"
              >
                <option value="NONE">No encryption</option>
                <option value="DATA">Data only</option>
                <option value="FULL">Data and filenames</option>
              </select>
            </td>
            <td>
              <button type="button" className="btn-small ml-0">
                Add
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  );
}
