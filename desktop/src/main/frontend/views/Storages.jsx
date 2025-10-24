import React, { useCallback, useEffect, useState } from "react";
import { useNotification } from "../NotificationContext.jsx";
import PasswordDialog from "../components/PasswordDialog.jsx";

const ENCRYPTION_LABELS = {
  NONE: "No encryption",
  DATA: "Data only",
  FULL: "Data and filenames",
};

export default function Storages() {
  const [localStorages, setLocalStorages] = useState([]);
  const [connectedStorages, setConnectedStorages] = useState([]);
  const [newLocalStorage, setNewLocalStorage] = useState({ name: '', path: '', inMemory: false });
  const [newConnectedStorage, setNewConnectedStorage] = useState({ 
    name: '', 
    path: '', 
    encryption: 'NONE', 
    transmissionPassword: '', 
    fileEncryptionPassword: '' 
  });
  const [portInputs, setPortInputs] = useState({});
  const [showPasswordDialog, setShowPasswordDialog] = useState(false);
  const [runningStatus, setRunningStatus] = useState({});
  const notify = useNotification();

  const fetchControlStatus = useCallback(() => {
    if (typeof window.invoke !== "function") {
      return;
    }

    try {
      const response = window.invoke('controlStatus');
      if (!response) {
        return;
      }
      const parsed = JSON.parse(response);
      const states = {};
      if (parsed && Array.isArray(parsed.storages)) {
        parsed.storages.forEach(storage => {
          if (storage && storage.name) {
            states[storage.name] = !!storage.running;
          }
        });
      }
      setRunningStatus(states);
    } catch (err) {
      console.error('Failed to fetch control status', err);
    }
  }, []);

  const reloadStorages = useCallback(() => {
    const result = window.invoke('list');
    try {
      const parsed = JSON.parse(result);
      setLocalStorages(parsed.localStorages || []);
      setConnectedStorages(parsed.connectedStorages || []);
    } catch {
      setLocalStorages([]);
      setConnectedStorages([]);
    }
    fetchControlStatus();
  }, [fetchControlStatus]);

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
    setNewConnectedStorage(s => ({ 
      ...s, 
      path, 
      name, 
      transmissionPassword: '', 
      fileEncryptionPassword: '' 
    }));
  };

  useEffect(() => {
    reloadStorages();
  }, [reloadStorages]);

  useEffect(() => {
    const interval = setInterval(fetchControlStatus, 5000);
    return () => clearInterval(interval);
  }, [fetchControlStatus]);

  // Handler for adding a new local storage
  const handleAddLocalStorage = () => {
    if (!newLocalStorage.name.trim() || !newLocalStorage.path.trim()) return;
    const result = window.invoke('init', [newLocalStorage.name, newLocalStorage.path, newLocalStorage.inMemory]);
    if (notify) notify(result);
    setNewLocalStorage({ name: '', path: '', inMemory: false });
    reloadStorages();
  };

  // Handler for adding a new connected storage
  const handleAddConnectedStorage = () => {
    if (!newConnectedStorage.name.trim() || !newConnectedStorage.path.trim()) return;
    
    // Open password dialog
    setShowPasswordDialog(true);
  };

  // Handler for confirming password dialog
  const handleConfirmPasswords = (passwords) => {
    // Call backend with passwords
    const result = window.invoke('connect', [
      newConnectedStorage.name, 
      newConnectedStorage.path, 
      newConnectedStorage.encryption,
      passwords.transmissionPassword,
      passwords.fileEncryptionPassword,
    ]);
    
    if (notify) notify(result);
    
    // Reset form and close dialog
    setNewConnectedStorage({ 
      name: '', 
      path: '', 
      encryption: 'NONE', 
      transmissionPassword: '', 
      fileEncryptionPassword: '' 
    });
    setShowPasswordDialog(false);
    reloadStorages();
  };

  // Handler for canceling password dialog
  const handleCancelPasswords = () => {
    setShowPasswordDialog(false);
  };

  // Handler for port input change
  const handlePortInputChange = (path, value) => {
    setPortInputs(inputs => ({ ...inputs, [path]: value }));
  };

  // Handler for publish
  const handlePublish = (name, path, port) => {
    const result = window.invoke('publish', [name, port]);
    if (notify) notify(result);
    reloadStorages();
  };

  const handleStartStorage = (name) => {
    if (!name) return;
    try {
      window.invoke('start', [name]);
      if (notify) notify(`Started backup for ${name}`);
    } catch (err) {
      console.error('Failed to start backup', err);
      if (notify) notify(`Failed to start backup for ${name}`, 'error');
    } finally {
      fetchControlStatus();
    }
  };

  const handleStopStorage = (name) => {
    if (!name) return;
    try {
      window.invoke('stop', [name]);
      if (notify) notify(`Stopped backup for ${name}`);
    } catch (err) {
      console.error('Failed to stop backup', err);
      if (notify) notify(`Failed to stop backup for ${name}`, 'error');
    } finally {
      fetchControlStatus();
    }
  };

  const handleCompleteStorage = (name) => {
    if (!name) return;
    try {
      window.invoke('complete', [name]);
      if (notify) notify(`Complete backup started for ${name}`);
    } catch (err) {
      console.error('Failed to run complete backup', err);
      if (notify) notify(`Failed to run complete backup for ${name}`, 'error');
    }
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
                        value={portInputs[storage.path] || ''}
                        onChange={e => handlePortInputChange(storage.path, e.target.value)}
                      />
                      <button className="btn-small" disabled={!Number(portInputs[storage.path])} onClick={() => handlePublish(storage.name, storage.path, portInputs[storage.path])}>
                        Publish
                      </button>
                    </div>
                  )}
                </td>
                <td>
                  {!isPathConnected(storage.path) && (
                    <button type="button" className="btn-small" onClick={() => handleConnectLocal(storage.path, storage.name)}>
                      Copy to Connected
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
            <th>Status</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          {connectedStorages.length === 0 ? (
            <tr>
              <td colSpan={5} className="center-muted">
                No connected storages found.
              </td>
            </tr>
          ) : (
            connectedStorages.map((storage, idx) => {
              const running = !!runningStatus[storage.name];
              const color = running ? '#2ecc71' : '#e74c3c';
              return (
                <tr key={idx}>
                  <td>{storage.name}</td>
                  <td>{storage.path}</td>
                  <td>{ENCRYPTION_LABELS[storage.encryption] || storage.encryption}</td>
                  <td>
                    <span style={{ color, fontWeight: 600 }}>
                      {running ? 'Running' : 'Stopped'}
                    </span>
                  </td>
                  <td>
                    <div className="flex-center-gap" style={{ flexWrap: 'wrap', gap: '0.5rem' }}>
                      <button
                        type="button"
                        className="btn-small"
                        disabled={running}
                        onClick={() => handleStartStorage(storage.name)}
                      >
                        Start
                      </button>
                      <button
                        type="button"
                        className="btn-small"
                        disabled={!running}
                        onClick={() => handleStopStorage(storage.name)}
                      >
                        Stop
                      </button>
                      <button
                        type="button"
                        className="btn-small"
                        disabled={running}
                        onClick={() => handleCompleteStorage(storage.name)}
                      >
                        Complete
                      </button>
                    </div>
                  </td>
                </tr>
              );
            })
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
            <td className="center-muted">—</td>
            <td>
              <button 
                type="button" 
                className="btn-small ml-0" 
                onClick={handleAddConnectedStorage}
                disabled={!newConnectedStorage.name.trim() || !newConnectedStorage.path.trim()}
              >
                Add
              </button>
            </td>
          </tr>
        </tbody>
      </table>

      <PasswordDialog
        isOpen={showPasswordDialog}
        storageName={newConnectedStorage.name}
        encryptionType={newConnectedStorage.encryption}
        onConfirm={handleConfirmPasswords}
        onCancel={handleCancelPasswords}
      />
    </div>
  );
}
