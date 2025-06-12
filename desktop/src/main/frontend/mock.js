export function initializeMockBridge() {
  if (typeof window.invoke !== "function") {
    window.invoke = (method, params) => {
      if (method === "list") {
        return JSON.stringify({
          localStorages: [
            { name: "Mock Local", path: "/tmp/mock", port: 1234 }
          ],
          connectedStorages: [
            { name: "Mock Connected", path: "/mnt/mock", encryption: "none" }
          ]
        });
      }
      return "{}";
    };
  }
}
