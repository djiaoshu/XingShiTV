package com.xingshi.tv;

final class Channel {
    final String number;
    final String name;
    final String streamId;
    final String url;
    final String[] urls;
    final String[] sourceNames;
    final String yangshipinPid;
    final String yangshipinStreamId;
    final String mgtvActivityId;
    final String mgtvCameraId;
    final String jstvChannelId;
    final String jstvEn;
    final String jstvStreamName;
    final String jstvPath;
    final String kankanewsChannelId;
    final String kankanewsStreamName;
    final String gdtvChannelId;
    final String gdtvStreamName;
    final String webUrl;
    final String webExtra;
    final String fullscreenType;
    final String epgId;
    final String epgSource;

    Channel(String number, String name, String streamId, String url,
            String yangshipinPid, String yangshipinStreamId) {
        this(number, name, streamId, url, yangshipinPid, yangshipinStreamId,
                null, null);
    }

    Channel(String number, String name, String streamId, String url,
            String yangshipinPid, String yangshipinStreamId,
            String mgtvActivityId, String mgtvCameraId) {
        this(number, name, streamId, url, yangshipinPid, yangshipinStreamId,
                mgtvActivityId, mgtvCameraId, null);
    }

    Channel(String number, String name, String streamId, String url,
            String yangshipinPid, String yangshipinStreamId,
            String mgtvActivityId, String mgtvCameraId, String webUrl) {
        this(number, name, streamId, url, yangshipinPid, yangshipinStreamId,
                mgtvActivityId, mgtvCameraId, webUrl, null);
    }

    Channel(String number, String name, String streamId, String url,
            String yangshipinPid, String yangshipinStreamId,
            String mgtvActivityId, String mgtvCameraId, String webUrl, String webExtra) {
        this(number, name, streamId, url, yangshipinPid, yangshipinStreamId,
                mgtvActivityId, mgtvCameraId, webUrl, webExtra, null);
    }

    Channel(String number, String name, String streamId, String url,
            String yangshipinPid, String yangshipinStreamId,
            String mgtvActivityId, String mgtvCameraId, String webUrl,
            String webExtra, String fullscreenType) {
        this(number, name, streamId, url, yangshipinPid, yangshipinStreamId,
                mgtvActivityId, mgtvCameraId, null, null, null,
                null, null, null, null, null, webUrl, webExtra, fullscreenType);
    }

    Channel(String number, String name, String streamId, String url,
            String yangshipinPid, String yangshipinStreamId,
            String mgtvActivityId, String mgtvCameraId, String jstvChannelId,
            String jstvEn, String jstvStreamName, String webUrl, String webExtra,
            String fullscreenType) {
        this(number, name, streamId, url, yangshipinPid, yangshipinStreamId,
                mgtvActivityId, mgtvCameraId, jstvChannelId, jstvEn, jstvStreamName,
                null, null, null, null, null, webUrl, webExtra, fullscreenType);
    }

    Channel(String number, String name, String streamId, String url,
            String yangshipinPid, String yangshipinStreamId,
            String mgtvActivityId, String mgtvCameraId, String jstvChannelId,
            String jstvEn, String jstvStreamName, String jstvPath, String webUrl,
            String webExtra, String fullscreenType) {
        this(number, name, streamId, url, yangshipinPid, yangshipinStreamId,
                mgtvActivityId, mgtvCameraId, jstvChannelId, jstvEn, jstvStreamName,
                jstvPath, null, null, null, null, webUrl, webExtra, fullscreenType);
    }

    Channel(String number, String name, String streamId, String url,
            String yangshipinPid, String yangshipinStreamId,
            String mgtvActivityId, String mgtvCameraId, String jstvChannelId,
            String jstvEn, String jstvStreamName, String jstvPath,
            String kankanewsChannelId, String kankanewsStreamName, String webUrl,
            String webExtra, String fullscreenType) {
        this(number, name, streamId, url, yangshipinPid, yangshipinStreamId,
                mgtvActivityId, mgtvCameraId, jstvChannelId, jstvEn, jstvStreamName,
                jstvPath, kankanewsChannelId, kankanewsStreamName,
                null, null, webUrl, webExtra, fullscreenType);
    }

    Channel(String number, String name, String streamId, String url,
            String yangshipinPid, String yangshipinStreamId,
            String mgtvActivityId, String mgtvCameraId, String jstvChannelId,
            String jstvEn, String jstvStreamName, String jstvPath,
            String kankanewsChannelId, String kankanewsStreamName,
            String gdtvChannelId, String gdtvStreamName, String webUrl,
            String webExtra, String fullscreenType) {
        this(number, name, streamId,
                url == null ? new String[0] : new String[] { url },
                null,
                yangshipinPid, yangshipinStreamId, mgtvActivityId, mgtvCameraId,
                jstvChannelId, jstvEn, jstvStreamName, jstvPath,
                kankanewsChannelId, kankanewsStreamName, gdtvChannelId, gdtvStreamName,
                webUrl, webExtra, fullscreenType, null, null);
    }

