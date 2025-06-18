export function initializeMockBridge() {
  if (typeof window.invoke !== "function") {
    window.invoke = (method, params) => {
      if (method === "list") {
        return JSON.stringify({
          localStorages: [
            { name: "Mock Local", path: "/tmp/mock", port: 1234 },
            { name: "Another Local", path: "/tmp/another", port: null },
            { name: "Third Local", path: "/tmp/third", port: null }
          ],
          connectedStorages: [
            { name: "Mock Connected", path: "/mnt/mock", encryption: "none" },
            { name: "Another Connected", path: "/mnt/another", encryption: "data" },
            { name: "Third Connected", path: "/mnt/third", encryption: "full" }
          ]
        });
      }
      return "{}";
    };
  }
}
