package com.molina.suite.feature.code

import android.app.AlertDialog
import android.content.Context
import androidx.annotation.StringRes

/** Dialog pilih satu dari daftar; [checked] bernilai -1 bila tidak ada pilihan aktif. */
internal object CodeChoiceDialog {

    fun show(
        context: Context,
        @StringRes title: Int,
        items: List<String>,
        checked: Int,
        onChosen: (Int) -> Unit
    ) {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setSingleChoiceItems(items.toTypedArray(), checked) { dialog, which ->
                dialog.dismiss()
                onChosen(which)
            }
            .setNegativeButton(R.string.code_picker_cancel, null)
            .show()
    }
}
