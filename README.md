# আমার হিসাব (Android)

বেতন, দৈনিক হাতখরচ, ওভারটাইম, ছুটি আর জমা খাতার অফলাইন হিসাব। পুরো UI `app/src/main/assets/index.html`-এ, `MainActivity.java` শুধু WebView চালায় আর ডেটা অ্যাপের internal storage-এ (`hisab.json`) সেভ করে।

## APK বানানো (GitHub Actions, কিছু ইনস্টল লাগবে না)

1. GitHub-এ নতুন একটা **private** রিপো খুলুন।
2. এই `android` ফোল্ডারের ভেতরের সবকিছু (`.github` ফোল্ডারসহ) রিপোর রুটে push করুন:
   ```sh
   cd android
   git init && git add . && git commit -m "Amar Hisab app"
   git branch -M main
   git remote add origin git@github.com:<user>/amar-hisab.git
   git push -u origin main
   ```
3. রিপোর **Actions** ট্যাবে "Build APK" শেষ হলে (২-৪ মিনিট) নিচে **amar-hisab-apk** ডাউনলোড করুন। zip খুললে `app-release.apk` পাবেন।
4. APK ভাইয়ের ফোনে পাঠিয়ে ইনস্টল করুন ("Unknown sources / এই উৎস থেকে ইনস্টল" অনুমতি দিতে হবে)।

## লোকালি বানাতে চাইলে

Android Studio দিয়ে `android` ফোল্ডার খুলুন → Build → Build APK(s)। অথবা JDK 17 আর Gradle 8.7+ থাকলে `gradle assembleRelease`।

## প্রথমবার চালানোর পর

- **সেটিংস** ট্যাব → পিন `1234` → সাথে সাথে পিন বদলে দিন।
- হিসাব শুরুর মাস আর বেতনের নিয়ম সেভ করুন।
- মাসে একবার **ব্যাকআপ নিন → শেয়ার করুন** দিয়ে WhatsApp/Drive-এ রেখে দিন। অ্যাপ আনইনস্টল করলে ডেটা মুছে যায়।

## আপডেট করা

APK আপডেট করতে `versionCode` বাড়িয়ে নতুন বিল্ড ইনস্টল করুন, ডেটা থাকবে। একই debug keystore দিয়ে সাইন না হলে Android আপডেট নেবে না, আর GitHub Actions প্রতিবার নতুন debug key বানায়। তাই দ্বিতীয় আপডেটের আগে নিজের keystore বানিয়ে `signingConfigs`-এ দিন (keystore-টা GitHub Secrets-এ রাখুন), নাহলে আপডেটের আগে ব্যাকআপ নিয়ে আনইনস্টল-ইনস্টল করতে হবে।
