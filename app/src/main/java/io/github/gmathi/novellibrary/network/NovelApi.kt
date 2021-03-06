package io.github.gmathi.novellibrary.network

import CloudFlareByPasser
import android.net.Uri
import io.github.gmathi.novellibrary.dataCenter
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException
import java.net.URL
import java.util.regex.Pattern
import javax.net.ssl.SSLPeerUnverifiedException


object NovelApi {

    fun getDocumentWithUserAgent(url: String): Document {
        try {
            val doc = Jsoup
                    .connect(url)
                    .referrer(url)
                    .cookies(CloudFlareByPasser.getCookieMap(URL(url)))
                    .ignoreHttpErrors(true)
                    .timeout(30000)
                    .userAgent(HostNames.USER_AGENT)
                    .get()

            return doc

        } catch (e: SSLPeerUnverifiedException) {
            val p = Pattern.compile("Hostname\\s(.*?)\\snot", Pattern.DOTALL or Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE or Pattern.MULTILINE) // Regex for the value of the key
            val m = p.matcher(e.localizedMessage ?: "")
            if (m.find()) {
                val hostName = m.group(1)
                val hostNames = dataCenter.getVerifiedHosts()
                if (!hostNames.contains(hostName ?: "")) {
                    dataCenter.saveVerifiedHost(hostName ?: "")
                    return getDocumentWithUserAgent(url)
                }
            }
            throw e
        } catch (e: IOException) {
            if (e.localizedMessage != null && e.localizedMessage.contains("was not verified")) {
                val hostName = Uri.parse(url)?.host!!
                if (!HostNames.isVerifiedHost(hostName)) {
                    dataCenter.saveVerifiedHost(hostName)
                    return getDocumentWithUserAgent(url)
                }
            }
            throw e
        }
    }

    fun getDocumentWithUserAgentIgnoreContentType(url: String): Document {
        try {
            return Jsoup
                    .connect(url)
                    .referrer(url)
                    .timeout(30000)
                    .cookies(CloudFlareByPasser.getCookieMap(URL(url)))
                    .userAgent(HostNames.USER_AGENT)
                    .ignoreContentType(true)
                    .get()
        } catch (e: SSLPeerUnverifiedException) {
            val p = Pattern.compile("Hostname\\s(.*?)\\snot", Pattern.DOTALL or Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE or Pattern.MULTILINE) // Regex for the value of the key
            val m = p.matcher(e.localizedMessage)
            if (m.find()) {
                val hostName = m.group(1)
                val hostNames = dataCenter.getVerifiedHosts()
                if (!hostNames.contains(hostName)) {
                    dataCenter.saveVerifiedHost(hostName)
                    return getDocumentWithUserAgent(url)
                }
            }
            throw e
        }
    }

}