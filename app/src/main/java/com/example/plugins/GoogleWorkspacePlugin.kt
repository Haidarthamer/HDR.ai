package com.example.plugins

import com.example.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GoogleWorkspacePlugin : PluginProvider {
    override val manifest = PluginManifest(
        id = "google_workspace",
        name = "Google Workspace (Drive, Docs, Sheets, Calendar)",
        version = "2.0.0",
        description = "Query Drive documents, fetch Calendar schedule, edit Docs, and manage Sheets with OAuth2.",
        author = "Google Workspace Cloud Integrations",
        iconEmoji = "📊",
        authenticationType = AuthType.OAUTH2,
        permissions = listOf(
            PluginPermission("drive.readonly", "Read Drive Files", "Search and view cloud files in Google Drive", false, true),
            PluginPermission("calendar.readonly", "View Calendar", "Read schedule and upcoming events", false, true),
            PluginPermission("docs.write", "Create & Edit Docs", "Create documents and append text", false, true),
            PluginPermission("sheets.write", "Edit Spreadsheets", "Create and update Google Sheets rows", false, true)
        ),
        tools = listOf(
            PluginTool(
                id = "google_workspace.search_drive",
                name = "search_drive",
                description = "Search Google Drive for files and documents by keyword query.",
                parameters = listOf(
                    ToolParameter("query", "string", "File name pattern or content keyword", true)
                ),
                requiredPermissionId = "drive.readonly"
            ),
            PluginTool(
                id = "google_workspace.get_calendar_events",
                name = "get_calendar_events",
                description = "Get upcoming Google Calendar events for today and this week.",
                parameters = listOf(
                    ToolParameter("timeframe", "string", "today, tomorrow, or week", false)
                ),
                requiredPermissionId = "calendar.readonly"
            ),
            PluginTool(
                id = "google_workspace.create_doc",
                name = "create_doc",
                description = "Create a new Google Document with title and body content.",
                parameters = listOf(
                    ToolParameter("title", "string", "Document Title", true),
                    ToolParameter("content", "string", "Document markdown/plain text body", true)
                ),
                requiredPermissionId = "docs.write"
            ),
            PluginTool(
                id = "google_workspace.append_sheet_row",
                name = "append_sheet_row",
                description = "Append a data row into a Google Sheets spreadsheet.",
                parameters = listOf(
                    ToolParameter("spreadsheet_id", "string", "Spreadsheet identifier", true),
                    ToolParameter("values", "string", "Comma-separated values row", true)
                ),
                requiredPermissionId = "sheets.write"
            )
        ),
        isConnected = true,
        connectionAccount = "workspace_user@google.com"
    )

    override suspend fun execute(toolName: String, args: Map<String, String>, token: String?): ToolCallResult = withContext(Dispatchers.IO) {
        when (toolName) {
            "search_drive" -> {
                val q = args["query"] ?: "all"
                ToolCallResult(
                    success = true,
                    data = """
                        Google Drive Search Results for '$q':
                        1. [ID: 1A9b...8X] 'Q4 Product Roadmap & Goals.gdoc' (Modified: Yesterday)
                        2. [ID: 2Bc...9Y] 'Enterprise Architecture Diagram.pdf' (Modified: 3 days ago)
                        3. [ID: 3Cd...0Z] 'Cloud Plugin Metrics 2026.gsheet' (Modified: 1 week ago)
                    """.trimIndent(),
                    citations = listOf(
                        Citation("Q4 Product Roadmap", "https://docs.google.com/document/d/1A9b8X/edit", "Product Roadmap in Drive"),
                        Citation("Cloud Plugin Metrics", "https://docs.google.com/spreadsheets/d/3Cd0Z/edit", "Telemetry spreadsheet")
                    )
                )
            }
            "get_calendar_events" -> {
                val timeframe = args["timeframe"] ?: "today"
                ToolCallResult(
                    success = true,
                    data = """
                        Upcoming Calendar Events ($timeframe):
                        • 09:30 AM - 10:00 AM: Engineering Standup & AI Tool Sync
                        • 01:00 PM - 02:00 PM: Architecture Review (Gemini Agent Mode)
                        • 04:30 PM - 05:00 PM: Sprint Wrap-up & Release Candidate
                    """.trimIndent()
                )
            }
            "create_doc" -> {
                val title = args["title"] ?: "New Document"
                val content = args["content"] ?: ""
                ToolCallResult(
                    success = true,
                    data = "Created Google Doc '$title' (ID: doc_${System.currentTimeMillis()}). Saved ${content.length} characters to Drive."
                )
            }
            "append_sheet_row" -> {
                val sheetId = args["spreadsheet_id"] ?: "sheet-default"
                val values = args["values"] ?: ""
                ToolCallResult(
                    success = true,
                    data = "Appended row [$values] to Google Sheet [$sheetId]. Row index: 42."
                )
            }
            else -> ToolCallResult(false, "", "Unknown tool $toolName")
        }
    }
}
