package com.yberkayinci.mola.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets

class JsonMolaRepository internal constructor(private val stateFile: File) : MolaRepository {
    constructor(context: Context) : this(File(context.filesDir, FILE_NAME))

    private val lock = Any()

    override fun loadProfile(): UserProfile? = synchronized(lock) {
        val profile = readRoot().optJSONObject(KEY_PROFILE) ?: return@synchronized null
        UserProfile(
            name = profile.optString("isim"),
            department = profile.optString("bolum"),
            hobbies = profile.optJSONArray("hobiler").toStringList(),
            improvementArea = profile.optString("gelisim_alani"),
            reason = profile.optString("neden"),
            targetAppLabel = profile.optString("hedef_app"),
            targetPackage = profile.optString("hedef_paket"),
            dailyLimitMinutes = profile.optInt("limit_dk", DEFAULT_LIMIT_MINUTES)
                .coerceAtLeast(1),
            biography = profile.optString("biyografi"),
            personalization = profile.optJSONObject("kisisellestirme").toPersonalization(),
            schemaVersion = profile.optInt("sema_surum", 1).coerceAtLeast(1),
        )
    }

    override fun saveProfile(profile: UserProfile) = synchronized(lock) {
        val root = readRoot()
        root.put(
            KEY_PROFILE,
            JSONObject()
                .put("sema_surum", UserProfile.CURRENT_SCHEMA_VERSION)
                .put("isim", profile.name)
                .put("bolum", profile.department)
                .put("hobiler", JSONArray(profile.hobbies))
                .put("gelisim_alani", profile.improvementArea)
                .put("neden", profile.reason)
                .put("hedef_app", profile.targetAppLabel)
                .put("hedef_paket", profile.targetPackage)
                .put("limit_dk", profile.dailyLimitMinutes)
                .put("biyografi", profile.biography)
                .put("kisisellestirme", profile.personalization.toJson()),
        )
        ensureRecordsArray(root)
        writeRoot(root)
    }

    override fun loadRecords(): List<InterventionRecord> = synchronized(lock) {
        val records = readRoot().optJSONArray(KEY_RECORDS) ?: return@synchronized emptyList()
        buildList {
            for (index in 0 until records.length()) {
                val item = records.optJSONObject(index) ?: continue
                add(
                    InterventionRecord(
                        timestampEpochMillis = item.optLong("zaman_ms", System.currentTimeMillis()),
                        usageMinutes = item.optInt("kullanim_dk", 0).coerceAtLeast(0),
                        text = item.optString("metin"),
                        choice = UserChoice.fromStorage(item.optString("secim")),
                        stateId = item.optString("durum_id"),
                        stateLabel = item.optString("durum_etiket"),
                        inputMethod = InterventionInputMethod.fromStorage(
                            item.optString("girdi_yontemi"),
                        ),
                        aiQuestion = item.optString("ai_soru"),
                        aiAlternative = item.optString("ai_alternatif"),
                        aiReflection = item.optString("ai_yansima"),
                        aiActivityTitle = item.optString("ai_aktivite_basligi"),
                        aiDurationMinutes = item.optInt("ai_sure_dk", 0).coerceAtLeast(0),
                        aiStrategy = item.optString("ai_strateji"),
                        trigger = item.optString("tetikleyici"),
                        hypothesisStateId = item.optString("tahmin_durum"),
                        hypothesisAccepted = if (item.has(KEY_HYPOTHESIS_ACCEPTED)) {
                            item.optBoolean(KEY_HYPOTHESIS_ACCEPTED)
                        } else {
                            null
                        },
                    ),
                )
            }
        }
    }

    override fun appendRecord(record: InterventionRecord) = synchronized(lock) {
        val root = readRoot()
        val records = ensureRecordsArray(root)
        records.put(record.toJson())
        writeRoot(root)
    }

    override fun replaceRecords(records: List<InterventionRecord>) = synchronized(lock) {
        val root = readRoot()
        root.put(KEY_RECORDS, JSONArray().apply { records.forEach { put(it.toJson()) } })
        writeRoot(root)
    }

    override fun clearAll() = synchronized(lock) {
        if (stateFile.exists() && !stateFile.delete()) {
            writeRoot(emptyRoot())
        }
    }

    private fun readRoot(): JSONObject {
        if (!stateFile.exists()) return emptyRoot()
        return runCatching {
            JSONObject(stateFile.readText(StandardCharsets.UTF_8))
        }.getOrElse { emptyRoot() }
    }

