import os

with open(r'e:\Collabsphere\collabsphere_server\src\main\kotlin\plugins\Routing.kt', 'r', encoding='utf-8') as f:
    r_text = f.read()

r_text = r_text.replace(
    'it[UserVerificationTable.attempts] = UserVerificationTable.attempts + 1',
    'it[UserVerificationTable.attempts] = org.jetbrains.exposed.sql.SqlExpressionBuilder.run { UserVerificationTable.attempts + 1 }'
)

r_text = r_text.replace(
    'it[PasswordResetTable.attempts] = PasswordResetTable.attempts + 1',
    'it[PasswordResetTable.attempts] = org.jetbrains.exposed.sql.SqlExpressionBuilder.run { PasswordResetTable.attempts + 1 }'
)

r_text = r_text.replace(
    'PasswordHasher.hash(request.newPassword)',
    'PasswordHasher.hash(request.newPassword ?: "")'
)

with open(r'e:\Collabsphere\collabsphere_server\src\main\kotlin\plugins\Routing.kt', 'w', encoding='utf-8') as f:
    f.write(r_text)

with open(r'e:\Collabsphere\collabsphere_server\src\main\kotlin\plugins\routes\WorkspaceRoutes.kt', 'r', encoding='utf-8') as f:
    w_text = f.read()

w_text = w_text.replace(
    'it[recipientId] = recipientRow[UsersTable.id]',
    'it[NotificationsTable.recipientId] = recipientRow[UsersTable.id]'
)

w_text = w_text.replace(
    'invRow[WorkspaceInvitationsTable.id]',
    'invRow!![WorkspaceInvitationsTable.id]'
)

with open(r'e:\Collabsphere\collabsphere_server\src\main\kotlin\plugins\routes\WorkspaceRoutes.kt', 'w', encoding='utf-8') as f:
    f.write(w_text)

print('Patched successfully!')
