package com.go2group.servlet;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.sal.api.auth.LoginUriProvider;
import com.atlassian.sal.api.user.UserManager;
import com.atlassian.templaterenderer.TemplateRenderer;
import com.go2group.hipchat.components.ConfigurationManager;
import com.go2group.hipchat.components.HipChatProxyClient;
import com.go2group.hipchat.utils.InvalidAuthTokenException;
import com.google.common.collect.Maps;

public class HipChatConfig extends HttpServlet {
	private static final long serialVersionUID = -4874788544017342927L;
	private static final String HIPCHAT_SERVER_URL = "https://api.hipchat.com";
	private static final String CONFIG_VM = "templates/hipchat-config.vm";

	private static final Logger log = LoggerFactory.getLogger(HipChatConfig.class);

	private final TemplateRenderer templateRenderer;
	private final ConfigurationManager configurationManager;
	private final UserManager userManager;
	private final LoginUriProvider loginUriProvider;
	private final HipChatProxyClient hipChatProxyClient;

	public HipChatConfig(TemplateRenderer templateRenderer, ConfigurationManager configurationManager,
			UserManager userManager, LoginUriProvider loginUriProvider, HipChatProxyClient hipChatProxyClient) {
		this.templateRenderer = templateRenderer;
		this.configurationManager = configurationManager;
		this.userManager = userManager;
		this.loginUriProvider = loginUriProvider;
		this.hipChatProxyClient = hipChatProxyClient;
	}

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		generateResponse(request, response, true);
	}

	private void generateResponse(HttpServletRequest request, HttpServletResponse response, boolean success) throws IOException {
		response.setContentType("text/html; charset=utf-8");
		Map<String, Object> context = Maps.newHashMap();
		String hipChatAuthToken = configurationManager.getHipChatAuthToken();
		context.put("hipChatAuthToken", hipChatAuthToken);
		String serverUrl = configurationManager.getHipChatServerUrl();
		if (StringUtils.isEmpty(serverUrl)) {
			serverUrl = HIPCHAT_SERVER_URL;
		}
		context.put("serverUrl", serverUrl);
		String showPushComments = configurationManager.getShowPushComments();
		if ("Yes".equals(showPushComments)){
			context.put("showPushComments", showPushComments);
		}
		String showPRComments = configurationManager.getShowPRComments();
		if ("Yes".equals(showPRComments)){
			context.put("showPRComments", showPRComments);
		}
		String color = configurationManager.getMessageColor();
		if (StringUtils.isEmpty(color)) {
			color = "yellow";
		}
		context.put("color", color);
		if (hipChatAuthToken != null && !hipChatAuthToken.trim().isEmpty()) {
			context.put("showGlobalConfig", success);
			context.put("request", request);
		}
		if (!success) {
			context.put("error", true);
		}
		this.templateRenderer.render(CONFIG_VM, context, response.getWriter());
	}

	private void redirectToLogin(HttpServletRequest request, HttpServletResponse response) throws IOException {
		response.sendRedirect(loginUriProvider.getLoginUri(getUri(request)).toASCIIString());
	}

	private URI getUri(HttpServletRequest request) {
		StringBuffer builder = request.getRequestURL();
		if (request.getQueryString() != null) {
			builder.append("?");
			builder.append(request.getQueryString());
		}
		return URI.create(builder.toString());
	}

	@Override
	protected final void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException,
			IOException {
		String username = userManager.getRemoteUsername(request);
		if (username == null || !userManager.isAdmin(username)) {
			redirectToLogin(request, response);
			return;
		}

		String authToken = request.getParameter("hipChatAuthToken");
		String serverUrl = request.getParameter("serverUrl");
		String showPushComments = request.getParameter("showPushComments");
		String showPRComments = request.getParameter("showPRComments");
		String color = request.getParameter("color");
		try {
			this.hipChatProxyClient.getRooms(serverUrl, authToken);
			//Valid token
			configurationManager.updateConfiguration(authToken);
			configurationManager.updateServerUrl(serverUrl);
			configurationManager.updateShowPushComments(showPushComments);
			configurationManager.updateShowPRComments(showPRComments);
			configurationManager.updateMessageColor(color);
			generateResponse(request, response, true);
		} catch (InvalidAuthTokenException authException) {
			authException.printStackTrace();
			generateResponse(request, response, false);
		}
		
	}

}