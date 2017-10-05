package com.go2group.servlet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.bitbucket.user.UserService;
import com.atlassian.templaterenderer.TemplateRenderer;
import com.go2group.hipchat.components.ConfigurationManager;
import com.google.common.collect.Maps;

public class BlacklistConfig extends HttpServlet {
	private static final long serialVersionUID = 4364895186762543255L;

	private static final String CONFIG_VM = "templates/blacklist-config.vm";

	private static final Logger log = LoggerFactory.getLogger(BlacklistConfig.class);

	private final TemplateRenderer templateRenderer;
	private final ConfigurationManager configurationManager;
	private final UserService userService;

	public BlacklistConfig(TemplateRenderer templateRenderer, ConfigurationManager configurationManager,
							UserService userService) {
		this.templateRenderer = templateRenderer;
		this.configurationManager = configurationManager;
		this.userService = userService;
	}

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		generateResponse(request, response, null);
	}

	private void generateResponse(HttpServletRequest request, HttpServletResponse response, List<String> errors)
			throws IOException {
		response.setContentType("text/html; charset=utf-8");
		Map<String, Object> context = Maps.newHashMap();
		context.put("request", request);
		if (errors != null && errors.size() > 0) {
			context.put("errors", errors);
		}
		String blackListedUsers = this.configurationManager.getBlacklistUsers();
		if (!StringUtils.isEmpty(blackListedUsers)) {
			String[] selectedUsers = blackListedUsers.split(",");
			context.put("selectedUsers", selectedUsers);
		}
		this.templateRenderer.render(CONFIG_VM, context, response.getWriter());
	}

	@Override
	protected final void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException,
			IOException {
		String[] users = request.getParameterValues("users");
		List<String> errors = new ArrayList<String>();
		List<String> validUsers = new ArrayList<String>();
		if (null != users && users.length > 0) {
			for (String user : users) {
				if (userService.getUserByName(user) == null) {
					errors.add("Invalid User:" + user);
				} else {
					validUsers.add(user);
				}
			}
		}
		if (validUsers.size() > 0) {
			this.configurationManager.updateBlacklistUsers(StringUtils.join(validUsers, ","));
		} else {
			this.configurationManager.updateBlacklistUsers(null);
		}
		generateResponse(request, response, errors);
	}

}