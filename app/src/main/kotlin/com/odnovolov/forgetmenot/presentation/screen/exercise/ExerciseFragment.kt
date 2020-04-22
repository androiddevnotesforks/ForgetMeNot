package com.odnovolov.forgetmenot.presentation.screen.exercise

import android.annotation.SuppressLint
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.*
import android.view.View.MeasureSpec
import android.widget.PopupWindow
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.TooltipCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import androidx.viewpager2.widget.findViewHolderForAdapterPosition
import com.odnovolov.forgetmenot.R
import com.odnovolov.forgetmenot.presentation.common.*
import com.odnovolov.forgetmenot.presentation.common.base.BaseFragment
import com.odnovolov.forgetmenot.presentation.screen.exercise.ExerciseController.Command.*
import com.odnovolov.forgetmenot.presentation.screen.exercise.ExerciseEvent.*
import com.odnovolov.forgetmenot.presentation.screen.exercise.KeyGestureDetector.Gesture
import com.odnovolov.forgetmenot.presentation.screen.exercise.KeyGestureDetector.Gesture.*
import com.odnovolov.forgetmenot.presentation.screen.exercise.exercisecard.entry.EntryTestExerciseCardViewHolder
import com.odnovolov.forgetmenot.presentation.screen.walkingmodesettings.KeyGesture
import com.odnovolov.forgetmenot.presentation.screen.walkingmodesettings.KeyGesture.*
import kotlinx.android.synthetic.main.fragment_exercise.*
import kotlinx.android.synthetic.main.popup_choose_hint.view.*
import kotlinx.coroutines.launch

class ExerciseFragment : BaseFragment() {
    init {
        ExerciseDiScope.reopenIfClosed()
    }

    private lateinit var viewModel: ExerciseViewModel
    private var controller: ExerciseController? = null
    private val chooseHintPopup: PopupWindow by lazy { createChooseHintPopup() }
    private val levelOfKnowledgePopup: PopupWindow by lazy { createLevelOfKnowledgePopup() }
    private val intervalsAdapter: IntervalsAdapter by lazy { createIntervalsAdapter() }

