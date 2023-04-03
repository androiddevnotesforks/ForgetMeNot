package com.odnovolov.forgetmenot.presentation.screen.cardsimport.cardsfile.cards

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.odnovolov.forgetmenot.R
import com.odnovolov.forgetmenot.domain.interactor.cardsimport.CardPrototype
import com.odnovolov.forgetmenot.presentation.common.base.BaseFragment
import com.odnovolov.forgetmenot.presentation.common.setTooltipTextFromContentDescription
import com.odnovolov.forgetmenot.presentation.screen.cardsimport.CardsImportDiScope
import com.odnovolov.forgetmenot.presentation.screen.cardsimport.cardsfile.ControllingTheScrollPosition
import com.odnovolov.forgetmenot.presentation.screen.cardsimport.cardsfile.cards.ImportedCardsEvent.*
import kotlinx.android.synthetic.main.fragment_imported_cards.*
import kotlinx.coroutines.launch

class ImportedCardsFragment : BaseFragment(), ControllingTheScrollPosition {
    companion object {
        private const val ARG_ID = "ARG_ID"

        fun create(id: Long) = ImportedCardsFragment().apply {
            arguments = Bundle(1).apply {
                putLong(ARG_ID, id)
            }
        }
    }

    private var controller: ImportedCardsController? = null
    private lateinit var viewModel: ImportedCardsViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_imported_cards, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupView()
        viewCoroutineScope!!.launch {
            val diScope = CardsImportDiScope.getAsync() ?: return@launch
            controller = diScope.importedCardsController
            val id = requireArguments().getLong(ARG_ID)
            viewModel = diScope.importedCardsViewModel(id)
            observeViewModel()
        }
    }

    private fun setupView() {
        val onCardClicked: (Long) -> Unit = { id: Long ->
            controller?.dispatch(CardClicked(id))
        }
        cardsRecycler.adapter = CardPrototypeAdapter(onCardClicked)
        selectAllButton.setOnClickListener {
            controller?.dispatch(SelectAllButtonClicked)
        }
        unselectAllButton.setOnClickListener {
            controller?.dispatch(UnselectAllButtonClicked)
        }
        selectOnlyNewButton.setOnClickListener {
            controller?.dispatch(SelectOnlyNewButtonClicked)
        }
        selectAllButton.setTooltipTextFromContentDescription()
        unselectAllButton.setTooltipTextFromContentDescription()
        selectOnlyNewButton.setTooltipTextFromContentDescription()
    }

    private fun observeViewModel() {
        with(viewModel) {
            cardPrototypes.observe { cardPrototypes: List<CardPrototype> ->
                (cardsRecycler.adapter as CardPrototypeAdapter).submitList(cardPrototypes)
            }
            hasCards.observe { hasCards: Boolean ->
                cannotExtractAnyCardsTextView.isVisible = !hasCards
            }
            numberOfSelectedCards.observe { numberOfSelectedCards: Int ->
                numberOfSelectedItemsTextView.text = resources.getQuantityString(
                    R.plurals.title_card_selection_toolbar,
                    numberOfSelectedCards,
                    numberOfSelectedCards
                )
            }
            isSelectOnlyNewButtonAvailable.observe { isButtonAvailable: Boolean ->
                selectOnlyNewButton.isVisible = isButtonAvailable
            }
        }
    }

    override fun getScrollPosition(): Float {
        val offset: Float = cardsRecycler.computeVerticalScrollOffset().toFloat()
        val extent: Int = cardsRecycler.computeVerticalScrollExtent()
        val range: Int = cardsRecycler.computeVerticalScrollRange()
        return offset / (range - extent)
    }

    override fun scrollTo(scrollPercentage: Float) {
        val extent: Int = cardsRecycler.computeVerticalScrollExtent()
        val range: Int = cardsRecycler.computeVerticalScrollRange()
        val offset: Int = ((range - extent) * scrollPercentage).toInt()
        (cardsRecycler.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(0, -offset)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cardsRecycler.adapter = null
    }
}