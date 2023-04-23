package com.odnovolov.forgetmenot.presentation.screen.home

import com.odnovolov.forgetmenot.domain.entity.*
import com.odnovolov.forgetmenot.domain.interactor.autoplay.PlayerStateCreator
import com.odnovolov.forgetmenot.domain.interactor.cardeditor.BatchCardEditor
import com.odnovolov.forgetmenot.domain.interactor.cardeditor.CardsEditor.State
import com.odnovolov.forgetmenot.domain.interactor.cardeditor.CardsEditorForEditingSpecificCards
import com.odnovolov.forgetmenot.domain.interactor.cardeditor.EditableCard
import com.odnovolov.forgetmenot.domain.interactor.decklistseditor.DeckListsEditor
import com.odnovolov.forgetmenot.domain.interactor.decklistseditor.addDeckIds
import com.odnovolov.forgetmenot.domain.interactor.decklistseditor.removeDeckIds
import com.odnovolov.forgetmenot.domain.interactor.decksettings.DeckPresetSetter
import com.odnovolov.forgetmenot.domain.interactor.exercise.Exercise
import com.odnovolov.forgetmenot.domain.interactor.exercise.ExerciseStateCreator
import com.odnovolov.forgetmenot.domain.interactor.exercise.ExerciseStateCreatorWithFiltering
import com.odnovolov.forgetmenot.domain.interactor.operationsondecks.DeckMerger
import com.odnovolov.forgetmenot.domain.interactor.operationsondecks.DeckRemover
import com.odnovolov.forgetmenot.domain.interactor.operationsondecks.into
import com.odnovolov.forgetmenot.domain.interactor.searcher.CardsSearcher
import com.odnovolov.forgetmenot.domain.interactor.searcher.FoundCard
import com.odnovolov.forgetmenot.presentation.common.LongTermStateSaver
import com.odnovolov.forgetmenot.presentation.common.Navigator
import com.odnovolov.forgetmenot.presentation.common.ShortTermStateProvider
import com.odnovolov.forgetmenot.presentation.common.base.BaseController
import com.odnovolov.forgetmenot.presentation.common.firstBlocking
import com.odnovolov.forgetmenot.presentation.screen.cardfilterforautoplay.CardFilterForAutoplayDiScope
import com.odnovolov.forgetmenot.presentation.screen.cardfilterforexercise.CardFilterForExerciseDiScope
import com.odnovolov.forgetmenot.presentation.screen.cardseditor.CardsEditorDiScope
import com.odnovolov.forgetmenot.presentation.screen.changegrade.ChangeGradeCaller
import com.odnovolov.forgetmenot.presentation.screen.changegrade.ChangeGradeDiScope
import com.odnovolov.forgetmenot.presentation.screen.changegrade.ChangeGradeDialogState
import com.odnovolov.forgetmenot.presentation.screen.changegrade.GradeItem
import com.odnovolov.forgetmenot.presentation.screen.deckchooser.DeckChooserDiScope
import com.odnovolov.forgetmenot.presentation.screen.deckchooser.DeckChooserScreenState
import com.odnovolov.forgetmenot.presentation.screen.deckchooser.DeckChooserScreenState.Purpose.*
import com.odnovolov.forgetmenot.presentation.screen.deckeditor.DeckEditorDiScope
import com.odnovolov.forgetmenot.presentation.screen.deckeditor.DeckEditorScreenState
import com.odnovolov.forgetmenot.presentation.screen.deckeditor.DeckEditorScreenTab
import com.odnovolov.forgetmenot.presentation.screen.deckeditor.DeckEditorTabs
import com.odnovolov.forgetmenot.presentation.screen.decklistseditor.DeckListEditorScreenState
import com.odnovolov.forgetmenot.presentation.screen.decklistseditor.DeckListsEditorDiScope
import com.odnovolov.forgetmenot.presentation.screen.exercise.ExerciseDiScope
import com.odnovolov.forgetmenot.presentation.screen.exercisesettings.CardPrefilterMode.*
import com.odnovolov.forgetmenot.presentation.screen.exercisesettings.ExerciseSettings
import com.odnovolov.forgetmenot.presentation.screen.cardsexport.CardsExportDiScope
import com.odnovolov.forgetmenot.presentation.screen.cardsexport.CardsExportDialogState
import com.odnovolov.forgetmenot.presentation.screen.home.ChooseDeckListDialogPurpose.ToAddDeckToDeckList
import com.odnovolov.forgetmenot.presentation.screen.home.ChooseDeckListDialogPurpose.ToRemoveDeckFromDeckList
import com.odnovolov.forgetmenot.presentation.screen.home.DeckSorting.Direction.Asc
import com.odnovolov.forgetmenot.presentation.screen.home.DeckSorting.Direction.Desc
import com.odnovolov.forgetmenot.presentation.screen.home.HomeController.Command
import com.odnovolov.forgetmenot.presentation.screen.home.HomeController.Command.*
import com.odnovolov.forgetmenot.presentation.screen.home.HomeEvent.*
import com.odnovolov.forgetmenot.presentation.screen.renamedeck.RenameDeckDiScope
import com.odnovolov.forgetmenot.presentation.screen.renamedeck.RenameDeckDialogPurpose.ToRenameExistingDeckOnHomeScreen
import com.odnovolov.forgetmenot.presentation.screen.renamedeck.RenameDeckDialogState
import kotlinx.coroutines.flow.Flow

