package gradlebuild;

/**
 * Credentials for accessing bintray. Only required when releasing.
 */
public class BintrayCredentials {
    private String userName;
    private String apiKey;

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    void assertPresent() {
        if (userName == null) {
            throw new IllegalStateException("No bintray user name specified. You can set project property 'bintrayUserName' to provide this.");
        }
        if (apiKey == null) {
            throw new IllegalStateException("No bintray API key specified. You can set project property 'bintrayApiKey' to provide this.");
        }
    }
}
