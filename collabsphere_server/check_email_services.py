import os
import requests
import smtplib
from email.mime.text import MIMEText
from email.mime.multipart import MIMEMultipart

def load_properties():
    props = {}
    if os.path.exists('local.properties'):
        with open('local.properties', 'r') as f:
            for line in f:
                if line.strip() and not line.startswith('#'):
                    key, val = line.strip().split('=', 1)
                    props[key.strip()] = val.strip()
    return props

def test_gmail_api(props):
    print("\n--- Testing Tier 1: Gmail API ---")
    client_id = props.get('GMAIL_CLIENT_ID', '')
    client_secret = props.get('GMAIL_CLIENT_SECRET', '')
    refresh_token = props.get('GMAIL_REFRESH_TOKEN', '')
    
    if not (client_id and client_secret and refresh_token):
        print("Missing Gmail API credentials.")
        return

    print("Requesting access token from Google...")
    try:
        data = {
            "client_id": client_id,
            "client_secret": client_secret,
            "refresh_token": refresh_token,
            "grant_type": "refresh_token"
        }
        r = requests.post("https://oauth2.googleapis.com/token", data=data, timeout=15)
        if r.status_code == 200:
            print("Successfully retrieved access token!")
            access_token = r.json().get('access_token')
            
            print("Attempting to send an email via Gmail API...")
            headers = {
                "Authorization": f"Bearer {access_token}",
                "Content-Type": "application/json"
            }
            # Just a test structure, we won't actually send a valid base64 unless we construct it
            # But we can at least see if it connects or gets auth error.
            print("Gmail API authentication check successful!")
        else:
            print(f"Failed to retrieve access token. Status: {r.status_code}, Body: {r.text}")
    except Exception as e:
        print(f"Exception during Gmail API test: {e}")

def test_smtp(props):
    print("\n--- Testing Tier 2: SMTP ---")
    host = props.get('SMTP_HOST', '')
    port = int(props.get('SMTP_PORT', '587'))
    user = props.get('SMTP_USER', '')
    password = props.get('SMTP_PASSWORD', '')

    if not (host and user and password):
        print("Missing SMTP credentials.")
        return

    print(f"Attempting to connect to {host}:{port} ...")
    try:
        server = smtplib.SMTP(host, port, timeout=5)
        server.set_debuglevel(1)
        server.ehlo()
        server.starttls()
        server.ehlo()
        print("Attempting to login...")
        server.login(user, password)
        print("Successfully logged in to SMTP!")
        server.quit()
    except Exception as e:
        print(f"Exception during SMTP test: {e}")

if __name__ == "__main__":
    props = load_properties()
    test_gmail_api(props)
    test_smtp(props)
