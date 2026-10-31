package com.sidbh.tvlauncher

import android.content.ComponentName
import android.content.Context
import android.content.ActivityNotFoundException
import android.content.Intent

enum class Category(val label: String) {
    Streaming("Streaming"),
    Games("Games"),
    TvInputs("TV & Inputs"),
    Utilities("Utilities"),
}

/** Which home layout is shown. Only the selected one is composed and loads data. */
enum class HomeStyle(val label: String) {
    Classic("Classic"),
    Glass("Glass"),
    ListStyle("List Style"),
    Arcade("Arcade"),
    Retro("Retro Console"),
}

sealed interface Target {
    data class App(val component: ComponentName) : Target
    data class Input(val inputId: String) : Target
}

/** Anything that can sit on a shelf: an installed app or a TV input. Apps use their package as [id]. */
data class Item(
    val id: String,
    val label: String,
    val category: Category,
    val target: Target,
    val dimmed: Boolean = false,
)

/** Starts [intent] in a new task. False if nothing can handle it or the target refuses. */
fun Context.startSafely(intent: Intent): Boolean = try {
    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}
