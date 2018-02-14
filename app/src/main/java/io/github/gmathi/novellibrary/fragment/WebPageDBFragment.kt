package io.github.gmathi.novellibrary.fragment

import android.annotation.SuppressLint
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import co.metalab.asyncawait.async
import com.afollestad.materialdialogs.MaterialDialog
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.github.gmathi.novellibrary.R
import io.github.gmathi.novellibrary.activity.ReaderDBPagerActivity
import io.github.gmathi.novellibrary.activity.startInitialWebViewActivity
import io.github.gmathi.novellibrary.cleaner.HtmlHelper
import io.github.gmathi.novellibrary.dataCenter
import io.github.gmathi.novellibrary.database.getNovel
import io.github.gmathi.novellibrary.database.getWebPage
import io.github.gmathi.novellibrary.database.updateWebPage
import io.github.gmathi.novellibrary.dbHelper
import io.github.gmathi.novellibrary.model.ReaderSettingsEvent
import io.github.gmathi.novellibrary.model.WebPage
import io.github.gmathi.novellibrary.network.CloudFlare
import io.github.gmathi.novellibrary.network.HostNames
import io.github.gmathi.novellibrary.network.NovelApi
import io.github.gmathi.novellibrary.util.Constants
import io.github.gmathi.novellibrary.util.Utils
import kotlinx.android.synthetic.main.activity_new_reader_pager.*
import kotlinx.android.synthetic.main.fragment_reader.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.File


class WebPageDBFragment : BaseFragment() {

    var webPage: WebPage? = null
    var doc: Document? = null

    var history: ArrayList<WebPage> = ArrayList()


