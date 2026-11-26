package app.exteraless.math

import android.icu.text.TimeZoneFormat
import android.icu.text.TimeZoneNames
import android.icu.util.TimeZone
import android.icu.util.ULocale
import android.os.Build

import org.telegram.messenger.FileLog
import org.telegram.messenger.LocaleController
import org.telegram.messenger.Utilities

import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

object InlineZonePlaces {

    private const val MIN_NAME_LENGTH = 3

    private val marks = Regex("\\p{Mn}+")
    private val building = AtomicBoolean()

    @Volatile
    private var index: Map<String, String>? = null

    @Volatile
    private var indexLocale: Locale? = null

    @JvmStatic
    fun lookup(name: String): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return null
        }
        val built = index
        if (built == null || LocaleController.getInstance().currentLocale != indexLocale) {
            prepare()
        }
        return built?.get(name)
    }

    @JvmStatic
    fun prepare() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return
        }
        val locale = LocaleController.getInstance().currentLocale
        if (index != null && locale == indexLocale || !building.compareAndSet(false, true)) {
            return
        }
        Utilities.globalQueue.postRunnable {
            val built = try {
                build(locale)
            } catch (e: Exception) {
                FileLog.e(e)
                emptyMap()
            }
            indexLocale = locale
            index = built
            building.set(false)
        }
    }

    private fun build(locale: Locale?): Map<String, String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return emptyMap()
        }
        val result = HashMap<String, String>()
        val locales = linkedSetOf(ULocale.ENGLISH, ULocale("ru"))
        if (locale != null) {
            locales.add(ULocale.forLocale(locale))
        }
        val zones = TimeZone.getAvailableIDs(TimeZone.SystemTimeZoneType.CANONICAL_LOCATION, null, null)
        val countries = primaryZones()
        for (uLocale in locales) {
            val names = TimeZoneNames.getInstance(uLocale)
            for (id in zones) {
                names.getExemplarLocationName(id)?.let { put(result, it, id) }
            }
            for ((region, id) in countries) {
                put(result, ULocale("", region).getDisplayCountry(uLocale), id)
            }
        }
        return result
    }

    private fun primaryZones(): Map<String, String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return emptyMap()
        }
        val format = TimeZoneFormat.getInstance(ULocale.ENGLISH)
        val now = System.currentTimeMillis()
        val result = HashMap<String, String>()
        for (region in ULocale.getISOCountries()) {
            val ids = TimeZone.getAvailableIDs(TimeZone.SystemTimeZoneType.CANONICAL_LOCATION, region, null)
            if (ids.size == 1) {
                result[region] = ids.first()
                continue
            }
            val country = "${ULocale("", region).getDisplayCountry(ULocale.ENGLISH)} Time"
            val primary = ids.filter {
                format.format(TimeZoneFormat.Style.GENERIC_LOCATION, TimeZone.getTimeZone(it), now) == country
            }
            if (primary.size == 1) {
                result[region] = primary.first()
            }
        }
        return result
    }

    private fun put(index: HashMap<String, String>, name: String, id: String) {
        val key = InlineTime.normalize(name)
        if (key.length < MIN_NAME_LENGTH) {
            return
        }
        index.putIfAbsent(key, id)
        if (key.none { it in 'а'..'я' }) {
            index.putIfAbsent(Normalizer.normalize(key, Normalizer.Form.NFD).replace(marks, ""), id)
        }
    }
}
