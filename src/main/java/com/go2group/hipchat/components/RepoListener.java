package com.go2group.hipchat.components;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.StringTokenizer;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import com.atlassian.bitbucket.comment.Comment;
import com.atlassian.bitbucket.commit.Commit;
import com.atlassian.bitbucket.commit.CommitService;
import com.atlassian.bitbucket.commit.CommitsBetweenRequest;
import com.atlassian.bitbucket.event.project.ProjectCreatedEvent;
import com.atlassian.bitbucket.event.project.ProjectDeletedEvent;
import com.atlassian.bitbucket.event.pull.PullRequestCommentDeletedEvent;
import com.atlassian.bitbucket.event.pull.PullRequestCommentEditedEvent;
import com.atlassian.bitbucket.event.pull.PullRequestCommentEvent;
import com.atlassian.bitbucket.event.pull.PullRequestCommentRepliedEvent;
import com.atlassian.bitbucket.event.pull.PullRequestDeclinedEvent;
import com.atlassian.bitbucket.event.pull.PullRequestEvent;
import com.atlassian.bitbucket.event.pull.PullRequestMergedEvent;
import com.atlassian.bitbucket.event.pull.PullRequestOpenedEvent;
import com.atlassian.bitbucket.event.pull.PullRequestParticipantApprovedEvent;
import com.atlassian.bitbucket.event.pull.PullRequestParticipantsUpdatedEvent;
import com.atlassian.bitbucket.event.pull.PullRequestReopenedEvent;
import com.atlassian.bitbucket.event.pull.PullRequestRescopedEvent;
import com.atlassian.bitbucket.event.pull.PullRequestRolesUpdatedEvent;
import com.atlassian.bitbucket.event.pull.PullRequestUpdatedEvent;
import com.atlassian.bitbucket.event.repository.RepositoryCreatedEvent;
import com.atlassian.bitbucket.event.repository.RepositoryDeletedEvent;
import com.atlassian.bitbucket.event.repository.RepositoryPullEvent;
import com.atlassian.bitbucket.event.repository.RepositoryPushEvent;
import com.atlassian.bitbucket.project.Project;
import com.atlassian.bitbucket.pull.PullRequest;
import com.atlassian.bitbucket.pull.PullRequestAction;
import com.atlassian.bitbucket.repository.RefChange;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.user.ApplicationUser;
import com.atlassian.bitbucket.util.Page;
import com.atlassian.bitbucket.util.PageUtils;
import com.atlassian.event.api.EventListener;
import com.atlassian.event.api.EventPublisher;
import com.atlassian.sal.api.ApplicationProperties;

public class RepoListener implements DisposableBean, InitializingBean {

	private static final String COMMA = ",";
	private static final String ANONYMOUS = "Anonymous";
	private static final String REPO = "repo";
	private static final String GLOBAL = "global";
	private static final String PROJECT = "project";
	private static final String GLOBAL_G2G_CONFIG = "global.g2g.config";
	private static final String REFS_HEADS = "refs/heads/";

	private static final Logger log = LoggerFactory.getLogger(RepoListener.class);

	private final EventPublisher eventPublisher;
	private final ConfigurationManager configurationManager;
	private final HipChatProxyClient hipChatProxyClient;
	private final ApplicationProperties applicationProperties;
	private final CommitService commitService;

	public RepoListener(EventPublisher eventPublisher, ConfigurationManager configurationManager,
			HipChatProxyClient hipChatProxyClient, ApplicationProperties applicationProperties,
			CommitService commitService) {
		this.eventPublisher = eventPublisher;
		this.configurationManager = configurationManager;
		this.hipChatProxyClient = hipChatProxyClient;
		this.applicationProperties = applicationProperties;
		this.commitService = commitService;
	}

