package com.yberkayinci.mola.data

import java.io.File
import java.nio.charset.StandardCharsets
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonMolaRepositoryTest {
    @Test
    fun v2ProfileStillLoadsWithSafeV3Defaults() {
        val repository = JsonMolaRepository(tempFile(v2StateJson()))

        val profile = repository.loadProfile()

        assertTrue(profile != null)
        requireNotNull(profile)
        assertEquals(2, profile.schemaVersion)
        assertEquals("Ayşe", profile.name)
        assertEquals("İstatistik", profile.department)
        assertEquals(listOf("gitar", "koşu"), profile.hobbies)
        assertEquals("daha düzenli çalışmak", profile.improvementArea)
        assertEquals("Instagram", profile.targetAppLabel)
        assertEquals("com.instagram.android", profile.targetPackage)
        assertEquals(60, profile.dailyLimitMinutes)
        assertEquals(listOf("daha düzenli çalışmak", "gece daha rahat uyumak"), profile.personalization.goals)
        assertEquals(listOf("yorgunluk", "erteleme"), profile.personalization.recurringContexts)
        assertEquals(listOf("gitar", "koşu"), profile.personalization.preferredActivities)
        assertEquals("supportive_direct", profile.personalization.tone.storageValue)
        assertEquals(3, profile.personalization.quickStates.size)
        assertEquals("tired", profile.personalization.quickStates.first().id)
        assertEquals("", profile.personalization.profileSummary)
        assertEquals(emptyList<String>(), profile.personalization.focusTargets)
    }

    @Test
    fun v3ProfilePersistsAndReloads() {
        val file = tempFile("{}")
        val repository = JsonMolaRepository(file)
        val profile = UserProfile(
            name = "Ayşe",
            department = "İstatistik",
            hobbies = listOf("gitar"),
            improvementArea = "daha düzenli çalışmak",
            reason = "gece daha rahat uyumak",
            targetAppLabel = "Instagram",
            targetPackage = "com.instagram.android",
            dailyLimitMinutes = 45,
            biography = "Derslerden sonra yoruluyorum.",
            personalization = PersonalizationProfile(
                profileSummary = "Mola senin anlattıklarından kısa başlangıç adımları ve müzik seçeneklerini kullanabilir.",
                focusTargets = listOf("ders çalışmak"),
                goals = listOf("daha düzenli çalışmak"),
                recurringContexts = listOf("erteleme", "yorgunluk"),
                preferredActivities = listOf("gitar"),
                lowEnergyActivities = listOf("bir şarkı boyunca telefonu bırakmak"),
                quickStates = listOf(
                    QuickStateOption("tired", "Biraz yoruldum", "😴", "low_energy"),
                    QuickStateOption("low_motivation", "Motivasyonum düşük", "🪫", "activation"),
                    QuickStateOption("procrastinating", "Başlamayı erteliyorum", "🫠", "avoidance"),
                ),
                tone = ProfileTone.GENTLE,
            ),
        )

        repository.saveProfile(profile)
        val reloaded = repository.loadProfile()

        requireNotNull(reloaded)
        assertEquals(profile.copy(schemaVersion = CURRENT), reloaded)
        assertEquals(CURRENT, reloaded.schemaVersion)
        assertEquals(profile.personalization.profileSummary, reloaded.personalization.profileSummary)
        assertEquals(profile.personalization.focusTargets, reloaded.personalization.focusTargets)
        assertEquals(profile.personalization.quickStates, reloaded.personalization.quickStates)
        assertEquals(ProfileTone.GENTLE, reloaded.personalization.tone)
    }

    @Test
    fun v3RecordRoundTripsAndLegacyRecordKeepsDefaults() {
        val file = tempFile("{\"kayitlar\":[$LEGACY_RECORD_JSON]}")
        val repository = JsonMolaRepository(file)

        val legacy = repository.loadRecords()
        assertEquals(1, legacy.size)
        assertEquals("", legacy[0].aiReflection)
        assertEquals("", legacy[0].aiActivityTitle)
        assertEquals(0, legacy[0].aiDurationMinutes)
        assertEquals("", legacy[0].aiStrategy)

        repository.appendRecord(
            InterventionRecord(
                timestampEpochMillis = 1_700_000_000_000,
                usageMinutes = 70,
                text = "Ders çalışmam lazım",
                choice = UserChoice.STOPPED,
                stateId = "procrastinating",
                stateLabel = "Başlamayı erteliyorum",
                inputMethod = InterventionInputMethod.QUICK_REPLY,
                aiQuestion = "İlk adım mı zor geliyor?",
                aiAlternative = "Notlarını aç ve ilk soruyu yaz.",
                aiReflection = "Başlama anı zor geliyor olabilir.",
                aiActivityTitle = "İlk 3 dakika",
                aiDurationMinutes = 3,
                aiStrategy = "micro_start",
            ),
        )

        val reloaded = repository.loadRecords()
        assertEquals(2, reloaded.size)
        val enriched = reloaded.last()
        assertEquals("Başlama anı zor geliyor olabilir.", enriched.aiReflection)
        assertEquals("İlk 3 dakika", enriched.aiActivityTitle)
        assertEquals(3, enriched.aiDurationMinutes)
        assertEquals("micro_start", enriched.aiStrategy)
    }

    private companion object {
        const val CURRENT = UserProfile.CURRENT_SCHEMA_VERSION

        val V2_PROFILE_JSON = """
            {
              "sema_surum": 2,
              "isim": "Ayşe",
              "bolum": "İstatistik",
              "hobiler": ["gitar", "koşu"],
              "gelisim_alani": "daha düzenli çalışmak",
              "neden": "gece daha rahat uyumak istiyorum",
              "hedef_app": "Instagram",
              "hedef_paket": "com.instagram.android",
              "limit_dk": 60,
              "biyografi": "Derslerden sonra yoruluyorum.",
              "kisisellestirme": {
                "hedefler": ["daha düzenli çalışmak", "gece daha rahat uyumak"],
                "tekrarlayan_baglamlar": ["yorgunluk", "erteleme"],
                "tercih_edilen_aktiviteler": ["gitar", "koşu"],
                "dusuk_enerji_aktiviteleri": ["bir bardak su içmek"],
                "ton": "supportive_direct",
                "hizli_durumlar": [
                  {"id": "tired", "etiket": "Biraz yoruldum", "emoji": "😴", "kategori": "low_energy"},
                  {"id": "procrastinating", "etiket": "Bir şeyi erteliyorum", "emoji": "🫠", "kategori": "avoidance"},
                  {"id": "relaxing", "etiket": "Sadece kafa dağıtıyorum", "emoji": "😌", "kategori": "intentional_rest"}
                ]
              }
            }
        """.trimIndent()

        fun v2StateJson(): String = JSONObject().put("profil", JSONObject(V2_PROFILE_JSON)).toString()

        val LEGACY_RECORD_JSON = JSONObject()
            .put("zaman_ms", 1_690_000_000_000)
            .put("saat", "23:10")
            .put("kullanim_dk", 78)
            .put("metin", "Bir şeyi erteliyorum")
            .put("secim", "vazgectim")
            .put("durum_id", "procrastinating")
            .put("durum_etiket", "Bir şeyi erteliyorum")
            .put("girdi_yontemi", "quick_reply")
            .put("ai_soru", "Ertelediğin şeyin ilk iki dakikasını yapmak ulaşılabilir olabilir mi?")
            .put("ai_alternatif", "İlk adımı iki dakika boyunca açıp yapmayı deneyebilirsin.")
            .toString()
    }
}

private fun tempFile(content: String): File =
    File.createTempFile("mola_state_test", ".json").apply {
        deleteOnExit()
        writeText(content, StandardCharsets.UTF_8)
    }

private fun debugProbe() {}
