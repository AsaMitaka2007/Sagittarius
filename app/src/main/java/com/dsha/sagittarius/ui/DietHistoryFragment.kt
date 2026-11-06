package com.dsha.sagittarius.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dsha.sagittarius.SagittariusApp
import com.dsha.sagittarius.R
import com.dsha.sagittarius.data.DietRecordEntity
import com.dsha.sagittarius.data.DietRepository
import com.dsha.sagittarius.databinding.FragmentDietHistoryBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.LocalDate

class DietHistoryFragment : Fragment() {

    private var _binding: FragmentDietHistoryBinding? = null
    private val binding get() = _binding!!
    private lateinit var repository: DietRepository
    private lateinit var adapter: DietHistoryAdapter
    private var recordsJob: Job? = null
    private var startDate: LocalDate = LocalDate.now().withDayOfMonth(1)
    private var endDate: LocalDate = LocalDate.now()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDietHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = (requireActivity().application as SagittariusApp).dietRepository
        adapter = DietHistoryAdapter(
            onDelete = { record -> deleteWithUndo(record) },
            onRecordsChanged = { records ->
                viewLifecycleOwner.lifecycleScope.launch { repository.updateRecords(records) }
            }
        )
        binding.recyclerDiet.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerDiet.adapter = adapter
        attachDrag()
        DateConsoleBinding.observe(
            this,
            binding.dateConsole,
            DateConsolePage.HISTORY,
            R.string.tab_diet_history
        ) { state ->
            val range = DateConsoleManager.rangeOf(state)
            startDate = range.start
            endDate = range.end
            loadRange()
        }
        loadRange()
    }

    private fun loadRange() {
        recordsJob?.cancel()
        recordsJob = viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.observeRecordsBetween(startDate, endDate).collect { records ->
                    adapter.submit(records)
                    binding.tvDietEmpty.isVisible = records.isEmpty()
                }
            }
        }
    }

    private fun attachDrag() {
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            override fun isLongPressDragEnabled(): Boolean = true

            override fun getMovementFlags(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                val position = viewHolder.bindingAdapterPosition
                return if (adapter.recordAt(position) != null) {
                    makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)
                } else {
                    makeMovementFlags(0, 0)
                }
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = adapter.move(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                    Haptics.longPress(viewHolder.itemView)
                    adapter.setDragging(true)
                    viewHolder.itemView.animate().scaleX(1.035f).scaleY(1.035f).translationZ(8f * resources.displayMetrics.density).setDuration(120L).start()
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                viewHolder.itemView.animate().scaleX(1f).scaleY(1f).translationZ(0f).setDuration(140L).start()
                adapter.finishDrag()
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit
        }
        ItemTouchHelper(callback).attachToRecyclerView(binding.recyclerDiet)
    }

    private fun deleteWithUndo(record: DietRecordEntity) {
        Haptics.longPress(binding.root)
        viewLifecycleOwner.lifecycleScope.launch {
            repository.deleteRecord(record)
            Snackbar.make(binding.root, R.string.diet_deleted, Snackbar.LENGTH_LONG)
                .setAction(R.string.undo) {
                    Haptics.tap(binding.root)
                    viewLifecycleOwner.lifecycleScope.launch { repository.addRecords(listOf(record)) }
                }
                .show()
        }
    }

    override fun onDestroyView() {
        recordsJob?.cancel()
        _binding = null
        super.onDestroyView()
    }
}
