package com.mgn.bingenovelreader.activity

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.MenuItem
import android.view.View
import com.mgn.bingenovelreader.R
import com.mgn.bingenovelreader.adapter.GenericAdapter
import com.mgn.bingenovelreader.extension.applyFont
import com.mgn.bingenovelreader.extension.setDefaults
import com.mgn.bingenovelreader.model.Novel
import kotlinx.android.synthetic.main.activity_meta_data.*
import kotlinx.android.synthetic.main.content_chapters.*
import kotlinx.android.synthetic.main.listitem_metadata.view.*
import java.util.*

class MetaDataActivity : AppCompatActivity(), GenericAdapter.Listener<Map.Entry<String, String>> {

    lateinit var novel: Novel
    lateinit var adapter: GenericAdapter<Map.Entry<String, String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_meta_data)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        novel = intent.getSerializableExtra("novel") as Novel
        setRecyclerView()


        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun setRecyclerView() {
        adapter = GenericAdapter(items = (ArrayList(novel.metaData.entries) as ArrayList<Map.Entry<String, String>>), layoutResId = R.layout.listitem_metadata, listener = this)
        recyclerView.setDefaults(adapter)
        swipeRefreshLayout.setOnRefreshListener { swipeRefreshLayout.isRefreshing = false }
    }

    override fun bind(item: Map.Entry<String, String>, itemView: View, position: Int) {
        itemView.metadataKey.applyFont(assets).text = item.key.toUpperCase(Locale.getDefault())
        itemView.metadataValue.applyFont(assets).text = item.value
    }

    override fun onItemClick(item: Map.Entry<String, String>) {

    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        if (item?.itemId == android.R.id.home) finish()
        return super.onOptionsItemSelected(item)
    }


}