	@EventListener
	public void onPull(RepositoryPullEvent event) {
		Repository repository = event.getRepository();
		ApplicationUser user = event.getUser();
		String message = new StringBuffer("<a href=\"").append(applicationProperties.getBaseUrl()).append("/users/")
				.append(user != null ? user.getName() : ANONYMOUS).append("\">")
				.append(user != null ? user.getDisplayName() : ANONYMOUS).append("</a> pulled from <a href=\"")
				.append(applicationProperties.getBaseUrl()).append("/projects/")
				.append(repository.getProject().getKey()).append("/repos/").append(repository.getSlug())
				.append("/browse\">").append(repository.getName()).append("</a>.").toString();
		notifyRooms(REPO, "Pull", repository.getName(), repository.getProject(), message, user);
	}

	@EventListener
	public void onPush(RepositoryPushEvent event) {
		Repository repository = event.getRepository();
		Collection<RefChange> changes = event.getRefChanges();

		ApplicationUser user = event.getUser();
		StringBuffer changeString = new StringBuffer("");
		boolean hasValidBranches = false;
		for (RefChange change : changes) {
			String refId = change.getRef().getId();
			if (refId.startsWith(REFS_HEADS)) {
				refId = refId.substring(11); // Extract branch name
				if (isValidRef(refId, repository.getName())) {
					hasValidBranches = true;
					changeString.append("<br>Commits to <b>" + refId + "</b>:<ul>");
					final CommitsBetweenRequest request = new CommitsBetweenRequest.Builder(repository)
							.exclude(change.getFromHash()).include(change.getToHash()).build();
					final Page<Commit> commits = commitService.getCommitsBetween(request,
							PageUtils.newRequest(0, 9999));
					for (Commit commit : commits.getValues()) {
						changeString.append("<li><a href=\"").append(applicationProperties.getBaseUrl())
								.append("/projects/").append(repository.getProject().getKey()).append("/repos/")
								.append(repository.getSlug()).append("/commits/").append(commit.getId())
								.append("\">").append(commit.getDisplayId()).append("</a>");
						if ("Yes".equals(configurationManager.getShowPushComments())) {
							changeString = changeString.append("<br><code>").append(commit.getMessage())
									.append("</code>");
						}
						changeString.append("</li>");
					}
					changeString.append("</ul>");
				}
			}
		}
		if (hasValidBranches) {
			String message = new StringBuffer("<a href=\"").append(applicationProperties.getBaseUrl())
					.append("/users/").append(user != null ? user.getName() : ANONYMOUS).append("\">")
					.append(user != null ? user.getDisplayName() : ANONYMOUS)
					.append("</a> pushed following changes to <a href=\"").append(applicationProperties.getBaseUrl())
					.append("/projects/").append(repository.getProject().getKey()).append("/repos/")
					.append(repository.getSlug()).append("/browse\">").append(repository.getName()).append("</a>.<br>")
					.append(changeString).toString();
			notifyRooms(REPO, "Push", repository.getName(), repository.getProject(), message, event.getUser());
		}
	}

	private boolean isValidRef(String refId, String repoName) {
		String exclusions = configurationManager.getHipChatRepoExclusions(repoName);
		if (!StringUtils.isEmpty(exclusions)) {
			String[] patterns = exclusions.split(COMMA);
			for (String pattern : patterns) {
				if (refId.matches(pattern)) {
					return false;
				}
			}
		}
		return true;
	}

	@EventListener
	public void onPullRequest(PullRequestOpenedEvent event) {
		String action = event.getAction().name();
		ApplicationUser user = event.getUser();
		notifyPullRequest(event, action, user, "PullR-O");
	}

	@EventListener
	public void onPullRequest(PullRequestParticipantApprovedEvent event) {
		String action = event.getAction().name();
		ApplicationUser user = event.getUser();
		notifyPullRequest(event, action, user, "PullR-A");
	}

	@EventListener
	public void onPullRequest(PullRequestDeclinedEvent event) {
		String action = event.getAction().name();
		ApplicationUser user = event.getUser();
		notifyPullRequest(event, action, user, "PullR-D");
	}

	@EventListener
	public void onPullRequest(PullRequestMergedEvent event) {
		String action = event.getAction().name();
		ApplicationUser user = event.getUser();
		notifyPullRequest(event, action, user, "PullR-M");
	}

