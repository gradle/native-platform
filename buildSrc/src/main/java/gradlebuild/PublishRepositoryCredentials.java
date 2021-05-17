package gradlebuild;

/**
 * Credentials for accessing the repository for publishing. Only required when releasing.
 */
public class PublishRepositoryCredentials {
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
            throw new IllegalStateException("No publish repository user name specified. You can set project property 'publishUserName' to provide this.");
        }
        if (apiKey == null) {
            throw new IllegalStateException("No publish repository API key specified. You can set project property 'publishApiKey' to provide this.");
        }
    }
}
