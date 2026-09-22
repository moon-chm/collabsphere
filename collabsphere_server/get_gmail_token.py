import os
import glob
import sys

def main():
    try:
        from google_auth_oauthlib.flow import InstalledAppFlow
    except ImportError:
        print("\n[!] Missing dependency: google-auth-oauthlib")
        print("Please install it by running:")
        print("    pip install google-auth-oauthlib\n")
        sys.exit(1)

    # Scopes: gmail.send is minimal privilege (only allows sending email, cannot read/delete emails)
    SCOPES = ['https://www.googleapis.com/auth/gmail.send']

    candidates = glob.glob("credentials.json") + glob.glob("client_secret*.json")
    if not candidates:
        print("\n[!] ERROR: No 'credentials.json' or 'client_secret*.json' found in current directory.")
        print(f"Current directory: {os.getcwd()}")
        sys.exit(1)

    cred_file = candidates[0]
    print(f"\n[*] Found client credentials file: {cred_file}")
    print("[*] Launching browser for Google OAuth authorization...")
    print("[*] Please log in with your email (e.g. collabsphere.studio@gmail.com) and approve permission.\n")

    # access_type='offline' and prompt='consent' ensure Google generates a refresh token
    flow = InstalledAppFlow.from_client_secrets_file(cred_file, SCOPES)
    creds = flow.run_local_server(port=0, access_type='offline', prompt='consent')

    print("\n" + "=" * 65)
    print("SUCCESS! OAuth2 Credentials Obtained:")
    print("=" * 65)
    print(f"GMAIL_CLIENT_ID={creds.client_id}")
    print(f"GMAIL_CLIENT_SECRET={creds.client_secret}")
    print(f"GMAIL_REFRESH_TOKEN={creds.refresh_token}")
    print("=" * 65)
    print("\nNext steps:")
    print("1. Save these keys to 'local.properties' for local server execution.")
    print("2. Add these keys as Environment Variables in your Render Dashboard.")
    print("=" * 65 + "\n")

if __name__ == '__main__':
    main()
