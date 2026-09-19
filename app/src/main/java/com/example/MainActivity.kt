package com.example

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.ui.GoalDotsApp
import com.example.ui.theme.MyApplicationTheme
import com.example.data.GoalRepository

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "GOAL_ACTIVITY"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "MainActivity onCreate: taskId=$taskId, isTaskRoot=$isTaskRoot")
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    GoalDotsApp()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "MainActivity onDestroy: isFinishing=$isFinishing")
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            // HyperOS can occasionally omit visible=true for the wallpaper after Recents
            // dismisses this Activity. The Engine validates its Surface before one retry.
            sendBroadcast(
                android.content.Intent(GoalRepository.ACTION_APP_MOVED_TO_BACKGROUND)
                    .setPackage(packageName)
            )
            Log.d(TAG, "MainActivity onStop: sent wallpaper recovery hint")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GoalDotsAppPreview() {
    MyApplicationTheme {
        GoalDotsApp()
    }
}
