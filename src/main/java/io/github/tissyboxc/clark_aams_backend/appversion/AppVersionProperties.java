package io.github.tissyboxc.clark_aams_backend.appversion;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "clark-aams.client-version")
public class AppVersionProperties {
    private String latestVersion = "1.0.0";
    private int latestBuild = 1;
    private String minimumSupportedVersion = "1.0.0";
    private int minimumSupportedBuild = 1;
    private String title = "发现新版本";
    private String optionalUpdateMessage = "有新版本可用，建议更新后继续使用。";
    private String requiredUpdateMessage = "当前版本已停止支持，请更新后继续使用。";
    private String releasePageUrl = "";
    private List<String> releaseNotes = new ArrayList<>();
    private List<DownloadSource> downloadSources = new ArrayList<>();

    public String getLatestVersion() {
        return latestVersion;
    }

    public void setLatestVersion(String latestVersion) {
        this.latestVersion = latestVersion;
    }

    public int getLatestBuild() {
        return latestBuild;
    }

    public void setLatestBuild(int latestBuild) {
        this.latestBuild = latestBuild;
    }

    public String getMinimumSupportedVersion() {
        return minimumSupportedVersion;
    }

    public void setMinimumSupportedVersion(String minimumSupportedVersion) {
        this.minimumSupportedVersion = minimumSupportedVersion;
    }

    public int getMinimumSupportedBuild() {
        return minimumSupportedBuild;
    }

    public void setMinimumSupportedBuild(int minimumSupportedBuild) {
        this.minimumSupportedBuild = minimumSupportedBuild;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getOptionalUpdateMessage() {
        return optionalUpdateMessage;
    }

    public void setOptionalUpdateMessage(String optionalUpdateMessage) {
        this.optionalUpdateMessage = optionalUpdateMessage;
    }

    public String getRequiredUpdateMessage() {
        return requiredUpdateMessage;
    }

    public void setRequiredUpdateMessage(String requiredUpdateMessage) {
        this.requiredUpdateMessage = requiredUpdateMessage;
    }

    public String getReleasePageUrl() {
        return releasePageUrl;
    }

    public void setReleasePageUrl(String releasePageUrl) {
        this.releasePageUrl = releasePageUrl;
    }

    public List<String> getReleaseNotes() {
        return releaseNotes;
    }

    public void setReleaseNotes(List<String> releaseNotes) {
        this.releaseNotes = releaseNotes;
    }

    public List<DownloadSource> getDownloadSources() {
        return downloadSources;
    }

    public void setDownloadSources(List<DownloadSource> downloadSources) {
        this.downloadSources = downloadSources;
    }

    public static class DownloadSource {
        private String type;
        private String label;
        private String url;
        private boolean primary;
        private String description;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public boolean isPrimary() {
            return primary;
        }

        public void setPrimary(boolean primary) {
            this.primary = primary;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }
}
