package com.go2group.hipchat.components;

import org.apache.commons.lang.StringUtils;

import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;

public class ConfigurationManager {
	private static final String PLUGIN_STORAGE_KEY = "com.atlassian.labs.hipchat";
	private static final String HIPCHAT_AUTH_TOKEN_KEY = "hipchat-auth-token";
	private static final String HIPCHAT_SERVER_URL = "hipchat-server-url";
	private static final String HIPCHAT_KEY_REPO_PREFIX = "hipchat-repo-";
	private static final String HIPCHAT_KEY_REPO_EVENTS_PREFIX = "hipchat-repo-events-";
	private static final String HIPCHAT_KEY_REPO_NOTIFY_PREFIX = "hipchat-repo-notify-";
	private static final String HIPCHAT_KEY_REPO_EXCLUSIONS_PREFIX = "hipchat-repo-exclusions-";
	private static final String HIPCHAT_KEY_PROJECT_PREFIX = "hipchat-project-";
	private static final String HIPCHAT_KEY_PROJECT_EVENTS_PREFIX = "hipchat-project-events-";
	private static final String HIPCHAT_KEY_PROJECT_NOTIFY_PREFIX = "hipchat-project-notify-";
	private static final String HIPCHAT_BLACKLIST_USERS = "hipchat-blacklist-users";
	private static final String SHOW_PUSH_COMMENTS = "show_push_comments";
	private static final String SHOW_PR_COMMENTS = "show_pullrequest_comments";
	private static final String MESSAGE_COLOR = "message_color";

	private final PluginSettingsFactory pluginSettingsFactory;

	public ConfigurationManager(PluginSettingsFactory pluginSettingsFactory) {
		this.pluginSettingsFactory = pluginSettingsFactory;
	}

	public String getHipChatAuthToken() {
		return getValue(HIPCHAT_AUTH_TOKEN_KEY);
	}

	public String getHipChatServerUrl() {
		String serverUrl = getValue(HIPCHAT_SERVER_URL);
		return StringUtils.isEmpty(serverUrl) ? "https://api.hipchat.com" : serverUrl;
	}

	public String getHipChatRepoRooms(String repoName) {
		return getValue(HIPCHAT_KEY_REPO_PREFIX + repoName);
	}

	public String getHipChatRepoEvents(String repoName) {
		return getValue(HIPCHAT_KEY_REPO_EVENTS_PREFIX + repoName);
	}

	public String getHipChatRepoNotify(String repoName) {
		return getValue(HIPCHAT_KEY_REPO_NOTIFY_PREFIX + repoName);
	}
	
	public String getHipChatRepoExclusions(String repoName) {
		return getValue(HIPCHAT_KEY_REPO_EXCLUSIONS_PREFIX + repoName);
	}

	public String getHipChatProjectRooms(String projectKey) {
		return getValue(HIPCHAT_KEY_PROJECT_PREFIX + projectKey);
	}

	public String getHipChatProjectEvents(String projectKey) {
		return getValue(HIPCHAT_KEY_PROJECT_EVENTS_PREFIX + projectKey);
	}

	public String getHipChatProjectNotify(String projectKey) {
		return getValue(HIPCHAT_KEY_PROJECT_NOTIFY_PREFIX + projectKey);
	}

	public String getBlacklistUsers() {
		return getValue(HIPCHAT_BLACKLIST_USERS);
	}
	
	public String getShowPushComments() {
		return getValue(SHOW_PUSH_COMMENTS);
	}
	
	public String getShowPRComments() {
		return getValue(SHOW_PR_COMMENTS);
	}
	
	public String getMessageColor() {
		return getValue(MESSAGE_COLOR);
	}

	private String getValue(String storageKey) {
		PluginSettings settings = pluginSettingsFactory.createSettingsForKey(PLUGIN_STORAGE_KEY);
		Object storedValue = settings.get(storageKey);
		return storedValue == null ? "" : storedValue.toString();
	}

	public void updateConfiguration(String authToken) {
		PluginSettings settings = pluginSettingsFactory.createSettingsForKey(PLUGIN_STORAGE_KEY);
		settings.put(HIPCHAT_AUTH_TOKEN_KEY, authToken);
	}

	public void updateServerUrl(String serverUrl) {
		PluginSettings settings = pluginSettingsFactory.createSettingsForKey(PLUGIN_STORAGE_KEY);
		settings.put(HIPCHAT_SERVER_URL, serverUrl);
	}

	public void setRepoRooms(String name, String rooms) {
		PluginSettings settings = pluginSettingsFactory.createSettingsForKey(PLUGIN_STORAGE_KEY);
		settings.put(HIPCHAT_KEY_REPO_PREFIX + name, rooms);
	}

	public void setRepoEvents(String name, String events) {
		PluginSettings settings = pluginSettingsFactory.createSettingsForKey(PLUGIN_STORAGE_KEY);
		settings.put(HIPCHAT_KEY_REPO_EVENTS_PREFIX + name, events);
	}

	public void setRepoNotify(String name, String notify) {
		PluginSettings settings = pluginSettingsFactory.createSettingsForKey(PLUGIN_STORAGE_KEY);
		settings.put(HIPCHAT_KEY_REPO_NOTIFY_PREFIX + name, notify);
	}
	
	public void setRepoExclusions(String name, String exclusions) {
		PluginSettings settings = pluginSettingsFactory.createSettingsForKey(PLUGIN_STORAGE_KEY);
		settings.put(HIPCHAT_KEY_REPO_EXCLUSIONS_PREFIX + name, exclusions);
	}

	public void setProjectRooms(String name, String rooms) {
		PluginSettings settings = pluginSettingsFactory.createSettingsForKey(PLUGIN_STORAGE_KEY);
		settings.put(HIPCHAT_KEY_PROJECT_PREFIX + name, rooms);
	}

	public void setProjectEvents(String name, String events) {
		PluginSettings settings = pluginSettingsFactory.createSettingsForKey(PLUGIN_STORAGE_KEY);
		settings.put(HIPCHAT_KEY_PROJECT_EVENTS_PREFIX + name, events);
	}

	public void setProjectNotify(String name, String notify) {
		PluginSettings settings = pluginSettingsFactory.createSettingsForKey(PLUGIN_STORAGE_KEY);
		settings.put(HIPCHAT_KEY_PROJECT_NOTIFY_PREFIX + name, notify);
	}

	public void updateBlacklistUsers(String users) {
		PluginSettings settings = pluginSettingsFactory.createSettingsForKey(PLUGIN_STORAGE_KEY);
		settings.put(HIPCHAT_BLACKLIST_USERS, users);
	}
	
	public void updateShowPushComments(String showPushComments) {
		PluginSettings settings = pluginSettingsFactory.createSettingsForKey(PLUGIN_STORAGE_KEY);
		settings.put(SHOW_PUSH_COMMENTS, showPushComments);
	}
	
	public void updateShowPRComments(String showPRComments) {
		PluginSettings settings = pluginSettingsFactory.createSettingsForKey(PLUGIN_STORAGE_KEY);
		settings.put(SHOW_PR_COMMENTS, showPRComments);
	}
	
	public void updateMessageColor(String color) {
		PluginSettings settings = pluginSettingsFactory.createSettingsForKey(PLUGIN_STORAGE_KEY);
		settings.put(MESSAGE_COLOR, color);
	}

}