package com.lazarus.personalojtdtr.util

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts

// Ask the system picker for local documents; the app has no network or broad storage permission.
class CreateLocalBackup : ActivityResultContracts.CreateDocument("application/zip") {
    override fun createIntent(context: Context, input: String): Intent =
        super.createIntent(context, input).putExtra(Intent.EXTRA_LOCAL_ONLY, true)
}
class OpenLocalBackup : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent =
        super.createIntent(context, input).putExtra(Intent.EXTRA_LOCAL_ONLY, true)
}
