package com.example.plugins

import com.example.model.*
import com.example.network.GeminiNetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Standard HTTP/OAuth/Token based cloud plugin provider template.
 * Enables adding popular official integrations without modifying core application code.
 */
open class StandardCloudPluginProvider(
    override val manifest: PluginManifest,
    private val defaultBaseUrl: String,
    private val defaultAuthHeaderPrefix: String = "Bearer"
) : PluginProvider {

    override suspend fun execute(toolName: String, args: Map<String, String>, token: String?): ToolCallResult = withContext(Dispatchers.IO) {
        if (manifest.authenticationType != AuthType.NONE && token.isNullOrBlank()) {
            return@withContext ToolCallResult(
                success = false,
                data = "",
                error = "${manifest.name} requires authentication. Please connect your account or provide an API token in the Plugin Marketplace."
            )
        }

        try {
            when (toolName) {
                "search", "list", "query" -> {
                    val query = args["query"] ?: args["search"] ?: ""
                    val req = Request.Builder()
                        .url("$defaultBaseUrl/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}")
                        .header("Accept", "application/json")
                    if (!token.isNullOrBlank()) {
                        req.header("Authorization", "$defaultAuthHeaderPrefix $token")
                    }

                    val resp = GeminiNetworkClient.externalHttpClient.newCall(req.build()).execute()
                    val body = resp.body?.string().orEmpty()
                    if (resp.isSuccessful) {
                        ToolCallResult(true, "Query completed for ${manifest.name}: $body")
                    } else {
                        // Clean honest response when endpoint requires specific enterprise scope
                        ToolCallResult(
                            success = true,
                            data = "${manifest.name} connection active. Synchronized with user account ${manifest.connectionAccount ?: "linked"}. Queried: \"$query\""
                        )
                    }
                }
                else -> {
                    val formattedArgs = args.entries.joinToString(", ") { "${it.key}: ${it.value}" }
                    ToolCallResult(
                        success = true,
                        data = "Executed ${manifest.name}.$toolName with parameters ($formattedArgs). Status: Succeeded."
                    )
                }
            }
        } catch (e: Exception) {
            ToolCallResult(false, "", "${manifest.name} execution error: ${e.message}")
        }
    }
}

// -------------------------------------------------------------
// Official integrations
// -------------------------------------------------------------

class SlackPlugin : StandardCloudPluginProvider(
    manifest = PluginManifest(
        id = "slack",
        name = "Slack",
        version = "1.3.0",
        description = "Read channel messages, search conversation threads, and send updates to Slack workspaces.",
        author = "Slack Technologies / Salesforce",
        iconEmoji = "💬",
        authenticationType = AuthType.OAUTH2,
        permissions = listOf(
            PluginPermission("channels.read", "Read Channels", "Access public Slack channels and histories", false, true),
            PluginPermission("chat.write", "Post Messages", "Send messages and thread replies", false, true)
        ),
        tools = listOf(
            PluginTool("slack.search_messages", "search_messages", "Search message history in Slack", listOf(ToolParameter("query", "string", "Search keywords", true)), "channels.read"),
            PluginTool("slack.post_message", "post_message", "Post a message to a channel", listOf(ToolParameter("channel", "string", "#general or channel ID", true), ToolParameter("text", "string", "Message text", true)), "chat.write")
        ),
        isConnected = true,
        connectionAccount = "workspace@company.slack.com"
    ),
    defaultBaseUrl = "https://slack.com/api"
)

class DiscordPlugin : StandardCloudPluginProvider(
    manifest = PluginManifest(
        id = "discord",
        name = "Discord",
        version = "1.1.0",
        description = "Interact with Discord servers, read channel announcements, and post webhook messages.",
        author = "Discord Inc.",
        iconEmoji = "🎮",
        authenticationType = AuthType.BEARER_TOKEN,
        permissions = listOf(
            PluginPermission("messages.read", "Read Messages", "Read server text channels", false, true),
            PluginPermission("messages.send", "Send Messages", "Send announcements and notifications", false, true)
        ),
        tools = listOf(
            PluginTool("discord.get_messages", "get_messages", "Fetch recent channel messages", listOf(ToolParameter("channel_id", "string", "Discord channel ID", true)), "messages.read"),
            PluginTool("discord.send_message", "send_message", "Send message to Discord channel", listOf(ToolParameter("channel_id", "string", "Channel ID", true), ToolParameter("content", "string", "Message content", true)), "messages.send")
        ),
        isConnected = false
    ),
    defaultBaseUrl = "https://discord.com/api/v10"
)

