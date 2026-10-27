package com.eirahoutmoss.nextools.core.catalog

/** Bir aracın bu cihazdaki durumu. Ana ekrandaki kartın görünüşünü belirler. */
sealed interface ToolStatus {
    val spec: ToolSpec

    /** Kullanılabilir. [requirement] seçilen yol; [fallback] asıl yol yerine yedek yol mu. */
    data class Ready(
        override val spec: ToolSpec,
        val requirement: Requirement,
        val fallback: Boolean,
        /** Gereken donanımdan biri ancak ölçüm sırasında anlaşılabiliyor. */
        val uncertain: Boolean,
    ) : ToolStatus

    /** Donanım yok: gri kart, "Bu cihazda … yok". [missing] asıl yolun eksikleri. */
    data class MissingHardware(override val spec: ToolSpec, val missing: Set<Hardware>) : ToolStatus

    /** Donanım var ama araç bu sürümde henüz yazılmadı. */
    data class Planned(override val spec: ToolSpec, val hardwareReady: Boolean, val missing: Set<Hardware>) : ToolStatus

    /** Araç yazıldı, cihazda yalnızca henüz yazılmamış bir yedek yolun donanımı var (ör. ışık sensörü yok, kamera var). */
    data class FallbackPending(override val spec: ToolSpec, val missing: Set<Hardware>, val pendingMode: String) : ToolStatus

    /** Bu cihazda donanım bakımından çalışabilir mi (uygulamada yazılmış olsun olmasın). */
    val hardwareOk: Boolean
        get() = when (this) {
            is Ready -> true
            is MissingHardware -> false
            is Planned -> hardwareReady
            is FallbackPending -> true
        }
}

object Availability {

    fun evaluate(spec: ToolSpec, caps: DeviceCapabilities): ToolStatus {
        if (spec.requirements.isEmpty()) {
            return if (spec.implemented) ToolStatus.Ready(spec, Requirement(emptySet()), fallback = false, uncertain = false)
            else ToolStatus.Planned(spec, hardwareReady = true, missing = emptySet())
        }
        val primaryMissing = missingFor(spec.requirements.first(), caps)
        val satisfied = spec.requirements.withIndex().filter { (_, r) -> missingFor(r, caps).isEmpty() }

        if (!spec.implemented) {
            return ToolStatus.Planned(spec, hardwareReady = satisfied.isNotEmpty(), missing = primaryMissing)
        }
        val usable = satisfied.firstOrNull { it.value.implemented }
        if (usable != null) {
            val uncertain = usable.value.hardware.any { caps.presenceOf(it) == Presence.UNKNOWN }
            return ToolStatus.Ready(spec, usable.value, fallback = usable.index > 0, uncertain = uncertain)
        }
        val pending = satisfied.firstOrNull()
        return if (pending != null) ToolStatus.FallbackPending(spec, primaryMissing, pending.value.mode)
        else ToolStatus.MissingHardware(spec, primaryMissing)
    }

    /** UNKNOWN eksik sayılmaz: araç açılır, belirsizlik ölçüm sırasında çözülür. */
    private fun missingFor(r: Requirement, caps: DeviceCapabilities): Set<Hardware> =
        r.hardware.filter { caps.isAbsent(it) }.toSet()

    fun evaluateAll(caps: DeviceCapabilities): List<ToolStatus> = ToolCatalog.all.map { evaluate(it, caps) }

    /** "34 / 41 araç bu cihazda çalışır" özeti: donanım bakımından. */
    data class Summary(val hardwareOk: Int, val total: Int, val usableNow: Int)

    fun summarize(statuses: List<ToolStatus>): Summary = Summary(
        hardwareOk = statuses.count { it.hardwareOk },
        total = statuses.size,
        usableNow = statuses.count { it is ToolStatus.Ready },
    )
}
