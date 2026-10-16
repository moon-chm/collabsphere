package com.collabsphere.util

data class EmailContent(val subject: String, val html: String, val text: String)

object EmailTemplates {

    private const val CORAL = "#F0633D"
    private const val INDIGO = "#4C55C4"
    private const val MINT = "#4E9E78"
    private const val INK = "#2C2A28"
    private const val MUTED = "#6F6A64"
    private const val PAGE = "#ECE6DA"
    private const val CARD = "#FDFBF7"
    private const val WELL_BASE = "#EFE9DF"
    private const val CORAL_LIGHT = "#FF8A6B"
    private const val CORAL_DARK = "#C24A26"
    private const val MINT_LIGHT = "#6CC197"
    private const val MINT_DARK = "#3A7C5D"

    private const val CARD_STYLE =
        "background-color: #FDFBF7; background-image: linear-gradient(180deg, #FFFFFF 0%, #F8F4EC 100%); " +
            "border: 1px solid #DDD5C7; border-top-color: #FFFFFF; border-left-color: #F1ECE3; " +
            "border-bottom-color: #C9BFAE; border-right-color: #D8CFC0; border-radius: 22px; " +
            "box-shadow: 0 18px 36px -12px rgba(60,44,30,0.22), 0 4px 10px rgba(60,44,30,0.08), inset 0 1px 0 #FFFFFF;"

    private const val WELL_STYLE =
        "background-color: #EFE9DF; background-image: linear-gradient(180deg, #E7E0D3 0%, #F4EFE7 100%); " +
            "border: 1px solid #D8CFC0; border-top-color: #C9BFAE; border-left-color: #D3C9B9; " +
            "border-bottom-color: #FFFFFF; border-right-color: #FAF7F2; border-radius: 16px; " +
            "box-shadow: inset 0 3px 6px rgba(60,44,30,0.16), inset 0 -1px 0 #FFFFFF;"
    private const val FONT = "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif"

