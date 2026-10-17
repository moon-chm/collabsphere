package plugins

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

internal object PublicPages {
    const val CONTACT_EMAIL = "collabsphere.studio@gmail.com"
    const val POLICY_UPDATED = "16 October 2026"
    const val LOGO_PATH = "/brand/logo.png"

    val logoPng: ByteArray? by lazy { PublicPages::class.java.getResourceAsStream("/email/logo.png")?.use { it.readBytes() } }

    private fun page(title: String, body: String): String = """
        <!DOCTYPE html>
        <html lang="en">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <title>$title</title>
          <link rel="icon" type="image/png" href="$LOGO_PATH">
          <style>
            :root { color-scheme: light; }
            body { margin: 0; background: #FBF5EA; color: #1E2A5A; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; line-height: 1.6; }
            main { max-width: 760px; margin: 0 auto; padding: 32px 16px 56px; }
            .brand { display: flex; align-items: center; gap: 12px; font-family: Georgia, "Times New Roman", serif; font-weight: 700; font-size: 22px; margin-bottom: 24px; }
            .logo-ring { display: inline-flex; padding: 5px; border: 3px dotted #F28B6C; border-radius: 50%; }
            .logo { width: 44px; height: 44px; display: block; }
            .card { background: #FFFFFF; border: 2px solid #1E2A5A; border-radius: 24px; padding: 32px 28px; box-shadow: 4px 4px 0 #1E2A5A; }
            h1 { font-family: Georgia, "Times New Roman", serif; font-size: 30px; line-height: 1.2; margin: 0 0 10px; text-shadow: 2px 2px 0 #F28B6C; }
            h2 { font-size: 19px; margin: 28px 0 8px; }
            p, li { font-size: 16px; }
            ul { padding-left: 22px; }
            .muted { color: #5B6384; font-size: 14px; }
            a { color: #1E2A5A; font-weight: 700; text-decoration-thickness: 2px; text-decoration-color: #F28B6C; }
            nav { margin-top: 28px; font-size: 14px; color: #5B6384; }
            nav a { margin-right: 16px; }
            @media (max-width: 480px) { .card { padding: 24px 18px; border-radius: 20px; } h1 { font-size: 25px; } }
          </style>
        </head>
        <body>
          <main>
            <div class="brand"><span class="logo-ring"><img class="logo" src="$LOGO_PATH" alt="CollabSphere logo" width="44" height="44"></span>CollabSphere</div>
            <div class="card">
            $body
            </div>
            <nav><a href="/home">Home</a><a href="/privacy">Privacy policy</a><a href="mailto:$CONTACT_EMAIL">Contact</a></nav>
          </main>
        </body>
        </html>
    """.trimIndent()

    fun home(): String = page(
        "CollabSphere: team chat, tasks and code in one place",
        """
        <h1>Your team's workspace for chat, tasks and code</h1>
        <p>CollabSphere is an Android app that brings a team's conversations, tasks, notes, files and GitHub activity into one place, and keeps working when you're offline.</p>
        <ul>
          <li>Channels and direct messages with replies, reactions, polls, voice notes and files</li>
          <li>Tasks with due dates, checklists and team voting</li>
          <li>Focus mode, so teammates can deliver messages when you're free</li>
          <li>Automatic daily standups drafted from your tasks and GitHub</li>
          <li>GitHub integration for commits, pull requests and CI results</li>
        </ul>
        <p>Questions or support: <a href="mailto:$CONTACT_EMAIL">$CONTACT_EMAIL</a></p>
        """.trimIndent()
    )

    fun privacy(): String = page(
        "CollabSphere privacy policy",
        """
        <h1>Privacy policy</h1>
        <p class="muted">Last updated $POLICY_UPDATED</p>
        <p>This policy explains what CollabSphere collects, why, who processes it, and the choices you have. We don't sell your data and we don't show ads.</p>

        <h2>What we collect</h2>
        <ul>
          <li><strong>Account details:</strong> your name, email address, password (stored only as a one-way hash), and optional profile photo, bio, status and time zone.</li>
          <li><strong>Content you create:</strong> messages, direct messages, files, tasks, notes, polls, reactions and other workspace content.</li>
          <li><strong>Activity and status:</strong> online status, last seen time and focus status, shown to people in your workspaces according to your privacy settings.</li>
          <li><strong>Device data:</strong> a push notification token so we can deliver notifications to your device.</li>
          <li><strong>GitHub data (only if you connect GitHub):</strong> your GitHub account ID and username, access tokens (stored encrypted), and activity from repositories you link, such as commits, pull requests, issues and build results.</li>
        </ul>

        <h2>How we use it</h2>
        <ul>
          <li>To run CollabSphere: delivering messages, syncing your workspaces and showing your activity to your teammates.</li>
          <li>To send notifications and service emails, such as verification codes, password resets and workspace invitations.</li>
          <li>To keep the service secure and prevent abuse.</li>
        </ul>

        <h2>Who processes your data</h2>
        <p>We use these providers to run the service. Each one only receives what it needs for its job:</p>
        <ul>
          <li><strong>Render</strong> hosts our servers and database.</li>
          <li><strong>Cloudinary</strong> stores uploaded files, voice notes and profile photos.</li>
          <li><strong>Google Firebase Cloud Messaging</strong> delivers push notifications.</li>
          <li><strong>Google Gmail API</strong> sends service emails from CollabSphere's own email account.</li>
          <li><strong>GitHub</strong>, only if you choose to connect it.</li>
        </ul>

        <h2>Google API services</h2>
        <p>CollabSphere uses the Gmail API only to send service emails from its own account. It does not access, read or store the Gmail data of anyone who uses CollabSphere. CollabSphere's use of information received from Google APIs adheres to the <a href="https://developers.google.com/terms/api-services-user-data-policy">Google API Services User Data Policy</a>, including the Limited Use requirements.</p>

        <h2>Who can see your content</h2>
        <p>Messages, files and tasks are visible to the members of the workspace or conversation they belong to. You can block people, hide your email, online status and last seen time, and limit your profile to workspace members in the app's privacy settings.</p>

        <h2>Keeping and deleting data</h2>
        <p>We keep your data while your account is active. You can delete your account at any time in the app under <strong>Profile &rarr; Delete account</strong>. You can also email us to ask for a copy of your data or for it to be deleted. Some records may remain briefly in backups before they are overwritten.</p>

        <h2>Security</h2>
        <p>Data is sent over encrypted connections, passwords are hashed, and third-party access tokens are encrypted at rest. No system is perfectly secure, but we work to protect your information.</p>

        <h2>Children</h2>
        <p>CollabSphere is not intended for children under 13, and we don't knowingly collect their data.</p>

        <h2>Changes</h2>
        <p>If we change this policy we'll update the date above, and for significant changes we'll let you know in the app.</p>

        <h2>Contact</h2>
        <p>Questions or requests: <a href="mailto:$CONTACT_EMAIL">$CONTACT_EMAIL</a></p>
        """.trimIndent()
    )
}

internal fun Route.publicPages() {
    get("/home") {
        call.respondText(PublicPages.home(), ContentType.Text.Html, HttpStatusCode.OK)
    }
    get("/privacy") {
        call.respondText(PublicPages.privacy(), ContentType.Text.Html, HttpStatusCode.OK)
    }
    get(PublicPages.LOGO_PATH) {
        val logo = PublicPages.logoPng
        if (logo == null) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            call.response.header(HttpHeaders.CacheControl, "public, max-age=86400")
            call.respondBytes(logo, ContentType.Image.PNG)
        }
    }
}