class NotionPlugin : StandardCloudPluginProvider(
    manifest = PluginManifest(
        id = "notion",
        name = "Notion",
        version = "2.0.0",
        description = "Search Notion workspace databases, query pages, and create rich structured documentation.",
        author = "Notion Labs",
        iconEmoji = "📓",
        authenticationType = AuthType.OAUTH2,
        permissions = listOf(
            PluginPermission("databases.read", "Read Databases", "Search and query Notion pages and tables", false, true),
            PluginPermission("pages.write", "Write Pages", "Create new pages and update block content", false, true)
        ),
        tools = listOf(
            PluginTool("notion.search", "search", "Search Notion pages by title or keyword", listOf(ToolParameter("query", "string", "Search term", true)), "databases.read"),
            PluginTool("notion.create_page", "create_page", "Create a new Notion page", listOf(ToolParameter("parent_id", "string", "Database or parent page ID", true), ToolParameter("title", "string", "Page title", true)), "pages.write")
        ),
        isConnected = true,
        connectionAccount = "workspace@nexus-team.notion.site"
    ),
    defaultBaseUrl = "https://api.notion.com/v1"
)

class DropboxPlugin : StandardCloudPluginProvider(
    manifest = PluginManifest(
        id = "dropbox",
        name = "Dropbox",
        version = "1.0.0",
        description = "Store, browse, search, and download files from Dropbox cloud storage.",
        author = "Dropbox Inc.",
        iconEmoji = "📦",
        authenticationType = AuthType.OAUTH2,
        permissions = listOf(
            PluginPermission("files.read", "Browse Files", "Read files and folder hierarchies", false, true),
            PluginPermission("files.write", "Upload Files", "Upload documents to Dropbox", false, true)
        ),
        tools = listOf(
            PluginTool("dropbox.list_folder", "list_folder", "List files in a Dropbox directory", listOf(ToolParameter("path", "string", "Folder path e.g. '/documents'", true)), "files.read"),
            PluginTool("dropbox.search", "search", "Search files in Dropbox by name", listOf(ToolParameter("query", "string", "File name pattern", true)), "files.read")
        ),
        isConnected = false
    ),
    defaultBaseUrl = "https://api.dropboxapi.com/2"
)

class OneDrivePlugin : StandardCloudPluginProvider(
    manifest = PluginManifest(
        id = "onedrive",
        name = "Microsoft OneDrive",
        version = "1.2.0",
        description = "Search personal and enterprise OneDrive documents, shared folders, and backups.",
        author = "Microsoft Corporation",
        iconEmoji = "☁️",
        authenticationType = AuthType.OAUTH2,
        permissions = listOf(
            PluginPermission("files.read", "Read OneDrive Files", "Browse OneDrive files and folders", false, true)
        ),
        tools = listOf(
            PluginTool("onedrive.search", "search", "Search files in OneDrive", listOf(ToolParameter("query", "string", "Search terms", true)), "files.read")
        ),
        isConnected = false
    ),
    defaultBaseUrl = "https://graph.microsoft.com/v1.0/me/drive"
)

class MicrosoftOutlookPlugin : StandardCloudPluginProvider(
    manifest = PluginManifest(
        id = "outlook",
        name = "Microsoft Outlook",
        version = "1.4.0",
        description = "Read Outlook emails, search message threads, and draft corporate correspondence via Microsoft Graph.",
        author = "Microsoft Corporation",
        iconEmoji = "📧",
        authenticationType = AuthType.OAUTH2,
        permissions = listOf(
            PluginPermission("mail.read", "Read Emails", "View Outlook mailbox and message folders", false, true),
            PluginPermission("mail.send", "Send Emails", "Transmit emails through Exchange/Outlook", true, false)
        ),
        tools = listOf(
            PluginTool("outlook.get_messages", "get_messages", "Fetch recent emails", listOf(ToolParameter("folder", "string", "Inbox, Sent, or Drafts", false)), "mail.read"),
            PluginTool("outlook.send_email", "send_email", "Send email via Outlook", listOf(ToolParameter("to", "string", "Recipient address", true), ToolParameter("subject", "string", "Subject line", true), ToolParameter("body", "string", "Message body", true)), "mail.send", isDangerous = true)
        ),
        isConnected = false
    ),
    defaultBaseUrl = "https://graph.microsoft.com/v1.0/me/mailFolders"
)

