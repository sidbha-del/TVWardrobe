package com.sidbh.tvlauncher

import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputManager

/** HDMI and other passthrough inputs. Live TV itself is the TV app, which lists under TV & Inputs. */
class InputRepository(private val context: Context) {
    private val manager = context.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager

    fun loadInputs(): List<Item> {
        val tv = manager ?: return emptyList()
        return tv.tvInputList
            .filter { it.isPassthroughInput }
            .map { info ->
                Item(
                    id = INPUT_PREFIX + info.id,
                    label = info.loadLabel(context)?.toString()?.takeIf { it.isNotBlank() } ?: "Input",
                    category = Category.TvInputs,
                    target = Target.Input(info.id),
                    dimmed = tv.getInputState(info.id) == TvInputManager.INPUT_STATE_DISCONNECTED,
                )
            }
    }

    fun launch(inputId: String): Boolean =
        context.startSafely(Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(inputId)))

    private companion object {
        const val INPUT_PREFIX = "input:"
    }
}
