package com.xatkit.plugins.github.platform;

import com.jcabi.github.Comment;
import com.jcabi.github.Github;
import com.jcabi.github.Issue;
import com.jcabi.github.RtGithub;
import com.xatkit.core.XatkitCore;
import com.xatkit.core.XatkitException;
import com.xatkit.core.platform.RuntimePlatform;
import com.xatkit.core.platform.action.RuntimeActionResult;
import com.xatkit.core.platform.io.RuntimeEventProvider;
import com.xatkit.execution.StateContext;
import com.xatkit.plugins.github.platform.action.AssignUser;
import com.xatkit.plugins.github.platform.action.CommentIssue;
import com.xatkit.plugins.github.platform.action.GetIssue;
import com.xatkit.plugins.github.platform.action.OpenIssue;
import com.xatkit.plugins.github.platform.action.SetLabel;
import fr.inria.atlanmod.commons.log.Log;
import lombok.NonNull;
import org.apache.commons.configuration2.Configuration;

import java.io.IOException;

import static fr.inria.atlanmod.commons.Preconditions.checkArgument;
import static fr.inria.atlanmod.commons.Preconditions.checkNotNull;
import static java.util.Objects.nonNull;

/**
 * A {@link RuntimePlatform} class that connects and interacts with the Github API.
 */
public class GithubPlatform extends RuntimePlatform {

    /**
     * The {@link Configuration} key to store the Github username.
     * <p>
     * The {@link GithubPlatform} can handle {@link Configuration} with username/password as well as oauth token. Note
     * that if an username/password is provided it will be used instead of the oauth token to initialize the client.
     *
     * @see #GITHUB_PASSWORD_KEY
     * @see #GITHUB_OAUTH_TOKEN
     */
    public static String GITHUB_USERNAME_KEY = "xatkit.github.username";

    /**
     * The {@link Configuration} key to store the Github password associated to the defined username.
     * <p>
     * The {@link GithubPlatform} can handle {@link Configuration} with username/password as well as oauth token. Note
     * that if an username/password is provided it will be used instead of the oauth token to initialize the client.
     *
     * @see #GITHUB_USERNAME_KEY
     * @see #GITHUB_OAUTH_TOKEN
     */
    public static String GITHUB_PASSWORD_KEY = "xatkit.github.password";

    /**
     * The {@link Configuration} key to store the Github oauth token.
     * <p>
     * The {@link GithubPlatform} can handle {@link Configuration} with username/password as well as oauth token. Note
     * that if an username/password is provided it will be used instead of the oauth token to initialize the client.
     *
     * @see #GITHUB_USERNAME_KEY
     * @see #GITHUB_PASSWORD_KEY
     */
    public static String GITHUB_OAUTH_TOKEN = "xatkit.github.oauth.token";

    /**
     * The {@link Github} client used to access the Github API.
     * <p>
     * This client is initialized from the provided {@link Configuration}, and can be {@code null} if the
     * authentication failed or if the {@link Configuration} does not define any credentials information. Note that
     * {@link GithubPlatform} initialized with no credentials can still be used to construct Github-related
     * {@link RuntimeEventProvider}s.
     */
    private Github githubClient;

    /**
     * {@inheritDoc}
     * <p>
     * This constructor tries to initialize the {@link Github} client used to access the Github API by looking for
     * the username/login or oauth token in the provided {@code configuration}.
     *
     * @throws IllegalArgumentException if the provided {@code configuration} contains a {@code username} but does not
     *                                  contain a valid {@code password}
     * @throws XatkitException          if the provided credentials are not valid or if a network error occurred when
     *                                  accessing the Github API.
     */
    @Override
    public void start(XatkitCore xatkitCore, Configuration configuration) {
        super.start(xatkitCore, configuration);
        String username = configuration.getString(GITHUB_USERNAME_KEY);
        if (nonNull(username)) {
            String password = configuration.getString(GITHUB_PASSWORD_KEY);
            checkArgument(nonNull(password) && !password.isEmpty(), "Cannot construct a %s from the " +
                            "provided username and password, please ensure that the Xatkit configuration contains a " +
                            "valid password for the username %s (configuration key: %s)", this.getClass()
                            .getSimpleName(),
                    username, GITHUB_PASSWORD_KEY);
            githubClient = new RtGithub(username, password);
            checkGithubClient(githubClient);
        } else {
            String oauthToken = configuration.getString(GITHUB_OAUTH_TOKEN);
            if (nonNull(oauthToken) && !oauthToken.isEmpty()) {
                githubClient = new RtGithub(oauthToken);
                checkGithubClient(githubClient);
            } else {
                Log.warn("No authentication method set in the configuration, {0} will not be able to call methods on " +
                        "the remote Github API. If you want to use the Github API you must provide a " +
                        "username/password or an oauth token in the Xatkit configuration", this.getClass().getSimpleName
                        ());
            }
        }
    }

