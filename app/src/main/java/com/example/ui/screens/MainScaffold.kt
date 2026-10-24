package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AuraViewModel
import com.example.ui.NavigationTab

@Composable
fun MainScaffold(
    viewModel: AuraViewModel,
    modifier: Modifier = Modifier
) {
    val currentTab by viewModel.currentTab.collectAsState()
    val statusSnackbar by viewModel.statusSnackbar.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Display transient snackbars
    LaunchedEffect(statusSnackbar) {
        statusSnackbar?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissSnackbar()
        }
    }

    // Handle back button on secondary screens
    if (currentTab != NavigationTab.ASSISTANT) {
        BackHandler {
            viewModel.setTab(NavigationTab.ASSISTANT)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier.testTag("main_bottom_nav")
            ) {
                NavigationBarItem(
                    selected = currentTab == NavigationTab.ASSISTANT,
                    onClick = { viewModel.setTab(NavigationTab.ASSISTANT) },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Assistant"
                        )
                    },
                    label = { Text("Assistant", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.testTag("nav_item_assistant")
                )

                NavigationBarItem(
                    selected = currentTab == NavigationTab.SCHEDULE,
                    onClick = { viewModel.setTab(NavigationTab.SCHEDULE) },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = "Schedule"
                        )
                    },
                    label = { Text("Calendar", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.testTag("nav_item_calendar")
                )

                NavigationBarItem(
                    selected = currentTab == NavigationTab.TASKS,
                    onClick = { viewModel.setTab(NavigationTab.TASKS) },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Tasks"
                        )
                    },
                    label = { Text("Tasks", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.testTag("nav_item_tasks")
                )

                NavigationBarItem(
                    selected = currentTab == NavigationTab.EMAILS,
                    onClick = { viewModel.setTab(NavigationTab.EMAILS) },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = "Drafts"
                        )
                    },
                    label = { Text("Drafts", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.testTag("nav_item_emails")
                )
            }
        }
    ) { innerPadding ->
        when (currentTab) {
            NavigationTab.ASSISTANT -> AssistantScreen(
                viewModel = viewModel,
                modifier = Modifier.padding(innerPadding)
            )
            NavigationTab.SCHEDULE -> ScheduleScreen(
                viewModel = viewModel,
                modifier = Modifier.padding(innerPadding)
            )
            NavigationTab.TASKS -> TasksScreen(
                viewModel = viewModel,
                modifier = Modifier.padding(innerPadding)
            )
            NavigationTab.EMAILS -> EmailDrafterScreen(
                viewModel = viewModel,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}