class HomeController(
    private val screenState: HomeScreenState,
    private val deckReviewPreference: DeckReviewPreference,
    private val deckRemover: DeckRemover,
    private val deckMerger: DeckMerger,
    private val exerciseStateCreator: ExerciseStateCreator,
    private val cardsSearcher: CardsSearcher,
    private val batchCardEditor: BatchCardEditor,
    private val deckPresetSetter: DeckPresetSetter,
    private val exerciseSettings: ExerciseSettings,
    private val globalState: GlobalState,
    private val navigator: Navigator,
    private val longTermStateSaver: LongTermStateSaver,
    private val screenStateProvider: ShortTermStateProvider<HomeScreenState>,
    private val batchCardEditorProvider: ShortTermStateProvider<BatchCardEditor>
) : BaseController<HomeEvent, Command>() {
    sealed class Command {
        object ShowNoExerciseCardDialog : Command()
        object ShowDeckOptions : Command()
        object ShowDeckSelectionOptions : Command()
        object ShowCardSelectionOptions : Command()
        class ShowDeckRemovingMessage(val numberOfRemovedDecks: Int) : Command()
        class ShowDeckMergingMessage(
            val numberOfMergedDecks: Int,
            val deckNameMergedInto: String
        ) : Command()

        class ShowCardsAreInvertedMessage(val numberOfInvertedCards: Int) : Command()
        class ShowGradeIsChangedMessage(val grade: Int, val numberOfAffectedCards: Int) : Command()
        class ShowCardsAreMarkedAsLearnedMessage(val numberOfMarkedCards: Int) : Command()
        class ShowCardsAreMarkedAsUnlearnedMessage(val numberOfMarkedCards: Int) : Command()
        class ShowCardsAreRemovedMessage(val numberOfRemovedCards: Int) : Command()
        class ShowCardsAreMovedMessage(
            val numberOfMovedCards: Int,
            val deckNameToWhichCardsWereMoved: String
        ) : Command()

        class ShowCardsAreCopiedMessage(
            val numberOfCopiedCards: Int,
            val deckNameToWhichCardsWereCopied: String
        ) : Command()

        object ShowDeckListsChooser : Command()
        object ShowPresetChooser : Command()

        class ShowPresetHasBeenAppliedMessage(
            val numberOfAffectedDecks: Int,
            val presetName: String
        ) : Command()
    }

    init {
        if (screenState.searchText.isNotEmpty()) {
            cardsSearcher.search(screenState.searchText)
        }
    }

    lateinit var displayedDeckIds: Flow<List<Long>>
    private var needToResearchOnCancel = false
    private val deckIdsInOptionsMenu: List<Long>
        get() = screenState.deckSelection?.selectedDeckIds
            ?: screenState.deckForDeckOptionMenu?.let { listOf(it.id) }
            ?: emptyList()

    override fun handle(event: HomeEvent) {
        when (event) {
            is SearchTextChanged -> {
                screenState.searchText = event.searchText
                cardsSearcher.search(event.searchText)
            }

            DecksAvailableForExerciseCheckboxClicked -> {
                with(deckReviewPreference) {
                    displayOnlyDecksAvailableForExercise = !displayOnlyDecksAvailableForExercise
                }
            }

            EditDeckListsButtonClicked -> {
                navigator.navigateToDeckListsEditor {
                    val deckListsEditorState = DeckListsEditor.State.create(globalState)
                    val screenState = DeckListEditorScreenState(isForCreation = false)
                    DeckListsEditorDiScope.create(deckListsEditorState, screenState)
                }
            }

            is DeckListWasSelected -> {
                val selectedDeckList: DeckList? = event.deckListId?.let { deckListId: Long ->
                    globalState.deckLists.find { deckList: DeckList -> deckList.id == deckListId }
                }
                deckReviewPreference.deckList = selectedDeckList
            }

            CreateDeckListButtonClicked -> {
                navigator.navigateToDeckListsEditor {
                    val deckListsEditorState = DeckListsEditor.State.create(globalState)
                    val screenState = DeckListEditorScreenState(isForCreation = true)
                    DeckListsEditorDiScope.create(deckListsEditorState, screenState)
                }
            }

            SortingDirectionButtonClicked -> {
                with(deckReviewPreference) {
                    val newDirection = if (deckSorting.direction == Asc) Desc else Asc
                    deckSorting = deckSorting.copy(direction = newDirection)
                }
            }

            is SortByButtonClicked -> {
                with(deckReviewPreference) {
                    deckSorting = if (event.criterion == deckSorting.criterion) {
                        val newDirection = if (deckSorting.direction == Asc) Desc else Asc
                        deckSorting.copy(direction = newDirection)
                    } else {
                        deckSorting.copy(criterion = event.criterion)
                    }
                }
            }

            NewDecksFirstCheckboxClicked -> {
                with(deckReviewPreference) {
                    deckSorting = deckSorting.copy(newDecksFirst = !deckSorting.newDecksFirst)
                }
            }

            is DeckButtonClicked -> {
                if (screenState.deckSelection != null) {
                    toggleDeckSelection(event.deckId)
                } else {
                    tryToStartExercise(listOf(event.deckId))
                }
            }

            is DeckButtonLongClicked -> {
                toggleDeckSelection(event.deckId)
            }

            is DeckSelectorClicked -> {
                toggleDeckSelection(event.deckId)
            }

            is DeckOptionButtonClicked -> {
                val deck: Deck = globalState.decks.first { it.id == event.deckId }
                screenState.deckForDeckOptionMenu = deck
                sendCommand(ShowDeckOptions)
            }

            StartExerciseDeckOptionWasSelected -> {
                val deckId: Long = screenState.deckForDeckOptionMenu?.id ?: return
                tryToStartExercise(deckIds = listOf(deckId))
            }

            AutoplayDeckOptionWasSelected -> {
                val deckId: Long = screenState.deckForDeckOptionMenu?.id ?: return
                navigateToAutoplaySettings(deckIds = listOf(deckId))
            }

            RenameDeckOptionWasSelected -> {
                val deckId: Long = screenState.deckForDeckOptionMenu?.id ?: return
                navigator.showRenameDeckDialogFromNavHost {
                    val deck = globalState.decks.first { it.id == deckId }
                    val dialogState = RenameDeckDialogState(
                        purpose = ToRenameExistingDeckOnHomeScreen(deck),
                        typedDeckName = deck.name
                    )
                    RenameDeckDiScope.create(dialogState)
                }
            }

            SetupDeckOptionWasSelected -> {
                val deckId: Long = screenState.deckForDeckOptionMenu?.id ?: return
                navigateToDeckEditor(deckId, DeckEditorScreenTab.Settings)
            }

            EditCardsDeckOptionWasSelected -> {
                val deckId: Long = screenState.deckForDeckOptionMenu?.id ?: return
                navigateToDeckEditor(deckId, DeckEditorScreenTab.Cards)
            }

            PinDeckOptionWasSelected -> {
                screenState.deckForDeckOptionMenu?.isPinned = true
                notifyDeckListUpdated()
            }

            UnpinDeckOptionWasSelected -> {
                screenState.deckForDeckOptionMenu?.isPinned = false
                notifyDeckListUpdated()
            }

            AddToDeckListDeckOptionWasSelected, AddToDeckListDeckSelectionOptionWasSelected -> {
                screenState.chooseDeckListDialogPurpose = ToAddDeckToDeckList
                sendCommand(ShowDeckListsChooser)
            }

            RemoveFromDeckListDeckOptionWasSelected, RemoveFromDeckListDeckSelectionOptionWasSelected -> {
                var theOnlyDeckListToWhichRelevantDecksBelong: DeckList? = null
                for (deckId: Long in deckIdsInOptionsMenu) {
                    for (deckList: DeckList in globalState.deckLists) {
                        if (deckId in deckList.deckIds) {
                            when {
                                theOnlyDeckListToWhichRelevantDecksBelong == null -> {
                                    theOnlyDeckListToWhichRelevantDecksBelong = deckList
                                }
                                deckList.id != theOnlyDeckListToWhichRelevantDecksBelong.id -> {
                                    screenState.chooseDeckListDialogPurpose =
                                        ToRemoveDeckFromDeckList
                                    sendCommand(ShowDeckListsChooser)
                                    return
                                }
                            }
                        }
                    }
                }
                theOnlyDeckListToWhichRelevantDecksBelong?.removeDeckIds(deckIdsInOptionsMenu)
                screenState.deckSelection = null
                notifyDeckListUpdated()
            }

            SetPresetDeckSelectionOptionWasSelected -> {
                sendCommand(ShowPresetChooser)
            }

            is PresetButtonClicked -> {
                val selectedDeckIds = screenState.deckSelection?.selectedDeckIds ?: return
                val decks = globalState.decks.filter { deck: Deck -> deck.id in selectedDeckIds }
                val exercisePreference: ExercisePreference =
                    if (event.exercisePreferenceId == ExercisePreference.Default.id) {
                        ExercisePreference.Default
                    } else {
                        globalState.sharedExercisePreferences.find { sharedExercisePreference ->
                            sharedExercisePreference.id == event.exercisePreferenceId
                        } ?: return
                    }
                val numberOfAffectedDecks: Int =
                    deckPresetSetter.setDeckPreset(decks, exercisePreference)
                if (numberOfAffectedDecks > 0) {
                    sendCommand(
                        ShowPresetHasBeenAppliedMessage(
                            numberOfAffectedDecks,
                            exercisePreference.name
                        )
                    )
                }
                screenState.deckSelection = null
            }

            PresetHasBeenAppliedSnackbarCancelButtonClicked -> {
                deckPresetSetter.cancel()
            }

            ExportDeckOptionWasSelected -> {
                val deck = screenState.deckForDeckOptionMenu ?: return
                navigator.navigateToCardsExportFromNavHost {
                    val dialogState = CardsExportDialogState(listOf(deck))
                    CardsExportDiScope.create(dialogState)
                }
            }

            MergeIntoDeckOptionWasSelected -> {
                navigateToDeckChooser()
            }

            RemoveDeckOptionWasSelected -> {
                val deckId: Long = screenState.deckForDeckOptionMenu?.id ?: return
                val numberOfRemovedDecks = deckRemover.removeDeck(deckId)
                sendCommand(ShowDeckRemovingMessage(numberOfRemovedDecks))
                notifyDeckListUpdated()
            }

            AutoplayButtonClicked -> {
                screenState.deckSelection?.let { deckSelection: DeckSelection ->
                    if (deckSelection.selectedDeckIds.isEmpty()
                        || deckSelection.purpose !in listOf(
                            DeckSelection.Purpose.General,
                            DeckSelection.Purpose.ForAutoplay
                        )
                    ) {
                        return
                    }
                    navigateToAutoplaySettings(deckSelection.selectedDeckIds)
                } ?: kotlin.run {
                    screenState.deckSelection = DeckSelection(
                        selectedDeckIds = emptyList(),
                        purpose = DeckSelection.Purpose.ForAutoplay
                    )
                }
            }

            ExerciseButtonClicked -> {
                screenState.deckSelection?.let { deckSelection: DeckSelection ->
                    if (deckSelection.selectedDeckIds.isEmpty()
                        || deckSelection.purpose !in listOf(
                            DeckSelection.Purpose.General,
                            DeckSelection.Purpose.ForExercise
                        )
                    ) {
                        return
                    }
                    tryToStartExercise(deckSelection.selectedDeckIds)
                } ?: kotlin.run {
                    screenState.deckSelection = DeckSelection(
                        selectedDeckIds = emptyList(),
                        purpose = DeckSelection.Purpose.ForExercise
                    )
                }
            }

            is FoundCardClicked -> {
                val foundCard: FoundCard = cardsSearcher.state.searchResult
                    .find { it.card.id == event.cardId } ?: return
                if (isCardSelection()) {
                    toggleCardSelection(foundCard)
                } else {
                    navigateToCardsEditor(foundCard)
                }
            }

            is FoundCardLongClicked -> {
                val foundCard: FoundCard = cardsSearcher.state.searchResult
                    .find { it.card.id == event.cardId } ?: return
                toggleCardSelection(foundCard)
            }

            SelectionWasCancelled -> {
                when {
                    isDeckSelection() -> screenState.deckSelection = null
                    isCardSelection() -> batchCardEditor.clearSelection()
                }
            }

            SelectAllSelectionToolbarButtonClicked -> {
                when {
                    isDeckSelection() -> {
                        val allDisplayedDeckIds: List<Long> = displayedDeckIds.firstBlocking()
                        screenState.deckSelection =
                            screenState.deckSelection?.copy(selectedDeckIds = allDisplayedDeckIds)
                    }
                    isCardSelection() -> {
                        val allEditableCards: List<EditableCard> =
                            cardsSearcher.state.searchResult.map { foundCard: FoundCard ->
                                EditableCard(foundCard.card, foundCard.deck)
                            }
                        batchCardEditor.addCardsToSelection(allEditableCards)
                    }
                }
            }

            MoreSelectionToolbarButtonClicked -> {
                when {
                    isDeckSelection() -> sendCommand(ShowDeckSelectionOptions)
                    isCardSelection() -> sendCommand(ShowCardSelectionOptions)
                }
            }

            PinDeckSelectionOptionWasSelected -> {
                val selectedDeckIds: List<Long> =
                    screenState.deckSelection?.selectedDeckIds ?: return
                for (deck in globalState.decks) {
                    if (deck.id in selectedDeckIds && !deck.isPinned) {
                        deck.isPinned = true
                    }
                }
                screenState.deckSelection = null
                notifyDeckListUpdated()
            }

            UnpinDeckSelectionOptionWasSelected -> {
                val selectedDeckIds: List<Long> =
                    screenState.deckSelection?.selectedDeckIds ?: return
                for (deck in globalState.decks) {
                    if (deck.id in selectedDeckIds && deck.isPinned) {
                        deck.isPinned = false
                    }
                }
                screenState.deckSelection = null
                notifyDeckListUpdated()
            }

            ExportDeckSelectionOptionWasSelected -> {
                val selectedDeckIds: List<Long> =
                    screenState.deckSelection?.selectedDeckIds ?: return
                if (selectedDeckIds.isEmpty()) return
                navigator.navigateToCardsExportFromNavHost {
                    val decks: List<Deck> =
                        globalState.decks.filter { deck: Deck -> deck.id in selectedDeckIds }
                    val dialogState = CardsExportDialogState(decks)
                    CardsExportDiScope.create(dialogState)
                }
            }

            MergeIntoDeckSelectionOptionWasSelected -> {
                navigateToDeckChooser()
            }

            is DeckToMergeIntoWasSelected -> {
                val selectedDeckIds: List<Long> =
                    screenState.deckSelection?.selectedDeckIds
                        ?: screenState.deckForDeckOptionMenu?.let { deck -> listOf(deck.id) }
                        ?: return
                val selectedDecks: List<Deck> =
                    globalState.decks.filter { deck: Deck -> deck.id in selectedDeckIds }
                val numberOfMergedDecks = deckMerger.merge(selectedDecks into event.abstractDeck)
                if (numberOfMergedDecks > 0) {
                    val deckNameMergedInto: String = when (event.abstractDeck) {
                        is NewDeck -> event.abstractDeck.deckName
                        is ExistingDeck -> event.abstractDeck.deck.name
                        else -> error(ERROR_MESSAGE_UNKNOWN_IMPLEMENTATION_OF_ABSTRACT_DECK)
                    }
                    sendCommand(
                        command = ShowDeckMergingMessage(numberOfMergedDecks, deckNameMergedInto),
                        postponeIfNotActive = true
                    )
                }
                screenState.deckSelection = null
            }

            MergedDecksSnackbarCancelButtonClicked -> {
                deckMerger.cancel()
            }

            RemoveDeckSelectionOptionWasSelected -> {
                removeSelectedDecks()
            }

            RemovedDecksSnackbarCancelButtonClicked -> {
                deckRemover.cancelRemoving()
                notifyDeckListUpdated()
            }

            InvertCardSelectionOptionWasSelected -> {
                val numberOfInvertedCards: Int = batchCardEditor.state.selectedCards.size
                batchCardEditor.invert()
                sendCommand(ShowCardsAreInvertedMessage(numberOfInvertedCards))
                needToResearchOnCancel = false
            }

            ChangeGradeCardSelectionOptionWasSelected -> {
                navigator.showChangeGradeDialogFromNavHost {
                    val dialogState = ChangeGradeDialogState(
                        gradeItems = determineGradeItems(),
                        caller = ChangeGradeCaller.HomeSearch
                    )
                    ChangeGradeDiScope.create(dialogState)
                }
            }

            is GradeWasSelected -> {
                val numberOfAffectedCards: Int = batchCardEditor.state.selectedCards.size
                batchCardEditor.changeGrade(event.grade)
                sendCommand(ShowGradeIsChangedMessage(event.grade, numberOfAffectedCards))
                needToResearchOnCancel = false
            }

            MarkAsLearnedCardSelectionOptionWasSelected -> {
                val numberOfMarkedCards: Int = batchCardEditor.state.selectedCards.size
                batchCardEditor.markAsLearned()
                sendCommand(ShowCardsAreMarkedAsLearnedMessage(numberOfMarkedCards))
                needToResearchOnCancel = false
            }

            MarkAsUnlearnedCardSelectionOptionWasSelected -> {
                val numberOfMarkedCards: Int = batchCardEditor.state.selectedCards.size
                batchCardEditor.markAsUnlearned()
                sendCommand(ShowCardsAreMarkedAsUnlearnedMessage(numberOfMarkedCards))
                needToResearchOnCancel = false
            }

            RemoveCardsCardSelectionOptionWasSelected -> {
                val numberOfRemovedCards: Int = batchCardEditor.state.selectedCards.size
                batchCardEditor.remove()
                cardsSearcher.research()
                sendCommand(ShowCardsAreRemovedMessage(numberOfRemovedCards))
                needToResearchOnCancel = true
            }

            MoveCardSelectionOptionWasSelected -> {
                navigator.navigateToDeckChooserFromNavHost {
                    val screenState = DeckChooserScreenState(purpose = ToMoveCardsInHomeSearch)
                    DeckChooserDiScope.create(screenState)
                }
            }

            is DeckToMoveCardsToWasSelected -> {
                val numberOfMovedCards: Int = batchCardEditor.state.selectedCards.size
                batchCardEditor.moveTo(event.abstractDeck)
                val deckName: String = event.abstractDeck.name
                cardsSearcher.research()
                sendCommand(
                    command = ShowCardsAreMovedMessage(numberOfMovedCards, deckName),
                    postponeIfNotActive = true
                )
                needToResearchOnCancel = true
            }

            CopyCardSelectionOptionWasSelected -> {
                navigator.navigateToDeckChooserFromNavHost {
                    val screenState = DeckChooserScreenState(purpose = ToCopyCardsInHomeSearch)
                    DeckChooserDiScope.create(screenState)
                }
            }

            is DeckToCopyCardsToWasSelected -> {
                val numberOfCopiedCards: Int = batchCardEditor.state.selectedCards.size
                batchCardEditor.copyTo(event.abstractDeck)
                val deckName: String = event.abstractDeck.name
                cardsSearcher.research()
                sendCommand(
                    command = ShowCardsAreCopiedMessage(numberOfCopiedCards, deckName),
                    postponeIfNotActive = true
                )
                needToResearchOnCancel = true
            }

            CancelCardSelectionActionSnackbarButtonClicked -> {
                batchCardEditor.cancelLastAction()
                if (needToResearchOnCancel) {
                    needToResearchOnCancel = false
                    cardsSearcher.research()
                }
            }

            is DeckListForAddingDecksWasSelected -> {
                val deckList: DeckList = globalState.deckLists
                    .find { deckList: DeckList -> deckList.id == event.deckListId }
                    ?: return
                deckList.addDeckIds(deckIdsInOptionsMenu)
                screenState.deckSelection = null
                notifyDeckListUpdated()
            }

            CreateDeckListForAddingDecksButtonClicked -> {
                navigator.navigateToDeckListsEditor {
                    val deckListsEditorState = DeckListsEditor.State.create(
                        globalState,
                        deckIdsForNewDeckList = deckIdsInOptionsMenu.toSet()
                    )
                    val screenState = DeckListEditorScreenState(isForCreation = true)
                    DeckListsEditorDiScope.create(deckListsEditorState, screenState)
                }
            }

            is DeckListForRemovingDecksWasSelected -> {
                val deckList: DeckList = globalState.deckLists
                    .find { deckList: DeckList -> deckList.id == event.deckListId }
                    ?: return
                deckList.removeDeckIds(deckIdsInOptionsMenu)
                screenState.deckSelection = null
                notifyDeckListUpdated()
            }

            GoToDeckSettingsButtonClicked -> {
                val deckId = screenState.deckRelatedToNoExerciseCardDialog?.id ?: return
                navigateToDeckEditor(deckId, initialTab = DeckEditorScreenTab.Settings)
            }

            FragmentResumed -> {
                val isCurrentDeckListExists = deckReviewPreference.deckList
                    ?.let { deckList: DeckList -> deckList in globalState.deckLists }
                    ?: true
                if (!isCurrentDeckListExists) {
                    deckReviewPreference.deckList = null
                    notifyDeckListUpdated()
                }
            }
        }
    }

    private fun notifyDeckListUpdated() {
        screenState.updateDeckListSignal = Unit
    }

    private fun tryToStartExercise(deckIds: List<Long>) {
        if (!exerciseStateCreator.areThereCardsAvailableForExerciseMoreThan(0, deckIds)) {
            screenState.deckRelatedToNoExerciseCardDialog =
                if (deckIds.size != 1) {
                    null
                } else {
                    globalState.decks.find { it.id == deckIds.first() } ?: return
                }
            screenState.timeWhenTheFirstCardWillBeAvailable =
                exerciseStateCreator.calculateTimeWhenTheFirstCardWillBeAvailable(deckIds)
            sendCommand(ShowNoExerciseCardDialog)
            return
        }
        when (val cardPrefilterMode = exerciseSettings.cardPrefilterMode) {
            DoNotFilter -> {
                navigateToExercise(deckIds, limit = null)
            }
            is LimitCardsTo -> {
                navigateToExercise(deckIds, limit = cardPrefilterMode.numberOfCards)
            }
            is ShowFilterWhenCardsMoreThan -> {
                val needToShowCardFilter =
                    exerciseStateCreator.areThereCardsAvailableForExerciseMoreThan(
                        cardPrefilterMode.numberOfCards,
                        deckIds
                    )
                if (needToShowCardFilter) {
                    navigateToCardFilterForExercise(deckIds)
                } else {
                    navigateToExercise(deckIds, limit = null)
                }
            }
            AlwaysShowFilter -> {
                navigateToCardFilterForExercise(deckIds)
            }
        }
        screenState.deckSelection = null
    }

    private fun navigateToExercise(deckIds: List<Long>, limit: Int?) {
        navigator.navigateToExerciseFromNavHost {
            val exerciseState: Exercise.State = exerciseStateCreator.create(deckIds, limit)
            ExerciseDiScope.create(exerciseState)
        }
    }

    private fun navigateToCardFilterForExercise(deckIds: List<Long>) {
        navigator.navigateToCardFilterForExercise {
            val decks: List<Deck> = globalState.decks.filter { it.id in deckIds }
            val exerciseCreatorState = ExerciseStateCreatorWithFiltering.State(
                decks,
                globalState.cardFilterForExercise
            )
            CardFilterForExerciseDiScope.create(exerciseCreatorState)
        }
    }

    private fun navigateToAutoplaySettings(deckIds: List<Long>) {
        screenState.deckSelection = null
        navigator.navigateToCardFilterForAutoplay {
            val decks: List<Deck> = globalState.decks.filter { it.id in deckIds }
            val playerCreatorState = PlayerStateCreator.State(
                decks,
                globalState.cardFilterForAutoplay
            )
            CardFilterForAutoplayDiScope.create(playerCreatorState)
        }
    }

    private fun navigateToDeckChooser() {
        navigator.navigateToDeckChooserFromNavHost {
            val screenState = DeckChooserScreenState(purpose = ToMergeInto)
            DeckChooserDiScope.create(screenState)
        }
    }

    private fun navigateToDeckEditor(
        deckId: Long,
        initialTab: DeckEditorScreenTab
    ) {
        screenState.deckSelection = null
        navigator.navigateToDeckEditorFromNavHost {
            val deck: Deck = globalState.decks.first { it.id == deckId }
            val tabs = DeckEditorTabs.All(initialTab)
            val screenState = DeckEditorScreenState(deck, tabs)
            val batchCardEditor = BatchCardEditor(globalState)
            DeckEditorDiScope.create(screenState, batchCardEditor)
        }
    }

    private fun navigateToCardsEditor(foundCard: FoundCard) {
        navigator.navigateToCardsEditorFromNavHost {
            val editableCard = EditableCard(foundCard.card, foundCard.deck)
            val editableCards: List<EditableCard> = listOf(editableCard)
            val cardsEditorState = State(editableCards)
            val cardsEditor = CardsEditorForEditingSpecificCards(
                cardsEditorState,
                globalState
            )
            CardsEditorDiScope.create(cardsEditor)
        }
    }

    private fun isDeckSelection(): Boolean = screenState.deckSelection != null
    private fun isCardSelection(): Boolean = batchCardEditor.state.selectedCards.isNotEmpty()

    private fun toggleDeckSelection(deckId: Long) {
        screenState.deckSelection?.let { deckSelection: DeckSelection ->
            val newSelectedDeckIds =
                if (deckId in deckSelection.selectedDeckIds)
                    deckSelection.selectedDeckIds - deckId else
                    deckSelection.selectedDeckIds + deckId
            if (newSelectedDeckIds.isEmpty()
                && deckSelection.purpose == DeckSelection.Purpose.General
            ) {
                screenState.deckSelection = null
            } else {
                screenState.deckSelection =
                    deckSelection.copy(selectedDeckIds = newSelectedDeckIds)
            }
        } ?: kotlin.run {
            screenState.deckSelection = DeckSelection(
                selectedDeckIds = listOf(deckId),
                purpose = DeckSelection.Purpose.General
            )
        }
    }

    private fun toggleCardSelection(foundCard: FoundCard) {
        val editableCard = EditableCard(foundCard.card, foundCard.deck)
        batchCardEditor.toggleSelected(editableCard)
    }

    private fun removeSelectedDecks() {
        val deckIdsToRemove: List<Long> =
            screenState.deckSelection?.selectedDeckIds ?: return
        val numberOfRemovedDecks = deckRemover.removeDecks(deckIdsToRemove)
        sendCommand(ShowDeckRemovingMessage(numberOfRemovedDecks))
        screenState.deckSelection = null
        notifyDeckListUpdated()
    }

    private fun removeSelectedCards() {
        val numberOfRemovedCards: Int = batchCardEditor.state.selectedCards.size
        batchCardEditor.remove()
        cardsSearcher.research()
        sendCommand(ShowCardsAreRemovedMessage(numberOfRemovedCards))
        needToResearchOnCancel = true
    }

    private fun determineGradeItems(): List<GradeItem> {
        var baseIntervalScheme: IntervalScheme? = null
        for (foundCard: FoundCard in cardsSearcher.state.searchResult) {
            val intervalScheme: IntervalScheme =
                foundCard.deck.exercisePreference.intervalScheme ?: continue
            when {
                baseIntervalScheme == null -> {
                    baseIntervalScheme = intervalScheme
                }
                baseIntervalScheme.id != intervalScheme.id -> {
                    baseIntervalScheme = null
                    break
                }
            }
        }
        return baseIntervalScheme?.intervals?.map { interval: Interval ->
            GradeItem(
                grade = interval.grade,
                waitingPeriod = interval.value
            )
        } ?: listOf(
            GradeItem(0, null),
            GradeItem(1, null),
            GradeItem(2, null),
            GradeItem(3, null),
            GradeItem(4, null),
            GradeItem(5, null),
            GradeItem(6, null)
        )
    }

    override fun saveState() {
        longTermStateSaver.saveStateByRegistry()
        screenStateProvider.save(screenState)
        batchCardEditorProvider.save(batchCardEditor)
    }
}