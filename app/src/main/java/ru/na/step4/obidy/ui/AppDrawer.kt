package ru.na.step4.obidy.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.Badge
import androidx.compose.material3.DrawerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.na.step4.obidy.Ru
import ru.na.step4.obidy.ui.theme.Amber
import ru.na.step4.obidy.ui.theme.Forest
import ru.na.step4.obidy.ui.theme.Sand
import ru.na.step4.obidy.ui.theme.SandDeep

val LocalOpenDrawer = staticCompositionLocalOf<() -> Unit> { {} }

data class AppMenuItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val badge: Int = 0
)

@Composable
fun AppNavIcon(onBack: (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = Ru.back,
                    tint = Forest
                )
            }
        }
        IconButton(onClick = LocalOpenDrawer.current) {
            Icon(
                Icons.Outlined.Menu,
                contentDescription = Ru.menu,
                tint = Forest
            )
        }
    }
}

@Composable
fun AppMenuDrawer(
    drawerState: DrawerState,
    selectedRoute: String?,
    items: List<AppMenuItem>,
    onOpenRoute: (String) -> Unit,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    CompositionLocalProvider(LocalOpenDrawer provides openDrawer) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    drawerContainerColor = Sand,
                    drawerContentColor = Forest
                ) {
                    Spacer(Modifier.statusBarsPadding().height(8.dp))
                    Text(
                        Ru.homeEyebrow,
                        style = MaterialTheme.typography.labelMedium,
                        color = Forest.copy(alpha = 0.65f),
                        modifier = Modifier.padding(horizontal = 28.dp)
                    )
                    Text(
                        Ru.appName,
                        style = MaterialTheme.typography.titleLarge,
                        color = Forest,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 4.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    items.forEach { item ->
                        NavigationDrawerItem(
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(item.label) },
                            badge = if (item.badge > 0) {
                                {
                                    Badge(containerColor = Amber, contentColor = Forest) {
                                        Text(if (item.badge > 99) "99+" else item.badge.toString())
                                    }
                                }
                            } else {
                                null
                            },
                            selected = selectedRoute == item.route,
                            onClick = {
                                scope.launch { drawerState.close() }
                                onOpenRoute(item.route)
                            },
                            modifier = Modifier.padding(horizontal = 12.dp),
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = SandDeep,
                                selectedIconColor = Forest,
                                selectedTextColor = Forest,
                                unselectedContainerColor = Sand,
                                unselectedIconColor = Forest,
                                unselectedTextColor = Forest
                            )
                        )
                    }
                }
            },
            content = content
        )
    }
}
