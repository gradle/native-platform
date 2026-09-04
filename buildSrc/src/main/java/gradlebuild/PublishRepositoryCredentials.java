package gradlebuild;

/**
 * Token for accessing the repository for publishing. Only required when releasing.
 */
public class PublishRepositoryCredentials {
    private String token;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    void assertPresent() {
        if (token == null) {
            throw new IllegalStateException("No publish repository token specified. You can set project property 'publishToken' to provide this.");
        }
    }
}