	@EventListener
	public void onPullRequest(PullRequestParticipantsUpdatedEvent event) {
		String action = event.getAction().name();
		ApplicationUser user = event.getUser();
		notifyPullRequest(event, action, user, "PullR-PU");
	}

	@EventListener
	public void onPullRequest(PullRequestRolesUpdatedEvent event) {
		String action = event.getAction().name();
		ApplicationUser user = event.getUser();
		notifyPullRequest(event, action, user, "PullR-RU");
	}

	@EventListener
	public void onPullRequest(PullRequestReopenedEvent event) {
		String action = event.getAction().name();
		ApplicationUser user = event.getUser();
		notifyPullRequest(event, action, user, "PullR-RO");
	}

	@EventListener
	public void onPullRequest(PullRequestRescopedEvent event) {
		String action = event.getAction().name();
		ApplicationUser user = event.getUser();
		notifyPullRequest(event, action, user, "PullR-RS");
	}

	@EventListener
	public void onPullRequest(PullRequestUpdatedEvent event) {
		String action = event.getAction().name();
		ApplicationUser user = event.getUser();
		notifyPullRequest(event, action, user, "PullR-U");
	}

	@EventListener
	public void onPullRequest(PullRequestCommentEvent event) {
		String action = event.getAction().name();
		ApplicationUser user = event.getUser();
		if (action.equals(PullRequestAction.COMMENTED.name())) { // Make it
																	// Commented
																	// on
			PullRequestCommentEvent commentEvent = (PullRequestCommentEvent) event;
			Comment comment = commentEvent.getComment();
			PullRequest pullRequest = commentEvent.getPullRequest();
			Repository repo = pullRequest.getToRef().getRepository();
			StringBuffer repoUrl = new StringBuffer(applicationProperties.getBaseUrl()).append("/projects/")
					.append(repo.getProject().getKey()).append("/repos/").append(repo.getSlug());
			String actionName = null;
			// Add comment name with a link to the comment, where possible
			if (commentEvent instanceof PullRequestCommentDeletedEvent) {
				actionName = "Deleted COMMENT";
			} else if (commentEvent instanceof PullRequestCommentEditedEvent) {
				actionName = new StringBuffer("MODIFIED <a href=\"").append(repoUrl).append("/pull-requests/")
						.append(pullRequest.getId()).append("/overview?commentId=").append(comment.getId())
						.append("\">COMMENT</a> to").toString();
			} else if (commentEvent instanceof PullRequestCommentRepliedEvent) {
				actionName = new StringBuffer("<a href=\"").append(repoUrl).append("/pull-requests/")
						.append(pullRequest.getId()).append("/overview?commentId=").append(comment.getId())
						.append("\">REPLIED</a>").toString();
			} else { // Added
				actionName = new StringBuffer("<a href=\"").append(repoUrl).append("/pull-requests/")
						.append(pullRequest.getId()).append("/overview?commentId=").append(comment.getId())
						.append("\">").append(action).append("</a>").toString();
			}
			if ("Yes".equals(configurationManager.getShowPRComments())) {
				action = actionName + "<br><code>" + comment.getText() + "</code><br> on";
			} else {
				action = actionName + " on";
			}
		}
		notifyPullRequest(event, action, user, "PullR-C");
	}

