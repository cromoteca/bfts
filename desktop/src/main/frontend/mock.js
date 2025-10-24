export function initializeMockBridge() {
  let localStorages = [
    { name: "Mock Local", path: "/mnt/mock", port: 1234 },
    { name: "Another Local", path: "/mnt/another", port: null },
    { name: "Third Local", path: "/mnt/third", port: null }
  ];
  const connectedStorages = [
    { 
      name: "Mock Connected", 
      path: "192.168.2.15:5757", 
      encryption: "NONE", 
      transmissionPassword: "tx123" 
    },
    { 
      name: "Another Connected", 
      path: "/mnt/mock", 
      encryption: "DATA", 
      transmissionPassword: "secure789", 
      fileEncryptionPassword: "encrypt321" 
    },
    { 
      name: "Third Connected", 
      path: "backup.example.com:8686", 
      encryption: "FULL", 
      transmissionPassword: "remote999", 
      fileEncryptionPassword: "files777" 
    }
  ];
  const logEntries = [];
  let lastLogId = 0;
  let controlPort = 8615;
  let controlOwner = true;
  const runningState = Object.fromEntries(connectedStorages.map(s => [s.name, false]));
  const completingState = {};

  const pushLog = (message, stream = "stdout") => {
    const entry = {
      id: ++lastLogId,
      timestamp: Date.now(),
      stream,
      message,
    };
    logEntries.push(entry);
    if (logEntries.length > 1000) {
      logEntries.shift();
    }
  };

  pushLog("Mock bridge initialized");

  window.invoke = (method, params) => {
    if (method === "list") {
      pushLog("Listing storages");
      return JSON.stringify({
        localStorages,
        connectedStorages
      });
    } else if (method === "init") {
      const [ name, path ] = params || [];
      localStorages.push({ name, path, port: null });
      pushLog(`Local storage ${name} initialized in directory ${path}`);
      return `Local storage ${name} initialized in directory ${path}`;
    } else if (method === "publish") {
      const [ name, port, password ] = params || [];
      const storage = localStorages.find(s => s.name === name);
      storage.port = port;
      pushLog(`Local storage ${name} published on port ${port}`);
      return `Local storage ${name} published on port ${port} with password ${password}`;
    } else if (method === "connect") {
      const [ name, path, encryption, transmissionPassword, fileEncryptionPassword ] = params || [];
      connectedStorages.push({ 
        name, 
        path, 
        encryption, 
        transmissionPassword, 
        fileEncryptionPassword 
      });
      pushLog(`Connected to storage ${name} at ${path}`);
      runningState[name] = false;
      return `Connected to storage ${name} at ${path}`;
    } else if (method === "logs") {
      const [ afterId = 0 ] = params || [];
      const entries = logEntries.filter(entry => entry.id > afterId);
      const lastId = logEntries.length > 0 ? logEntries[logEntries.length - 1].id : afterId;
      return JSON.stringify({
        available: true,
        lastId,
        entries,
      });
    } else if (method === "start") {
      const [ name ] = params || [];
      if (name) {
        runningState[name] = true;
        pushLog(`Backup scheduler requested start for ${name}`);
      } else {
        Object.keys(runningState).forEach(key => runningState[key] = true);
        pushLog("Backup scheduler requested start for all storages");
      }
      return "null";
    } else if (method === "stop") {
      const [ name ] = params || [];
      if (name) {
        runningState[name] = false;
        pushLog(`Backup scheduler requested stop for ${name}`);
      } else {
        Object.keys(runningState).forEach(key => runningState[key] = false);
        pushLog("Backup scheduler requested stop for all storages");
      }
      return "null";
    } else if (method === "complete") {
      const [ name ] = params || [];
      if (name) {
        if (completingState[name]) {
          return "null";
        }
        completingState[name] = true;
        pushLog(`Complete backup requested for ${name}`);
        const duration = 2000 + Math.random() * 5000;
        setTimeout(() => {
          delete completingState[name];
          pushLog(`Complete backup finished for ${name}`);
          if (runningState[name]) {
            runningState[name] = false;
            pushLog(`Scheduler set to stopped for ${name} after complete`);
          }
        }, duration);
      } else {
        Object.keys(runningState).forEach(storageName => {
          if (!completingState[storageName]) {
            completingState[storageName] = true;
            pushLog(`Complete backup requested for ${storageName}`);
            const duration = 2000 + Math.random() * 5000;
            setTimeout(() => {
              delete completingState[storageName];
              pushLog(`Complete backup finished for ${storageName}`);
              if (runningState[storageName]) {
                runningState[storageName] = false;
                pushLog(`Scheduler set to stopped for ${storageName} after complete`);
              }
            }, duration);
          }
        });
      }
      return "null";
    } else if (method === "controlStatus") {
      return JSON.stringify({
        port: controlPort,
        configuredPort: controlPort,
        owner: controlOwner,
        storages: Object.keys(runningState).map(name => ({
          name,
          running: !!runningState[name],
          completing: !!completingState[name],
        })),
      });
    } else if (method === "controlPort") {
      const [ newPort ] = params || [];
      if (Number.isInteger(newPort)) {
        controlPort = newPort;
        pushLog(`Control port changed to ${newPort}`);
      }
      return "null";
    }

    return "null";
  };

  window.openDirectoryPicker = () => {
    return "/mnt/new-directory";
  };
}
