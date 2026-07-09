# CollabSphere

CollabSphere is a premium, local-first team collaboration platform consisting of a **Jetpack Compose Android Client** and a **Ktor Backend Server** backed by **PostgreSQL**. It supports real-time communication, workspace management, task tracking, notes, and file sharing with seamless offline support.

---

## 🏗️ Project Architecture

```mermaid
graph TD
    subgraph "Android Client (Local-First)"
        UI[Jetpack Compose UI] --> VM[ViewModels]
        VM --> Repo[Repositories]
        Repo --> Room[(Room Local SQLite)]
        Repo --> WorkMgr[WorkManager Sync Queue]
        Repo --> KtorClient[Ktor Client REST/WS]
    end

    subgraph "Backend Services"
        KtorClient -->|REST / WebSocket| KtorServer[Ktor Backend Server]
        WorkMgr -->|REST| KtorServer
        KtorServer -->|Exposed ORM| PostgreSQL[(PostgreSQL Database)]
        KtorServer -->|Local File System| Disk[Uploaded Files Directory]
    end
end
```

---

## ✨ Features

### 1. 🔐 User Authentication & Cache FALLBACK
* **Precedence-based Login**: Authenticates online first, caching credentials if successful. Falls back to Room local cache if offline, ensuring uninterrupted login.
* **Profile Management**: Instant profile update with real-time UI propagation across Compose screens.

### 2. 🏢 Workspace Management
* **Custom Workspaces**: Create private workspaces, configure owners, and invite teammates via email.
* **Cascading Swaps**: Generates offline-safe temporary IDs when created offline. Automatically resolves and swaps temporary workspace IDs to server-generated IDs once online, propagating updates to all associated channels, messages, files, and tasks.

### 3. 💬 Real-Time Messaging (Channels & DMs)
* **Real-time DMs**: WebSockets-driven direct messaging with active connection monitoring in background sync workers.
* **Channels**: Dedicated communication channels per workspace with offline queuing.
* **DMs Cache Integrity**: Random negative ID generation for unsent offline DMs to avoid cache collisions. Auto-matches content & timestamp on server acknowledgment to delete temporary local copies and insert the official server DM record.

### 4. 📋 Task Management
* **Workspace Tracker**: Create and delegate tasks to team members within a workspace.
* **Task Statuses**: Track task flow via `To Do`, `In Progress`, and `Done` states. Syncs automatically in the background.

### 5. 📝 Workspace Notes
* **Local Notes Cache**: Document ideas and meeting notes locally. Synchronizes via WorkManager in the background once internet connectivity is restored.

### 6. 📁 File Uploads & Downloads
* **Multipart File Transfer**: Upload document attachments directly to the server.
* **Offline Upload Queue**: Queues upload operations and resolves local-to-remote file IDs for seamless management.

---

## 🛠️ Tech Stack

### Android Client
* **UI**: Jetpack Compose, Material 3
* **Local Database**: Room DB (SQLite)
* **DI**: Koin
* **HTTP & Sockets**: Ktor Client, OkHttp
* **Background Worker**: Android Jetpack WorkManager
* **Preferences**: DataStore Preferences

### Backend Server
* **Engine**: Ktor (Netty)
* **Database Access**: Kotlin Exposed ORM, HikariCP
* **Database**: PostgreSQL
* **Serialization**: kotlinx.serialization (JSON)
* **Real-time**: Ktor WebSockets

---

## 🚀 Running Locally

### 1. Database Setup
Ensure you have a PostgreSQL instance running locally. Create a database named `Collabsphere`:
```sql
CREATE DATABASE "Collabsphere";
```

### 2. Run Ktor Server
Configure connection details in `DatabaseFactory.kt` and run:
```bash
cd collabpshere_server
./gradlew run
```

### 3. Run Android Client
Open `Rohit_Project_Challlange` in Android Studio and run the app on an emulator or physical device. Ensure your device is on the same local network subnet as your laptop.
