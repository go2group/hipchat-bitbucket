package com.go2group.hipchat.components;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;

import com.atlassian.sal.api.executor.ThreadLocalDelegateExecutorFactory;
import com.atlassian.sal.api.net.Request;
import com.atlassian.sal.api.net.RequestFactory;
import com.atlassian.sal.api.net.Response;
import com.atlassian.sal.api.net.ResponseException;
import com.atlassian.sal.api.net.ReturningResponseHandler;
import com.go2group.hipchat.utils.InvalidAuthTokenException;

public class HipChatProxyClient implements DisposableBean
{
    private static final Logger log = LoggerFactory.getLogger("atlassian.plugin");
    private static final int CONNECTION_TIMEOUT_MILLIS = 5000;
    private ConfigurationManager configurationManager;
    private ExecutorService executorService;
    private ThreadLocalDelegateExecutorFactory executorFactory;
    private RequestFactory<Request<?, Response>> requestFactory;

    public HipChatProxyClient(ConfigurationManager configurationManager,
                              RequestFactory<Request<?, Response>> requestFactory,
                              ThreadLocalDelegateExecutorFactory executorFactory)
    {
        this.configurationManager = configurationManager;
        this.requestFactory = requestFactory;
        this.executorFactory = executorFactory;
        this.executorService = executorFactory.createExecutorService(Executors.newFixedThreadPool(1));
    }

    /**
     * Simple wrapper to indicate that the value is JSON.
     */
    public static class JSONString {
        private final String value;
        private JSONString(String value) {
            this.value = value;
        }
        @Override public String toString() {
            return value;
        }
    }

    /**
     * Returns the JSON representation of a user in HipChat.
     * <pre>
     * "user": {
     * "user_id": 5,
     * "name": "Garret Heaton",
     * "email": "garret@hipchat.com",
     * "title": "Co-founder",
     * "photo_url": "https:\/\/www.hipchat.com\/img\/silhouette_125.png",
     * "status": "available",
     * "status_message": "Come see what I'm working on!",
     * "is_group_admin": 1
     * }
     * </pre>
     *
     * @param userId ID or email address of the user.
     * @return JSON String
     */
    public JSONString getUser(final String userId) {    	
        final String url = this.configurationManager.getHipChatServerUrl() + "/v1/users/show?user_id=" + userId;
        return jsonGet(url, this.configurationManager.getHipChatAuthToken());
    }

    public JSONString getUsers(){
        final String url = this.configurationManager.getHipChatServerUrl() + "/v1/users/list";
        return jsonGet(url, this.configurationManager.getHipChatAuthToken());
    }
    
/*    private String getIconUrl(String baseUrl){
        return "<img src=\"" +
                baseUrl + "/images/icons/blogentry_16.gif" +
                "\" width=16 height=16 />&nbsp;";
    }
*/
    /**
     * Returns the JSON representation of a list of Rooms in HipChat.
     *
     * Example response
     * <pre>
     *     {
     * "rooms": [
     * {
     * "room_id": 7,
     * "name": "Development",
     * "topic": "Make sure to document your API functions well!",
     * "last_active": 1269020400,
     * "created": 1269010311,
     * "owner_user_id": 1,
     * "is_archived": false,
     * "is_private": false,
     * "xmpp_jid": "7_development@conf.hipchat.com"
     * },
     * {
     * "room_id": 10,
     * "name": "Ops",
     * "topic": "Chef is so awesome.",
     * "last_active": 1269010500,
     * "created": 1269010211,
     * "owner_user_id": 5,
     * "is_archived": false,
     * "is_private": true,
     * "xmpp_jid": "10_ops@conf.hipchat.com"
     * }
     * ]
     * }
     * </pre>
     *
     * @return JSON String containing a list of rooms
     * @throws com.go2group.hipchat.utils.InvalidAuthTokenException
     */
    public JSONString getRooms(final String serverUrl, final String authToken) throws InvalidAuthTokenException
    {
        final String url = serverUrl + "/v1/rooms/list";
        return jsonGet(url, authToken);
    }

    /**
     * Send a message to a room.
     * This method does not block and executes the request asynchronously.
     *
     * @param room    ID or name of the room.
     * @param message The message body. Must be valid XHTML. HTML entities must be escaped (e.g.: &amp; instead of &).
     *                May contain basic tags: a, b, i, strong, em, br, img, pre, code. 5000 characters max.
     */
    public void notifyRoom(final String room, final String message, final String color, final String format, final String notify) {
        final String authToken = configurationManager.getHipChatAuthToken();
        final String serverUrl = configurationManager.getHipChatServerUrl();
        final String defaultColor = configurationManager.getMessageColor();
        if (StringUtils.isEmpty(authToken)) {
            return;
        }

        Runnable postRequest = new Runnable() {
            @Override public void run() {
                final String url = serverUrl + "/v1/rooms/message?auth_token=" + authToken;
                final Request<?, Response> request = requestFactory.createRequest(Request.MethodType.POST, url);
                String notifyRooms = "0";
                if (notify != null && notify.equals("true")){
                	notifyRooms = "1";
                }
                request.addRequestParameters(
                        "room_id", room,
                        "from", "Stash",
                        "message", message,
                        "color", color == null ? defaultColor : color,
                        "format", "json",
                        "message_format", format,
                        "notify", notifyRooms);
                try {
                    request.executeAndReturn(new ResponseBodyReturningHandler());
                }
                catch (ResponseException e) {
                    log.error("Failed to notify rooms", e);
                }
            }
        };
        Runnable executeRequest = executorFactory.createRunnable(postRequest);
        executorService.execute(executeRequest);
    }

    /**
     * Execute an HTTP GET request to {@code url}.
     *
     * @param url
     * @return String containing JSON if successful, empty String otherwise. Never returns null.
     *         This will always return an empty string if the Hip-Chat authentication token is not set.
     */
    private JSONString jsonGet(String url, String authToken) {
        if (StringUtils.isEmpty(authToken) || StringUtils.isEmpty(url)) {
            return new JSONString("");
        }
        final String urlWithAuthToken = url.contains("?") ? url + "&auth_token=" + authToken : url + "?auth_token=" + authToken;
        final Request<?, Response> request = requestFactory.createRequest(Request.MethodType.GET, urlWithAuthToken);
        request.setConnectionTimeout(CONNECTION_TIMEOUT_MILLIS);
        try {
            return new JSONString(request.executeAndReturn(new ResponseBodyReturningHandler()));
        }
        catch (ResponseException e) {
            log.error("Failed to retrieve user", e);
        }
        return new JSONString("");
    }

    private class ResponseBodyReturningHandler implements ReturningResponseHandler<Response, String>
    {
        @Override public String handle(Response response) throws ResponseException {
            if(response.getStatusCode() == 401) {
                throw new InvalidAuthTokenException();
            }
            return response.getResponseBodyAsString();
        }
    }

    @Override
    public void destroy() throws Exception
    {
        executorService.shutdown();
    }
}