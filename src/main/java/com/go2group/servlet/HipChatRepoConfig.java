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
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.repository.RepositoryService;
import com.atlassian.templaterenderer.TemplateRenderer;
import com.go2group.hipchat.components.ConfigurationManager;
import com.go2group.hipchat.components.HipChatProxyClient;
import com.go2group.hipchat.utils.HipchatUserHelper;
import com.go2group.hipchat.utils.InvalidAuthTokenException;
import com.google.common.collect.Maps;

public class HipChatRepoConfig extends HttpServlet {
	private static final long serialVersionUID = -8408503006452822218L;

	private static final String CONFIG_VM = "templates/hipchat-repo-config.vm";

	private static final Logger log = LoggerFactory.getLogger(HipChatRepoConfig.class);

	private final TemplateRenderer templateRenderer;
	private final ConfigurationManager configurationManager;
	private final HipChatProxyClient hipChatProxyClient;
	private final RepositoryService repositoryService;
	private final HipchatUserHelper hipchatUserHelper;

	public HipChatRepoConfig(TemplateRenderer templateRenderer, ConfigurationManager configurationManager,
			HipChatProxyClient hipChatProxyClient, RepositoryService repositoryService, AuthenticationContext authenticationContext) {
		this.templateRenderer = templateRenderer;
		this.configurationManager = configurationManager;
		this.hipChatProxyClient = hipChatProxyClient;
		this.repositoryService = repositoryService;
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
		String repo = request.getParameter("repo");
		if (repo != null) {
			Repository repository = this.repositoryService.getById(Integer.parseInt(repo));
			context.put("repo", repo);
			String repoName = repository.getName();
			context.put("repository", repoName);
			context.put("repositorySlug", repository.getSlug());
			context.put("project", repository.getProject().getKey());
			context.put("roomsToNotifyStrHtml", configurationManager.getHipChatRepoRooms(repoName));
			String events = configurationManager.getHipChatRepoEvents(repoName);
			if (events != null) {
				context.put("events", Arrays.asList(events.split(",")));
			} else {
				context.put("events", new ArrayList<String>());
			}
			String notify = configurationManager.getHipChatRepoNotify(repoName);
			if (notify != null && notify.equals("true")) {
				context.put("notify", notify);
			}
			String exclusions = configurationManager.getHipChatRepoExclusions(repoName);
			if (!StringUtils.isEmpty(exclusions)) {
				context.put("exclusions", exclusions);
			}
		} else {
			context.put("roomsToNotifyStrHtml", configurationManager.getHipChatRepoRooms("global.g2g.config"));
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
		String repo = request.getParameter("repo");
		String rooms = StringUtils.join(request.getParameterValues("roomId"), ",");
		String events = StringUtils.join(request.getParameterValues("event"), ",");
		if (repo != null) {
			Repository repository = this.repositoryService.getById(new Integer(repo));
			String repoName = repository.getName();
			configurationManager.setRepoRooms(repoName, rooms);
			configurationManager.setRepoEvents(repoName, events);
			String notify = request.getParameter("notify");
			if (notify != null && notify.equals("true")) {
				configurationManager.setRepoNotify(repoName, notify);
			} else {
				configurationManager.setRepoNotify(repoName, "false");
			}
			String exclusions = request.getParameter("exclusions");
			if (!StringUtils.isEmpty(exclusions)) {
				configurationManager.setRepoExclusions(repoName, exclusions);
			}
		} else {
			configurationManager.setRepoRooms("global.g2g.config", rooms);
		}
		generateResponse(request, response);
	}

}