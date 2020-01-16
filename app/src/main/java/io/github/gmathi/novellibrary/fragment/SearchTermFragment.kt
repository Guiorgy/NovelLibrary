package io.github.gmathi.novellibrary.fragment

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import co.metalab.asyncawait.async
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import io.github.gmathi.novellibrary.R
import io.github.gmathi.novellibrary.adapter.GenericAdapter
import io.github.gmathi.novellibrary.extensions.isFragmentActive
import io.github.gmathi.novellibrary.extensions.startNovelDetailsActivity
import io.github.gmathi.novellibrary.model.Novel
import io.github.gmathi.novellibrary.network.*
import io.github.gmathi.novellibrary.util.Logs
import io.github.gmathi.novellibrary.util.Utils
import io.github.gmathi.novellibrary.util.getGlideUrl
import io.github.gmathi.novellibrary.util.setDefaults
import kotlinx.android.synthetic.main.content_recycler_view.*
import kotlinx.android.synthetic.main.listitem_novel.view.*
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicBoolean


class SearchTermFragment : BaseFragment(), GenericAdapter.Listener<Novel>, GenericAdapter.LoadMoreListener {

    override val preloadCount:Int = 50
    override val isPageLoading: AtomicBoolean = AtomicBoolean(false)

    companion object {
        fun newInstance(searchTerms: String, resultType: String): SearchTermFragment {
            android.util.Log.i("MyState3", "newInstance")
            val bundle = Bundle()
            bundle.putString("searchTerm", searchTerms)
            bundle.putString("resultType", resultType)
            val fragment = SearchTermFragment()
            fragment.arguments = bundle
            return fragment
        }
    }

    override var currentPageNumber: Int = 1
    private lateinit var searchTerm: String
    private lateinit var resultType: String
    private lateinit var adapter: GenericAdapter<Novel>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        android.util.Log.i("MyState3", "onCreate, visible=$isVisible")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.content_recycler_view, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        //(activity as AppCompatActivity).setSupportActionBar(null)

        searchTerm = arguments?.getString("searchTerm")!!
        if (savedInstanceState != null && savedInstanceState.containsKey("searchTerm"))
            searchTerm = savedInstanceState.getString("searchTerm", searchTerm)

        resultType = arguments?.getString("resultType")!!
        if (savedInstanceState != null && savedInstanceState.containsKey("resultType"))
            resultType = savedInstanceState.getString("resultType", resultType)

        setRecyclerView()
        android.util.Log.i("MyState3", "onActivityCreated with ${if (savedInstanceState == null) "null" else "non null"} state, searchTerm=$searchTerm, resultType=$resultType, visible=$isVisible")

        if (savedInstanceState != null && savedInstanceState.containsKey("results") && savedInstanceState.containsKey("page")) {
            @Suppress("UNCHECKED_CAST")
            adapter.updateData(savedInstanceState.getSerializable("results") as java.util.ArrayList<Novel>)
            currentPageNumber = savedInstanceState.getInt("page")
            android.util.Log.i("MyState3", "restoring ${adapter.items.count()}/${recyclerView.adapter?.itemCount} items, currentPageNumber=$currentPageNumber, visible=$isVisible")
            return
        }

