package io.github.gmathi.novellibrary.cleaner

import android.net.Uri
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.File

class TumblrCleaner : HtmlHelper() {

    override fun additionalProcessing(doc: Document) {
        removeCSS(doc)
        doc.getElementsByTag("link")?.remove()
        doc.getElementsByTag("style")?.remove()
        var contentElement = doc.body().getElementsByTag("div").firstOrNull { it.hasClass("textpostbody") }

        if (contentElement == null) {
            contentElement = doc.body().getElementsByTag("div").firstOrNull { it.id() == "content" }
        }

        contentElement?.prepend("<h4>${getTitle(doc)}</h4><br>")
        do {
            contentElement?.siblingElements()?.remove()
            cleanClassAndIds(contentElement)
            contentElement = contentElement?.parent()
        } while (contentElement != null && contentElement.tagName() != "body")
        cleanClassAndIds(contentElement)
        contentElement?.getElementsByClass("wpcnt")?.remove()
        contentElement?.getElementById("jp-post-flair")?.remove()
    }

    override fun downloadImage(element: Element, file: File): File? {
        val uri = Uri.parse(element.attr("src"))
        return if (uri.toString().contains("uploads/avatars")) null
        else super.downloadImage(element, file)
    }


    override fun toggleTheme(isDark: Boolean, doc: Document): Document {
        return super.toggleThemeDefault(isDark, doc)
    }
}