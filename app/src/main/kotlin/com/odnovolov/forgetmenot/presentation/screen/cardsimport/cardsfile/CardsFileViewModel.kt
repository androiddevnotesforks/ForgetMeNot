package com.odnovolov.forgetmenot.presentation.screen.cardsimport.cardsfile

import com.odnovolov.forgetmenot.domain.entity.*
import com.odnovolov.forgetmenot.domain.interactor.deckeditor.checkDeckName
import com.odnovolov.forgetmenot.domain.interactor.cardsimport.CardsFile
import com.odnovolov.forgetmenot.domain.interactor.cardsimport.CardsImporter
import kotlinx.coroutines.flow.*

class CardsFileViewModel(
    private val cardsFileId: Long,
    cardsImporterState: CardsImporter.State,
    private val globalState: GlobalState
) {
    sealed class FileImportScreenTitle {
        object Regular : FileImportScreenTitle()
        class Position(val title: String) : FileImportScreenTitle()
    }

    private val positionInList: Flow<Pair<Int, Int>> = cardsImporterState
        .flowOf(CardsImporter.State::files)
        .mapNotNull { files: List<CardsFile> ->
            val position = files.indexOfFirst { file: CardsFile -> file.id == cardsFileId }
            if (position == -1) null else position to files.size
        }

    val screenTitle: Flow<FileImportScreenTitle> =
        positionInList.map { (position: Int, size: Int) ->
            if (size < 2) {
                FileImportScreenTitle.Regular
            } else {
                val ordinal = position + 1
                val title = "$ordinal/$size"
                FileImportScreenTitle.Position(title)
            }
        }

    val isPreviousButtonEnabled: Flow<Boolean> =
        positionInList.map { (position: Int, _) -> position > 0 }

    val isNextButtonEnabled: Flow<Boolean> =
        positionInList.map { (position: Int, size: Int) -> position < size - 1 }

    val isSkipButtonEnabled: Flow<Boolean> =
        positionInList.map { (_, size: Int) -> size > 1 }

    private val cardsFile: Flow<CardsFile> =
        cardsImporterState.flowOf(CardsImporter.State::files)
            .map { files: List<CardsFile> ->
                files.find { file: CardsFile -> file.id == cardsFileId }
            }
            .filterNotNull()

    private val deckWhereToAdd = cardsFile.flatMapLatest { cardsFile: CardsFile ->
        cardsFile.flowOf(CardsFile::deckWhereToAdd)
    }

    val deckName: Flow<String> = deckWhereToAdd.flatMapLatest { deckWhereToAdd: AbstractDeck ->
        when (deckWhereToAdd) {
            is NewDeck -> deckWhereToAdd.flowOf(NewDeck::deckName)
            is ExistingDeck -> deckWhereToAdd.deck.flowOf(Deck::name)
            else -> error(ERROR_MESSAGE_UNKNOWN_IMPLEMENTATION_OF_ABSTRACT_DECK)
        }
    }

    val deckNameCheckResult: Flow<NameCheckResult> =
        deckWhereToAdd.flatMapLatest { abstractDeck: AbstractDeck ->
            when (abstractDeck) {
                is NewDeck -> {
                    abstractDeck.flowOf(NewDeck::deckName)
                        .map { deckName: String -> checkDeckName(deckName, globalState) }
                }
                is ExistingDeck -> flowOf(NameCheckResult.Ok)
                else -> error(ERROR_MESSAGE_UNKNOWN_IMPLEMENTATION_OF_ABSTRACT_DECK)
            }
        }

    val isNewDeck: Flow<Boolean> =
        deckWhereToAdd.map { deckWhereToAdd: AbstractDeck -> deckWhereToAdd is NewDeck }

    val hasErrorsInSourceText: Flow<Boolean> = cardsFile.flatMapLatest { cardsFile: CardsFile ->
        cardsFile.flowOf(CardsFile::errors)
    }
        .map { it.isNotEmpty() }
        .distinctUntilChanged()
}