package com.lazarus.personalojtdtr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lazarus.personalojtdtr.ui.OjtViewModel
import com.lazarus.personalojtdtr.ui.activity.ActivityViewModel
import com.lazarus.personalojtdtr.ui.navigation.OjtNavigation
import com.lazarus.personalojtdtr.ui.theme.OjtTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OjtTheme {
                val model: OjtViewModel = viewModel(factory = OjtViewModel.factory((application as OjtApplication).repository, (application as OjtApplication).recorder, (application as OjtApplication).backup))
                val activities: ActivityViewModel = viewModel(factory = ActivityViewModel.factory((application as OjtApplication).journal))
                OjtNavigation(model, activities)
            }
        }
    }
}
