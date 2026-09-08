package com.yberkayinci.mola.ai

import com.yberkayinci.mola.data.AiCard
import com.yberkayinci.mola.data.DailyReport
import com.yberkayinci.mola.data.InterventionInput
import com.yberkayinci.mola.data.InterventionRecord
import com.yberkayinci.mola.data.PersonalizationDefaults
import com.yberkayinci.mola.data.PersonalizationProfile
import com.yberkayinci.mola.data.ProfileIntake
import com.yberkayinci.mola.data.ProfileTone
import com.yberkayinci.mola.data.QuickStateOption
import com.yberkayinci.mola.data.UserChoice
import com.yberkayinci.mola.data.UserProfile

class LocalAiGateway : AiGateway {
    override suspend fun generateProfile(intake: ProfileIntake): PersonalizationProfile {
        val combined = listOf(
            intake.biography,
            intake.reason,
            intake.improvementArea,
            intake.hobbies.joinToString(" "),
        ).joinToString(" ").lowercase()

        val focusTargets = buildList {
            if (combined.containsAny("ders", "çalışma", "derslere", "study")) {
                add("ders çalışmak")
            }
            if (combined.containsAny("ödev", "homework", "assignment")) {
                add("ödeve başlamak")
            }
            if (combined.containsAny("uyku", "uyumak", "sleep", "yatmadan")) {
                add("uykuya geçmek")
            }
            if (combined.containsAny("işi", "işe", "work")) {
                add("işe odaklanmak")
            }
        }.distinct().take(4)

        val goals = buildList {
            intake.improvementArea.trim().takeIf(String::isNotEmpty)?.let(::add)
            intake.reason.trim().takeIf(String::isNotEmpty)?.let(::add)
            if (isEmpty()) add("Telefonu daha bilinçli kullanmak")
        }.distinct().take(3)

        val contexts = buildList {
            if (combined.containsAny("yorgun", "yorul", "bitkin", "enerjim yok", "tired", "exhausted")) {
                add("yorgunluk")
            }
            if (
                combined.containsAny(
                    "ertel",
                    "procrast",
                    "başlayam",
                    "başlamak yerine",
                    "oyalan",
                    "odaklan",
                    "avoiding",
                )
            ) {
                add("erteleme")
            }
            if (combined.containsAny("motivasyon", "başlayasım", "canım istemiyor", "unmotivated")) {
                add("motivasyon düşüklüğü")
            }
            if (combined.containsAny("bunald", "nereden başlaya", "gözümde büyü", "çok fazla iş", "overwhelmed")) {
                add("bunalmışlık")
            }
            if (combined.containsAny("sıkıl", "bored", "boş kald")) add("sıkılma")
            if (combined.containsAny("rahatla", "dinlen", "kafa dağıt", "relax")) add("dinlenme")
            if (combined.containsAny("uyku", "gece", "uyuyam", "sleep", "bedtime")) {
                add("gece kullanımı")
            }
        }.distinct().take(4)

        val narrativeActivities = listOf(
            "müzik",
            "gitar",
            "koşu",
            "spor",
            "kitap",
            "podcast",
            "yürüyüş",
            "resim",
            "film",
        ).filter { activity -> combined.contains(activity) }
        val preferredActivities = (intake.hobbies + narrativeActivities)
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .take(5)

        val lowEnergyActivities = buildList {
            preferredActivities.firstOrNull(::isExerciseActivity)?.let {
                add("iki dakika hafifçe esnemek")
            }
            preferredActivities.firstOrNull { !isExerciseActivity(it) }?.let { hobby ->
                add(lowEnergyVersion(hobby))
            }
            add("bir bardak su içip ekrandan uzaklaşmak")
            if (preferredActivities.any { it.containsAny("müzik", "şarkı", "music", "song") }) {
                add("bir şarkı boyunca telefonu bırakmak")
            }
        }.distinct().take(3)

        val personalizedStates = contexts.mapNotNull(::stateForContext)
        val quickStates = (personalizedStates + PersonalizationDefaults.quickStates)
            .distinctBy(QuickStateOption::id)
            .take(6)

        return PersonalizationProfile(
            profileSummary = ProfileGroundingSanitizer.buildLocalSummary(
                focusTargets = focusTargets,
                goals = goals,
                contexts = contexts,
                activities = preferredActivities,
            ),
            focusTargets = focusTargets,
            goals = goals,
            recurringContexts = contexts,
            preferredActivities = preferredActivities,
            lowEnergyActivities = lowEnergyActivities,
            quickStates = quickStates,
            tone = ProfileTone.SUPPORTIVE_DIRECT,
        )
    }

    suspend fun generateCard(
        profile: UserProfile,
        currentUsageMinutes: Int,
        input: InterventionInput,
    ): AiCard = generateCard(profile, currentUsageMinutes, input, emptyList())