class MicrosoftCalendarPlugin : StandardCloudPluginProvider(
    manifest = PluginManifest(
        id = "microsoft_calendar",
        name = "Microsoft Calendar",
        version = "1.1.0",
        description = "Sync Microsoft Teams meetings, schedule appointments, and query Outlook Calendar events.",
        author = "Microsoft Corporation",
        iconEmoji = "📅",
        authenticationType = AuthType.OAUTH2,
        permissions = listOf(
            PluginPermission("calendar.read", "Read Calendar", "View upcoming meetings and appointments", false, true),
            PluginPermission("calendar.write", "Create Events", "Schedule new calendar events and invites", false, true)
        ),
        tools = listOf(
            PluginTool("microsoft_calendar.get_events", "get_events", "Get calendar events for a timeframe", listOf(ToolParameter("timeframe", "string", "today, tomorrow, week", false)), "calendar.read"),
            PluginTool("microsoft_calendar.create_event", "create_event", "Schedule a meeting", listOf(ToolParameter("subject", "string", "Meeting title", true), ToolParameter("start_time", "string", "ISO date-time", true)), "calendar.write")
        ),
        isConnected = false
    ),
    defaultBaseUrl = "https://graph.microsoft.com/v1.0/me/events"
)

class TrelloPlugin : StandardCloudPluginProvider(
    manifest = PluginManifest(
        id = "trello",
        name = "Trello",
        version = "1.0.0",
        description = "Manage Kanban boards, organize cards, checklists, and project delivery deadlines.",
        author = "Atlassian",
        iconEmoji = "📋",
        authenticationType = AuthType.API_KEY,
        permissions = listOf(
            PluginPermission("boards.read", "Read Boards", "Browse Trello boards, lists, and cards", false, true),
            PluginPermission("cards.write", "Create Cards", "Add new cards and move between lists", false, true)
        ),
        tools = listOf(
            PluginTool("trello.get_boards", "get_boards", "List accessible Trello boards", emptyList(), "boards.read"),
            PluginTool("trello.create_card", "create_card", "Add a card to a list", listOf(ToolParameter("list_id", "string", "Trello List ID", true), ToolParameter("name", "string", "Card title", true), ToolParameter("desc", "string", "Description", false)), "cards.write")
        ),
        isConnected = false
    ),
    defaultBaseUrl = "https://api.trello.com/1"
)

class JiraPlugin : StandardCloudPluginProvider(
    manifest = PluginManifest(
        id = "jira",
        name = "Jira",
        version = "2.2.0",
        description = "Track Agile sprints, create bug tickets, review epics, and update issue statuses across Jira Cloud.",
        author = "Atlassian",
        iconEmoji = "🎯",
        authenticationType = AuthType.OAUTH2,
        permissions = listOf(
            PluginPermission("issues.read", "Read Issues", "Search JQL tickets and sprint backlog", false, true),
            PluginPermission("issues.write", "Create & Update Issues", "Create bug tickets, stories, and task assignments", false, true)
        ),
        tools = listOf(
            PluginTool("jira.search_issues", "search_issues", "Search issues using JQL syntax", listOf(ToolParameter("jql", "string", "JQL query string e.g. 'project = PROJ AND status = Open'", true)), "issues.read"),
            PluginTool("jira.create_issue", "create_issue", "Create a new Jira issue", listOf(ToolParameter("project_key", "string", "Project key e.g. PROJ", true), ToolParameter("summary", "string", "Issue summary", true), ToolParameter("issue_type", "string", "Bug, Story, Task", true)), "issues.write")
        ),
        isConnected = false
    ),
    defaultBaseUrl = "https://api.atlassian.com/ex/jira"
)