    private fun writeRoot(root: JSONObject) {
        stateFile.parentFile?.mkdirs()
        val temporary = File(stateFile.parentFile, "$FILE_NAME.tmp")
        temporary.writeText(root.toString(2), StandardCharsets.UTF_8)
        if (!temporary.renameTo(stateFile)) {
            stateFile.writeText(root.toString(2), StandardCharsets.UTF_8)
            temporary.delete()
        }
    }

    private fun emptyRoot(): JSONObject =
        JSONObject()
            .put(KEY_PROFILE, JSONObject.NULL)
            .put(KEY_RECORDS, JSONArray())

    private fun ensureRecordsArray(root: JSONObject): JSONArray {
        val existing = root.optJSONArray(KEY_RECORDS)
        if (existing != null) return existing
        return JSONArray().also { root.put(KEY_RECORDS, it) }
    }

    private fun InterventionRecord.toJson(): JSONObject =
        JSONObject()
            .put("zaman_ms", timestampEpochMillis)
            .put("saat", localTime())
            .put("kullanim_dk", usageMinutes)
            .put("metin", text)
            .put("secim", choice.storageValue)
            .put("durum_id", stateId)
            .put("durum_etiket", stateLabel)
            .put("girdi_yontemi", inputMethod.storageValue)
            .put("ai_soru", aiQuestion)
            .put("ai_alternatif", aiAlternative)
            .put("ai_yansima", aiReflection)
            .put("ai_aktivite_basligi", aiActivityTitle)
            .put("ai_sure_dk", aiDurationMinutes)
            .put("ai_strateji", aiStrategy)
            .put("tetikleyici", trigger)
            .put("tahmin_durum", hypothesisStateId)
            .apply {
                // Absent rather than false: never having been asked is not a rejection.
                hypothesisAccepted?.let { put(KEY_HYPOTHESIS_ACCEPTED, it) }
            }

    private fun PersonalizationProfile.toJson(): JSONObject =
        JSONObject()
            .put("profil_ozeti", profileSummary)
            .put("odak_hedefleri", JSONArray(focusTargets))
            .put("hedefler", JSONArray(goals))
            .put("tekrarlayan_baglamlar", JSONArray(recurringContexts))
            .put("tercih_edilen_aktiviteler", JSONArray(preferredActivities))
            .put("dusuk_enerji_aktiviteleri", JSONArray(lowEnergyActivities))
            .put("ton", tone.storageValue)
            .put(
                "hizli_durumlar",
                JSONArray().apply {
                    quickStates.forEach { option ->
                        put(
                            JSONObject()
                                .put("id", option.id)
                                .put("etiket", option.label)
                                .put("emoji", option.emoji)
                                .put("kategori", option.category),
                        )
                    }
                },
            )

    private fun JSONObject?.toPersonalization(): PersonalizationProfile {
        if (this == null) return PersonalizationProfile()
        return PersonalizationProfile(
            profileSummary = optString("profil_ozeti"),
            focusTargets = optJSONArray("odak_hedefleri").toStringList(),
            goals = optJSONArray("hedefler").toStringList(),
            recurringContexts = optJSONArray("tekrarlayan_baglamlar").toStringList(),
            preferredActivities = optJSONArray("tercih_edilen_aktiviteler").toStringList(),
            lowEnergyActivities = optJSONArray("dusuk_enerji_aktiviteleri").toStringList(),
            quickStates = optJSONArray("hizli_durumlar").toQuickStates(),
            tone = ProfileTone.fromStorage(optString("ton")),
        )
    }

    private fun JSONArray?.toQuickStates(): List<QuickStateOption> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
                val id = QuickStateTaxonomy.canonicalize(item.optString("id")) ?: continue
                val label = item.optString("etiket").trim()
                if (id.isEmpty() || label.isEmpty()) continue
                add(
                    QuickStateOption(
                        id = id,
                        label = label,
                        emoji = item.optString("emoji"),
                        category = item.optString("kategori", id),
                    ),
                )
            }
        }
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                optString(index).trim().takeIf { it.isNotEmpty() }?.let(::add)
            }
        }
    }

    private companion object {
        const val FILE_NAME = "mola_state.json"
        const val KEY_PROFILE = "profil"
        const val KEY_RECORDS = "kayitlar"
        const val KEY_HYPOTHESIS_ACCEPTED = "tahmin_kabul"
        const val DEFAULT_LIMIT_MINUTES = 60
    }
}
