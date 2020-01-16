package io.github.gmathi.novellibrary.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import co.metalab.asyncawait.async
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import io.github.gmathi.novellibrary.R
import io.github.gmathi.novellibrary.adapter.GenericAdapter
import io.github.gmathi.novellibrary.extensions.isFragmentActive
import io.github.gmathi.novellibrary.extensions.startNovelDetailsActivity
import io.github.gmathi.novellibrary.model.Novel
import io.github.gmathi.novellibrary.network.NovelApi
import io.github.gmathi.novellibrary.network.searchUrl
import io.github.gmathi.novellibrary.util.Logs
import io.github.gmathi.novellibrary.util.Utils
import io.github.gmathi.novellibrary.util.getGlideUrl
import io.github.gmathi.novellibrary.util.setDefaults
import kotlinx.android.synthetic.main.content_recycler_view.*
import kotlinx.android.synthetic.main.listitem_novel.view.*

class SearchUrlFragment : BaseFragment(), GenericAdapter.Listener<Novel>, GenericAdapter.LoadMoreListener {

    companion object {
        fun newInstance(url: String): SearchUrlFragment {
            android.util.Log.i("MyState2", "newInstance")
            val bundle = Bundle()
            bundle.putString("url", url)
            val fragment = SearchUrlFragment()
            fragment.arguments = bundle
            return fragment
        }
    }

    override var currentPageNumber: Int = 1
    private lateinit var searchUrl: String
    private lateinit var adapter: GenericAdapter<Novel>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        android.util.Log.i("MyState2", "onCreate")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.content_recycler_view, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        //(activity as AppCompatActivity).setSupportActionBar(null)
        searchUrl = arguments?.getString("url")!!
        setRecyclerView()
        android.util.Log.i("MyState2", "onActivityCreated with ${if (savedInstanceState == null) "null" else "non null"} state")

        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey("url")) {
                searchUrl = savedInstanceState.getString("url", searchUrl)
            }
            if (savedInstanceState.containsKey("results") && savedInstanceState.containsKey("page")) {
                @Suppress("UNCHECKED_CAST")
                adapter.updateData(savedInstanceState.getSerializable("results") as java.util.ArrayList<Novel>)
                currentPageNumber = savedInstanceState.getInt("page")
                progressLayout.showContent()
                android.util.Log.i("MyState2", "restoring ${adapter.items.count()} items, currentPageNumber=$currentPageNumber, url=$searchUrl")
                return
            }
        }

        progressLayout.showLoading()
        searchNovels()
    }

    private fun setRecyclerView() {
        adapter = GenericAdapter(items = ArrayList(), layoutResId = R.layout.listitem_novel, listener = this, loadMoreListener = this)
        recyclerView.setDefaults(adapter)
        swipeRefreshLayout.setOnRefreshListener { searchNovels() }
    }

    private fun searchNovels() {
        android.util.Log.i("MyState2", "searchNovels")

        async search@{

            if (!Utils.isConnectedToNetwork(activity)) {
                progressLayout.showError(ContextCompat.getDrawable(context!!, R.drawable.ic_warning_white_vector), getString(R.string.no_internet), getString(R.string.try_again)) {
                    progressLayout.showLoading()
                    currentPageNumber = 1
                    searchNovels()
                }
                return@search
            }

            val results = await { NovelApi.searchUrl(searchUrl, pageNumber = currentPageNumber) }
            if (results != null) {
                android.util.Log.i("MyState2", "searchNovels! visible=$isVisible, detached=$isDetached, removing=$isRemoving")
                if (isVisible && (!isDetached || !isRemoving)) {
                    loadSearchResults(results)
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
        android.util.Log.i("MyState2", "loadSearchResults")

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

//region Adapter Listener Methods - onItemClick(), viewBinder()

    override fun onItemClick(item: Novel) {
        (activity as? AppCompatActivity)?.startNovelDetailsActivity(item, false)
        //addToDownloads(item)
    }

    override fun bind(item: Novel, itemView: View, position: Int) {
        itemView.novelImageView.setImageResource(android.R.color.transparent)
        if (!item.imageUrl.isNullOrBlank()) {
            Glide.with(this)
                    .load(item.imageUrl?.getGlideUrl())
                    .apply(RequestOptions.circleCropTransform())
                    .into(itemView.novelImageView)
        }

        //Other Data Fields
        itemView.novelTitleTextView.text = item.name
        if (item.rating != null) {
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

    override fun loadMore() {
        currentPageNumber++
        searchNovels()
    }

//endregion

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        android.util.Log.i("MyState2", "onSaveInstanceState with ${adapter.items.count()} items, currentPageNumber=$currentPageNumber, url=$searchUrl")
        if (adapter.items.isNotEmpty())
            outState.putSerializable("results", adapter.items)
        outState.putSerializable("page", currentPageNumber)
        outState.putString("url", searchUrl)
    }


}
