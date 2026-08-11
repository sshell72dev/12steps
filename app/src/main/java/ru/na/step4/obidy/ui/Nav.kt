package ru.na.step4.obidy.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ru.na.step4.obidy.Step4App

private object Routes {
    const val LIST = "list"
    const val GUIDE = "guide"
    const val CATEGORIES = "categories"
    const val ASSISTANT = "assistant"
    const val ASSISTANT_FOCUS = "assistant/{situationId}/{focus}"
    const val EDIT = "edit/{id}"
    const val SITUATION = "situation/{id}"

    fun edit(id: Long) = "edit/$id"
    fun situation(id: Long) = "situation/$id"
    fun assistantFocus(situationId: Long, focus: String) = "assistant/$situationId/$focus"
}

@Composable
fun Step4Nav() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val app = context.applicationContext as Step4App
    val repository = app.repository
    val activity = context as ComponentActivity

    NavHost(navController = navController, startDestination = Routes.LIST) {
        composable(Routes.LIST) {
            val vm: ListViewModel = viewModel(factory = ListViewModel.factory(repository))
            ListScreen(
                viewModel = vm,
                onOpen = { id -> navController.navigate(Routes.edit(id)) },
                onGuide = { navController.navigate(Routes.GUIDE) },
                onCategories = { navController.navigate(Routes.CATEGORIES) },
                onAssistant = { navController.navigate(Routes.ASSISTANT) }
            )
        }
        composable(Routes.GUIDE) {
            GuideScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.CATEGORIES) {
            val vm: CategoriesViewModel = viewModel(
                factory = CategoriesViewModel.factory(repository)
            )
            CategoriesScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.ASSISTANT) {
            val vm: AssistantViewModel = viewModel(
                factory = AssistantViewModel.factory(
                    app = app as Application,
                    repository = repository
                )
            )
            DisposableEffect(activity) {
                vm.attachHost(activity, activity.lifecycle)
                onDispose { }
            }
            AssistantScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onOpenResentment = { id ->
                    navController.navigate(Routes.edit(id)) {
                        popUpTo(Routes.LIST)
                    }
                }
            )
        }
        composable(
            route = Routes.ASSISTANT_FOCUS,
            arguments = listOf(
                navArgument("situationId") { type = NavType.LongType },
                navArgument("focus") { type = NavType.StringType }
            )
        ) { entry ->
            val situationId = entry.arguments?.getLong("situationId") ?: -1L
            val focus = entry.arguments?.getString("focus").orEmpty()
            val vm: AssistantViewModel = viewModel(
                key = "assist-$situationId-$focus",
                factory = AssistantViewModel.factory(
                    app = app as Application,
                    repository = repository,
                    situationId = situationId,
                    focusKey = focus
                )
            )
            DisposableEffect(activity) {
                vm.attachHost(activity, activity.lifecycle)
                onDispose { }
            }
            AssistantScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onOpenResentment = { id ->
                    navController.navigate(Routes.edit(id)) {
                        popUpTo(Routes.LIST)
                    }
                }
            )
        }
        composable(
            route = Routes.EDIT,
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: 0L
            val vm: EditViewModel = viewModel(
                factory = EditViewModel.factory(repository, id)
            )
            EditScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onOpenSituation = { situationId ->
                    navController.navigate(Routes.situation(situationId))
                }
            )
        }
        composable(
            route = Routes.SITUATION,
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: 0L
            val vm: SituationEditViewModel = viewModel(
                factory = SituationEditViewModel.factory(repository, id)
            )
            SituationEditScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onAssistantFocus = { situationId, focus ->
                    navController.navigate(Routes.assistantFocus(situationId, focus))
                }
            )
        }
    }
}
