package com.dsha.sagittarius.agent.ui

import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.dsha.sagittarius.R
import com.dsha.sagittarius.agent.debug.AgentRuntimeLog
import com.dsha.sagittarius.agent.tools.AgentTool
import com.dsha.sagittarius.agent.tools.ToolCatalog
import com.dsha.sagittarius.databinding.FragmentAgentTerminalBinding
import com.dsha.sagittarius.ui.ThemeTokens
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 终端页：运行日志与工具目录，均可展开查看并导出。 */
class AgentTerminalFragment : Fragment() {

    private var _binding: FragmentAgentTerminalBinding? = null
    private val binding get() = _binding!!
    private val viewModel get() = (requireActivity() as SagittariusActivity).viewModel
    private var pendingLogText = ""
    private var pendingToolCsv = ""

    private val exportLogs = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri -> uri?.let { writeText(it, pendingLogText) } }

    private val exportTools = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> uri?.let { writeText(it, pendingToolCsv) } }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAgentTerminalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.cardLogs.setOnClickListener { showLogsDialog() }
        binding.cardTools.setOnClickListener { showToolsDialog() }
        binding.btnExportLogs.setOnClickListener { requestLogExport() }
        binding.btnExportTools.setOnClickListener { requestToolExport() }
        observe()
    }

    private fun observe() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    AgentRuntimeLog.entries.collect { entries ->
                        binding.tvLogSummary.text = if (entries.isEmpty()) {
                            getString(R.string.agent_terminal_logs_empty)
                        } else {
                            val last = entries.last()
                            "${entries.size} 条 · ${last.level.name} ${last.message.take(40)}"
                        }
                    }
                }
                launch {
                    viewModel.tools.collect { tools ->
                        val categories = tools.map { ToolCatalog.metaFor(it).category }.distinct()
                        binding.tvToolSummary.text =
                            "${tools.size} ${getString(R.string.agent_terminal_tools_unit)} · " +
                                categories.joinToString(" / ")
                    }
                }
            }
        }
    }

    private fun showLogsDialog() {
        val tokens = ThemeTokens.of(requireContext())
        val text = TextView(requireContext()).apply {
            typeface = Typeface.MONOSPACE
            textSize = 10f
            setTextIsSelectable(true)
            setPadding(24, 16, 24, 16)
            setTextColor(tokens.textSecondary)
            text = AgentRuntimeLog.format(AgentRuntimeLog.snapshot())
                .ifBlank { getString(R.string.agent_terminal_logs_empty) }
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.agent_terminal_logs_title)
            .setView(ScrollView(requireContext()).apply { addView(text) })
            .setPositiveButton(R.string.agent_terminal_export) { _, _ -> requestLogExport() }
            .setNeutralButton(R.string.agent_terminal_clear) { _, _ -> AgentRuntimeLog.clear() }
            .setNegativeButton(R.string.agent_terminal_close, null)
            .show()
    }

    private fun showToolsDialog() {
        val tokens = ThemeTokens.of(requireContext())
        val text = TextView(requireContext()).apply {
            typeface = Typeface.MONOSPACE
            textSize = 10f
            setTextIsSelectable(true)
            setPadding(24, 16, 24, 16)
            setTextColor(tokens.textSecondary)
            text = buildToolsText(viewModel.tools.value)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.agent_terminal_tools_title)
            .setView(ScrollView(requireContext()).apply { addView(text) })
            .setPositiveButton(R.string.agent_terminal_export) { _, _ -> requestToolExport() }
            .setNegativeButton(R.string.agent_terminal_close, null)
            .show()
    }

    private fun requestLogExport() {
        pendingLogText = AgentRuntimeLog.format(AgentRuntimeLog.snapshot())
            .ifBlank { getString(R.string.agent_terminal_logs_empty) }
        exportLogs.launch(fileName("logs", "txt"))
    }

    private fun requestToolExport() {
        pendingToolCsv = buildToolCsv(viewModel.tools.value)
        exportTools.launch(fileName("tools", "csv"))
    }

    private fun buildToolsText(tools: List<AgentTool>): String {
        if (tools.isEmpty()) return "暂无工具"
        return tools.joinToString("\n\n") { tool ->
            val meta = ToolCatalog.metaFor(tool)
            "${tool.name}  [${meta.category}]${if (meta.readOnly) " 只读" else ""}\n" +
                "  ${meta.displayName}\n  ${tool.description}"
        }
    }

    private fun buildToolCsv(tools: List<AgentTool>): String {
        val header = "tool,display_name,category,read_only,description"
        return (listOf(header) + tools.map { tool ->
            val meta = ToolCatalog.metaFor(tool)
            listOf(
                tool.name,
                meta.displayName,
                meta.category,
                if (meta.readOnly) "true" else "false",
                tool.description
            ).joinToString(",") { csv(it) }
        }).joinToString("\n")
    }

    private fun csv(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""

    private fun fileName(prefix: String, ext: String): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.CHINA).format(Date())
        return "sagittarius-$prefix-$stamp.$ext"
    }

    private fun writeText(uri: Uri, content: String) {
        runCatching {
            requireContext().contentResolver.openOutputStream(uri, "wt")?.use { out ->
                out.write(content.toByteArray(Charsets.UTF_8))
            } ?: error("no stream")
        }.onSuccess {
            Toast.makeText(
                requireContext(), R.string.agent_terminal_export_done, Toast.LENGTH_SHORT
            ).show()
        }.onFailure {
            Toast.makeText(
                requireContext(), R.string.agent_terminal_export_failed, Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