    /**
     * Assigns the provided {@code username} to the given {@code issue}.
     *
     * @param context  the current {@link StateContext}
     * @param issue    the {@link Issue} to assign the user to
     * @param username the username of the Github user to assign
     * @return the username
     * @throws XatkitException if an internal error occurred
     */
    public @NonNull String assignUser(@NonNull StateContext context, @NonNull Issue issue, @NonNull String username) {
        AssignUser action = new AssignUser(this, context, issue, username);
        RuntimeActionResult result = this.executeRuntimeAction(action);
        return (String) result.getResult();
    }

    /**
     * Posts a comment on the provided {@code issue}.
     *
     * @param context        the current {@link StateContext}
     * @param issue          the {@link Issue} to post a comment on
     * @param commentContent the content of the comment to post
     * @return the posted {@link Comment}
     * @throws XatkitException if an internal error occurred
     */
    public @NonNull Comment commentIssue(@NonNull StateContext context, @NonNull Issue issue,
                                   @NonNull String commentContent) {
        CommentIssue action = new CommentIssue(this, context, issue, commentContent);
        RuntimeActionResult result = this.executeRuntimeAction(action);
        return (Comment) result.getResult();
    }

    /**
     * Retrieves the {@link Issue} with the provided {@code issueNumber} in the given {@code repository}.
     *
     * @param context     the current {@link StateContext}
     * @param user        the Github user managing the repository to access
     * @param repository  the name of the repository containing the issue
     * @param issueNumber the number of the issue to retrieve
     * @return the {@link Issue}
     */
    public @NonNull Issue.Smart getIssue(@NonNull StateContext context, @NonNull String user, @NonNull String repository,
                                         @NonNull String issueNumber) {
        GetIssue action = new GetIssue(this, context, user, repository, issueNumber);
        RuntimeActionResult result = this.executeRuntimeAction(action);
        return (Issue.Smart) result.getResult();
    }

    /**
     * Opens an issue on the given {@code repository}.
     *
     * @param context      the current {@link StateContext}
     * @param user         the Github user managing the repository to access
     * @param repository   the name of the repository to open an issue on
     * @param issueTitle   the title of the issue
     * @param issueContent the content of the issue
     * @return the {@link Issue}
     * @throws XatkitException if an internal error occurred
     */
    public @NonNull Issue.Smart openIssue(@NonNull StateContext context, @NonNull String user, @NonNull String repository,
                                 @NonNull String issueTitle, @NonNull String issueContent) {
        OpenIssue action = new OpenIssue(this, context, user, repository, issueTitle, issueContent);
        RuntimeActionResult result = this.executeRuntimeAction(action);
        return (Issue.Smart) result.getResult();
    }

    /**
     * Sets the provided {@code label} on the given {@code issue}.
     * <p>
     * This action creates a new label if the provided one doesn't match any label on the repository.
     *
     * @param context the current {@link StateContext}
     * @param issue   the {@link Issue} to set a label to
     * @param label   the label
     * @return the label
     * @throws XatkitException if an internal error occurred
     */
    public @NonNull String setLabel(@NonNull StateContext context, @NonNull Issue issue, @NonNull String label) {
        SetLabel action = new SetLabel(this, context, issue, label);
        RuntimeActionResult result = this.executeRuntimeAction(action);
        return (String) result.getResult();
    }

    /**
     * Checks that provided {@code githubClient} is initialized and has valid credentials.
     * <p>
     * Credentials are checked by trying to retrieve the <i>self user login</i> from the {@link Github} client. If
     * the credentials are valid the {@link Github} client defines a <i>self</i> instance, otherwise it throws an
     * {@link AssertionError} when receiving the API result.
     *
     * @param githubClient the {@link Github} client to check
     * @throws XatkitException if the provided {@link Github} client credentials are invalid or if an
     *                         {@link IOException} occurred when accessing the Github API.
     */
    private void checkGithubClient(Github githubClient) {
        checkNotNull(githubClient, "Cannot check the provided %s %s", Github.class.getSimpleName(), githubClient);
        try {
            String selfLogin = githubClient.users().self().login();
            Log.info("Logged in Github under the user {0}", selfLogin);
        } catch (IOException e) {
            throw new XatkitException(e);
        } catch (AssertionError e) {
            throw new XatkitException("Cannot access the Github API, please check your credentials", e);
        }
    }

    /**
     * Returns the {@link Github} client used to access the Github API.
     *
     * @return the {@link Github} client used to access the Github API
     * @throws XatkitException if the {@link GithubPlatform} does not define a valid {@link Github} client (i.e. if the
     *                         provided {@link Configuration} does not contain any credentials information or if the
     *                         authentication failed).
     */
    public Github getGithubClient() {
        if (nonNull(githubClient)) {
            return githubClient;
        } else {
            throw new XatkitException("Cannot access the Github client, make sure it has been initialized correctly");
        }
    }
}
