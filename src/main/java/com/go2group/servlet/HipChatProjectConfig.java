package com.go2group.servlet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.bitbucket.auth.AuthenticationContext;
import com.atlassian.templaterenderer.TemplateRenderer;
import com.go2group.hipchat.components.ConfigurationManager;
import com.go2group.hipchat.components.HipChatProxyClient;
import com.go2group.hipchat.utils.HipchatUserHelper;
import com.go2group.hipchat.utils.InvalidAuthTokenException;
import com.google.common.collect.Maps;

public class HipChatProjectConfig extends HttpServlet {
	private static final long serialVersionUID = 8241202083977241521L;

	private static final String CONFIG_VM = "templates/hipchat-project-config.vm";

	private static final Logger log = LoggerFactory.getLogger(HipChatProjectConfig.class);

	private final TemplateRenderer templateRenderer;
	private final ConfigurationManager configurationManager;
	private final HipChatProxyClient hipChatProxyClient;
	private final HipchatUserHelper hipchatUserHelper;

	public HipChatProjectConfig(TemplateRenderer templateRenderer, ConfigurationManager configurationManager,
			HipChatProxyClient hipChatProxyClient, AuthenticationContext authenticationContext) {
		this.templateRenderer = templateRenderer;
		this.configurationManager = configurationManager;
		this.hipChatProxyClient = hipChatProxyClient;
		this.hipchatUserHelper = new HipchatUserHelper(hipChatProxyClient, authenticationContext);
	}

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		generateResponse(request, response);
	}

	private void generateResponse(HttpServletRequest request, HttpServletResponse response) throws IOException {
		response.setContentType("text/html; charset=utf-8");
		Map<String, Object> context = Maps.newHashMap();
		context.put("request", request);
		String projectKey = request.getParameter("project");
		if (projectKey != null) {
			context.put("projectKey", projectKey);
			context.put("roomsToNotifyStrHtml", configurationManager.getHipChatProjectRooms(projectKey));
			String events = configurationManager.getHipChatProjectEvents(projectKey);
			if (events != null) {
				context.put("events", Arrays.asList(events.split(",")));
			} else {
				context.put("events", new ArrayList<String>());
			}
			String notify = configurationManager.getHipChatProjectNotify(projectKey);
			if (notify != null && notify.equals("true")) {
				context.put("notify", notify);
			}
		}
		try {
			context.put("roomsJsonHtml", hipChatProxyClient.getRooms(this.configurationManager.getHipChatServerUrl(),
					this.configurationManager.getHipChatAuthToken()));
			context.put("hcOwnerUserId", hipchatUserHelper.getHipchatUserIdForLoggedInUser());
		} catch (InvalidAuthTokenException e) {
			e.printStackTrace();
		}
		this.templateRenderer.render(CONFIG_VM, context, response.getWriter());
	}

	@Override
	protected final void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException,
			IOException {
		String projectKey = request.getParameter("project");
		String rooms = StringUtils.join(request.getParameterValues("roomId"), ",");
		String events = StringUtils.join(request.getParameterValues("event"), ",");
		String notify = request.getParameter("notify");
		if (projectKey != null) {
			configurationManager.setProjectRooms(projectKey, rooms);
			configurationManager.setProjectEvents(projectKey, events);
			if (notify != null && notify.equals("true")) {
				configurationManager.setProjectNotify(projectKey, notify);
			} else {
				configurationManager.setProjectNotify(projectKey, "false");
			}
		}
		generateResponse(request, response);
	}

}