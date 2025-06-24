export function initializeMockBridge() {
  window.invoke = (method, params) => {
    // `list` has no parameters
    if (method === "list") {
      return JSON.stringify({
        localStorages: [
          { name: "Mock Local", path: "/mnt/mock", port: 1234 },
          { name: "Another Local", path: "/mnt/another", port: null },
          { name: "Third Local", path: "/mnt/third", port: null }
        ],
        connectedStorages: [
          { name: "Mock Connected", path: "192.168.2.15:5757", encryption: "NONE" },
          { name: "Another Connected", path: "/mnt/mock", encryption: "DATA" },
          { name: "Third Connected", path: "backup.example.com:8686", encryption: "FULL" }
        ]
      });
    } else if (method === "init") {
      // `init` has 3 parameters: name (string), path (string), inMemory (boolean)
      const [ name, path, inMemory ] = params || [];
      return `Local storage ${name} initialized in directory ${path}`;
    }
  };
  window.openDirectoryPicker = () => {
    return "/mnt/new-directory";
  };
}
