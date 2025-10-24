import React, { useState, useCallback } from "react";
import Dashboard from "./views/Dashboard.jsx";
import Storages from "./views/Storages.jsx";
import { NotificationContext } from "./NotificationContext.jsx";

function App() {
  const [currentView, setCurrentView] = useState('dashboard');
  const [notifications, setNotifications] = useState([]);

  // Add a notification (type: 'success' | 'error')
  const addNotification = useCallback((message, type = 'success') => {
    const id = Date.now() + Math.random();
    setNotifications(n => [...n, { id, message, type }]);
    if (type === 'error') {
      console.error(message);
    } else {
      console.log(message);
    }
    setTimeout(() => {
      setNotifications(n => n.filter(notif => notif.id !== id));
    }, 4000);
  }, []);

  const menuItems = [
    { key: 'dashboard', label: 'Dashboard' },
    { key: 'storages', label: 'Storages' },
  ];

  return (
    <NotificationContext.Provider value={addNotification}>
      {/* Notification area */}
      <div className="notification-area">
        {notifications.map(n => (
          <div
            key={n.id}
            className={`notification ${n.type}`}
          >
            {n.message}
          </div>
        ))}
      </div>
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
        {currentView === 'storages' && <Storages />}
      </main>
      <footer>
        &copy; {new Date().getFullYear()} BFTS. All rights reserved.
      </footer>
    </NotificationContext.Provider>
  );
}

export default App;
