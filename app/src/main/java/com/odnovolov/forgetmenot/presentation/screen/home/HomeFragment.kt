package com.odnovolov.forgetmenot.presentation.screen.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.badoo.mvicore.android.AndroidTimeCapsule
import com.google.android.material.snackbar.Snackbar
import com.odnovolov.forgetmenot.R
import com.odnovolov.forgetmenot.presentation.common.BaseFragment
import com.odnovolov.forgetmenot.presentation.screen.home.HomeScreenFeature.*
import com.odnovolov.forgetmenot.presentation.screen.home.HomeScreenFeature.News.*
import com.odnovolov.forgetmenot.presentation.screen.home.HomeScreenFeature.UiEvent.*
import com.odnovolov.forgetmenot.presentation.screen.home.di.HomeScreenComponent
import kotlinx.android.synthetic.main.fragment_home.*
import leakcanary.LeakSentry
import javax.inject.Inject

class HomeFragment : BaseFragment<ViewState, UiEvent, News>() {

    @Inject lateinit var bindings: HomeFragmentBindings
    @Inject lateinit var recyclerAdapter: DecksPreviewAdapter
    private lateinit var timeCapsule: AndroidTimeCapsule
    private lateinit var addButtonClickListener: AddButtonClickListener

    override fun onCreate(savedInstanceState: Bundle?) {
        timeCapsule = AndroidTimeCapsule(savedInstanceState)
        HomeScreenComponent.createWith(timeCapsule).inject(this)
        super.onCreate(savedInstanceState)
        bindings.setup(this)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_home, container, false)
        val toolbar: Toolbar = rootView.findViewById(R.id.toolbar)
        toolbar.inflateMenu(R.menu.home_actions)
        return rootView
    }

    override fun onAttachFragment(childFragment: Fragment) {
        if (childFragment is AddButtonClickListener) {
            addButtonClickListener = childFragment
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupToolbar()
        initRecyclerAdapter()
        super.onViewCreated(view, savedInstanceState)
    }

    private fun setupToolbar() {
        toolbar.setOnMenuItemClickListener { item: MenuItem? ->
            when (item?.itemId) {
                R.id.action_add -> {
                    addButtonClickListener.onAddButtonClicked()
                    true
                }
                R.id.action_sort_by -> {
                    emitEvent(SortByMenuItemClicked)
                    true
                }
                else -> false
            }
        }
        configureSearchView()
    }

    private fun configureSearchView() {
        val searchItem = toolbar.menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String): Boolean {
                emitEvent(SearchTextChanged(newText))
                return true
            }
        })
    }

    private fun initRecyclerAdapter() {
        decksPreviewRecycler.adapter = recyclerAdapter
    }

    override fun accept(viewState: ViewState) {
        progressBar.visibility =
            if (viewState.isProcessing) {
                View.VISIBLE
            } else {
                View.GONE
            }
    }

    override fun acceptNews(news: News) {
        when (news) {
            ShowDeckIsDeletedSnackbar -> showDeckIsDeletedSnackbar()
            NavigateToExercise -> navigateToExercise()
            ShowDeckSortingBottomSheet -> showDeckSortingBottomSheet()
        }
    }

    private fun showDeckIsDeletedSnackbar() {
        Snackbar
            .make(
                homeFragmentRootView,
                getString(R.string.snackbar_message_deck_is_deleted),
                resources.getInteger(R.integer.duration_deck_is_deleted_snackbar)
            )
            .setAction(
                R.string.snackbar_action_cancel,
                { emitEvent(DeckIsDeletedSnackbarCancelActionClicked) }
            )
            .show()
    }

    private fun navigateToExercise() {
        findNavController().navigate(R.id.action_home_screen_to_exercise_screen)
    }

    private fun showDeckSortingBottomSheet() {
        DeckSortingBottomSheet().show(childFragmentManager, "DeckSortingBottomSheet Tag")
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        timeCapsule.saveState(outState)
    }

    override fun onDestroyView() {
        decksPreviewRecycler.adapter = null
        super.onDestroyView()
    }

    override fun onDestroy() {
        super.onDestroy()
        HomeScreenComponent.destroy()
        LeakSentry.refWatcher.watch(this)
    }

    interface AddButtonClickListener {
        fun onAddButtonClicked()
    }
}