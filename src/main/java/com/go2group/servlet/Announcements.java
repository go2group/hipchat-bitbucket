package com.go2group.servlet;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.bitbucket.auth.AuthenticationContext;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.repository.RepositoryService;
import com.atlassian.bitbucket.util.Page;
import com.atlassian.bitbucket.util.PageRequestImpl;
import com.atlassian.templaterenderer.TemplateRenderer;
import com.go2group.hipchat.components.ConfigurationManager;
import com.go2group.hipchat.components.HipChatProxyClient;
import com.go2group.hipchat.components.HipChatProxyClient.JSONString;
import com.go2group.hipchat.utils.HipchatUserHelper;
import com.go2group.hipchat.utils.InvalidAuthTokenException;
import com.google.common.collect.Maps;

public class Announcements extends HttpServlet {
	private static final long serialVersionUID = 1797685896463550980L;

	private static final String CONFIG_VM = "templates/announcements.vm";

	private static final Logger log = LoggerFactory.getLogger(Announcements.class);

	private final TemplateRenderer templateRenderer;
	private final ConfigurationManager configurationManager;
	private final HipChatProxyClient hipChatProxyClient;
	private final RepositoryService repositoryService;
	private final HipchatUserHelper hipchatUserHelper;

	public Announcements(TemplateRenderer templateRenderer, HipChatProxyClient hipChatProxyClient,
			ConfigurationManager configurationManager, RepositoryService repositoryService, AuthenticationContext authenticationContext) {
		this.templateRenderer = templateRenderer;
		this.configurationManager = configurationManager;
		this.hipChatProxyClient = hipChatProxyClient;
		this.repositoryService = repositoryService;
		this.hipchatUserHelper = new HipchatUserHelper(hipChatProxyClient, authenticationContext);
	}

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		generateResponse(request, response, false);
	}

	private void generateResponse(HttpServletRequest request, HttpServletResponse response, boolean success)
			throws IOException {
		response.setContentType("text/html; charset=utf-8");
		Map<String, Object> context = Maps.newHashMap();
		context.put("request", request);
		try {
			context.put("roomsJsonHtml", hipChatProxyClient.getRooms(this.configurationManager.getHipChatServerUrl(),
					this.configurationManager.getHipChatAuthToken()));
			context.put("hcOwnerUserId", hipchatUserHelper.getHipchatUserIdForLoggedInUser());
			context.put("success", success);
		} catch (InvalidAuthTokenException e) {
			e.printStackTrace();
		}
		this.templateRenderer.render(CONFIG_VM, context, response.getWriter());
	}

	@Override
	protected final void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String message = req.getParameter("announcement");
		if (message != null) {
			String roomOption = req.getParameter("roomOption");
			String color = req.getParameter("color");
			String format = req.getParameter("format");
			String notify = req.getParameter("notify");
			if ("all".equals(roomOption)) {
				JSONString roomString = hipChatProxyClient.getRooms(this.configurationManager.getHipChatServerUrl(),
						this.configurationManager.getHipChatAuthToken());
				JSONObject rooms;
				try {
					rooms = new JSONObject(roomString.toString());
					if (rooms != null) {
						JSONArray roomArray = rooms.getJSONArray("rooms");
						for (int i = 0; i < roomArray.length(); i++) {
							JSONObject room = roomArray.getJSONObject(i);
							this.hipChatProxyClient.notifyRoom(room.getString("room_id"), message, color, format, notify);
						}
					}
				} catch (JSONException e) {
					log.error("Error parsing room json:" + roomString.toString(), e);
					e.printStackTrace();
				}
			} else if ("subscribed".equals(roomOption)) {
				Set<String> rooms = new HashSet<String>();
				Page<? extends Repository> repos = this.repositoryService.findAll(new PageRequestImpl(0,
						Integer.MAX_VALUE));
				for (Repository repo : repos.getValues()) {
					String roomsToNotify = this.configurationManager.getHipChatRepoRooms(repo.getName());
					StringTokenizer roomsForRepo = new StringTokenizer(roomsToNotify, ",");

					while (roomsForRepo.hasMoreTokens()) {
						rooms.add(roomsForRepo.nextToken());
					}
				}
				for (String room : rooms) {
					this.hipChatProxyClient.notifyRoom(room, message, color, format, notify);
				}
			} else {
				String[] rooms = req.getParameterValues("roomId");
				for (String room : rooms) {
					hipChatProxyClient.notifyRoom(room, message, color, format, notify);
				}
			}
		}
		generateResponse(req, resp, true);
	}

}