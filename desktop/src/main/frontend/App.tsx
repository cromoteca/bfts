import React, { useState } from "react";
import Dashboard from "./views/Dashboard.jsx";
import Backups from "./views/Backups.jsx";
import Storages from "./views/Storages.jsx";
import Settings from "./views/Settings.jsx";

function App() {

  const [currentView, setCurrentView] = useState('storages');

  const menuItems = [
    { key: 'dashboard', label: 'Dashboard' },
    { key: 'backups', label: 'Backups' },
    { key: 'storages', label: 'Storages' },
    { key: 'settings', label: 'Settings' },
  ];

  return (
    <>
      <nav>
        {menuItems.map(item => (
          <a
            key={item.key}
            href="#"
            onClick={() => setCurrentView(item.key)}
            className={currentView === item.key ? 'active' : ''}
          >
            {item.label}
          </a>
        ))}
      </nav>
      <main>
        {currentView === 'dashboard' && <Dashboard />}
        {currentView === 'backups' && <Backups />}
        {currentView === 'storages' && <Storages />}
        {currentView === 'settings' && <Settings />}
      </main>
      <footer>
        &copy; {new Date().getFullYear()} BFTS. All rights reserved.
      </footer>
    </>
  );
}

export default App;
