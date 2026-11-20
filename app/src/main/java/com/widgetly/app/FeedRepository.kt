package com.widgetly.app

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL

data class FeedItem(val title:String,val link:String,val summary:String,val image:String?,val source:String,val category:String)

object FeedRepository {
    private data class Source(val url:String,val name:String,val category:String)
    private val sources=listOf(
        Source("https://www.youtube.com/feeds/videos.xml?channel_id=UCsooa4yRKGN_zEE8iknghZA","TED-Ed","Videos"),
        Source("https://www.youtube.com/feeds/videos.xml?channel_id=UCHnyfMqiRRG1u-2MsSQLbXA","Veritasium","Videos"),
        Source("https://www.youtube.com/feeds/videos.xml?channel_id=UCX6b17PVsYBQ0ip5gyeme-Q","CrashCourse","Videos"),
        Source("https://www.youtube.com/feeds/videos.xml?channel_id=UCsXVk37bltHxD1rDPwtNM8Q","Kurzgesagt","Videos"),
        Source("https://news.google.com/rss/search?q=education+science+technology+explained&hl=en&gl=US&ceid=US:en","Google News","News"),
        Source("https://news.google.com/rss/search?q=productivity+learning+research&hl=en&gl=US&ceid=US:en","Google News","News"),
        Source("https://editorial.rottentomatoes.com/feed/","Rotten Tomatoes","Movies"),
        Source("https://www.youtube.com/feeds/videos.xml?channel_id=UC3gNmTGu-TTbFPpfSs5kNkg","Movieclips","Movies")
    )

    fun load(category:String):List<FeedItem> {
        val chosen=sources.filter{category=="All"||it.category.equals(category,true)}
        val output=mutableListOf<FeedItem>()
        chosen.forEach{source->runCatching{output.addAll(fetch(source))}}
        return output.distinctBy{it.link}.take(60)
    }

    private fun fetch(source:Source):List<FeedItem> {
        val connection=URL(source.url).openConnection() as HttpURLConnection
        connection.connectTimeout=10000
        connection.readTimeout=10000
        connection.setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36 Nexus/1.0")
        return try {
            connection.inputStream.use{input->
                val parser=Xml.newPullParser()
                parser.setInput(input,"UTF-8")
                val result=mutableListOf<FeedItem>()
                var event=parser.eventType
                var inside=false
                var title="";var link="";var summary="";var image:String?=null
                while(event!=XmlPullParser.END_DOCUMENT) {
                    when(event) {
                        XmlPullParser.START_TAG->{
                            val tag=parser.name.substringAfter(':').lowercase()
                            if(tag=="item"||tag=="entry"){inside=true;title="";link="";summary="";image=null}
                            else if(inside) {
                                when(tag) {
                                    "title"->title=parser.nextText().trim()
                                    "link"->{
                                        val href=parser.getAttributeValue(null,"href")
                                        link=if(!href.isNullOrBlank())href else parser.nextText().trim()
                                    }
                                    "description","summary"->summary=parser.nextText().replace(Regex("<[^>]*>")," ").replace("&nbsp;"," ").trim().take(420)
                                    "thumbnail","content"->{
                                        val media=parser.getAttributeValue(null,"url")?:parser.getAttributeValue(null,"src")
                                        if(!media.isNullOrBlank())image=media
                                        else if(tag=="content") summary=parser.nextText().take(420)
                                    }
                                    "enclosure"->{
                                        val media=parser.getAttributeValue(null,"url")
                                        if(image.isNullOrBlank()&&!media.isNullOrBlank())image=media
                                    }
                                }
                            }
                        }
                        XmlPullParser.END_TAG->if(parser.name.substringAfter(':').equals("item",true)||parser.name.substringAfter(':').equals("entry",true)){
                            inside=false
                            if(title.isNotBlank()&&link.startsWith("http"))result.add(FeedItem(title,link,summary,image,source.name,source.category))
                        }
                    }
                    event=parser.next()
                }
                result
            }
        } finally {connection.disconnect()}
    }
}