	private void notifyPullRequest(PullRequestEvent event, String action, ApplicationUser user, String eventType) {
		PullRequest pullRequest = event.getPullRequest();

		if (!pullRequest.isCrossRepository()) {
			Repository repository = pullRequest.getFromRef().getRepository();
			String fromBranch = pullRequest.getFromRef().getDisplayId();
			String toBranch = pullRequest.getToRef().getDisplayId();
			if (isValidRef(fromBranch, repository.getName()) || isValidRef(toBranch, repository.getName())) {
				Project project = repository.getProject();
				StringBuffer repoUrl = new StringBuffer(applicationProperties.getBaseUrl()).append("/projects/")
						.append(project.getKey()).append("/repos/").append(repository.getSlug());
				String message = new StringBuffer("<a href=\"").append(applicationProperties.getBaseUrl())
						.append(user != null ? user.getName() : ANONYMOUS).append("\">")
						.append(user != null ? user.getDisplayName() : ANONYMOUS).append("</a> ").append(action)
						.append(" Pull Request: <a href=\"").append(repoUrl).append("/pull-requests/")
						.append(pullRequest.getId()).append("\">").append(pullRequest.getTitle())
						.append("</a> in <a href=\"").append(repoUrl).append("/browse\">")
						.append(repository.getName()).append("</a>.").toString();
				notifyRooms(REPO, eventType, repository.getName(), repository.getProject(), message,
						event.getUser());
			}
		} else {
			Repository fromRepo = pullRequest.getFromRef().getRepository();
			Repository toRepo = pullRequest.getToRef().getRepository();
			String fromBranch = pullRequest.getFromRef().getDisplayId();
			String toBranch = pullRequest.getToRef().getDisplayId();

			StringBuffer toRepoUrl = new StringBuffer(applicationProperties.getBaseUrl()).append("/projects/")
					.append(toRepo.getProject().getKey()).append("/repos/").append(toRepo.getSlug());
			StringBuffer fromRepoUrl = new StringBuffer(applicationProperties.getBaseUrl()).append("/projects/")
					.append(fromRepo.getProject().getKey()).append("/repos/").append(fromRepo.getSlug());
			StringBuffer message = new StringBuffer("<a href=\"").append(applicationProperties.getBaseUrl())
					.append("/users/").append(event.getUser().getName()).append("\">")
					.append(event.getUser().getDisplayName()).append("</a> ").append(action)
					.append(" Pull Request: <a href=\"").append(toRepoUrl).append("/pull-requests/")
					.append(pullRequest.getId()).append("\">").append(pullRequest.getTitle()).append("</a>");
			// Send message to source repo
			if (isValidRef(fromBranch, fromRepo.getName())) {
				String fromMessage = new StringBuffer(message).append(" from <a href=\"").append(fromRepoUrl)
						.append("/browse\">").append(fromRepo.getName()).append("</a>.").toString();
				notifyRooms(REPO, eventType, fromRepo.getName(), fromRepo.getProject(), fromMessage, event.getUser());
			}
			// Send message to destination repo
			if (isValidRef(toBranch, toRepo.getName())) {
				String toMessage = new StringBuffer(message).append(" to <a href=\"").append(toRepoUrl)
						.append("/browse\">").append(toRepo.getName()).append("</a>.").toString();
				notifyRooms(REPO, eventType, toRepo.getName(), toRepo.getProject(), toMessage, event.getUser());
			}
		}
	}

	@EventListener
	public void onRepoCreate(RepositoryCreatedEvent event) {
		Repository repository = event.getRepository();
		Project project = repository.getProject();
		ApplicationUser user = event.getUser();
		String message = new StringBuffer("<a href=\"").append(applicationProperties.getBaseUrl()).append("/users/")
				.append(user != null ? user.getName() : ANONYMOUS).append("\">")
				.append(user != null ? user.getDisplayName() : ANONYMOUS)
				.append("</a> created new repository: <a href=\"").append(applicationProperties.getBaseUrl())
				.append("/projects/").append(project.getKey()).append("/repos/").append(repository.getSlug())
				.append("/browse\">").append(repository.getName()).append("</a>.").toString();
		notifyRooms(PROJECT, "RepoC", project.getKey(), project, message, event.getUser());
	}

	@EventListener
	public void onRepoDelete(RepositoryDeletedEvent event) {
		Repository repository = event.getRepository();
		Project project = repository.getProject();
		ApplicationUser user = event.getUser();
		String message = new StringBuffer("<a href=\"").append(applicationProperties.getBaseUrl()).append("/users/")
				.append(user != null ? user.getName() : ANONYMOUS).append("\">")
				.append(user != null ? user.getDisplayName() : ANONYMOUS).append("</a> deleted repository: <b>")
				.append(repository.getName()).append("</b>").toString();
		notifyRooms(PROJECT, "RepoD", project.getKey(), project, message, event.getUser());
	}