    fun escape(value: String): String = buildString(value.length) {
        value.forEach { ch ->
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(ch)
            }
        }
    }

    private fun oneLine(value: String): String = value.replace(Regex("[\\r\\n\\t]+"), " ").trim()

    fun verification(otp: String, minutes: Int): EmailContent = codeEmail(
        subject = "$otp is your CollabSphere verification code",
        preheader = "Enter this code to finish creating your account. It expires in $minutes minutes.",
        heading = "Confirm your email",
        intro = "Welcome to CollabSphere! Enter this code in the app to finish setting up your account.",
        code = otp,
        accent = CORAL,
        minutes = minutes,
        ignoreNote = "Didn't sign up? You can safely ignore this email and no account will be created."
    )

    fun emailChange(otp: String, newEmail: String, minutes: Int): EmailContent = codeEmail(
        subject = "$otp is your code to confirm your new email",
        preheader = "Confirm ${oneLine(newEmail)} as your CollabSphere email. The code expires in $minutes minutes.",
        heading = "Confirm your new email",
        intro = "You asked to change your CollabSphere email to <strong>${escape(oneLine(newEmail))}</strong>. Enter this code in the app to confirm it.",
        textIntro = "You asked to change your CollabSphere email to ${oneLine(newEmail)}. Enter this code in the app to confirm it.",
        code = otp,
        accent = INDIGO,
        minutes = minutes,
        ignoreNote = "Didn't ask for this? Ignore this email and your current address stays in place."
    )

    fun passwordReset(otp: String, minutes: Int): EmailContent = codeEmail(
        subject = "$otp is your CollabSphere password reset code",
        preheader = "Use this code to set a new password. It expires in $minutes minutes.",
        heading = "Reset your password",
        intro = "We got a request to reset your CollabSphere password. Enter this code in the app to choose a new one.",
        code = otp,
        accent = INDIGO,
        minutes = minutes,
        ignoreNote = "Didn't request this? Your password hasn't changed, and you can ignore this email."
    )

    fun workspaceInvitation(workspaceName: String, inviterName: String, inviteCode: String, validDays: Int): EmailContent {
        val workspace = oneLine(workspaceName).take(120)
        val inviter = oneLine(inviterName).take(120)
        val subject = "$inviter invited you to $workspace on CollabSphere"
        val steps = listOf(
            "Open the CollabSphere app and sign in (or create an account with this email).",
            "On your Dashboard, tap <strong>Join with Code</strong>.",
            "Enter the code above and you're in."
        )
        val body = """
            ${paragraph("<strong>${escape(inviter)}</strong> wants you to collaborate in <strong>${escape(workspace)}</strong>: chat, share tasks and ship together.")}
            ${codeBox("Your invite code", inviteCode, MINT)}
            <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="margin: 4px 0 8px 0;">
              ${steps.mapIndexed { i, step -> stepRow(i + 1, step) }.joinToString("\n")}
            </table>
            ${note("This invite expires in $validDays days. If you weren't expecting it, you can ignore this email.")}
        """.trimIndent()
        val text = buildString {
            appendLine("$inviter invited you to $workspace on CollabSphere.")
            appendLine()
            appendLine("Your invite code: $inviteCode")
            appendLine()
            appendLine("1. Open the CollabSphere app and sign in (or create an account with this email).")
            appendLine("2. On your Dashboard, tap Join with Code.")
            appendLine("3. Enter the code above and you're in.")
            appendLine()
            appendLine("This invite expires in $validDays days. If you weren't expecting it, you can ignore this email.")
            append(textFooter())
        }
        return EmailContent(
            subject = subject,
            html = layout("Your invite code is $inviteCode. It's valid for $validDays days.", "You're invited to ${escape(workspace)}", body),
            text = text
        )
    }

    private fun codeEmail(
        subject: String,
        preheader: String,
        heading: String,
        intro: String,
        textIntro: String = intro,
        code: String,
        accent: String,
        minutes: Int,
        ignoreNote: String
    ): EmailContent {
        val body = """
            ${paragraph(intro)}
            ${codeBox("Your code", code, accent)}
            ${note("This code expires in <strong>$minutes minutes</strong> and can only be used once. Never share it. CollabSphere will never ask you for it.")}
            ${note(ignoreNote)}
        """.trimIndent()
        val text = buildString {
            appendLine(heading)
            appendLine()
            appendLine(textIntro)
            appendLine()
            appendLine("Your code: $code")
            appendLine()
            appendLine("This code expires in $minutes minutes and can only be used once. Never share it.")
            appendLine(ignoreNote)
            append(textFooter())
        }
        return EmailContent(subject, layout(preheader, escape(heading), body), text)
    }

    private fun paragraph(html: String) =
        """<p style="margin: 0 0 20px 0; font-family: $FONT; font-size: 16px; line-height: 24px; color: $INK;">$html</p>"""

    private fun note(html: String) =
        """<p style="margin: 0 0 12px 0; font-family: $FONT; font-size: 13px; line-height: 20px; color: $MUTED;">$html</p>"""

    private fun raisedBadge(base: String, light: String, dark: String): String =
        "background-color: $base; background-image: linear-gradient(180deg, $light 0%, $base 100%); " +
            "border: 1px solid $dark; border-top-color: $light; border-bottom: 2px solid $dark; " +
            "box-shadow: 0 3px 6px rgba(60,44,30,0.25), inset 0 1px 0 rgba(255,255,255,0.45); " +
            "text-shadow: 0 -1px 0 rgba(0,0,0,0.18);"

    private fun codeBox(label: String, code: String, accent: String) = """
        <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="margin: 4px 0 24px 0;">
          <tr>
            <td align="center" bgcolor="$WELL_BASE" class="cs-well" style="$WELL_STYLE padding: 18px 12px 22px 12px;">
              <div style="width: 36px; height: 4px; border-radius: 2px; background-color: $accent; margin: 0 auto 10px auto; font-size: 0; line-height: 0;">&nbsp;</div>
              <div style="font-family: $FONT; font-size: 12px; line-height: 16px; font-weight: 700; letter-spacing: 1.5px; text-transform: uppercase; color: $MUTED; margin-bottom: 8px; text-shadow: 0 1px 0 #FFFFFF;">${escape(label)}</div>
              <div class="cs-code" style="font-family: 'SF Mono', Menlo, Consolas, 'Courier New', monospace; font-size: 34px; line-height: 40px; font-weight: 800; letter-spacing: 8px; color: $INK; text-shadow: 0 1px 0 #FFFFFF;">${escape(code)}</div>
            </td>
          </tr>
        </table>
    """.trimIndent()

    private fun stepRow(number: Int, html: String) = """
        <tr>
          <td width="36" valign="top" style="padding: 0 0 14px 0;">
            <div style="width: 26px; height: 26px; border-radius: 13px; ${raisedBadge(MINT, MINT_LIGHT, MINT_DARK)} color: #FFFFFF; font-family: $FONT; font-size: 13px; line-height: 24px; font-weight: 800; text-align: center;">$number</div>
          </td>
          <td valign="top" style="padding: 3px 0 14px 0; font-family: $FONT; font-size: 15px; line-height: 22px; color: $INK;">$html</td>
        </tr>
    """.trimIndent()

    private fun textFooter() = "\n\n--\nCollabSphere: your team's workspace for chat, tasks and code.\nThis is an automated message, so replies aren't monitored."

    fun layout(preheader: String, headingHtml: String, bodyHtml: String): String = """
        <!DOCTYPE html>
        <html lang="en">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <meta name="color-scheme" content="light">
          <meta name="supported-color-schemes" content="light">
          <title>CollabSphere</title>
          <style>
            @media only screen and (max-width: 480px) {
              .cs-outer { padding: 20px 10px !important; }
              .cs-card { padding: 28px 20px !important; border-radius: 18px !important; }
              .cs-code { font-size: 28px !important; letter-spacing: 6px !important; }
            }
          </style>
        </head>
        <body style="margin: 0; padding: 0; background-color: $PAGE;">
          <div style="display: none; max-height: 0; overflow: hidden; opacity: 0; color: transparent;">${escape(oneLine(preheader))}</div>
          <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" bgcolor="$PAGE" style="background-color: $PAGE; background-image: linear-gradient(180deg, #F3EEE5 0%, $PAGE 100%);">
            <tr>
              <td align="center" class="cs-outer" style="padding: 36px 16px;">
                <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="max-width: 560px;">
                  <tr>
                    <td align="center" style="padding: 0 0 22px 0;">
                      <table role="presentation" cellpadding="0" cellspacing="0" border="0">
                        <tr>
                          <td width="38" height="38" align="center" bgcolor="$CORAL" style="border-radius: 11px; ${raisedBadge(CORAL, CORAL_LIGHT, CORAL_DARK)} font-family: $FONT; font-size: 20px; line-height: 36px; font-weight: 800; color: #FFFFFF;">C</td>
                          <td style="padding-left: 10px; font-family: $FONT; font-size: 20px; font-weight: 800; color: $INK; text-shadow: 0 1px 0 #FFFFFF;">CollabSphere</td>
                        </tr>
                      </table>
                    </td>
                  </tr>
                  <tr>
                    <td bgcolor="$CARD" class="cs-card" style="$CARD_STYLE padding: 36px 32px;">
                      <h1 style="margin: 0 0 16px 0; font-family: $FONT; font-size: 24px; line-height: 32px; font-weight: 800; color: $INK; text-shadow: 0 1px 0 #FFFFFF;">$headingHtml</h1>
                      $bodyHtml
                    </td>
                  </tr>
                  <tr>
                    <td align="center" style="padding: 22px 12px 0 12px; font-family: $FONT; font-size: 12px; line-height: 18px; color: $MUTED; text-shadow: 0 1px 0 rgba(255,255,255,0.8);">
                      CollabSphere: your team's workspace for chat, tasks and code.<br>
                      This is an automated message, so replies aren't monitored.
                    </td>
                  </tr>
                </table>
              </td>
            </tr>
          </table>
        </body>
        </html>
    """.trimIndent()
}
