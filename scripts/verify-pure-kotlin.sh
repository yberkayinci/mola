#!/usr/bin/env bash
set -euo pipefail

script_dir="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(CDPATH= cd -- "${script_dir}/.." && pwd)"

command -v kotlinc >/dev/null 2>&1 || {
  echo "kotlinc is required for this lightweight check. Use ./gradlew test otherwise." >&2
  exit 1
}
command -v java >/dev/null 2>&1 || {
  echo "java is required." >&2
  exit 1
}

work_dir="$(mktemp -d)"
trap 'rm -rf "$work_dir"' EXIT

cat > "${work_dir}/Verify.kt" <<'KOTLIN'
import com.yberkayinci.mola.ai.CrisisFilter
import com.yberkayinci.mola.ai.LocalAiGateway
import com.yberkayinci.mola.ai.SafetyLanguageValidator
import com.yberkayinci.mola.data.DemoDataSeeder
import com.yberkayinci.mola.data.InterventionInput
import com.yberkayinci.mola.data.InterventionInputMethod
import com.yberkayinci.mola.data.ProfileIntake
import com.yberkayinci.mola.data.UserProfile
import com.yberkayinci.mola.usage.CooldownPolicy
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

fun <T> runSuspend(block: suspend () -> T): T {
    var completed: Result<T>? = null
    block.startCoroutine(
        object : Continuation<T> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<T>) {
                completed = result
            }
        },
    )
    return checkNotNull(completed) { "Lightweight check only supports immediately completed suspend functions" }
        .getOrThrow()
}

fun main() {
    check(CrisisFilter.check("Kendimi öldürmek istiyorum.").isCrisisSignal)
    check(!CrisisFilter.check("Bugün yoruldum, biraz dinleneceğim.").isCrisisSignal)

    check(!SafetyLanguageValidator.isDisplaySafe("Bugün çok kullandın."))
    check(SafetyLanguageValidator.isDisplaySafe("İki dakikalık küçük bir başlangıç yapabilirsin."))

    check(CooldownPolicy.shouldShow(1_000L, null))
    check(!CooldownPolicy.shouldShow(CooldownPolicy.DEFAULT_COOLDOWN_MILLIS - 1L, 0L))
    check(CooldownPolicy.shouldShow(CooldownPolicy.DEFAULT_COOLDOWN_MILLIS, 0L))
    check(CooldownPolicy.shouldShow(500L, 1_000L))

    val fixedNow = LocalDateTime.of(2026, 8, 29, 12, 0)
    val records = DemoDataSeeder.records(fixedNow)
    check(records.size == 11)
    check(records.map { it.localDate() }.distinct().size == 4)
    check(records.count { it.occursOn(LocalDate.of(2026, 8, 29)) } == 8)
    check(records.all { it.timestampEpochMillis <= fixedNow.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() })
    check(records.any { it.stateId.isNotBlank() && it.aiAlternative.isNotBlank() })

    val gateway = LocalAiGateway()
    val personalization = runSuspend {
        gateway.generateProfile(
            ProfileIntake(
                name = "Ayşe",
                biography = "Derslerden sonra yoruluyorum ve çalışmaya başlamak yerine oyalanıyorum.",
                hobbies = listOf("gitar"),
                improvementArea = "daha düzenli çalışmak",
                reason = "gece daha rahat uyumak",
            ),
        )
    }
    check(personalization.quickStates.size == 6)

    val profile = UserProfile(
        name = "Ayşe",
        department = "İstatistik",
        hobbies = listOf("gitar"),
        improvementArea = "daha düzenli çalışmak",
        reason = "gece daha rahat uyumak",
        targetAppLabel = "Instagram",
        targetPackage = "com.instagram.android",
        dailyLimitMinutes = 60,
        biography = "Derslerden sonra yoruluyorum.",
        personalization = personalization,
    )
    check(runSuspend { gateway.generateDailyReport(profile, records.take(6), 78) }.insufficientData)
    val report = runSuspend { gateway.generateDailyReport(profile, records.take(7), 78) }
    check(!report.insufficientData)
    check(SafetyLanguageValidator.isDisplaySafe(report.observationQuestion, report.microStep))

    val card = runSuspend {
        gateway.generateCard(
            profile = profile,
            currentUsageMinutes = 78,
            input = InterventionInput(
                text = "Biraz yoruldum",
                stateId = "tired",
                stateLabel = "Biraz yoruldum",
                method = InterventionInputMethod.QUICK_REPLY,
            ),
        )
    }
    check(SafetyLanguageValidator.isDisplaySafe(card.question, card.alternative))

    println("Pure Kotlin verification passed.")
}
KOTLIN

cd "$repo_root"
kotlinc \
  app/src/main/java/com/yberkayinci/mola/data/Models.kt \
  app/src/main/java/com/yberkayinci/mola/data/DemoDataSeeder.kt \
  app/src/main/java/com/yberkayinci/mola/ai/AiGateway.kt \
  app/src/main/java/com/yberkayinci/mola/ai/LocalAiGateway.kt \
  app/src/main/java/com/yberkayinci/mola/ai/CrisisFilter.kt \
  app/src/main/java/com/yberkayinci/mola/ai/SafetyLanguageValidator.kt \
  app/src/main/java/com/yberkayinci/mola/usage/CooldownPolicy.kt \
  "${work_dir}/Verify.kt" \
  -include-runtime \
  -d "${work_dir}/verify.jar"
java -jar "${work_dir}/verify.jar"
