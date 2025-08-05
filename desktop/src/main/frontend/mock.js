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

  window.invoke = (method, params) => {
    if (method === "list") {
      return JSON.stringify({
        localStorages,
        connectedStorages
      });
    } else if (method === "init") {
      const [ name, path ] = params || [];
      localStorages.push({ name, path, port: null });
      return `Local storage ${name} initialized in directory ${path}`;
    } else if (method === "publish") {
      const [ name, port, password ] = params || [];
      const storage = localStorages.find(s => s.name === name);
      storage.port = port;
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
      return `Connected to storage ${name} at ${path}`;
    }
  };

  window.openDirectoryPicker = () => {
    return "/mnt/new-directory";
  };
}
