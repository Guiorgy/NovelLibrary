package io.github.gmathi.novellibrary.fragment

import android.animation.Animator
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import io.github.gmathi.novellibrary.R
import io.github.gmathi.novellibrary.adapter.GenericFragmentStatePagerAdapter
import io.github.gmathi.novellibrary.adapter.NavPageListener
import io.github.gmathi.novellibrary.extensions.hideSoftKeyboard
import io.github.gmathi.novellibrary.network.HostNames
import io.github.gmathi.novellibrary.util.SimpleAnimationListener
import io.github.gmathi.novellibrary.util.SuggestionsBuilder
import io.github.gmathi.novellibrary.util.addToSearchHistory
import kotlinx.android.synthetic.main.activity_nav_drawer.*
import kotlinx.android.synthetic.main.fragment_search.*
import org.cryse.widget.persistentsearch.PersistentSearchView


class SearchFragment : BaseFragment(){

    //private lateinit var adapter: GenericAdapter<Novel>
    private var searchMode: Boolean = false
    private var searchTerm: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        android.util.Log.i("MyState1", "onCreate")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
            inflater.inflate(R.layout.fragment_search, container, false)

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        android.util.Log.i("MyState1", "onActivityCreated with ${if (savedInstanceState == null) "null" else "non null"} state")

        setSearchView()

        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey("searchTerm"))
                searchTerm = savedInstanceState.getString("searchTerm")
            if (savedInstanceState.containsKey("searchMode"))
                searchMode = savedInstanceState.getBoolean("searchMode")
        }
        android.util.Log.i("MyState1", "onActivityCreated, searchTerm=$searchTerm, searchMode=$searchMode")

        if (searchMode && searchTerm != null)
            searchNovels(searchTerm!!)
        else
            setViewPager()
    }

    private fun setViewPager() {
        android.util.Log.i("MyState1", "setViewPager")
        while (childFragmentManager.backStackEntryCount > 0)
            childFragmentManager.popBackStack()
        searchTerm = null
        searchMode = false
        val titles = resources.getStringArray(R.array.search_tab_titles)
        val navPageAdapter = GenericFragmentStatePagerAdapter(childFragmentManager, titles, titles.size, NavPageListener())
        viewPager.offscreenPageLimit = 3
        viewPager.adapter = navPageAdapter
        tabStrip.setViewPager(viewPager)
    }

    private fun setSearchView() {
        //searchView.setHomeButtonVisibility(View.GONE)
        searchView.setHomeButtonListener {
            hideSoftKeyboard()
            activity?.drawerLayout?.openDrawer(GravityCompat.START)
        }
        searchView.setSuggestionBuilder(SuggestionsBuilder())
        val listener = object : PersistentSearchView.SearchListener {

            override fun onSearch(searchTerm: String?) {
                searchTerm?.addToSearchHistory()
                if (searchTerm != null) {
                    searchNovels(searchTerm)
                } else {
                    // Throw a empty search
                }
            }

            override fun onSearchEditOpened() {
                searchViewBgTint.visibility = View.VISIBLE
                searchViewBgTint
                        .animate()
                        .alpha(1.0f)
                        .setDuration(300)
                        .setListener(SimpleAnimationListener())
                        .start()
            }

            override fun onSearchEditClosed() {
                searchViewBgTint
                        .animate()
                        .alpha(0.0f)
                        .setDuration(300)
                        .setListener(object : SimpleAnimationListener() {
                            override fun onAnimationEnd(animation: Animator) {
                                super.onAnimationEnd(animation)
                                searchViewBgTint.visibility = View.GONE
                            }
                        })
                        .start()
            }

            override fun onSearchExit() {
                if (searchMode)
                    setViewPager()

            }

            override fun onSearchCleared() {
                //Toast.makeText(context, "onSearchCleared", Toast.LENGTH_SHORT).show()
            }

            override fun onSearchTermChanged(searchTerm: String?) {
                //Toast.makeText(context, "Search Exited", Toast.LENGTH_SHORT).show()
            }

            override fun onSearchEditBackPressed(): Boolean {
                //Toast.makeText(context, "onSearchEditBackPressed", Toast.LENGTH_SHORT).show()
                if (searchView.searchOpen) {
                    searchView.closeSearch()
                    return true
                }
                return false
            }
        }

        searchView.viewTreeObserver.addOnGlobalFocusChangeListener(
                object : ViewTreeObserver.OnGlobalFocusChangeListener {
                    override fun onGlobalFocusChanged(oldFocus: View?, newFocus: View?) {
                        searchView.viewTreeObserver.removeOnGlobalFocusChangeListener(this)
                        android.util.Log.i("MyState1", "searchView Ready!")
                        searchView.setSearchListener(listener)
                    }
                })
    }


    private fun searchNovels(searchTerm: String) {
        while (childFragmentManager.backStackEntryCount > 0)
            childFragmentManager.popBackStack()
        searchMode = true
        this.searchTerm = searchTerm

        val titles = ArrayList<String>()
        titles.add("Novel-Updates")
        if (!dataCenter.lockRoyalRoad)
            titles.add("RoyalRoad")
        if (!dataCenter.lockNovelFull)
            titles.add("NovelFull")
        if (!dataCenter.lockScribble)
            titles.add("ScribbleHub")
        titles.add("WLN-Updates")

        val searchPageAdapter: GenericFragmentStatePagerAdapter
        searchPageAdapter = GenericFragmentStatePagerAdapter(childFragmentManager, titles.toTypedArray(), titles.size, SearchResultsListener(searchTerm, titles))

        viewPager.offscreenPageLimit = 2
        viewPager.adapter = searchPageAdapter
        tabStrip.setViewPager(viewPager)

        android.util.Log.i("MyState1", "searchNovels, searchTerm=$searchTerm, visibility=${if (viewPager.visibility == View.VISIBLE) "visible" else "invisible"}, currentItem=${viewPager.currentItem}")
    }

    fun closeSearch() {
        android.util.Log.i("MyState1", "close")
        searchView.closeSearch()
        setViewPager()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("searchMode", searchMode)
        if (searchTerm != null) outState.putString("searchTerm", searchTerm)
        android.util.Log.i("MyState1", "onSaveInstanceState, searchTerm=$searchTerm, searchMode=$searchMode")
    }

}
