/*
 * Copyright (C) 2014-2019 Luciano Vernaschi (luciano at cromoteca.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.cromoteca.bfts;

import android.content.SharedPreferences;
import android.databinding.BaseObservable;
import android.databinding.Bindable;

public class ConfigBean extends BaseObservable {
    public static final String SHARED_PREFERENCES_NAME = "BackupPreferences";
    private final SharedPreferences prefs;

    public ConfigBean(SharedPreferences prefs) {
        if (prefs == null) {
            throw new NullPointerException();
        }

        this.prefs = prefs;
    }

    @Bindable
    public String getClientName() {
        return prefs.getString("clientName", "");
    }

    public void setClientName(String name) {
        prefs.edit().putString("clientName", name).commit();
    }

    @Bindable
    public String getPassword() {
        return prefs.getString("password", "");
    }

    public void setPassword(String password) {
        prefs.edit().putString("password", password).commit();
    }

    @Bindable
    public String getServerName() {
        return prefs.getString("serverName", "");
    }

    public void setServerName(String serverName) {
        prefs.edit().putString("serverName", serverName).commit();
    }

    @Bindable
    public int getServerPort() {
        return Integer.parseInt(prefs.getString("serverPort", "0"));
    }

    public void setServerPort(int serverPort) {
        prefs.edit().putString("serverPort", Integer.toString(serverPort)).commit();
    }

    @Bindable
    public boolean isRequireCharging() {
        return prefs.getBoolean("requireCharging", true);
    }

    public void setRequireCharging(boolean requireCharging) {
        prefs.edit().putBoolean("requireCharging", requireCharging).commit();
    }

    @Bindable
    public boolean isRequireWiFi() {
        return prefs.getBoolean("requireWiFi", true);
    }

    public void setRequireWiFi(boolean requireWiFi) {
        prefs.edit().putBoolean("requireWiFi", requireWiFi).commit();
    }

    public long getLastTrashCollectionDay() {
        return prefs.getLong("lastTrashCollectionDay", 0);
    }

    public void setLastTrashCollectionDay(long lastTrashCollectionDay) {
        prefs.edit().putLong("lastTrashCollectionDay", lastTrashCollectionDay).commit();
    }
}