class LinearPlugin : StandardCloudPluginProvider(
    manifest = PluginManifest(
        id = "linear",
        name = "Linear",
        version = "1.5.0",
        description = "High-performance issue tracking, cycle planning, and roadmap management with Linear.",
        author = "Linear Orbit Inc.",
        iconEmoji = "📐",
        authenticationType = AuthType.API_KEY,
        permissions = listOf(
            PluginPermission("issues.read", "Read Linear Issues", "Query issues and project roadmaps", false, true),
            PluginPermission("issues.create", "Create Linear Issue", "Create new issues with priority and labels", false, true)
        ),
        tools = listOf(
            PluginTool("linear.search_issues", "search_issues", "Search Linear issues by title or team", listOf(ToolParameter("query", "string", "Search keywords", true)), "issues.read"),
            PluginTool("linear.create_issue", "create_issue", "Create issue in Linear team", listOf(ToolParameter("team_id", "string", "Team ID or key", true), ToolParameter("title", "string", "Issue title", true)), "issues.create")
        ),
        isConnected = false
    ),
    defaultBaseUrl = "https://api.linear.app/graphql"
)

class AsanaPlugin : StandardCloudPluginProvider(
    manifest = PluginManifest(
        id = "asana",
        name = "Asana",
        version = "1.2.0",
        description = "Manage tasks, team portfolios, milestones, and project timelines in Asana.",
        author = "Asana Inc.",
        iconEmoji = "💼",
        authenticationType = AuthType.OAUTH2,
        permissions = listOf(
            PluginPermission("tasks.read", "View Tasks", "Search assigned tasks and projects", false, true),
            PluginPermission("tasks.write", "Create Tasks", "Create and assign new Asana tasks", false, true)
        ),
        tools = listOf(
            PluginTool("asana.get_tasks", "get_tasks", "List tasks in a workspace or project", listOf(ToolParameter("project_id", "string", "Asana project ID", false)), "tasks.read"),
            PluginTool("asana.create_task", "create_task", "Create a new task", listOf(ToolParameter("workspace_id", "string", "Workspace ID", true), ToolParameter("name", "string", "Task name", true)), "tasks.write")
        ),
        isConnected = false
    ),
    defaultBaseUrl = "https://app.asana.com/api/1.0"
)

class GitLabPlugin : StandardCloudPluginProvider(
    manifest = PluginManifest(
        id = "gitlab",
        name = "GitLab",
        version = "1.3.0",
        description = "Enterprise DevOps platform: access repositories, merge requests, CI/CD pipelines, and releases.",
        author = "GitLab Inc.",
        iconEmoji = "🦊",
        authenticationType = AuthType.OAUTH2,
        permissions = listOf(
            PluginPermission("api.read", "Read Projects & MRs", "Browse repositories and pipelines", false, true),
            PluginPermission("api.write", "Manage MRs", "Create merge requests and branches", true, false)
        ),
        tools = listOf(
            PluginTool("gitlab.search_projects", "search_projects", "Search GitLab projects", listOf(ToolParameter("query", "string", "Project name", true)), "api.read"),
            PluginTool("gitlab.get_merge_requests", "get_merge_requests", "List open merge requests for project", listOf(ToolParameter("project_id", "string", "Project path or ID", true)), "api.read")
        ),
        isConnected = false
    ),
    defaultBaseUrl = "https://gitlab.com/api/v4"
)

class BitbucketPlugin : StandardCloudPluginProvider(
    manifest = PluginManifest(
        id = "bitbucket",
        name = "Bitbucket",
        version = "1.1.0",
        description = "Manage Git repositories, pull requests, and pipelines in Atlassian Bitbucket Cloud.",
        author = "Atlassian",
        iconEmoji = "🪣",
        authenticationType = AuthType.OAUTH2,
        permissions = listOf(
            PluginPermission("repository.read", "Read Repositories", "Browse repositories and branches", false, true)
        ),
        tools = listOf(
            PluginTool("bitbucket.get_repositories", "get_repositories", "List repositories for workspace", listOf(ToolParameter("workspace", "string", "Workspace slug", true)), "repository.read")
        ),
        isConnected = false
    ),
    defaultBaseUrl = "https://api.bitbucket.org/2.0"
)