        progressLayout.showLoading()
        searchNovels()
    }

    private fun setRecyclerView() {
        adapter = GenericAdapter(items = ArrayList(), layoutResId = R.layout.listitem_novel, listener = this, loadMoreListener = if (resultType != HostNames.WLN_UPDATES) this else null)
        recyclerView.setDefaults(adapter)
        recyclerView.layoutManager = LinearLayoutManager(context ,LinearLayoutManager.VERTICAL, false)
        swipeRefreshLayout.setOnRefreshListener { searchNovels() }
        recyclerView.setBackgroundColor(Color.RED)
    }

    private fun searchNovels() {
        android.util.Log.i("MyState3", "searchNovels, visible=$isVisible")

        async search@{

            if (!Utils.isConnectedToNetwork(activity)) {
                progressLayout.showError(ContextCompat.getDrawable(context!!, R.drawable.ic_warning_white_vector), getString(R.string.no_internet), getString(R.string.try_again)) {
                    progressLayout.showLoading()
                    currentPageNumber = 1
                    searchNovels()
                }
                return@search
            }

            val searchTerms = URLEncoder.encode(searchTerm, "UTF-8")
            var results: ArrayList<Novel>? = null

            when (resultType) {
                HostNames.NOVEL_UPDATES -> results = await { NovelApi.searchNovelUpdates_New(searchTerms, currentPageNumber) }
                HostNames.ROYAL_ROAD -> results = await { NovelApi.searchRoyalRoad(searchTerms, currentPageNumber) }
                HostNames.NOVEL_FULL -> results = await { NovelApi.searchNovelFull(searchTerms, currentPageNumber) }
                HostNames.WLN_UPDATES -> results = await { NovelApi.searchWlnUpdates(searchTerms) }
                HostNames.SCRIBBLE_HUB -> results = await { NovelApi.searchScribbleHub(searchTerms, currentPageNumber) }
            }

            if (results != null) {
                android.util.Log.i("MyState3", "searchNovels! visible=$isVisible, detached=$isDetached, removing=$isRemoving")
                if (isVisible && (!isDetached || !isRemoving)) {
                    loadSearchResults(results)
                    isPageLoading.lazySet(false)
                    swipeRefreshLayout.isRefreshing = false
                }
            } else {
                if (isFragmentActive() && progressLayout != null)
                    progressLayout.showError(ContextCompat.getDrawable(context!!, R.drawable.ic_warning_white_vector), getString(R.string.connection_error), getString(R.string.try_again)) {
                        progressLayout.showLoading()
                        currentPageNumber = 1
                        searchNovels()
                    }
            }
        }
    }

    private fun loadSearchResults(results: ArrayList<Novel>) {
        android.util.Log.i("MyState3", "loadSearchResults, visible=$isVisible")
        if (results.isNotEmpty() && !adapter.items.containsAll(results)) {
            if (currentPageNumber == 1) {
                adapter.updateData(results)
            } else {
                adapter.addItems(results)
            }
        } else {
            adapter.loadMoreListener = null
            adapter.notifyDataSetChanged()
        }

        if (adapter.items.isEmpty()) {
            if (isFragmentActive() && progressLayout != null)
                progressLayout.showError(ContextCompat.getDrawable(context!!, R.drawable.ic_youtube_searched_for_white_vector), "No Novels Found!", "Try Again") {
                    progressLayout.showLoading()
                    currentPageNumber = 1
                    searchNovels()
                }
        } else {
            if (isFragmentActive() && progressLayout != null)
                progressLayout.showContent()
        }
    }

    override fun loadMore() {
        if (isPageLoading.compareAndSet(false, true)) {
            currentPageNumber++
            searchNovels()
        }
    }

    //region Adapter Listener Methods - onItemClick(), viewBinder()

    override fun onItemClick(item: Novel) {
        (activity as? AppCompatActivity)?.startNovelDetailsActivity(item, false)
        //addToDownloads(item)
    }

    override fun bind(item: Novel, itemView: View, position: Int) {
        android.util.Log.i("MyState3", "bind $position")
        itemView.novelImageView.setImageResource(android.R.color.transparent)

        if (!item.imageUrl.isNullOrBlank()) {
            Glide.with(this)
                    .load(item.imageUrl?.getGlideUrl())
                    .apply(RequestOptions.circleCropTransform())
                    .into(itemView.novelImageView)
        }

        //Other Data Fields
        itemView.novelTitleTextView.text = item.name
        if (item.rating != null && item.rating != "N/A") {
            var ratingText = "(N/A)"
            try {
                val rating = item.rating!!.toFloat()
                itemView.novelRatingBar.rating = rating
                ratingText = "(" + String.format("%.1f", rating) + ")"
            } catch (e: Exception) {
                Logs.warning("Library Activity", "Rating: " + item.rating, e)
            }
            itemView.novelRatingText.text = ratingText
        }
    }

//endregion

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        android.util.Log.i("MyState3", "onSaveInstanceState with ${adapter.items.count()} items, currentPageNumber=$currentPageNumber, searchTerm=$searchTerm, resultType=$resultType, visible=$isVisible")
        if (adapter.items.isNotEmpty())
            outState.putSerializable("results", adapter.items)
        outState.putInt("page", currentPageNumber)
        outState.putString("searchTerm", searchTerm)
        outState.putString("resultType", resultType)
    }

}
