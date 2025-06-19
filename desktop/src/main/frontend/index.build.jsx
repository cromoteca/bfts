import React from "react";
import { createRoot } from "react-dom/client";
import App from "./App.tsx";

const originalLog = console.log;
const originalError = console.error;
const originalWarn = console.warn;

console.log = function (...args) {
  if (typeof window.logToJava === 'function') {
    window.logToJava('LOG', ...args);
  } else {
    originalLog.apply(console, args);
  }
};
console.error = function (...args) {
  if (typeof window.logToJava === 'function') {
    window.logToJava('ERROR', ...args);
  } else {
    originalError.apply(console, args);
  }
};
console.warn = function (...args) {
  if (typeof window.logToJava === 'function') {
    window.logToJava('WARN', ...args);
  } else {
    originalWarn.apply(console, args);
  }
};

const root = createRoot(document.getElementById("root"));
root.render(<App />);
