package com.exteragram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

import com.exteragram.messenger.config.PrefEnum;
import com.exteragram.messenger.export.ExportSettings;

import org.telegram.messenger.ApplicationLoader;

import kotlin.reflect.KProperty;

private fun exportPrefs(): SharedPreferences =
    ApplicationLoader.applicationContext.getSharedPreferences("exteraconfig", Context.MODE_PRIVATE)

private class ExportIntPref(private val def: Int) {
    operator fun getValue(thisRef: Any?, prop: KProperty<*>): Int =
        exportPrefs().getInt(prop.name, def)

    operator fun setValue(thisRef: Any?, prop: KProperty<*>, value: Int) {
        exportPrefs().edit().putInt(prop.name, value).apply()
    }
}

private class ExportLongPref(private val def: Long) {
    operator fun getValue(thisRef: Any?, prop: KProperty<*>): Long =
        exportPrefs().getLong(prop.name, def)

    operator fun setValue(thisRef: Any?, prop: KProperty<*>, value: Long) {
        exportPrefs().edit().putLong(prop.name, value).apply()
    }
}

private class ExportStringPref(private val def: String) {
    operator fun getValue(thisRef: Any?, prop: KProperty<*>): String =
        exportPrefs().getString(prop.name, def) ?: def

    operator fun setValue(thisRef: Any?, prop: KProperty<*>, value: String) {
        exportPrefs().edit().putString(prop.name, value).apply()
    }
}

private class ExportEnumPref<E>(private val def: E) where E : Enum<E>, E : PrefEnum {
    operator fun getValue(thisRef: Any?, prop: KProperty<*>): E {
        val id = exportPrefs().getInt(prop.name, def.getPrefId())
        return def.declaringJavaClass.enumConstants?.firstOrNull { it.getPrefId() == id } ?: def
    }

    operator fun setValue(thisRef: Any?, prop: KProperty<*>, value: E) {
        exportPrefs().edit().putInt(prop.name, value.getPrefId()).apply()
    }
}

var exportTypes by ExportIntPref(ExportSettings.DefaultTypes)
var exportFullChats by ExportIntPref(ExportSettings.DefaultFullChats)
var exportMediaTypes by ExportIntPref(ExportSettings.MediaSettings.Photo)
var exportSizeLimit by ExportLongPref(8L * 1024 * 1024)
var exportFormat by ExportEnumPref(ExportSettings.Format.Html)
var exportFolder by ExportStringPref("")