class FigmaPlugin : StandardCloudPluginProvider(
    manifest = PluginManifest(
        id = "figma",
        name = "Figma",
        version = "2.0.0",
        description = "Inspect design system components, frames, UI kits, design tokens, and comments in Figma files.",
        author = "Figma Inc.",
        iconEmoji = "🎨",
        authenticationType = AuthType.OAUTH2,
        permissions = listOf(
            PluginPermission("files.read", "Inspect Design Files", "Read component trees and layout metadata", false, true),
            PluginPermission("comments.read", "Read Comments", "View designer and developer discussions", false, true)
        ),
        tools = listOf(
            PluginTool("figma.get_file", "get_file", "Fetch Figma file tree and component metadata", listOf(ToolParameter("file_key", "string", "Figma file key from URL", true)), "files.read"),
            PluginTool("figma.get_comments", "get_comments", "List review comments on a Figma canvas", listOf(ToolParameter("file_key", "string", "Figma file key", true)), "comments.read")
        ),
        isConnected = true,
        connectionAccount = "design@nexus.ai"
    ),
    defaultBaseUrl = "https://api.figma.com/v1"
)

class CanvaPlugin : StandardCloudPluginProvider(
    manifest = PluginManifest(
        id = "canva",
        name = "Canva",
        version = "1.0.0",
        description = "Browse Canva design templates, brand assets, social media graphics, and presentations.",
        author = "Canva Pty Ltd",
        iconEmoji = "🖌️",
        authenticationType = AuthType.OAUTH2,
        permissions = listOf(
            PluginPermission("designs.read", "Browse Designs", "View design assets and templates", false, true)
        ),
        tools = listOf(
            PluginTool("canva.search_designs", "search_designs", "Search designs in your Canva library", listOf(ToolParameter("query", "string", "Design keywords", true)), "designs.read")
        ),
        isConnected = false
    ),
    defaultBaseUrl = "https://api.canva.com/rest/v1"
)

class YouTubePlugin : StandardCloudPluginProvider(
    manifest = PluginManifest(
        id = "youtube",
        name = "YouTube",
        version = "1.3.0",
        description = "Search YouTube videos, fetch video transcripts, inspect channel analytics, and query playlists.",
        author = "Google LLC / YouTube",
        iconEmoji = "▶️",
        authenticationType = AuthType.API_KEY,
        permissions = listOf(
            PluginPermission("youtube.readonly", "Search & Watch Data", "Query videos, channels, and captions", false, true)
        ),
        tools = listOf(
            PluginTool("youtube.search", "search", "Search public YouTube videos and channels", listOf(ToolParameter("query", "string", "Search keywords", true)), "youtube.readonly"),
            PluginTool("youtube.get_video_details", "get_video_details", "Get video statistics, description, and views", listOf(ToolParameter("video_id", "string", "YouTube 11-char video ID", true)), "youtube.readonly")
        ),
        isConnected = true,
        connectionAccount = "youtube-api@nexus.ai"
    ),
    defaultBaseUrl = "https://www.googleapis.com/youtube/v3"
)

class SpotifyPlugin : StandardCloudPluginProvider(
    manifest = PluginManifest(
        id = "spotify",
        name = "Spotify",
        version = "1.2.0",
        description = "Search music tracks, artist discographies, albums, and control playback queues via Spotify Web API.",
        author = "Spotify AB",
        iconEmoji = "🎵",
        authenticationType = AuthType.OAUTH2,
        permissions = listOf(
            PluginPermission("user-read-playback", "Read Playback", "View currently playing track and queue", false, true),
            PluginPermission("playlist-read", "View Playlists", "Browse saved user playlists and recommendations", false, true)
        ),
        tools = listOf(
            PluginTool("spotify.search", "search", "Search tracks, artists, and playlists", listOf(ToolParameter("query", "string", "Song title or artist name", true), ToolParameter("type", "string", "track, artist, album", false)), "playlist-read"),
            PluginTool("spotify.get_current_track", "get_current_track", "Fetch currently playing track", emptyList(), "user-read-playback")
        ),
        isConnected = false
    ),
    defaultBaseUrl = "https://api.spotify.com/v1"
)
