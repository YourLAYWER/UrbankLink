# UrbanLink - Commuter App

A native Android application designed for commuters in Gqeberha to calculate optimal transit routes, purchase digital passes, and manage their daily travel with offline capabilities.

## 📱 Key Features

*   **Real-Time Route Preview:** Seamlessly calculates travel time, arrival times, and transfer locations using the UrbanLink API.
*   **Offline Ticketing:** Purchases are saved locally using a Room Database, allowing commuters to access their active QR tickets even without a network connection.
*   **Dynamic QR Generation:** Integrates the ZXing library to instantly generate scannable digital tickets for transit validators.
*   **Material Design UI:** Features a modern, dark-themed interface with custom bottom sheets, dynamic chip groups, and smooth vector animations.
*   **Transaction History:** Allows users to view past trips and pull up historical tickets instantly.

## 🛠️ Tech Stack

*   **Language:** Java
*   **Platform:** Android SDK
*   **Networking:** Retrofit2 & Gson
*   **Local Storage:** Room Persistence Library & SharedPreferences
*   **QR Generation:** ZXing Android Embedded
*   **UI Components:** Google Material Design 3

## ⚙️ Getting Started

### Prerequisites
*   Android Studio (Jellyfish or newer recommended)
*   Minimum SDK: API 26 (Android 8.0)
*   The UrbanLink Backend API must be running locally or hosted.

### Installation & Setup

1. **Clone the repository:**
   ```bash
   git clone [https://github.com/YourLAWYER/UrbanLink-Android.git](https://github.com/yourusername/UrbanLink-Android.git)