    override suspend fun generateCard(
        profile: UserProfile,
        currentUsageMinutes: Int,
        input: InterventionInput,
        recentRecords: List<InterventionRecord>,
    ): AiCard {
        val policy = InterventionContextBuilder.build(profile, input)
        val stateHistoryCount = recentRecords.count { it.stateId == policy.resolvedStateId }

        fun card(
            reflection: String,
            question: String,
            activityTitle: String,
            alternatives: List<String>,
            durationMinutes: Int,
            strategy: InterventionStrategy,
        ): AiCard = AiCard(
            reflection = reflection,
            question = question,
            activityTitle = activityTitle,
            alternative = alternatives
                .filter(String::isNotBlank)
                .ifEmpty { listOf("İki dakika sonra yeniden karar verebilirsin.") }
                .let { values -> values[stateHistoryCount % values.size] },
            durationMinutes = durationMinutes.coerceIn(1, policy.maxDurationMinutes),
            strategy = strategy.wireValue,
        )

        return when (policy.resolvedStateId) {
            "tired" -> card(
                reflection = "Enerjin düşmüş olabilir; şu an yükü artırmadan bir ara seçebilirsin.",
                question = "Şu an kısa bir dinlenme mi, yoksa otomatik bir kaydırma mı arıyorsun?",
                activityTitle = "Kısa ve düşük enerjili mola",
                alternatives = lowEnergyAlternatives(policy),
                durationMinutes = 2,
                strategy = InterventionStrategy.LOW_ENERGY_RESET,
            )

            "procrastinating" -> card(
                reflection = "Başlamak şu anda işin kendisinden daha zor geliyor olabilir.",
                question = "Şu an zor gelen işin kendisi mi, yoksa yalnızca ilk iki dakika mı?",
                activityTitle = "İlk 3 dakika",
                alternatives = microStartAlternatives(policy),
                durationMinutes = 3,
                strategy = InterventionStrategy.MICRO_START,
            )

            "low_motivation" -> card(
                reflection = "İstemek gelmeden de ilk adımı küçültebilirsin.",
                question = "Şu an zor gelen şey işin tamamı mı, yoksa ilk birkaç saniyesi mi?",
                activityTitle = "Sadece ilk adım",
                alternatives = microStartAlternatives(policy),
                durationMinutes = 3,
                strategy = InterventionStrategy.MICRO_START,
            )

            "overwhelmed" -> card(
                reflection = "Her şeyi birden çözmek yerine tek bir adım seçmek daha net olabilir.",
                question = "Şu an hepsini çözmek yerine tek bir sonraki adımı seçebilir misin?",
                activityTitle = "Tek adımı seç",
                alternatives = listOf(
                    "Yapılacaklar arasından yalnızca birini seç ve onun ilk fiziksel adımını yaz.",
                    "Bir işi seç, gereken ilk şeyi önüne koy ve yalnızca onu aç.",
                ),
                durationMinutes = 3,
                strategy = InterventionStrategy.MICRO_START,
            )

            "relaxing" -> card(
                reflection = "Bu anı dinlenmek için seçmiş olabilirsin.",
                question = "Bu molayı ne kadar sürdürmek istediğini baştan seçmek işine yarar mı?",
                activityTitle = "10 dakikalık bilinçli mola",
                alternatives = listOf(
                    "Bu molayı sürdürmek istiyorsan 10 dakikalık zamanlayıcı kur; çaldığında devam edip etmeyeceğine yeniden karar verebilirsin.",
                ),
                durationMinutes = 10,
                strategy = InterventionStrategy.TIMED_INTENTIONAL_USE,
            )

            "bored" -> card(
                reflection = "Can sıkıntısı ekranı kolay bir seçenek yapmış olabilir.",
                question = "Can sıkıntısını değiştirmek için küçük ve belirli bir seçenek denemek ister misin?",
                activityTitle = "Uyarıcıyı değiştir",
                alternatives = boredomAlternatives(policy),
                durationMinutes = 2,
                strategy = if (policy.anchors.activities.isNotEmpty()) {
                    InterventionStrategy.BRIEF_ACTIVITY
                } else {
                    InterventionStrategy.ENVIRONMENT_CHANGE
                },
            )

            "waiting" -> card(
                reflection = "Beklerken kısa bir aralık yakalamış olabilirsin.",
                question = "Beklerken ekran dışında kısa bir seçenek denemek ister misin?",
                activityTitle = "Kısa bekleme arası",
                alternatives = listOf(
                    "Beklerken bulunduğun yerdeki üç nesneyi sayıp sonra yeniden karar verebilirsin.",
                    "Beklediğin süre kısaysa telefonu ters çevirip bulunduğun ortamda üç sesi fark edebilirsin.",
                ),
                durationMinutes = 2,
                strategy = InterventionStrategy.SENSORY_BREAK,
            )

            "habit" -> card(
                reflection = "Bu açılış düşünmeden gerçekleşmiş olabilir.",
                question = "Bu açılışın bilinçli bir seçim mi, yoksa alışkanlık mı olduğunu ayırmak ister misin?",
                activityTitle = "Açılışın amacı",
                alternatives = listOf(
                    "Telefonu iki dakika masaya bırakıp uygulamayı ne için açtığını tek cümleyle yazdıktan sonra yeniden karar verebilirsin.",
                    "Uygulamayı kullanmak istediğin şeyi tek cümleye indirip sonra açıp açmayacağına karar verebilirsin.",
                ),
                durationMinutes = 2,
                strategy = InterventionStrategy.ENVIRONMENT_CHANGE,
            )

            "late_night" -> card(
                reflection = "Gece ekrandan ayrılmak daha zor hissedilebilir.",
                question = "Uyumadan önce ekrandan kısa bir süre uzaklaşmak sana iyi gelebilir mi?",
                activityTitle = "Uykuya geçiş",
                alternatives = listOf(
                    "Telefonu yatağından uzağa koyup ışığı azalt; iki dakika sonra ekrana dönüp dönmeyeceğine yeniden karar verebilirsin.",
                    "Telefonu iki dakika uzağa bırakıp gözlerini kapat; sonra devam edip etmeyeceğine yeniden karar verebilirsin.",
                ),
                durationMinutes = 2,
                strategy = InterventionStrategy.LOW_ENERGY_RESET,
            )

            else -> card(
                reflection = "Şu anki ihtiyacın tek bir seçeneğe sığmayabilir.",
                question = "Şu anda gerçekten neye ihtiyaç duyduğunu bir cümleyle ayırmak yardımcı olabilir mi?",
                activityTitle = "İki dakikalık ayrım",
                alternatives = listOf(
                    "Telefonu iki dakika masaya bırakıp şu an ne aradığını bir cümleyle yaz; sonra devam edip etmeyeceğine karar verebilirsin.",
                    "Ekranı iki dakika kapatıp şu anki ihtiyacını tek kelimeyle yaz; ardından yeniden karar verebilirsin.",
                ),
                durationMinutes = 2,
                strategy = InterventionStrategy.SENSORY_BREAK,
            )
        }
    }