    companion object {
        private val NOVEL_ID = "novelId"
        private val ORDER_ID = "orderId"

        fun newInstance(novelId: Long, orderId: Long): WebPageDBFragment {
            val fragment = WebPageDBFragment()
            val args = Bundle()
            args.putLong(NOVEL_ID, novelId)
            args.putLong(ORDER_ID, orderId)
            fragment.arguments = args
            return fragment
        }
    }


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_reader, container, false)

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        val activity = activity as? ReaderDBPagerActivity ?: return

        //Show the menu button on scroll
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            readerWebView.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                if (scrollY > oldScrollY && scrollY > 0) activity.menuNav.visibility = View.GONE
                if (oldScrollY - scrollY > Constants.SCROLL_LENGTH) activity.menuNav.visibility = View.VISIBLE
            }

        setWebView()

        //Get data from args or savedInstance in case of device rotation
        @Suppress("UNCHECKED_CAST")
        if (savedInstanceState != null && savedInstanceState.containsKey("webPage")) {
            webPage = savedInstanceState.getSerializable("webPage") as WebPage
            history = savedInstanceState.getSerializable("history") as ArrayList<WebPage>
        } else {
            val novelId = arguments!!.getLong(NOVEL_ID)
            val novel = dbHelper.getNovel(novelId)
            val orderId = if (dataCenter.japSwipe) novel!!.chapterCount - arguments!!.getLong(ORDER_ID) - 1 else arguments!!.getLong(ORDER_ID)

            dbHelper.getWebPage(novelId, orderId)

            val intentWebPage = dbHelper.getWebPage(novelId, orderId)
            if (intentWebPage == null) activity.finish()
            else webPage = intentWebPage
        }


        //Load data from webPage in to webView
        loadData()
        swipeRefreshLayout.setOnRefreshListener { loadData() }

    }

    private fun checkForCloudFlare() {
        async {

            val listener = object : CloudFlare.Companion.Listener {
                override fun onSuccess() {
                    //  loadFragment(currentNavId)
                    if (activity != null)
                        Toast.makeText(activity, "Cloud Flare Bypassed", Toast.LENGTH_SHORT).show()
                    readerWebView.loadUrl("about:blank")
                    readerWebView.clearHistory()
                    loadData()
                }

                override fun onFailure() {
                    if (activity != null)
                        MaterialDialog.Builder(activity!!)
                            .content("Cloud Flare ByPass Failed")
                            .positiveText("Try Again")
                            .onPositive { dialog, _ -> dialog.dismiss(); checkForCloudFlare() }
                            .show()
                }
            }

            if (activity != null)
                await { CloudFlare(activity!!, listener).check() }
        }
    }

    @SuppressLint("JavascriptInterface", "AddJavascriptInterface")
    private fun setWebView() {
        readerWebView.isVerticalScrollBarEnabled = dataCenter.showReaderScroll
        readerWebView.settings.javaScriptEnabled = !dataCenter.javascriptDisabled
        readerWebView.setBackgroundColor(Color.argb(1, 0, 0, 0))
        readerWebView.addJavascriptInterface(this, "HTMLOUT")
        readerWebView.webViewClient = object : WebViewClient() {
            @Suppress("OverridingDeprecatedMember")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                //First page
                if (url == doc?.location()) {
                    return true
                }

                if (url == "abc://retry_internal")
                    checkForCloudFlare()

                //Add current page to history, if it was not already added or if the history is empty
//                if (history.isEmpty()) history.add(webPage!!)
//                else if (history.last() != webPage) history.add(webPage!!)

                //Handle the known links like next and previous chapter if downloaded
                if (checkUrl(url)) return true

                if (dataCenter.readerMode)
                    url?.let {

                        //If url is an image
                        if (url.endsWith(".jpg", true) || url.endsWith(".jpeg", true) || url.endsWith(".png"))
                            return false //default loading

                        downloadWebPage(url)
                        return true
                    }

                //If everything else fails, default loading of the WebView
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                val cookies = CookieManager.getInstance().getCookie(url)
                Log.e("WebViewDBFragment", "All the cookiesMap in a string:" + cookies)

                if (cookies != null && cookies.contains("cfduid") && cookies.contains("cf_clearance")) {
                    val map: HashMap<String, String> = HashMap()
                    val cookiesArray = cookies.split("; ")
                    cookiesArray.forEach { cookie ->
                        val cookieSplit = cookie.split("=")
                        map.put(cookieSplit[0], cookieSplit[1])
                    }
                    NovelApi.cookies = cookies
                    NovelApi.cookiesMap = map
                }

            }
        }
        //readerWebView.setOnScrollChangeListener { webView, i, i, i, i ->  }
        changeTextSize()
    }

    private fun loadData() {
        doc = null
        readerWebView.stopLoading()
        readerWebView.clearView()
        if (webPage?.filePath != null)
            loadFromFile()
        else
            loadFromWeb()
    }

    private fun loadFromFile() {
        swipeRefreshLayout.isRefreshing = false
        swipeRefreshLayout.isEnabled = false
        val internalFilePath = "file://${webPage?.filePath}"
        val input = File(internalFilePath.substring(7))

        var url = webPage!!.redirectedUrl
        if (url == null) url = internalFilePath

        doc = Jsoup.parse(input, "UTF-8", url)

        doc?.let { doc ->
            if (dataCenter.readerMode)
                cleanDocument(doc)
            loadCreatedDocument()
        }

    }

    private fun loadFromWeb() {
        swipeRefreshLayout.isEnabled = true
        if (!dataCenter.readerMode) {
            swipeRefreshLayout.isRefreshing = false
            readerWebView.loadUrl(webPage?.url)
            return
        }

        //Download the page and clean it to make it readable!
        async.cancelAll()
        downloadWebPage(webPage?.url)
    }

    private fun loadCreatedDocument() {
        readerWebView.loadDataWithBaseURL(
            if (webPage!!.filePath != null) "file://${webPage!!.filePath}" else doc?.location(),
            doc?.outerHtml(),
            "text/html", "UTF-8", null)
        if (webPage!!.metaData.containsKey("scrollY"))
            readerWebView.scrollTo(0, webPage!!.metaData["scrollY"]!!.toInt())
    }


    private fun downloadWebPage(url: String?) {
        if (url == null) return

        progressLayout.showLoading()

        //If no network
        if (!Utils.checkNetwork(activity)) {
            progressLayout.showError(ContextCompat.getDrawable(activity!!, R.drawable.ic_warning_white_vector), getString(R.string.no_internet), getString(R.string.try_again), {
                downloadWebPage(url)
            })
            return
        }

        async download@ {
            try {

                doc = await { NovelApi().getDocumentWithUserAgent(url) }

                //If document fails to load and the fragment is still alive
                if (doc == null) {
                    if (isResumed && !isRemoving && !isDetached)
                        progressLayout.showError(ContextCompat.getDrawable(activity!!, R.drawable.ic_warning_white_vector), getString(R.string.failed_to_load_url), getString(R.string.try_again), {
                            downloadWebPage(url)
                        })
                    return@download
                }

                //Update the relative urls with the absolute urls for the images and links
                doc?.getElementsByTag("img")?.forEach {
                    if (it.hasAttr("src")) {
                        it.attr("src", it.absUrl("src"))
                    }
                }
                doc?.getElementsByTag("a")?.forEach {
                    if (it.hasAttr("href")) {
                        it.attr("href", it.absUrl("href"))
                    }
                }

                //Clean the document & Load the document on the webView
                doc?.let { doc ->
                    val htmlHelper = HtmlHelper.getInstance(doc)
                    htmlHelper.removeJS(doc)
                    htmlHelper.additionalProcessing(doc)
                    htmlHelper.toggleTheme(dataCenter.isDarkTheme, doc)

                    if (dataCenter.enableClusterPages) {
                        //Add the links-content to the doc
                        val hrefElements = doc.body().getElementsByTag("a")
                        hrefElements?.forEach {
                            if (it.hasAttr("href")) {

                                val linkedUrl = it.attr("href")
                                try {
                                    val uri = Uri.parse(linkedUrl)
                                    if (!HostNames.isItDoNotDownloadHost(uri.host)) {
                                        val otherDoc = await { NovelApi().getDocumentWithUserAgent(it.attr("href")) }
                                        val helper = HtmlHelper.getInstance(otherDoc)
                                        helper.removeJS(otherDoc)
                                        helper.additionalProcessing(otherDoc)
                                        doc.body().append(otherDoc.body().html())
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }

                            }
                        }
                    }

                    loadCreatedDocument()
                }

                progressLayout.showContent()
                swipeRefreshLayout.isRefreshing = false

            } catch (e: Exception) {
                e.printStackTrace()
                if (isResumed && !isRemoving && !isDetached)
                    progressLayout.showError(ContextCompat.getDrawable(activity!!, R.drawable.ic_warning_white_vector), getString(R.string.failed_to_load_url), getString(R.string.try_again), {
                        downloadWebPage(url)
                    })
            }
        }
    }

    private fun changeTextSize() {
        val settings = readerWebView.settings
        settings.textZoom = (dataCenter.textSize + 50) * 2
    }

    fun getUrl(): String? {
        //if (doc.location() != null) return doc.location()
        return webPage?.url
    }

    fun goBack() {
        webPage = history.last()
        history.remove(webPage!!)
        loadData()
    }

    private fun cleanDocument(doc: Document) {
        try {
            progressLayout.showLoading()
            readerWebView.settings.javaScriptEnabled = false
            val htmlHelper = HtmlHelper.getInstance(doc)
            htmlHelper.removeJS(doc)
            htmlHelper.additionalProcessing(doc)
            htmlHelper.toggleTheme(dataCenter.isDarkTheme, doc)

            if (dataCenter.enableClusterPages) {
                //Add the links-content to the doc
                if (webPage!!.metaData.containsKey(Constants.MD_OTHER_LINKED_WEB_PAGES)) {
                    val links: ArrayList<WebPage> = Gson().fromJson(webPage!!.metaData[Constants.MD_OTHER_LINKED_WEB_PAGES], object : TypeToken<java.util.ArrayList<WebPage>>() {}.type)
                    links.forEach {
                        val internalFilePath = "file://${it.filePath}"
                        val input = File(internalFilePath.substring(7))

                        var url = it.redirectedUrl
                        if (url == null) url = internalFilePath
                        val otherDoc = Jsoup.parse(input, "UTF-8", url)
                        if (otherDoc != null) {
                            val helper = HtmlHelper.getInstance(otherDoc)
                            helper.removeJS(otherDoc)
                            helper.additionalProcessing(otherDoc)
                            doc.body().append(otherDoc.body().html())
                        }
                    }
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            progressLayout.showContent()
        }
    }

    private fun applyTheme() {
        doc?.let {
            HtmlHelper.getInstance(it).toggleTheme(dataCenter.isDarkTheme, it)
            loadCreatedDocument()
        }
    }

    fun checkUrl(url: String?): Boolean {
        if (url == null) return false

        if (webPage!!.metaData.containsKey(Constants.MD_OTHER_LINKED_WEB_PAGES)) {
            val links: ArrayList<WebPage> = Gson().fromJson(webPage!!.metaData[Constants.MD_OTHER_LINKED_WEB_PAGES], object : TypeToken<java.util.ArrayList<WebPage>>() {}.type)
            links.forEach {
                if (it.url == url || (it.redirectedUrl != null && it.redirectedUrl == url)) {
                    history.add(webPage!!)
                    webPage = it
                    loadData()
                    return@checkUrl true
                }
            }
        }

        val readerActivity = (activity as ReaderDBPagerActivity?) ?: return false
        return readerActivity.checkUrl(url)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onReaderSettingsChanged(event: ReaderSettingsEvent) {
        when (event.setting) {
            ReaderSettingsEvent.NIGHT_MODE -> {
                applyTheme()
            }
            ReaderSettingsEvent.READER_MODE -> {
                readerWebView.loadUrl("about:blank")
                readerWebView.clearHistory()
                loadData()
            }
            ReaderSettingsEvent.TEXT_SIZE -> {
                changeTextSize()
            }
            ReaderSettingsEvent.JAVA_SCRIPT -> {
                readerWebView.settings.javaScriptEnabled = !dataCenter.javascriptDisabled
                loadData()
            }
            ReaderSettingsEvent.FONT -> {
                loadData()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (webPage != null) {
            webPage!!.metaData.put("scrollY", readerWebView.scrollY.toString())
            if (webPage!!.id > -1L)
                dbHelper.updateWebPage(webPage!!)
        }
    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
    }

    override fun onStop() {
        EventBus.getDefault().unregister(this)
        super.onStop()
    }

    override fun onDestroy() {
        async.cancelAll()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (webPage != null) {
            outState.putSerializable("webPage", webPage)
        }
        outState.putSerializable("history", history)
    }

}