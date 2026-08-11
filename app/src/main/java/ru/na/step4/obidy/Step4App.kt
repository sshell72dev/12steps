package ru.na.step4.obidy



import android.app.Application

import kotlinx.coroutines.CoroutineScope

import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.SupervisorJob

import kotlinx.coroutines.launch

import ru.na.step4.obidy.data.AppDatabase

import ru.na.step4.obidy.data.ResentmentRepository



class Step4App : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)



    lateinit var repository: ResentmentRepository

        private set



    override fun onCreate() {

        super.onCreate()

        val db = AppDatabase.get(this)

        repository = ResentmentRepository(

            db.resentmentDao(),

            db.categoryDao(),

            db.situationDao()

        )

        appScope.launch {

            repository.ensureDefaultCategories()

        }

    }

}

