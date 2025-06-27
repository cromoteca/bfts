import React from "react";
import { createRoot } from "react-dom/client";
import App from "./App.jsx";
import { initializeMockBridge } from "./mock.js";

initializeMockBridge();

const root = createRoot(document.getElementById("root"));
root.render(<App />);
