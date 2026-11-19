package com.dsha.sagittarius.agent.ui

import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 终端页：日志窗口与工具目录窗口内嵌显示，两个窗口各自可导出。 */
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
        binding.btnExportLogs.setOnClickListener { requestLogExport() }
        binding.btnExportTools.setOnClickListener { requestToolExport() }
        AgentTypography.apply(binding.root)
        observe()
    }

    private fun observe() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    AgentRuntimeLog.entries.collect { entries ->
                        binding.tvLogSummary.text = entries.size.toString()
                        binding.tvLogWindow.text = if (entries.isEmpty()) {
                            getString(R.string.agent_terminal_logs_empty)
                        } else {
                            AgentRuntimeLog.format(entries)
                        }
                        binding.logScroll.post {
                            binding.logScroll.fullScroll(View.FOCUS_DOWN)
                        }
                    }
                }
                launch {
                    viewModel.tools.collect { tools ->
                        binding.tvToolSummary.text = tools.size.toString()
                        renderTools(tools)
                    }
                }
            }
        }
    }

    private fun renderTools(tools: List<AgentTool>) {
        val container = binding.toolContainer
        container.removeAllViews()
        if (tools.isEmpty()) {
            container.addView(TextView(requireContext()).apply {
                text = "暂无工具"
                setTextColor(ThemeTokens.of(requireContext()).textTertiary)
                textSize = 11f
                setPadding(0, 8, 0, 8)
            })
            return
        }
        val tokens = ThemeTokens.of(requireContext())
        tools.forEach { tool ->
            val meta = ToolCatalog.metaFor(tool)
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 7, 0, 7)
            }
            row.addView(TextView(requireContext()).apply {
                text = meta.displayName + "  [" + meta.category + "]" +
                    if (meta.readOnly) " 只读" else ""
                setTextColor(tokens.textPrimary)
                textSize = 12f
                setTypeface(typeface, Typeface.BOLD)
            })
            row.addView(TextView(requireContext()).apply {
                text = tool.name + " · " + tool.description
                setTextColor(tokens.textTertiary)
                textSize = 10f
                setPadding(0, 2, 0, 0)
            })
            container.addView(row)
        }
        AgentTypography.apply(container)
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
