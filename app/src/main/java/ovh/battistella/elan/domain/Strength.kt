package ovh.battistella.elan.domain

fun epley1RM(weightKg: Double, reps: Int): Double {
    if (weightKg <= 0.0 || reps <= 0) return 0.0
    if (reps == 1) return weightKg
    return weightKg * (1 + reps / 30.0)
}