    override suspend fun generateDailyReport(
        profile: UserProfile,
        records: List<InterventionRecord>,
        currentUsageMinutes: Int,
    ): DailyReport {
        val continued = records.count { it.choice == UserChoice.CONTINUE }
        val stopped = records.count { it.choice == UserChoice.STOPPED }
        if (records.size < REPORT_MINIMUM_RECORDS) {
            return DailyReport(
                totalUsageMinutes = currentUsageMinutes,
                limitMinutes = profile.dailyLimitMinutes,
                interventionCount = records.size,
                continuedCount = continued,
                stoppedCount = stopped,
                observationQuestion = "Yeterli veri yok.",
                microStep = "Yarın yalnızca bir kaydı tamamlamayı dene.",
                insufficientData = true,
            )
        }

        val evidence = DailyReportEvidenceBuilder.build(records)
        val selectedStateId = evidence.higherContinueStateId ?: evidence.dominantStateId
        val selectedState = evidence.states.firstOrNull { it.stateId == selectedStateId }
        val observation = selectedState?.stateLabel
            ?.takeIf(String::isNotBlank)
            ?.takeIf { SafetyLanguageValidator.isDisplaySafe(it) }
            ?.let { label ->
                "“$label” dediğin anlarda verdiğin kararlar arasında bir örüntü olabilir mi?"
            }
            ?: "Bugünkü farklı Mola anlarından hangisi daha bilinçli bir seçim gibi hissettirdi?"

        val safeGoal = profile.personalization.goals.firstOrNull {
            SafetyLanguageValidator.isDisplaySafe(it)
        }
        val microStep = when (selectedStateId) {
            "procrastinating" -> safeGoal
                ?.let { goal ->
                    "Yarın ilk erteleme anında $goal için yalnızca iki dakika başla."
                }
                ?: "Yarın ilk erteleme anında işi iki dakika açıp sonra yeniden karar ver."
            "tired", "late_night" ->
                "Yarın ilk yorgunluk anında telefonu iki dakika uzağa bırakıp dinlenmeyi dene."
            "relaxing" ->
                "Yarın ilk molada beş dakikalık bir zamanlayıcı kurup sonra yeniden karar ver."
            else -> safeGoal
                ?.let { goal ->
                    "Yarın $goal için telefonu açmadan önce iki dakikalık tek bir başlangıç yap."
                }
                ?: "Yarın ilk müdahalede telefonu iki dakika uzağa bırakıp sonra yeniden karar ver."
        }

        return DailyReport(
            totalUsageMinutes = currentUsageMinutes,
            limitMinutes = profile.dailyLimitMinutes,
            interventionCount = records.size,
            continuedCount = continued,
            stoppedCount = stopped,
            observationQuestion = observation,
            microStep = microStep,
        )
    }

