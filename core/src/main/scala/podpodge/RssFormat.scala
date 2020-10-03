package podpodge

import java.time.format.DateTimeFormatter

import podpodge.rss.Podcast

import scala.xml.Elem

object RssFormat {
  def encode(podcast: Podcast): Elem =
    <rss xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd" version="2.0">
      <channel>
        <title>{podcast.title}</title>
        <link>{podcast.linkUrl}</link>
        <description>{podcast.description}</description>
        <category>{podcast.category}</category>
        <generator>{podcast.generator}</generator>
       <language>en-us</language>
        <lastBuildDate>{podcast.lastBuildDate.format(DateTimeFormatter.RFC_1123_DATE_TIME)}</lastBuildDate>
        <pubDate>{podcast.publishDate.format(DateTimeFormatter.RFC_1123_DATE_TIME)}</pubDate>
        <image>
          <url>{podcast.imageUrl.toString}</url>
          <title>{podcast.title}</title>
          <link>{podcast.linkUrl}</link>
        </image>
        <itunes:author>{podcast.title}</itunes:author>
        <itunes:subtitle>{podcast.title}</itunes:subtitle>
        <itunes:summary>{podcast.title}</itunes:summary>
       <itunes:image href={podcast.imageUrl.toString} />
        <itunes:explicit>no</itunes:explicit>
        <itunes:category text={podcast.category}/>{
      podcast.items.zipWithIndex.map { case (item, order) =>
        <item>
          <guid>{item.guid}</guid>
          <title>{item.title}</title>
          <link>{item.linkUrl}</link>
          <description></description>
          <pubDate>{item.publishDate.format(DateTimeFormatter.RFC_1123_DATE_TIME)}</pubDate>
          <enclosure url={item.downloadUrl.toString} length="0" type="audio/mpeg"/>
          <itunes:author>{podcast.title}</itunes:author>
          <itunes:subtitle>{item.title}</itunes:subtitle>
          <itunes:summary/>
          <itunes:image href={item.imageUrl.toString} />
          <itunes:duration>{item.duration.toSeconds}</itunes:duration>
          <itunes:explicit>no</itunes:explicit>
          <itunes:order>{order}</itunes:order>
        </item>
      }
    }
    </channel>
    </rss>
}
