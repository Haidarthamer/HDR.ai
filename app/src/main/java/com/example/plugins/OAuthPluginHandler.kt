package com.example.plugins

import android.content.Context
import android.content.Intent
import android.net.Uri

object OAuthPluginHandler {
    fun launchOAuthConsent(context: Context, pluginId: String, authUrl: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
        context.startActivity(intent)
    }

    fun buildGitHubOAuthUrl(clientId: String, redirectUri: String): String {
        val scope = "repo,read:user,user:email,workflow"
        return "https://github.com/login/oauth/authorize?client_id=$clientId&redirect_uri=$redirectUri&scope=$scope"
    }

    fun buildGoogleOAuthUrl(clientId: String, redirectUri: String): String {
        val scope = "https://www.googleapis.com/auth/gmail.readonly https://www.googleapis.com/auth/gmail.compose"
        return "https://accounts.google.com/o/oauth2/v2/auth?client_id=$clientId&redirect_uri=$redirectUri&response_type=code&scope=$scope&access_type=offline"
    }
}