    private Channel(String number, String name, String streamId, String[] urls,
            String[] sourceNames,
            String yangshipinPid, String yangshipinStreamId, String mgtvActivityId,
            String mgtvCameraId, String jstvChannelId, String jstvEn, String jstvStreamName,
            String jstvPath, String kankanewsChannelId, String kankanewsStreamName,
            String gdtvChannelId, String gdtvStreamName,
            String webUrl, String webExtra, String fullscreenType,
            String epgId, String epgSource) {
        this.number = number;
        this.name = name;
        this.streamId = streamId;
        this.urls = urls;
        this.url = urls.length == 0 ? null : urls[0];
        this.sourceNames = normalizeSourceNames(sourceNames, urls.length);
        this.yangshipinPid = yangshipinPid;
        this.yangshipinStreamId = yangshipinStreamId;
        this.mgtvActivityId = mgtvActivityId;
        this.mgtvCameraId = mgtvCameraId;
        this.jstvChannelId = jstvChannelId;
        this.jstvEn = jstvEn;
        this.jstvStreamName = jstvStreamName;
        this.jstvPath = jstvPath;
        this.kankanewsChannelId = kankanewsChannelId;
        this.kankanewsStreamName = kankanewsStreamName;
        this.gdtvChannelId = gdtvChannelId;
        this.gdtvStreamName = gdtvStreamName;
        this.webUrl = webUrl;
        this.webExtra = webExtra;
        this.fullscreenType = fullscreenType;
        this.epgId = normalize(epgId);
        this.epgSource = normalize(epgSource);
    }

    static Channel directSource(String number, String name, String streamId,
            String url, String sourceName) {
        return directSource(number, name, streamId, url, sourceName, null, null);
    }

    static Channel directSource(String number, String name, String streamId,
            String url, String sourceName, String epgId, String epgSource) {
        return new Channel(number, name, streamId,
                url == null ? new String[0] : new String[] { url },
                sourceName == null ? null : new String[] { sourceName },
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, epgId, epgSource);
    }

    Channel withAdditionalUrl(String additionalUrl) {
        return withAdditionalSource(null, additionalUrl);
    }

    Channel withAdditionalSource(String sourceName, String additionalUrl) {
        for (String existing : urls) {
            if (existing.equals(additionalUrl)) {
                return this;
            }
        }
        String[] combined = new String[urls.length + 1];
        System.arraycopy(urls, 0, combined, 0, urls.length);
        combined[urls.length] = additionalUrl;
        String[] combinedNames = new String[sourceNames.length + 1];
        System.arraycopy(sourceNames, 0, combinedNames, 0, sourceNames.length);
        combinedNames[sourceNames.length] = sourceName;
        return new Channel(number, name, streamId, combined,
                combinedNames,
                yangshipinPid, yangshipinStreamId, mgtvActivityId, mgtvCameraId,
                jstvChannelId, jstvEn, jstvStreamName, jstvPath,
                kankanewsChannelId, kankanewsStreamName, gdtvChannelId, gdtvStreamName,
                webUrl, webExtra, fullscreenType, epgId, epgSource);
    }

    Channel withEpg(String epgId, String epgSource) {
        return new Channel(number, name, streamId, urls, sourceNames,
                yangshipinPid, yangshipinStreamId, mgtvActivityId, mgtvCameraId,
                jstvChannelId, jstvEn, jstvStreamName, jstvPath,
                kankanewsChannelId, kankanewsStreamName, gdtvChannelId, gdtvStreamName,
                webUrl, webExtra, fullscreenType, epgId, epgSource);
    }

    int sourceCount() {
        return urls.length;
    }

    String sourceUrl(int index) {
        if (urls.length == 0) {
            return null;
        }
        int wrapped = (index % urls.length + urls.length) % urls.length;
        return urls[wrapped];
    }

    String sourceName(int index) {
        if (sourceNames.length == 0) {
            return null;
        }
        int wrapped = (index % sourceNames.length + sourceNames.length) % sourceNames.length;
        return sourceNames[wrapped];
    }

    private static String[] normalizeSourceNames(String[] names, int count) {
        String[] normalized = new String[count];
        for (int index = 0; index < count; index++) {
            String value = names != null && index < names.length ? names[index] : null;
            normalized[index] = value == null || value.trim().length() == 0
                    ? "线路 " + (index + 1) : value.trim();
        }
        return normalized;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() == 0 ? null : trimmed;
    }
}

