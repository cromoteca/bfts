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
      pushLog("Backup scheduler requested start");
      return "null";
    } else if (method === "stop") {
      pushLog("Backup scheduler requested stop");
      return "null";
    }

    return "null";
  };

  window.openDirectoryPicker = () => {
    return "/mnt/new-directory";
  };
}
