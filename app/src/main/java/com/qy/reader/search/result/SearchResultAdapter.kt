package com.qy.reader.search.result

import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.qy.reader.R
import com.qy.reader.common.entity.book.SearchBook
import com.qy.reader.crawler.source.SourceManager
import com.yuyh.easyadapter.recyclerview.EasyRVAdapter
import com.yuyh.easyadapter.recyclerview.EasyRVHolder

/**
 * Created by yuyuhang on 2018/1/11.
 */
class SearchResultAdapter(context: Context, list: List<SearchBook>) : EasyRVAdapter<SearchBook>(context, list, R.layout.item_search_list) {

    private var title: String? = null

    fun setTitle(title: String) {
        this.title = title
    }

    override fun onBindData(viewHolder: EasyRVHolder, position: Int, item: SearchBook) {

        val tvTitle = viewHolder.getView<TextView>(R.id.tv_search_item_title)
        tvTitle.text = changeTxtColor(item.title, title, -0xf7f80)

        val tvAuthor = viewHolder.getView<TextView>(R.id.tv_search_item_author)
        tvAuthor.text = changeTxtColor(item.author, title, -0xf7f80)

        val tvDesc = viewHolder.getView<TextView>(R.id.tv_search_item_desc)
        tvDesc.text = changeTxtColor(item.desc, title, -0xf7f80)

        val tvSource = viewHolder.getView<TextView>(R.id.tv_search_item_source)
        val builder = StringBuilder()
        for (sl in item.sources) {
            if (sl.source != null) {
                val source = SourceManager.SOURCES.get(sl.source.id)
                if (source != null) {
                    if (builder.isNotEmpty()) {
                        builder.append(" | ")
                    }
                    builder.append(source.name)
                }
            }
        }
        tvSource.text = builder.toString()

        val ivCover = viewHolder.getView<ImageView>(R.id.iv_search_item_cover)
        Glide.with(mContext).load(item.cover).into(ivCover)
    }

    private fun changeTxtColor(content: String?, splitText: String?, color: Int): SpannableString {
        var start = 0
        var end: Int
        val result = SpannableString(content ?: "")
        if (TextUtils.isEmpty(splitText)) {
            return result
        }
        if (!TextUtils.isEmpty(splitText) && content!!.length >= splitText!!.length) {
            start = content.indexOf(splitText, start)
            while (start >= 0) {
                end = start + splitText.length
                result.setSpan(ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_INCLUSIVE)
                start = end
                start = content.indexOf(splitText, start)
            }
        }
        return result
    }
}
