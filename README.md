# Edugate 🎓

**Edugate** is a modern, feature-rich Android application designed to facilitate virtual classrooms and real-time collaboration. Built entirely with **Jetpack Compose** and powered by **Agora** and **Firebase**, it provides a seamless experience for educators and students to connect, share screens, and manage educational resources.

## 🚀 Features

* **Real-time Video Conferencing:** High-quality video and audio communication powered by the **Agora RTC SDK**.
* **Live Screen Sharing:** Dedicated foreground service integration for broadcasting mobile screens during lectures.
* **Secure Authentication:** User sign-in and registration via **Firebase**, including Google Sign-In integration.
* **Document Management:**
    * Support for reading and processing Microsoft Office documents (Excel, Word) via **Apache POI**.
    * Professional PDF generation and manipulation using **OpenPDF**.
* **Cloud Storage:** Upload and share educational materials securely via **Firebase Storage**.
* **Real-time Data Sync:** Classroom metadata, chat, and permissions managed through **Firestore** and **Realtime Database**.
* **Modern UI:** A responsive, fluid user experience built from the ground up with **Jetpack Compose**.

## 🛠 Tech Stack

### Frontend & UI
* **Language:** Kotlin
* **UI Framework:** Jetpack Compose (Declarative UI)
* **Architecture:** MVVM (Model-View-ViewModel)
* **Image Loading:** Coil
* **Navigation:** Navigation Compose
* **Lifecycle:** `lifecycle-runtime-ktx` for efficient state management.

### Backend & Infrastructure
* **Firebase Ecosystem:**
    * **Authentication:** Google Sign-In & Email/Password.
    * **Cloud Firestore:** Scalable database for user and classroom metadata.
    * **Realtime Database:** Low-latency signaling for classroom states.
    * **Firebase Storage:** Secure hosting for PDFs and Office documents.

### Communication & Document Processing
* **Real-Time Communication:** Agora RTC Full SDK (v4.6.3)
* **Document Handling:** * Apache POI (for `.xlsx` and `.docx`).
    * OpenPDF / LibrePDF (for PDF generation).

## 📱 How It Works

1.  **Authentication:** Users log in using their Google account. The app stores and syncs profile details in Firestore.
2.  **Joining a Class:** Users enter a Room ID. The app initializes the `RtcEngine` from Agora to establish a low-latency video/audio connection.
3.  **Screen Sharing Service:** When screen sharing starts, the app launches `AgoraScreenSharingService`. This is a **Foreground Service** with the `mediaProjection` type, ensuring the stream continues even if the app is minimized to present documents.
4.  **Resource Handling:** A custom `FileProvider` (`EdugateFileProvider`) is implemented to securely handle document URI sharing across the Android system.
5.  **Lifecycle Awareness:** The app intelligently manages camera and microphone states based on the activity lifecycle to optimize battery performance.

## ⚙️ Installation & Setup

### Prerequisites
* **Android Studio Ladybug** or newer.
* A **Firebase Project** (with `google-services.json`).
* An **Agora Developer Account** (App ID and Token).

### Setup Steps
1.  **Clone the Repo:**
    ```bash
    git clone [https://github.com/yourusername/edugate.git](https://github.com/yourusername/edugate.git)
    ```
2.  **Firebase Configuration:**
    * Place your `google-services.json` in the `app/` directory.
    * Enable Authentication (Google), Firestore, and Storage in the Firebase Console.
3.  **Agora Configuration:**
    * Add your Agora App ID to the `local.properties` or your constants file.
4.  **Build & Run:**
    * Sync project with Gradle files and run on an Android device (API 24+ recommended).

## 🛡 License
Distributed under the MIT License. See `LICENSE` for more information.

---
Developed by [Junaid Shabir](https://junaidmir.vercel.app)