    private fun lowEnergyAlternatives(policy: InterventionPolicy): List<String> {
        val anchor = policy.anchors.lowEnergyActivities.firstOrNull()
            ?: policy.anchors.activities.firstOrNull()
        return buildList {
            when {
                anchor == null -> Unit
                anchor.containsAny("müzik", "şarkı", "music", "song") -> add(
                    "Bir şarkı boyunca telefonu ters çevirip yalnızca müziği dinleyebilirsin.",
                )
                anchor.containsAny("su", "water") -> add(
                    "Bir bardak su içip iki dakika ekrandan uzaklaşabilirsin.",
                )
                anchor.containsAny("yürüyüş", "walk") -> add(
                    "İki dakikalık yavaş bir yürüyüş yapıp sonra yeniden karar verebilirsin.",
                )
                anchor.containsAny("esneme", "esnemek", "stretch") -> add(
                    "İki dakika hafifçe esneyip sonra yeniden karar verebilirsin.",
                )
                else -> add("$anchor için iki dakika ayırıp sonra yeniden karar verebilirsin.")
            }
            add("Bir bardak su içip iki dakika ekrandan uzaklaşabilirsin.")
            add("Telefonu masaya bırakıp iki dakika gözlerini dinlendirebilirsin.")
        }
    }

    private fun microStartAlternatives(policy: InterventionPolicy): List<String> {
        val target = policy.anchors.goals.firstOrNull()
        return listOfNotNull(
            target?.let { "$it için ilgili işi aç ve iki dakika boyunca yalnızca ilk adımını tek cümleyle yaz." },
            "Ertelenen işi aç ve yalnızca ilk yapılacak şeyi tek cümleyle yaz.",
            "Başlamak istediğin işi açıp ilk hareketi yaz; üç dakika sonra yeniden karar verebilirsin.",
        )
    }

    private fun boredomAlternatives(policy: InterventionPolicy): List<String> {
        val activity = policy.anchors.activities.firstOrNull()
        return listOfNotNull(
            activity?.let { "$it için iki dakikalık küçük bir başlangıç yapıp sonra yeniden karar verebilirsin." },
            "Ayağa kalkıp bulunduğun odada farklı bir yere geç; iki dakika sonra yeniden karar verebilirsin.",
            "Telefonu ters çevirip bulunduğun ortamda üç farklı sesi fark edebilirsin.",
        )
    }

    private fun lowEnergyVersion(hobby: String): String = when {
        hobby.containsAny("müzik", "şarkı", "music", "song") -> "bir şarkı dinlemek"
        hobby.containsAny("kitap", "oku", "book", "read") -> "iki sayfa okumak"
        hobby.containsAny("gitar", "guitar") -> "iki dakika gitar çalmak"
        isExerciseActivity(hobby) -> "iki dakika hafifçe esnemek"
        else -> "$hobby için iki dakika ayırmak"
    }

    private fun isExerciseActivity(value: String): Boolean = value.containsAny(
        "koşu",
        "koşmak",
        "spor",
        "egzersiz",
        "run",
        "running",
        "exercise",
        "workout",
        "gym",
    )

    private fun stateForContext(context: String): QuickStateOption? = when (context) {
        "yorgunluk" -> QuickStateOption("tired", "Biraz yoruldum", "😴", "low_energy")
        "erteleme" -> QuickStateOption("procrastinating", "Bir şeyi erteliyorum", "🫠", "avoidance")
        "motivasyon düşüklüğü" -> QuickStateOption("low_motivation", "Motivasyonum düşük", "🪫", "activation")
        "bunalmışlık" -> QuickStateOption("overwhelmed", "Her şey bunaltıyor", "🌀", "activation")
        "dinlenme" -> QuickStateOption("relaxing", "Sadece kafa dağıtıyorum", "😌", "intentional_rest")
        "sıkılma" -> QuickStateOption("bored", "Biraz sıkıldım", "🥱", "boredom")
        "gece kullanımı" -> QuickStateOption("late_night", "Uyumadan önce bakıyorum", "🌙", "late_night")
        "alışkanlıkla açma" -> QuickStateOption("habit", "Alışkanlıkla açtım", "🔁", "habit")
        else -> null
    }

    private fun String.containsAny(vararg needles: String): Boolean =
        needles.any { needle -> contains(needle, ignoreCase = true) }

    private companion object {
        const val REPORT_MINIMUM_RECORDS = 7
    }
}
