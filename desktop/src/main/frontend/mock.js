export function initializeMockBridge() {
  let localStorages = [
    { name: "Mock Local", path: "/mnt/mock", port: 1234 },
    { name: "Another Local", path: "/mnt/another", port: null },
    { name: "Third Local", path: "/mnt/third", port: null }
  ];
  const connectedStorages = [
    { name: "Mock Connected", path: "192.168.2.15:5757", encryption: "NONE" },
    { name: "Another Connected", path: "/mnt/mock", encryption: "DATA" },
    { name: "Third Connected", path: "backup.example.com:8686", encryption: "FULL" }
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
      const [ name, port ] = params || [];
      const storage = localStorages.find(s => s.name === name);
      storage.port = port;
      return `Local storage ${name} published on port ${port}`;
    }
  };

  window.openDirectoryPicker = () => {
    return "/mnt/new-directory";
  };
}
