export function initializeMockBridge() {
  window.invoke = (method, params) => {
    if (method === "list") {
      return JSON.stringify({
        localStorages: [
          { name: "Mock Local", path: "/mnt/mock", port: 1234 },
          { name: "Another Local", path: "/mnt/another", port: null },
          { name: "Third Local", path: "/mnt/third", port: null }
        ],
        connectedStorages: [
          { name: "Mock Connected", path: "192.168.2.15:5757", encryption: "NONE" },
          { name: "Another Connected", path: "backup:1234", encryption: "DATA" },
          { name: "Third Connected", path: "backup.example.com:8686", encryption: "FULL" }
        ]
      });
    }
    return "{}";
  };
  window.openDirectoryPicker = () => {
    return "/mnt/new-directory";
  };
}
