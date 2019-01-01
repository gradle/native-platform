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
}
