package com.nuelious.android.apb

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ApbApp() }
    }
}

@Composable
private fun ApbApp() {
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("APB", style = MaterialTheme.typography.displayLarge)
                Text("Android Package Builder", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(20.dp))
                Text("Foundation build — project importer and build runtime are under development.")
            }
        }
    }
}
