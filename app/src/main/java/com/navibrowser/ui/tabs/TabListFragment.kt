package com.navibrowser.ui.tabs

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.navibrowser.R
import com.navibrowser.ui.browser.BrowserActivity
import com.navibrowser.ui.browser.BrowserViewModel
import com.navibrowser.ui.browser.WebViewManager

class TabListFragment : BottomSheetDialogFragment() {

    private val viewModel: BrowserViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = inflater.inflate(R.layout.fragment_tab_list, container, false)
        val rv = root.findViewById<RecyclerView>(R.id.rvTabs)
        rv.layoutManager = LinearLayoutManager(requireContext())

        val activity = requireActivity() as BrowserActivity
        val wm = activity.getWebViewManager()

        fun refresh() {
            rv.adapter = TabListAdapter(
                tabs = wm.allTabs,
                currentTabId = wm.currentTab?.id,
                onTabClick = { tab ->
                    wm.switchTo(tab.id)
                    activity.loadUrl(tab.info.url.ifEmpty { "navi://home" })
                    dismiss()
                },
                onTabClose = { tab ->
                    wm.closeTab(tab.id)
                    if (wm.tabCount == 0) {
                        activity.openNewTab()
                        dismiss()
                    } else {
                        activity.loadUrl(wm.currentTab?.info?.url?.ifEmpty { "navi://home" } ?: "navi://home")
                        refresh()
                    }
                }
            )
        }
        refresh()

        root.findViewById<Button>(R.id.btnNewTab).setOnClickListener {
            activity.openNewTab(false)
            dismiss()
        }
        root.findViewById<Button>(R.id.btnNewIncognito).setOnClickListener {
            activity.openNewTab(true)
            dismiss()
        }

        return root
    }
}

class TabListAdapter(
    private val tabs: List<WebViewManager.Tab>,
    private val currentTabId: String?,
    private val onTabClick: (WebViewManager.Tab) -> Unit,
    private val onTabClose: (WebViewManager.Tab) -> Unit
) : RecyclerView.Adapter<TabListAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvTabTitle)
        val tvUrl: TextView = view.findViewById(R.id.tvTabUrl)
        val btnClose: ImageButton = view.findViewById(R.id.btnCloseTab)
        val ivIncognito: ImageView = view.findViewById(R.id.ivIncognito)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        LayoutInflater.from(parent.context).inflate(R.layout.item_tab, parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) {
        val tab = tabs[position]
        holder.tvTitle.text = tab.info.title.ifEmpty { "新标签页" }
        holder.tvUrl.text = tab.info.url.ifEmpty { "navi://home" }
        holder.ivIncognito.visibility = if (tab.isIncognito) View.VISIBLE else View.GONE
        holder.itemView.isSelected = tab.id == currentTabId
        holder.itemView.setOnClickListener { onTabClick(tab) }
        holder.btnClose.setOnClickListener { onTabClose(tab) }
    }

    override fun getItemCount() = tabs.size
}
