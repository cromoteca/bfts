import React, { createContext, useContext } from "react";

export const NotificationContext = createContext(null);

export function useNotification() {
  return useContext(NotificationContext);
}
