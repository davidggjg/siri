# 🎙 Groq Voice Assistant - Android

עוזר קולי ל-Android המשתמש ב-Groq API עם Whisper STT + LLaMA LLM.

## Flow

```
לחיצה ארוכה על כפתור → הקלטה → Groq Whisper (STT) → Groq LLaMA (AI) → Android TTS
```

---

## ⚙️ הגדרה ראשונית

### 1. הכנס את ה-API Key שלך

פתח את הקובץ:
```
app/build.gradle.kts
```

מצא את השורה:
```kotlin
buildConfigField("String", "GROQ_API_KEY", "\"YOUR_GROQ_API_KEY_HERE\"")
```

החלף את `YOUR_GROQ_API_KEY_HERE` במפתח שלך מ- https://console.groq.com

---

## 🔨 בניית ה-APK

### דרישות:
- Android Studio Hedgehog (2023.1.1) ומעלה
- JDK 17
- Android SDK 34

### שלב 1 - פתח ב-Android Studio
```
File → Open → בחר את תיקיית GroqVoiceAssistant
```

### שלב 2 - Sync
לחץ על **Sync Now** כשמופיעה ההודעה למעלה.

### שלב 3 - בנה APK
```
Build → Build Bundle(s) / APK(s) → Build APK(s)
```

ה-APK יהיה ב:
```
app/build/outputs/apk/debug/app-debug.apk
```

### או דרך Command Line:
```bash
# Windows
gradlew.bat assembleDebug

# Mac/Linux
chmod +x gradlew
./gradlew assembleDebug
```

---

## 📱 התקנה על המכשיר

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## 📁 מבנה הפרויקט

```
app/src/main/java/com/groqvoice/assistant/
├── api/
│   └── GroqApiClient.kt      ← כל קריאות ה-API
├── audio/
│   └── AudioRecorder.kt      ← הקלטת מיקרופון
├── tts/
│   └── TtsManager.kt         ← Text-to-Speech
└── ui/
    ├── MainActivity.kt        ← מסך ראשי
    ├── MainViewModel.kt       ← לוגיקה עסקית
    └── ChatAdapter.kt         ← רשימת הודעות
```

---

## 🔧 שינוי מודל LLM

ב-`GroqApiClient.kt` שנה:
```kotlin
"model": "llama-3.3-70b-versatile"  // ← שנה כאן
```

מודלים זמינים ב-Groq:
- `llama-3.3-70b-versatile` - הכי חכם
- `llama-3.1-8b-instant` - הכי מהיר
- `mixtral-8x7b-32768` - הקשר ארוך (32K)

---

## 🌐 שינוי שפת STT

ב-`GroqApiClient.kt` שנה:
```kotlin
.addFormDataPart("language", "he")  // he=עברית, en=אנגלית
```