    @SuppressLint("RestrictedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (activity as AppCompatActivity).supportActionBar?.run {
            setShowHideAnimationEnabled(false)
            hide()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_exercise, container, false)
    }

    private fun createIntervalsAdapter(): IntervalsAdapter {
        val onItemClick: (Int) -> Unit = { levelOfKnowledge: Int ->
            controller?.dispatch(LevelOfKnowledgeSelected(levelOfKnowledge))
            levelOfKnowledgePopup.dismiss()
        }
        return IntervalsAdapter(onItemClick)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupView()
        viewCoroutineScope!!.launch {
            val diScope = ExerciseDiScope.get()
            controller = diScope.controller
            exerciseViewPager.adapter = diScope.getExerciseCardAdapter(viewCoroutineScope!!)
            viewModel = diScope.viewModel
            observeViewModel()
            setupWalkingModeIfEnabled()
            controller!!.commands.observe(::executeCommand)
        }
    }

    private fun setupView() {
        setupViewPagerAdapter()
        setupControlPanel()
    }

    private fun setupViewPagerAdapter() {
        exerciseViewPager.registerOnPageChangeCallback(onPageChangeCallback)
    }

    private fun setupControlPanel() {
        editCardButton.run {
            setOnClickListener { controller?.dispatch(EditCardButtonClicked) }
            TooltipCompat.setTooltipText(this, contentDescription)
        }
        hintButton.run {
            setOnClickListener { controller?.dispatch(HintButtonClicked) }
            TooltipCompat.setTooltipText(this, contentDescription)
        }
        levelOfKnowledgeButton.run {
            setOnClickListener { controller?.dispatch(LevelOfKnowledgeButtonClicked) }
            TooltipCompat.setTooltipText(this, contentDescription)
        }
    }

    private fun setupWalkingModeIfEnabled() {
        with(viewModel) {
            if (isWalkingMode) {
                val volumeUpGestureDetector: KeyGestureDetector? =
                    if (!needToDetectVolumeUpSinglePress
                        && !needToDetectVolumeUpDoublePress
                        && !needToDetectVolumeUpLongPress
                    ) null
                    else KeyGestureDetector(
                        detectSinglePress = needToDetectVolumeUpSinglePress,
                        detectDoublePress = needToDetectVolumeUpDoublePress,
                        detectLongPress = needToDetectVolumeUpLongPress,
                        coroutineScope = viewCoroutineScope!!,
                        onGestureDetect = { gesture: Gesture ->
                            val keyGesture: KeyGesture = when (gesture) {
                                SINGLE_PRESS -> VOLUME_UP_SINGLE_PRESS
                                DOUBLE_PRESS -> VOLUME_UP_DOUBLE_PRESS
                                LONG_PRESS -> VOLUME_UP_LONG_PRESS
                            }
                            controller?.dispatch(KeyGestureDetected(keyGesture))
                        })
                val volumeDownGestureDetector: KeyGestureDetector? =
                    if (!needToDetectVolumeDownSinglePress
                        && !needToDetectVolumeDownDoublePress
                        && !needToDetectVolumeDownLongPress
                    ) null
                    else KeyGestureDetector(
                        detectSinglePress = needToDetectVolumeDownSinglePress,
                        detectDoublePress = needToDetectVolumeDownDoublePress,
                        detectLongPress = needToDetectVolumeDownLongPress,
                        coroutineScope = viewCoroutineScope!!,
                        onGestureDetect = { gesture: Gesture ->
                            val keyGesture: KeyGesture = when (gesture) {
                                SINGLE_PRESS -> VOLUME_DOWN_SINGLE_PRESS
                                DOUBLE_PRESS -> VOLUME_DOWN_DOUBLE_PRESS
                                LONG_PRESS -> VOLUME_DOWN_LONG_PRESS
                            }
                            controller?.dispatch(KeyGestureDetected(keyGesture))
                        })
                val keyEventInterceptor: (KeyEvent) -> Boolean = { event: KeyEvent ->
                    when (event.keyCode) {
                        KeyEvent.KEYCODE_VOLUME_UP -> {
                            if (volumeUpGestureDetector == null) {
                                false
                            } else {
                                val isPressed = event.action == KeyEvent.ACTION_DOWN
                                volumeUpGestureDetector.dispatchKeyEvent(isPressed)
                                true
                            }
                        }
                        KeyEvent.KEYCODE_VOLUME_DOWN -> {
                            if (volumeDownGestureDetector == null) {
                                false
                            } else {
                                val isPressed = event.action == KeyEvent.ACTION_DOWN
                                volumeDownGestureDetector.dispatchKeyEvent(isPressed)
                                true
                            }
                        }
                        else -> false
                    }
                }
                (activity as MainActivity).keyEventInterceptor = keyEventInterceptor
            }
        }
    }

    private fun observeViewModel() {
        with(viewModel) {
            val exerciseCardAdapter = exerciseViewPager.adapter as ExerciseCardAdapter
            exerciseCards.observe(exerciseCardAdapter::submitList)
            if (exerciseViewPager.currentItem != currentPosition) {
                exerciseViewPager.setCurrentItem(currentPosition, false)
            }
            isCurrentExerciseCardLearned.observe { isLearned: Boolean ->
                with(notAskButton) {
                    setImageResource(
                        if (isLearned)
                            R.drawable.ic_undo_white_24dp else
                            R.drawable.ic_block_white_24dp
                    )
                    setOnClickListener {
                        controller?.dispatch(
                            if (isLearned)
                                AskAgainButtonClicked else
                                NotAskButtonClicked
                        )
                    }
                    contentDescription = getString(
                        if (isLearned)
                            R.string.description_ask_again_button else
                            R.string.description_not_ask_button
                    )
                    TooltipCompat.setTooltipText(this, contentDescription)
                }
            }
            isSpeaking.observe { isSpeaking: Boolean ->
                with(speakButton) {
                    setImageResource(
                        if (isSpeaking)
                            R.drawable.ic_volume_off_white_24dp else
                            R.drawable.ic_volume_up_white_24dp
                    )
                    setOnClickListener {
                        controller?.dispatch(
                            if (isSpeaking)
                                StopSpeakButtonClicked else
                                SpeakButtonClicked
                        )
                    }
                    contentDescription = getString(
                        if (isSpeaking)
                            R.string.description_stop_speak_button else
                            R.string.description_speak_button
                    )
                    TooltipCompat.setTooltipText(this, contentDescription)
                }
            }
            isHintAccessible.observe(hintButton::setEnabled)
            levelOfKnowledgeForCurrentCard.observe { levelOfKnowledge: Int ->
                val backgroundRes = getBackgroundResForLevelOfKnowledge(levelOfKnowledge)
                levelOfKnowledgeTextView.setBackgroundResource(backgroundRes)
                levelOfKnowledgeTextView.text = levelOfKnowledge.toString()
            }
        }
    }

    private fun executeCommand(command: ExerciseController.Command) {
        when (command) {
            MoveToNextPosition -> {
                val nextPosition = exerciseViewPager.currentItem + 1
                exerciseViewPager.setCurrentItem(nextPosition, true)
            }
            MoveToPreviousPosition -> {
                val previousPosition = exerciseViewPager.currentItem - 1
                exerciseViewPager.setCurrentItem(previousPosition, true)
            }
            ShowChooseHintPopup -> {
                showChooseHintPopup()
            }
            is ShowLevelOfKnowledgePopup -> {
                showLevelOfKnowledgePopup(command.intervalItems)
            }
            ShowIntervalsAreOffMessage -> {
                showToast(R.string.toast_text_intervals_are_off)
            }
        }
    }

    private fun createChooseHintPopup(): PopupWindow {
        val content = View.inflate(requireContext(), R.layout.popup_choose_hint, null).apply {
            hintAsQuizButton.setOnClickListener {
                controller?.dispatch(HintAsQuizButtonClicked)
                chooseHintPopup.dismiss()
            }
            maskLettersButton.setOnClickListener {
                controller?.dispatch(MaskLettersButtonClicked)
                chooseHintPopup.dismiss()
            }
        }
        content.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
        return PopupWindow(context).apply {
            width = content.measuredWidth
            height = content.measuredHeight
            contentView = content
            setBackgroundDrawable(
                ColorDrawable(
                    ContextCompat.getColor(
                        requireContext(),
                        R.color.exercise_control_panel_popup_background
                    )
                )
            )
            elevation = 20f
            isOutsideTouchable = true
            isFocusable = true
        }
    }

    private fun showChooseHintPopup() {
        val hintButtonLocation = IntArray(2).also { hintButton.getLocationOnScreen(it) }
        val x = hintButtonLocation[0] + (hintButton.width / 2) - (chooseHintPopup.width / 2)
        val y = hintButtonLocation[1] + hintButton.height - 8.dp - chooseHintPopup.height
        chooseHintPopup.showAtLocation(
            hintButton.rootView,
            Gravity.NO_GRAVITY,
            x,
            y
        )
    }

    private fun createLevelOfKnowledgePopup(): PopupWindow {
        val recycler: RecyclerView =
            View.inflate(context, R.layout.popup_set_level_of_knowledge, null) as RecyclerView
        recycler.adapter = intervalsAdapter
        return PopupWindow(context).apply {
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            contentView = recycler
            setBackgroundDrawable(
                ColorDrawable(
                    ContextCompat.getColor(
                        requireContext(),
                        R.color.exercise_control_panel_popup_background
                    )
                )
            )
            elevation = 20f
            isOutsideTouchable = true
            isFocusable = true
        }
    }

    private fun showLevelOfKnowledgePopup(intervalItems: List<IntervalItem>) {
        intervalsAdapter.intervalItems = intervalItems
        val content = levelOfKnowledgePopup.contentView
        content.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
        val location = IntArray(2)
        levelOfKnowledgeButton.getLocationOnScreen(location)
        val x = location[0] + levelOfKnowledgeButton.width - 8.dp - content.measuredWidth
        val y = location[1] + levelOfKnowledgeButton.height - 8.dp - content.measuredHeight
        levelOfKnowledgePopup.showAtLocation(
            levelOfKnowledgeButton.rootView,
            Gravity.NO_GRAVITY,
            x,
            y
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        exerciseViewPager.adapter = null
        exerciseViewPager.unregisterOnPageChangeCallback(onPageChangeCallback)
        if (viewModel.isWalkingMode) {
            (activity as MainActivity).keyEventInterceptor = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        (activity as AppCompatActivity).supportActionBar?.show()
        if (needToCloseDiScope()) {
            ExerciseDiScope.close()
        }
    }

    private val onPageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            controller?.dispatch(PageSelected(position))
            val currentViewHolder = exerciseViewPager.findViewHolderForAdapterPosition(position)
            if (currentViewHolder is EntryTestExerciseCardViewHolder) {
                currentViewHolder.onPageSelected()
            }
        }
    }
}