import React, { useEffect, useState } from "react";

const ENCRYPTION_LABELS = {
  NONE: "No encryption",
  DATA: "Data only",
  FULL: "Data and filenames",
};

export default function Storages() {
  const [storages, setStorages] = useState({ localStorages: [], connectedStorages: [] });
  const [newStorage, setNewStorage] = useState({ name: '', path: '' });
  const [newConnectedStorage, setNewConnectedStorage] = useState(null);

  useEffect(() => {
    // Fetch storages on mount
    const result = window.invoke('list');
    try {
      setStorages(JSON.parse(result));
    } catch {
      setStorages({ localStorages: [], connectedStorages: [] });
    }
  }, []);

  const handlePickDirectory = async () => {
    if (window.openDirectoryPicker) {
      const dir = await window.openDirectoryPicker();
      if (dir) setNewStorage(s => ({ ...s, path: dir }));
    }
  };

  return (
    <div>
      <h1>Storages</h1>
      <h2>Local Storages</h2>
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
          {storages.localStorages.length === 0 ? (
            <tr>
              <td colSpan={4} style={{ textAlign: 'center', color: '#888' }}>
                No local storages found.
              </td>
            </tr>
          ) : (
            storages.localStorages.map((storage, idx) => (
              <tr key={idx}>
                <td>{storage.name}</td>
                <td>{storage.path}</td>
                <td>
                  {storage.port !== null ? (
                    storage.port
                  ) : (
                    <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                      <input
                        type="number"
                        min={1}
                        max={65535}
                        style={{ width: 70, fontSize: 12, padding: 2 }}
                        placeholder="Port"
                      />
                      <button style={{ fontSize: 12, padding: '2px 8px' }}>Publish</button>
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
                value={newStorage.name}
                onChange={e => setNewStorage(s => ({ ...s, name: e.target.value }))}
                placeholder="Name"
                style={{ width: 100, fontSize: 12, padding: 2 }}
              />
            </td>
            <td>
              <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                <input
                  type="text"
                  value={newStorage.path}
                  readOnly
                  placeholder="Path"
                  style={{ width: 180, fontSize: 12, padding: 2 }}
                />
                <button type="button" style={{ fontSize: 12, padding: '2px 8px' }} onClick={handlePickDirectory}>
                  Browse
                </button>
              </div>
            </td>
            <td />
            <td>
              <button type="button" style={{ fontSize: 12, padding: '2px 8px' }}>
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
          {storages.connectedStorages.length === 0 ? (
            <tr>
              <td colSpan={4} style={{ textAlign: 'center', color: '#888' }}>
                No connected storages found.
              </td>
            </tr>
          ) : (
            storages.connectedStorages.map((storage, idx) => (
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
                value={newConnectedStorage?.name || ''}
                onChange={e => setNewConnectedStorage(s => ({ ...s, name: e.target.value }))}
                placeholder="Name"
                style={{ width: 100, fontSize: 12, padding: 2 }}
              />
            </td>
            <td>
              <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                <input
                  type="text"
                  value={newConnectedStorage?.host || ''}
                  onChange={e => setNewConnectedStorage(s => ({ ...s, host: e.target.value }))}
                  placeholder="Host"
                  style={{ width: 100, fontSize: 12, padding: 2 }}
                />
                <span>:</span>
                <input
                  type="number"
                  min={1}
                  max={65535}
                  value={newConnectedStorage?.port || ''}
                  onChange={e => setNewConnectedStorage(s => ({ ...s, port: e.target.value }))}
                  placeholder="Port"
                  style={{ width: 60, fontSize: 12, padding: 2 }}
                />
              </div>
            </td>
            <td>
              <select
                value={newConnectedStorage?.encryption || 'NONE'}
                onChange={e => setNewConnectedStorage(s => ({ ...s, encryption: e.target.value }))}
                style={{ fontSize: 12, padding: 2 }}
              >
                <option value="NONE">No encryption</option>
                <option value="DATA">Data only</option>
                <option value="FULL">Data and filenames</option>
              </select>
            </td>
            <td>
              <button type="button" style={{ fontSize: 12, padding: '2px 8px', marginLeft: 0 }}>
                Add
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  );
}
