package com.odnovolov.forgetmenot.presentation.common.mainactivity

import com.odnovolov.forgetmenot.persistence.longterm.fullscreenpreference.FullscreenPreferenceProvider
import com.odnovolov.forgetmenot.persistence.longterm.initialdecksadderstate.InitialDecksAdderStateProvider
import com.odnovolov.forgetmenot.presentation.common.di.AppDiScope
import com.odnovolov.forgetmenot.presentation.common.di.DiScopeManager
import com.odnovolov.forgetmenot.presentation.common.entity.FullscreenPreference

class MainActivityDiScope {
    private val initialDecksAdderStateProvider by lazy {
        InitialDecksAdderStateProvider(
            AppDiScope.get().database
        )
    }

    private val initialDecksAdderState: InitialDecksAdder.State by lazy {
        initialDecksAdderStateProvider.load()
    }

    val initialDecksAdder: InitialDecksAdder by lazy {
        InitialDecksAdder(
            initialDecksAdderState,
            AppDiScope.get().app.assets,
            AppDiScope.get().globalState,
            AppDiScope.get().cardsImportStorage,
            AppDiScope.get().longTermStateSaver
        )
    }

    val fullScreenPreference: FullscreenPreference =
        FullscreenPreferenceProvider(AppDiScope.get().database).load()

    companion object : DiScopeManager<MainActivityDiScope>() {
        override fun recreateDiScope() = MainActivityDiScope()
    }
}