	@EventListener
	public void onProjectCreate(ProjectCreatedEvent event) {
		Project project = event.getProject();
		String message = new StringBuffer("<a href=\"").append(applicationProperties.getBaseUrl()).append("/users/")
				.append(event.getUser().getName()).append("\">").append(event.getUser().getDisplayName())
				.append("</a> created new project: <a href=\"").append(applicationProperties.getBaseUrl())
				.append("/projects/").append(project.getKey()).append("\">").append(project.getName()).append("</a>.")
				.toString();
		notifyRooms(GLOBAL, null, project.getKey(), project, message, event.getUser());
	}

	@EventListener
	public void onProjectDelete(ProjectDeletedEvent event) {
		Project project = event.getProject();
		ApplicationUser user = event.getUser();
		String message = new StringBuffer("<a href=\"").append(applicationProperties.getBaseUrl()).append("/users/")
				.append(user != null ? user.getName() : ANONYMOUS).append("\">")
				.append(user != null ? user.getDisplayName() : ANONYMOUS).append("</a> deleted project: <b>")
				.append(project.getName()).append("</b>").toString();
		notifyRooms(GLOBAL, null, project.getKey(), project, message, event.getUser());
	}

	private void notifyRooms(String eventType, String event, String key, Project project, String message, ApplicationUser user) {
		String blackListedUsers = configurationManager.getBlacklistUsers();
		List<String> users = new ArrayList<String>();
		if (!StringUtils.isEmpty(blackListedUsers)) {
			String[] selectedUsers = blackListedUsers.split(COMMA);
			for (String selectedUser : selectedUsers) {
				users.add(selectedUser);
			}
		}

		if (user == null || !users.contains(user.getName())) {
			String roomsToNotify = "";
			String events = "";
			String notify = "false";

			if (eventType.equals(GLOBAL)) { // Global Event
				roomsToNotify = configurationManager.getHipChatRepoRooms(GLOBAL_G2G_CONFIG);
			} else if (eventType.equals(PROJECT)) { // Project Event
				roomsToNotify = configurationManager.getHipChatProjectRooms(key);
				events = configurationManager.getHipChatProjectEvents(key);
				notify = configurationManager.getHipChatProjectNotify(key);
			} else if (eventType.equals(REPO)) { // Repo Event
				// Get repo config first
				roomsToNotify = configurationManager.getHipChatRepoRooms(key);
				events = configurationManager.getHipChatRepoEvents(key);
				// If no repo config, check project config
				if (StringUtils.isEmpty(roomsToNotify) && project != null) {
					roomsToNotify = configurationManager.getHipChatProjectRooms(project.getKey());
					events = configurationManager.getHipChatProjectEvents(project.getKey());
				}
				notify = configurationManager.getHipChatRepoNotify(key);
			}
			// Fire if the event is selected or if no events are configured
			if (isValidEvent(event, events)) {
				StringTokenizer rooms = new StringTokenizer(roomsToNotify, COMMA);

				while (rooms.hasMoreTokens()) {
					hipChatProxyClient.notifyRoom(rooms.nextToken(), message, null, "html", notify);
				}
			}
		}
	}

	private boolean isValidEvent(String event, String events) {
		if (events == null || StringUtils.isEmpty(events)) {
			return true;
		} else {
			// Check for pull requests first. If All pull requests are opted, we
			// do not need to check for granular events
			List<String> eventList = Arrays.asList(events.split(COMMA));
			if (eventList.contains("PullR") && event.startsWith("PullR")) {
				return true;
			}
			return eventList.contains(event);
		}
	}

	@Override
	public void destroy() throws Exception {
		log.debug("Unregister repository event listener");
		eventPublisher.unregister(this);
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		log.debug("Register repository event listener");
		eventPublisher.register(this);
	}

}
